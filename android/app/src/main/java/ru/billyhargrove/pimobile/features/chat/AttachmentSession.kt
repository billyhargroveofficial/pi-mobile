package ru.billyhargrove.pimobile.features.chat

import ru.billyhargrove.pimobile.core.ImageGuard

/** Main-thread picker/request owner. URI and prepared-value types belong to its platform ports. */
internal class AttachmentSession<Key, Value>(private val readOnly: Boolean, private val port: Port<Key, Value>,
    private val begin: () -> Boolean, private val prepared: (List<Value>) -> Unit,
    private val limitNotice: () -> Unit, private val failed: (String) -> Unit,
    private val active: () -> Boolean = { true }) {
    data class Batch<Value>(val values: List<Value>, val error: String? = null)
    interface Control { fun cancel() }
    interface Port<Key, Value> {
        /** Port retains values until done returns true; otherwise it releases them. */
        fun start(keys: List<Key>, budget: Long, done: (Batch<Value>) -> Boolean): Control
    }
    private class Request { var control: Control? = null; var completed = false }
    private var request: Request? = null
    private var closed = false

    fun pick(keys: List<Key>, currentCount: Int, remainingBytes: Long): Boolean {
        if (closed || readOnly || !active() || request != null || keys.isEmpty()) return false
        val free = (ImageGuard.MAX_IMAGES - currentCount.coerceAtLeast(0)).coerceAtLeast(0)
        if (keys.size > free) limitNotice()
        if (free == 0 || !begin()) return false
        val ticket = Request(); request = ticket
        try {
            val control = port.start(keys.take(free), remainingBytes.coerceIn(0, ImageGuard.MAX_TOTAL_BYTES)) { complete(ticket, it) }
            if (request === ticket) ticket.control = control else if (!ticket.completed) control.cancel()
        } catch (cause: Exception) { complete(ticket, Batch(emptyList(), cause.message ?: "could not read file")) }
        return true
    }
    fun cancel() {
        val old = request ?: return
        request = null; prepared(emptyList())
        try { old.control?.cancel() } catch (_: Exception) {}
    }
    fun close() { if (!closed) { closed = true; cancel() } }
    private fun complete(ticket: Request, batch: Batch<Value>): Boolean {
        if (closed || request !== ticket) return false
        request = null; ticket.completed = true
        if (!active()) { prepared(emptyList()); return false }
        prepared(batch.values.toList())
        batch.error?.let(failed)
        return true
    }
}
