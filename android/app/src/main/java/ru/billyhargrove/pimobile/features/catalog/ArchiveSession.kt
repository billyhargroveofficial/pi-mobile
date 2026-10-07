package ru.billyhargrove.pimobile.features.catalog

import androidx.compose.runtime.*
import org.json.JSONObject
import java.time.*

/** Search and pagination owner. Every callback belongs to one request or timer. */
internal class ArchiveSession(private val transport: Transport, private val zone: ZoneId,
    private val today: () -> LocalDate, private val delete: ((String, (Result<Unit>) -> Unit) -> Unit)? = null) {
    interface Transport {
        fun fetch(offset: Int, query: String, result: (Result<JSONObject>) -> Unit)
        fun scheduleSearch(delayMs: Long, action: () -> Unit)
        fun cancelSearch()
    }
    var rows by mutableStateOf<List<ArchiveProjection.Row>>(emptyList()); private set
    var entries by mutableStateOf<List<ArchiveProjection.Entry>>(emptyList()); private set
    var input by mutableStateOf(""); private set
    var query by mutableStateOf(""); private set
    var loading by mutableStateOf(false); private set
    var hasMore by mutableStateOf(false); private set
    var error by mutableStateOf(""); private set
    var confirmation by mutableStateOf<ArchiveProjection.Row?>(null); private set
    private var pendingDeletes by mutableStateOf<Set<String>>(emptySet())
    private val deletes = mutableMapOf<String, Any>()
    private var offset = 0; private var closed = false; private var generation = 0L
    private var request: Any? = null; private var search: Any? = null
    fun changeInput(text: String) {
        if (closed) return
        input = text; cancelSearch()
        if (text.trim() == query) return
        val ticket = Any(); search = ticket
        transport.scheduleSearch(250) { if (!closed && search === ticket) { search = null; commitSearch(text.trim()) } }
    }
    fun submitSearch() { if (!closed) { cancelSearch(); commitSearch(input.trim()) } }
    private fun commitSearch(value: String) {
        if (value == query) return
        query = value; reset(); load()
    }
    fun load() {
        if (loading || closed) return
        loading = true; error = ""
        val ticket = Any(); request = ticket; val start = offset
        val complete: (Result<JSONObject>) -> Unit = complete@ { result ->
            if (closed || request !== ticket) return@complete
            request = null; loading = false
            result.fold({ value ->
                val page = ArchiveProjection.page(value, start, zone)
                rows = (rows + page.rows).distinctBy { it.id }; entries = ArchiveProjection.entries(rows, today())
                offset = page.next; hasMore = page.hasMore
            }, { cause -> hasMore = false; error = cause.message?.takeIf { it.isNotEmpty() } ?: "Could not load history" })
        }
        try { transport.fetch(start, query, complete) } catch (cause: Exception) { complete(Result.failure(cause)) }
    }
    fun canDelete(row: ArchiveProjection.Row) = delete != null && !closed && row.id !in pendingDeletes
    fun requestDelete(row: ArchiveProjection.Row) { if (canDelete(row) && rows.any { it.id == row.id }) confirmation = row }
    fun cancelDelete() { confirmation = null }
    fun confirmDelete() {
        val row = confirmation ?: return
        confirmation = null
        if (!canDelete(row)) return
        val ticket = Any(); deletes[row.id] = ticket; pendingDeletes = deletes.keys.toSet(); val version = generation
        val complete: (Result<Unit>) -> Unit = complete@ { result ->
            if (closed || deletes[row.id] !== ticket) return@complete
            deletes.remove(row.id); pendingDeletes = deletes.keys.toSet()
            result.fold({ reset(); load() }, { cause -> if (generation == version) error = cause.message ?: "Could not delete session" })
        }
        try { delete?.invoke(row.id, complete) } catch (cause: Exception) { complete(Result.failure(cause)) }
    }
    fun close() { closed = true; request = null; loading = false; cancelSearch(); confirmation = null; deletes.clear(); pendingDeletes = emptySet() }
    private fun reset() { generation++; request = null; loading = false; hasMore = false; rows = emptyList(); entries = emptyList(); offset = 0; error = ""; confirmation = null }
    private fun cancelSearch() { search = null; transport.cancelSearch() }
}
