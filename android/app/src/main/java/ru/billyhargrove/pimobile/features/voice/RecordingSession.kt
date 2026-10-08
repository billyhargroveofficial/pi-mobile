package ru.billyhargrove.pimobile.features.voice

import androidx.compose.runtime.*
import java.io.File
import ru.billyhargrove.pimobile.core.PcmAudio

/** Main-thread recording owner. The accepted file belongs to the draft/transcription caller. */
internal class RecordingSession(private val port: Port, private val ready: (File) -> Unit,
    private val error: (String) -> Unit, private val show: (Boolean) -> Unit = {}) {
    interface Control { fun finish(); fun cancel() }
    interface Port {
        fun start(meter: (PcmAudio.Meter) -> Unit, done: (Result<File>) -> Unit): Control
        fun deadline(delayMs: Long, action: () -> Unit): () -> Unit
        fun discard(file: File)
    }
    enum class Phase { NEW, LISTENING, FINISHING, ENDED }
    var phase by mutableStateOf(Phase.NEW); private set
    var seconds by mutableIntStateOf(0); private set
    var levels by mutableStateOf(List(48) { 0f }); private set
    private class Request { var control: Control? = null; var timer: (() -> Unit)? = null; var accepted: File? = null }
    private var request: Request? = null
    private var closed = false
    fun start() {
        if (closed || phase != Phase.NEW) return
        val ticket = Request(); request = ticket
        val done: (Result<File>) -> Unit = { result -> complete(ticket, result) }
        try {
            val control = port.start({ meter ->
                if (request === ticket && !closed && phase != Phase.FINISHING) {
                    seconds = maxOf(seconds, meter.seconds.coerceIn(0, PcmAudio.MAX_SECONDS))
                    val value = meter.level.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
                    levels = levels.drop(1) + value
                }
            }, done)
            if (request !== ticket) { if (ticket.accepted == null) control.cancel(); return }
            ticket.control = control; phase = Phase.LISTENING; show(true)
            val cancelTimer = port.deadline(PcmAudio.MAX_SECONDS * 1000L) { if (request === ticket && !closed) finish() }
            if (request === ticket) ticket.timer = cancelTimer else cancelTimer()
        } catch (cause: Exception) {
            val control = ticket.control; complete(ticket, Result.failure(cause)); try { control?.cancel() } catch (_: Exception) {}
        }
    }
    fun finish() {
        val ticket = request ?: return
        if (closed || phase != Phase.LISTENING) return
        phase = Phase.FINISHING; cancelTimer(ticket)
        try { ticket.control?.finish() } catch (cause: Exception) {
            complete(ticket, Result.failure(cause)); try { ticket.control?.cancel() } catch (_: Exception) {}
        }
    }
    fun cancel() {
        if (closed) return
        closed = true
        val old = request; request = null; phase = Phase.ENDED
        old?.let { cancelTimer(it); try { it.control?.cancel() } catch (_: Exception) {} }
        show(false)
    }
    private fun complete(ticket: Request, result: Result<File>) {
        if (closed || request !== ticket) {
            result.getOrNull()?.takeIf { it != ticket.accepted }?.let(port::discard)
            return
        }
        request = null; cancelTimer(ticket); phase = Phase.ENDED; show(false)
        result.fold({ file -> ticket.accepted = file; ready(file) }, { error(it.message ?: "Could not capture microphone audio") })
    }
    private fun cancelTimer(ticket: Request) {
        val cancel = ticket.timer; ticket.timer = null
        try { cancel?.invoke() } catch (_: Exception) {}
    }
}
