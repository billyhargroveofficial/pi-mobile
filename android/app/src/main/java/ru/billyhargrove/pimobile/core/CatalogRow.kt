package ru.billyhargrove.pimobile.core

/** Stable catalog identity shared by navigation and immutable feature state. */
class CatalogRow private constructor(private val kind: Kind, private val key: String,
    private val title: String, private val subtitle: String, private val sessionId: String,
    private val status: SessionStatus, private val connected: Boolean, private val readOnly: Boolean,
    private val badge: String, private val count: Int) {
    enum class Kind { HEADER, SESSION, TERMINAL }
    fun kind() = kind
    fun key() = key
    fun title() = title
    fun subtitle() = subtitle
    fun sessionId() = sessionId
    fun status() = status
    fun connected() = connected
    fun readOnly() = readOnly
    fun badge() = badge
    fun count() = count
    fun isHeader() = kind == Kind.HEADER
    companion object {
        @JvmStatic fun header(key: String, title: String, subtitle: String, count: Int) =
            CatalogRow(Kind.HEADER, key, title, subtitle, "", SessionStatus.UNKNOWN, false, false, "", count)
        @JvmStatic fun session(session: Session): CatalogRow {
            val subtitle = listOf(session.cwd(), session.model()).filter(String::isNotEmpty).joinToString(" · ")
                .ifEmpty { if (session.connected()) "" else "Process disconnected" }
            val badge = when (session.status()) {
                SessionStatus.RUNNING -> "running"; SessionStatus.IDLE -> "idle"
                SessionStatus.OFFLINE -> "offline"; else -> ""
            }
            return CatalogRow(Kind.SESSION, "session:${session.id()}", session.displayTitle(), subtitle,
                session.id(), session.status(), session.connected(), false, badge, 0)
        }
        @JvmStatic fun terminal(terminal: TerminalInfo) = CatalogRow(Kind.TERMINAL, "terminal:${terminal.id()}",
            terminal.displayTitle(), terminal.agent().ifEmpty { "Agent unspecified" }, "",
            if (terminal.connected()) SessionStatus.IDLE else SessionStatus.OFFLINE,
            terminal.connected(), true, "extension required", 0)
    }
}
