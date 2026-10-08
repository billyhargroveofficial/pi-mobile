package ru.billyhargrove.pimobile.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.io.*
import ru.billyhargrove.pimobile.core.PcmAudio

/** One private stream/source per capture. Completion transfers file ownership exactly once. */
internal class PcmRecorder(private val cacheDir: File, private val open: () -> Source,
    private val work: (() -> Unit) -> Unit, private val deliver: (() -> Unit) -> Unit) {
    interface Source {
        fun start()
        fun read(buffer: ByteArray, count: Int): Int
        fun stop()
        fun release()
    }
    interface Control { fun finish(); fun cancel() }
    constructor(context: Context) : this(context.cacheDir, { androidSource(context) },
        { Thread(it, "pi-mobile-microphone").start() }, mainDispatcher())

    fun start(meter: (PcmAudio.Meter) -> Unit, done: (Result<File>) -> Unit): Control {
        val dir = File(cacheDir, "dictation")
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) throw IOException("Cannot create recording file")
        val file = File.createTempFile("recording-", ".pcm", dir)
        var source: Source? = null
        try {
            source = open(); source.start()
            val job = Capture(source, file, meter, done)
            work { capture(job) }
            return job
        } catch (cause: Exception) {
            try { source?.stop() } catch (_: Exception) {}
            try { source?.release() } catch (_: Exception) {}
            file.delete(); throw cause
        }
    }
    private inner class Capture(val source: Source, val file: File,
        val meter: (PcmAudio.Meter) -> Unit, val done: (Result<File>) -> Unit) : Control {
        @Volatile var running = true; private var cancelled = false; private var transferred = false
        private var finished = false; private var queued = false; private var latest: PcmAudio.Meter? = null
        @Synchronized override fun finish() {
            if (!running) return
            running = false; try { source.stop() } catch (_: Exception) {}
        }
        @Synchronized override fun cancel() {
            if (transferred) return
            cancelled = true; finish()
            if (finished) file.delete()
        }
        @Synchronized fun publish(value: PcmAudio.Meter) {
            if (cancelled || transferred) return
            latest = value
            if (queued) return
            queued = true
            deliver {
                val next = synchronized(this) { queued = false; if (cancelled || transferred) null else latest.also { latest = null } }
                if (next != null) meter(next)
            }
        }
        @Synchronized fun complete(result: Result<File>) {
            finished = true
            if (cancelled) { file.delete(); return }
            if (result.isFailure) file.delete()
            deliver {
                val accepted = synchronized(this) {
                    if (cancelled) { file.delete(); false } else { transferred = result.isSuccess; true }
                }
                if (accepted) done(result)
            }
        }
    }
    private fun capture(job: Capture) {
        var total = 0
        val result = try {
            BufferedOutputStream(FileOutputStream(job.file)).use { output ->
                val buffer = ByteArray(PcmAudio.CHUNK_BYTES)
                while (job.running && total < PcmAudio.MAX_BYTES) {
                    val requested = minOf(buffer.size, PcmAudio.MAX_BYTES - total)
                    val count = job.source.read(buffer, requested)
                    if (count < 0) { if (job.running) throw IOException("Could not capture microphone audio"); break }
                    if (count == 0) { Thread.sleep(10); continue }
                    if (count > requested || count % 2 != 0) throw IOException("Invalid PCM16 capture")
                    output.write(buffer, 0, count); total += count
                    job.publish(PcmAudio.meter(buffer, count, total))
                }
            }
            if (!PcmAudio.validSize(total.toLong())) throw IOException("Recording is too short")
            Result.success(job.file)
        } catch (cause: Exception) { Result.failure(cause) }
        finally {
            job.finish(); try { job.source.release() } catch (_: Exception) {}
        }
        job.complete(result)
    }
    private companion object {
        fun mainDispatcher(): (() -> Unit) -> Unit {
            val main = Handler(Looper.getMainLooper())
            return { main.post(it); Unit }
        }
        fun androidSource(context: Context): Source {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                throw SecurityException("Microphone permission is required for dictation")
            val minimum = AudioRecord.getMinBufferSize(PcmAudio.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minimum <= 0) throw IOException("Microphone unavailable")
            val record = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, PcmAudio.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(8192, minimum))
            if (record.state != AudioRecord.STATE_INITIALIZED) { record.release(); throw IOException("Microphone unavailable") }
            return object : Source {
                override fun start() { record.startRecording() }
                override fun read(buffer: ByteArray, count: Int) = record.read(buffer, 0, count)
                override fun stop() = record.stop()
                override fun release() = record.release()
            }
        }
    }
}
