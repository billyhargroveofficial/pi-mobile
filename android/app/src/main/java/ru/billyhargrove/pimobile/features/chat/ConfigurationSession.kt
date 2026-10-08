package ru.billyhargrove.pimobile.features.chat

/** One explicit session-local change. Reports remain authoritative; reconnect never replays. */
internal class ConfigurationSession(private val readOnly: Boolean, private val idle: () -> Boolean,
    private val send: (Change) -> String?, private val pending: (Boolean) -> Unit,
    private val completed: (String?) -> Unit, private val notice: (String) -> Unit) {
    data class Change(val provider: String?, val model: String?, val effort: String?, val tier: String?)
    private class Ticket(var id: String? = null)
    private var ticket: Ticket? = null
    private var closed = false
    val canSubmit get() = !closed && !readOnly && ticket == null

    fun apply(change: Change, modelChange: Boolean): Boolean {
        if (closed || readOnly) return false
        if (ticket != null) { notice("Waiting for the previous change to be confirmed"); return false }
        if (modelChange && !idle()) { completed("Wait for the current task to finish"); return false }
        val next = Ticket(); ticket = next; pending(true)
        if (ticket !== next) return false
        val id = try { send(change) } catch (cause: Exception) {
            finish(next, "Changes could not be sent: ${cause.message ?: "write failed"}"); return false
        }
        if (ticket !== next) return false
        if (id.isNullOrEmpty()) { finish(next, "Pi is disconnected. Changes not sent."); return false }
        next.id = id
        return true
    }
    fun acknowledged(id: String, ok: Boolean, error: String): Boolean {
        val current = ticket?.takeIf { it.id == id } ?: return false
        finish(current, if (ok) null else error); return true
    }
    fun uncertain(id: String): Boolean {
        val current = ticket?.takeIf { it.id == id } ?: return false
        finish(current, "Result unknown. Check the model in the terminal before retrying."); return true
    }
    fun reconcile(live: Set<String>) { ticket?.id?.takeIf { it !in live }?.let(::uncertain) }
    private fun finish(current: Ticket, error: String?) {
        if (ticket !== current) return
        ticket = null; pending(false)
        if (!closed) completed(error)
    }
    fun close() {
        if (closed) return
        closed = true
        if (ticket != null) { ticket = null; pending(false) }
    }
}
