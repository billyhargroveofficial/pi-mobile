package ru.billyhargrove.pimobile.features.chat

/** Main-thread control ownership. Only an explicit command/stop can write; reconnect never replays. */
internal class ControlSession(private val readOnly: Boolean, private val capability: (String) -> Boolean,
    private val send: (Command) -> String?, private val stop: () -> String?,
    private val clearDraft: (String) -> Unit, private val notice: (String) -> Unit) {
    sealed interface Command {
        data object Mcp : Command
        data class Rename(val name: String) : Command
    }
    private class Control(val command: Command, val draft: String, var id: String? = null, var received: Boolean = false)
    private class Stop(var id: String? = null)
    private var control: Control? = null
    private var stopping: Stop? = null
    private var closed = false

    /** True also for a recognized but blocked command: it must never fall through to a prompt. */
    fun handle(text: String, hasAttachments: Boolean): Boolean {
        val draft = text.trim()
        val kind = when { draft == "/mcp" -> "mcp"; draft == "/name" || draft.startsWith("/name ") -> "name"; else -> return false }
        if (closed || readOnly) return true
        if (hasAttachments) { notice("This command does not send attachments. Remove them first"); return true }
        if (control != null) { notice("The previous command is still pending"); return true }
        if (!capability(kind)) { notice("Update the Pi bridge after the current task finishes"); return true }
        val command = if (kind == "mcp") Command.Mcp else {
            val name = draft.substring(5).trim()
            if (name.isEmpty()) { notice("Use /name New name"); return true }
            Command.Rename(name)
        }
        val ticket = Control(command, draft); control = ticket
        val id = try { send(command) } catch (cause: Exception) {
            if (control === ticket) { control = null; notice("Command could not be sent: ${cause.message ?: "write failed"}") }
            return true
        }
        if (control !== ticket) return true
        if (id.isNullOrEmpty()) { control = null; notice("Not connected to this Pi session") }
        else ticket.id = id
        return true
    }
    fun abort() {
        if (closed || readOnly) return
        if (stopping != null) { notice("Stop is already requested"); return }
        val ticket = Stop(); stopping = ticket
        val id = try { stop() } catch (cause: Exception) {
            if (stopping === ticket) { stopping = null; notice("Stop could not be requested: ${cause.message ?: "write failed"}") }
            return
        }
        if (stopping !== ticket) return
        if (id.isNullOrEmpty()) { stopping = null; notice("Stop is unavailable while Pi is disconnected") }
        else { ticket.id = id; notice("Stop requested") }
    }
    fun receivedMcp(id: String): Boolean {
        val ticket = control ?: return false
        if (closed || ticket.id != id || ticket.command != Command.Mcp || ticket.received) return false
        ticket.received = true; return true
    }
    fun acknowledged(id: String, ok: Boolean, error: String): Boolean {
        control?.takeIf { it.id == id }?.let { ticket ->
            control = null
            when {
                !ok -> notice(error)
                ticket.command == Command.Mcp && !ticket.received -> notice("Pi returned no MCP status")
                else -> { clearDraft(ticket.draft); if (ticket.command is Command.Rename) notice("Session renamed") }
            }
            return true
        }
        if (stopping?.id != id) return false
        stopping = null
        if (!ok) notice("Pi rejected the command: $error")
        return true
    }
    fun uncertain(id: String, reason: String): Boolean {
        if (control?.id == id) { control = null; notice("Command result unknown: $reason"); return true }
        if (stopping?.id == id) { stopping = null; notice("Result unknown: $reason"); return true }
        return false
    }
    fun reconcile(live: Set<String>) {
        listOfNotNull(control?.id, stopping?.id).filter { it !in live }.forEach {
            uncertain(it, "The result arrived while this screen was inactive")
        }
    }
    fun close() { closed = true; control = null; stopping = null }
}
