package ru.billyhargrove.pimobile.net

import java.io.File

/** Owns one PCM file from start through worker cleanup; cancellation never touches another call. */
internal class SpeechTranscriber(private val open: (String, String, File) -> HttpApi.Transcription,
    private val work: (() -> Unit) -> Unit, private val deliver: (() -> Unit) -> Unit) {
    interface Control { fun cancel() }
    constructor(api: HttpApi) : this(api::transcription, { AppExecutors.io().execute(it) }, { AppExecutors.main(it) })

    fun start(url: String, token: String, file: File, done: (Result<String>) -> Unit): Control {
        val job = Job(file, done)
        try { work { transcribe(job, url, token) } } catch (cause: Exception) { job.complete(Result.failure(cause)) }
        return job
    }
    private inner class Job(val file: File, val done: (Result<String>) -> Unit) : Control {
        private var started = false; private var cancelled = false; private var delivered = false; private var cleaned = false
        private var call: HttpApi.Transcription? = null
        @Synchronized fun begin(): Boolean {
            if (cancelled) return false
            started = true; return true
        }
        fun bind(value: HttpApi.Transcription): Boolean {
            val active = synchronized(this) { if (cancelled) false else { call = value; true } }
            if (!active) try { value.cancel() } catch (_: Exception) {}
            return active
        }
        override fun cancel() {
            val current = synchronized(this) {
                if (cancelled || delivered) return
                cancelled = true
                if (!started) cleanup()
                call.also { call = null }
            }
            try { current?.cancel() } catch (_: Exception) {}
        }
        @Synchronized fun cleanup() { if (!cleaned) { cleaned = true; file.delete() } }
        fun complete(result: Result<String>) {
            cleanup()
            synchronized(this) { call = null; if (cancelled) return }
            deliver {
                val active = synchronized(this) { if (cancelled || delivered) false else { delivered = true; true } }
                if (active) done(result)
            }
        }
    }
    private fun transcribe(job: Job, url: String, token: String) {
        if (!job.begin()) return
        val result = try {
            val call = open(url, token, job.file)
            if (job.bind(call)) Result.success(call.execute()) else Result.failure(java.io.IOException("Cancelled"))
        } catch (cause: Exception) { Result.failure(cause) }
        finally { job.cleanup() }
        job.complete(result)
    }
}
