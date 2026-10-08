package ru.billyhargrove.pimobile.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.function.Consumer
import ru.billyhargrove.pimobile.core.PcmAudio
import ru.billyhargrove.pimobile.features.voice.RecordingSession
import ru.billyhargrove.pimobile.features.voice.RecordingScreen
import ru.billyhargrove.pimobile.media.PcmRecorder

/** Public recording facade. A finished PCM file is handed to the draft caller, never sent here. */
class DictationRecorder internal constructor(private val context: Context, private val ready: Consumer<File>, private val error: Consumer<String>, port: RecordingSession.Port) {
    constructor(context: Context, ready: Consumer<File>, error: Consumer<String>) : this(context, ready, error, ports(context))
    companion object {
        const val MAX_SECONDS = PcmAudio.MAX_SECONDS; const val SAMPLE_RATE = PcmAudio.SAMPLE_RATE; const val MAX_BYTES = PcmAudio.MAX_BYTES
        private fun ports(context: Context): RecordingSession.Port {
            val recorder = PcmRecorder(context); val main = Handler(Looper.getMainLooper())
            return object : RecordingSession.Port {
                override fun start(meter: (PcmAudio.Meter) -> Unit, done: (Result<File>) -> Unit): RecordingSession.Control {
                    val capture = recorder.start(meter, done)
                    return object : RecordingSession.Control { override fun finish() = capture.finish(); override fun cancel() = capture.cancel() }
                }
                override fun deadline(delayMs: Long, action: () -> Unit): () -> Unit {
                    val callback = Runnable(action); main.postDelayed(callback, delayMs)
                    return { main.removeCallbacks(callback) }
                }
                override fun discard(file: File) { file.delete() }
            }
        }
    }
    private val main = Handler(Looper.getMainLooper())
    private var cancelled = false
    private var dialog: ComposeSheet? = null
    private val owner = RecordingSession(port, { ready.accept(it) }, { message -> main.post { if (!cancelled) error.accept(message) } }, ::present)
    init { owner.start() }
    private fun present(visible: Boolean) {
        if (!visible) { dialog?.dismiss(); dialog = null; return }
        dialog = object : ComposeSheet(context) { init { content {
            RecordingScreen(owner.seconds, owner.levels, owner.phase == RecordingSession.Phase.FINISHING,
                context.packageName, owner::finish, this@DictationRecorder::cancel)
        } } }.apply { setOnCancelListener { this@DictationRecorder.cancel() }; show() }
    }
    fun cancel() { cancelled = true; owner.cancel() }
}
