package ru.billyhargrove.pimobile.features.chat

/** Main-thread read-only pagination. A page belongs to one epoch/cursor/ticket and is accepted once. */
internal class HistorySession(private val read: (String) -> String?, private val hasUserContext: () -> Boolean,
    private val loading: (Boolean) -> Unit, private val notice: (String) -> Unit) {
    data class Cursor(val before: String, val hasMore: Boolean)
    private data class Request(val epoch: Long, val before: String, var id: String? = null, var received: Boolean = false)
    var epoch = 0L; private set
    var cached = false
    private var cursor: Cursor? = null
    private var request: Request? = null
    private val attempted = mutableSetOf<String>()
    private val consumed = mutableSetOf<String>()
    private var closed = false

    fun snapshot(epoch: Long, cursor: Cursor?, cached: Boolean) {
        if (closed) return
        retire()
        // A snapshot is authoritative; a same-epoch replacement may also discard an old prefix.
        attempted.clear(); consumed.clear()
        this.epoch = epoch; this.cursor = cursor; this.cached = cached
    }
    fun ensureUserContext() { if (!hasUserContext()) load(automatic = true) }
    fun loadOlder() = load(automatic = false)
    private fun load(automatic: Boolean): Boolean {
        val next = cursor ?: return false
        if (closed || cached || !next.hasMore || next.before.isEmpty() || request != null || next.before in consumed) return false
        if (automatic && next.before in attempted) return false
        attempted.add(next.before)
        val ticket = Request(epoch, next.before); request = ticket; loading(true)
        if (request !== ticket) return false
        val id = try { read(next.before) } catch (cause: Exception) {
            if (request === ticket) { retire(); notice("History could not be loaded: ${cause.message ?: "read failed"}") }
            return false
        }
        if (request !== ticket) return false
        if (id.isNullOrEmpty()) { retire(); notice("Pi must be connected to load history"); return false }
        ticket.id = id; return true
    }
    fun owns(id: String) = !closed && request?.id == id
    fun received(id: String, epoch: Long, next: Cursor?): Boolean {
        if (!owns(id)) return false
        val ticket = requireNotNull(request)
        if (ticket.received) return false
        if (ticket.epoch != epoch || this.epoch != epoch) { retire(); return false }
        // Keep the ticket through ACK; an overlapping live update cannot start another page.
        ticket.received = true; consumed.add(ticket.before); cursor = next
        if (next?.hasMore == true && (next.before.isEmpty() || next.before in consumed)) notice("History cursor did not advance")
        return true
    }
    fun invalid(id: String): Boolean {
        if (!owns(id) || request?.received == true) return false
        retire(); notice("Pi returned no history"); return true
    }
    fun acknowledged(id: String, ok: Boolean, error: String): Boolean {
        if (!owns(id)) return false
        val received = requireNotNull(request).received; retire()
        when {
            !ok -> notice(error)
            !received -> notice("Pi returned no history")
            else -> ensureUserContext()
        }
        return true
    }
    fun uncertain(id: String, reason: String): Boolean {
        if (!owns(id)) return false
        retire(); notice("History could not be loaded: $reason"); return true
    }
    fun reconcile(live: Set<String>) {
        request?.id?.takeIf { it !in live }?.let { uncertain(it, "The result arrived while this screen was inactive") }
    }
    private fun retire() { if (request != null) { request = null; loading(false) } }
    fun close() { closed = true; retire() }
}
