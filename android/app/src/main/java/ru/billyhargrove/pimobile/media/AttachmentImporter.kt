package ru.billyhargrove.pimobile.media

import java.io.IOException
import ru.billyhargrove.pimobile.core.ImageGuard

/** Background batch ownership; accepted prefix transfers once, cancellation releases only staged thumbnails. */
internal class AttachmentImporter<Key>(private val prepare: (Key, Long, AttachmentRead) -> Attachment,
    private val work: (() -> Unit) -> Unit, private val deliver: (() -> Unit) -> Unit,
    private val discard: (Attachment) -> Unit = { it.thumbnail()?.recycle() }) {
    data class Batch(val values: List<Attachment>, val error: String? = null)
    interface Control { fun cancel() }
    fun start(keys: List<Key>, budget: Long, done: (Batch) -> Boolean): Control {
        require(keys.size <= ImageGuard.MAX_IMAGES)
        require(budget in 0..ImageGuard.MAX_TOTAL_BYTES)
        val job = Job(done)
        val selected = keys.toList()
        try { work { import(job, selected, budget) } } catch (cause: Exception) { job.complete(Batch(emptyList(), cause.message ?: "could not read file")) }
        return job
    }
    private inner class Job(val done: (Batch) -> Boolean) : Control {
        val reader = AttachmentRead()
        private var cancelled = false; private var delivered = false; private var started = false
        private var pending: Batch? = null
        @Synchronized fun begin(): Boolean {
            if (cancelled || started) return false
            started = true; return true
        }
        override fun cancel() {
            val old = synchronized(this) {
                if (cancelled || delivered) return
                cancelled = true; pending.also { pending = null }
            }
            reader.cancel(); old?.values?.forEach(discard)
        }
        fun complete(batch: Batch) {
            val scheduled = synchronized(this) { if (cancelled) false else { pending = batch; true } }
            if (!scheduled) { batch.values.forEach(discard); return }
            try { deliver {
                val result = synchronized(this) {
                    if (cancelled || delivered) null else pending.also { pending = null; delivered = true }
                } ?: return@deliver
                val accepted = try { done(result) } catch (_: Exception) { false }
                if (!accepted) result.values.forEach(discard)
            } } catch (_: Exception) { cancel() }
        }
    }
    private fun import(job: Job, keys: List<Key>, initialBudget: Long) {
        if (!job.begin()) return
        val values = mutableListOf<Attachment>(); var budget = initialBudget; var failure: String? = null
        var transferred = false
        try {
            for (key in keys) try {
                job.reader.check()
                if (budget <= 0) throw IOException("Attachments must total no more than 10 MB")
                val value = prepare(key, budget, job.reader)
                // Enforce the shared wire budget even if a platform importer is faulty.
                if (value.payload().size() <= 0 || value.payload().size() > budget) {
                    discard(value); throw IOException("Attachments must total no more than 10 MB")
                }
                values.add(value); budget -= value.payload().size()
                job.reader.check()
            } catch (cause: Exception) { failure = cause.message ?: "could not read file"; break }
            job.complete(Batch(values.toList(), failure)); transferred = true
        } finally { if (!transferred) values.forEach(discard) }
    }
}
