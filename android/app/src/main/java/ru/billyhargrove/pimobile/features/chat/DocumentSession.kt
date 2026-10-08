package ru.billyhargrove.pimobile.features.chat

/** Main-thread read-only file request owner. Cancel retires waiting; it never sends a remote command. */
internal class DocumentSession(private val read: (String) -> String?, private val ready: (Document) -> Unit,
    private val notice: (String) -> Unit) {
    data class Document(val path: String, val text: String) {
        private val parent = path.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        fun resolve(link: String) = if (link.startsWith('/') || link.contains(':')) link else parent + link
    }
    private data class Request(val path: String, var id: String? = null)
    private var request: Request? = null
    private var closed = false
    fun open(path: String): Boolean {
        if (closed) return false
        if (request != null) { notice("A file is already loading"); return false }
        val ticket = Request(path); request = ticket
        val id = try { read(path) } catch (cause: Exception) {
            if (request === ticket) { request = null; notice("File could not be loaded: ${cause.message ?: "read failed"}") }
            return false
        }
        if (request !== ticket) return false
        if (id.isNullOrEmpty()) { request = null; notice("Pi must be connected to preview files"); return false }
        ticket.id = id; return true
    }
    fun owns(id: String) = !closed && request?.id == id
    fun received(id: String, path: String, text: String): Boolean {
        if (!owns(id)) return false
        val ticket = requireNotNull(request); request = null
        ready(Document(path.ifEmpty { ticket.path }, text)); return true
    }
    fun acknowledged(id: String, ok: Boolean, error: String): Boolean {
        if (!owns(id)) return false
        request = null; notice(if (ok) "Pi returned no document" else error); return true
    }
    fun uncertain(id: String, reason: String): Boolean {
        if (!owns(id)) return false
        request = null; notice("File could not be loaded: $reason"); return true
    }
    fun reconcile(live: Set<String>) {
        request?.id?.takeIf { it !in live }?.let { uncertain(it, "The result arrived while this screen was inactive") }
    }
    fun cancel() { request = null }
    fun close() { closed = true; cancel() }
}
