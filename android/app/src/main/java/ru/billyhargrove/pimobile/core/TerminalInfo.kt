package ru.billyhargrove.pimobile.core

class TerminalInfo(id: String?, title: String?, workspaceId: String?, agent: String?, connected: Boolean) {
    private val id = id.orEmpty()
    private val title = title.orEmpty()
    private val workspaceId = workspaceId.orEmpty()
    private val agent = agent.orEmpty()
    private val connected = connected
    fun id() = id
    fun title() = title
    fun workspaceId() = workspaceId
    fun agent() = agent
    fun connected() = connected
    fun displayTitle() = title.ifEmpty { id.ifEmpty { "Terminal" } }
    override fun equals(other: Any?): Boolean = other is TerminalInfo &&
        id == other.id && title == other.title && workspaceId == other.workspaceId && agent == other.agent && connected == other.connected
    override fun hashCode() = java.util.Objects.hash(id, title, workspaceId, agent, connected)
}
