package ru.billyhargrove.pimobile.features.chat

import java.io.File

/** Main-thread draft handoff; stopping an Activity retains the job, closing it retires the result. */
internal class TranscriptionSession(private val readOnly: Boolean, private val port: Port,
    private val busy: (Boolean) -> Unit, private val clearNotice: () -> Unit,
    private val insert: (String) -> Unit, private val notice: (String) -> Unit,
    private val active: () -> Boolean = { true }) {
    interface Control { fun cancel() }
    interface Port {
        /** Takes file ownership on entry, including request setup/worker errors. */
        fun start(file: File, done: (Result<String>) -> Unit): Control
        fun discard(file: File)
    }
    private class Request(val file: File) { var control: Control? = null; var completed = false }
    private var request: Request? = null
    private var closed = false
    fun start(file: File): Boolean {
        if (closed || readOnly || !active() || request != null) {
            if (request?.file != file) port.discard(file)
            return false
        }
        val ticket = Request(file); request = ticket
        clearNotice(); busy(true)
        try {
            val control = port.start(file) { complete(ticket, it) }
            if (request === ticket) ticket.control = control
            else if (!ticket.completed) control.cancel()
        } catch (cause: Exception) { port.discard(file); complete(ticket, Result.failure(cause)) }
        return true
    }
    fun cancel() {
        val old = request ?: return
        request = null; busy(false)
        try { old.control?.cancel() } catch (_: Exception) {}
    }
    fun close() { if (!closed) { closed = true; cancel() } }
    private fun complete(ticket: Request, result: Result<String>) {
        if (closed || request !== ticket) return
        request = null; ticket.completed = true; busy(false)
        if (!active()) return
        result.fold({ text -> if (text.isBlank()) notice("No speech detected") else insert(text) },
            { notice(it.message?.takeIf(String::isNotBlank) ?: "Could not transcribe recording") })
    }
}
