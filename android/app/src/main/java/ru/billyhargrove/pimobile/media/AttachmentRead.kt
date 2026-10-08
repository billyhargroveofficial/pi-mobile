package ru.billyhargrove.pimobile.media

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import ru.billyhargrove.pimobile.core.ImageGuard

/** One batch's bounded reads. Cancel closes its active stream, including a blocked read. */
internal class AttachmentRead {
    private class Lease(val input: InputStream) {
        private val closed = AtomicBoolean()
        fun close() { if (closed.compareAndSet(false, true)) try { input.close() } catch (_: Exception) {} }
    }
    private var cancelled = false
    private var current: Lease? = null
    @Synchronized fun check() { if (cancelled) throw IOException("Cancelled") }
    fun cancel() {
        val old = synchronized(this) { cancelled = true; current.also { current = null } }
        old?.close()
    }
    fun read(cap: Long, exceeded: String, open: () -> InputStream?): ByteArray {
        if (cap <= 0 || cap > ImageGuard.MAX_TOTAL_BYTES + 1) throw IOException("Attachments must total no more than 10 MB")
        check()
        val lease = Lease(open() ?: throw IOException("Could not open file"))
        try {
            synchronized(this) { check(); check(current == null); current = lease }
            val output = ByteArrayOutputStream(); val buffer = ByteArray(16 * 1024); var total = 0L
            while (true) {
                check()
                val count = lease.input.read(buffer, 0, minOf(buffer.size.toLong(), cap - total + 1).toInt())
                check()
                if (count == -1) break
                if (count == 0) {
                    val byte = lease.input.read(); check()
                    if (byte == -1) break
                    total++
                    if (total > cap) throw IOException(exceeded)
                    output.write(byte); continue
                }
                total += count
                if (total > cap) throw IOException(exceeded)
                output.write(buffer, 0, count)
            }
            if (total == 0L) throw IOException("Empty file")
            return output.toByteArray()
        } finally {
            synchronized(this) { if (current === lease) current = null }
            lease.close()
        }
    }
}
