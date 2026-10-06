package ru.billyhargrove.pimobile.core

/** Defensive immutable catalog, with the same public API as stored Java records. */
class Catalog(workspaces: List<Workspace>?, sessions: List<Session>?, terminals: List<TerminalInfo>?) {
    private val workspaces = java.util.Collections.unmodifiableList(ArrayList(workspaces.orEmpty()))
    private val sessions = java.util.Collections.unmodifiableList(ArrayList(sessions.orEmpty()))
    private val terminals = java.util.Collections.unmodifiableList(ArrayList(terminals.orEmpty()))
    fun workspaces(): List<Workspace> = workspaces
    fun sessions(): List<Session> = sessions
    fun terminals(): List<TerminalInfo> = terminals
    fun findSession(id: String?): Session? = sessions.firstOrNull { id != null && it.id() == id }
    companion object { @JvmStatic fun empty() = Catalog(null, null, null) }
}
