package ru.billyhargrove.pimobile.core

class Session(id: String?, title: String?, cwd: String?, workspaceId: String?, terminalId: String?, connected: Boolean, status: SessionStatus?, model: String?) {
    private val id = id.orEmpty()
    private val title = title.orEmpty()
    private val cwd = cwd.orEmpty()
    private val workspaceId = workspaceId.orEmpty()
    private val terminalId = terminalId.orEmpty()
    private val connected = connected
    private val status = status ?: SessionStatus.UNKNOWN
    private val model = model.orEmpty()
    fun id() = id
    fun title() = title
    fun cwd() = cwd
    fun workspaceId() = workspaceId
    fun terminalId() = terminalId
    fun connected() = connected
    fun status() = status
    fun model() = model
    fun hasWorkspace() = workspaceId.isNotEmpty()
    fun displayTitle() = title.ifEmpty { id.ifEmpty { "Сессия" } }
    fun sortRank() = when (status) {
        SessionStatus.RUNNING -> 0; SessionStatus.IDLE -> 1
        SessionStatus.OFFLINE -> 2; SessionStatus.UNKNOWN -> 3
    }
    override fun equals(other: Any?): Boolean = other is Session &&
        id == other.id && title == other.title && cwd == other.cwd && workspaceId == other.workspaceId && terminalId == other.terminalId && connected == other.connected && status == other.status && model == other.model
    override fun hashCode() = java.util.Objects.hash(id, title, cwd, workspaceId, terminalId, connected, status, model)
    override fun toString() = "Session{$id, $title, $status}"
}
