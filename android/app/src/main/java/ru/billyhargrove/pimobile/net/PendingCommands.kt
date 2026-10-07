package ru.billyhargrove.pimobile.net

/** Main-thread request ledger. Deadlines never replay commands or resolve a foreign session. */
internal class PendingCommands(
    private val schedule: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val uncertain: (String, String, String) -> Unit,
    private val timeoutMs: Long
) {
    private data class Entry(val session: String, val timer: Runnable)
    private val entries = linkedMapOf<String, Entry>()

    fun register(id: String, session: String, reason: String) {
        check(id !in entries) { "Request already pending" }
        lateinit var entry: Entry
        entry = Entry(session, Runnable {
            // A cancelled deadline may still be queued; compare the ticket itself.
            if (entries[id] === entry) {
                entries.remove(id)
                uncertain(id, session, reason)
            }
        })
        entries[id] = entry
        schedule(entry.timer, timeoutMs)
    }

    fun contains(id: String, session: String) = entries[id]?.session == session
    fun resolve(id: String, session: String): Boolean {
        if (!contains(id, session)) return false
        entries.remove(id)?.let { cancel(it.timer) }
        return true
    }
    fun failAll(reason: String) {
        val old = entries.toMap()
        entries.clear()
        old.forEach { (id, entry) -> cancel(entry.timer); uncertain(id, entry.session, reason) }
    }
    fun ids(session: String? = null) = entries.filterValues { session == null || it.session == session }.keys.toSet()
}
