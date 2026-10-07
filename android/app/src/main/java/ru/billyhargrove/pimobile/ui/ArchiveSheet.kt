package ru.billyhargrove.pimobile.ui

import android.content.Context
import android.os.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import ru.billyhargrove.pimobile.R
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.function.Consumer
import ru.billyhargrove.pimobile.PiApp
import ru.billyhargrove.pimobile.net.AppExecutors

/** Saved history: date groups, bounded placeholders, search generations and pagination. */
class ArchiveSheet @JvmOverloads constructor(context: Context, private val loader: Loader, private val listener: Listener,
    private val delete: Delete? = null) : ComposeSheet(context) {
    fun interface Listener { fun resume(id: String, title: String) }
    fun interface Loader { @Throws(Exception::class) fun fetch(offset: Int, query: String): JSONObject }
    fun interface Delete { fun remove(id: String, success: Runnable, failure: Consumer<String>) }
    @JvmOverloads constructor(context: Context, app: PiApp, listener: Listener, delete: Delete? = null) : this(context,
        Loader { offset, query -> app.api().fetchArchive(app.settings().baseUrl(), app.settings().token(), offset, query) }, listener, delete)
    private val handler = Handler(Looper.getMainLooper())
    private var rows by mutableStateOf<List<JSONObject>>(emptyList())
    private var query by mutableStateOf("")
    private var loading by mutableStateOf(false)
    private var hasMore by mutableStateOf(false)
    private var error by mutableStateOf("")
    private var confirm by mutableStateOf<JSONObject?>(null)
    private var offset = 0; private var generation = 0; private var closed = false
    private var pendingSearch: Runnable? = null
    init {
        content {
            val list = rememberLazyListState(); val keyboard = LocalSoftwareKeyboardController.current
            var input by remember { mutableStateOf("") }
            LaunchedEffect(query) { list.scrollToItem(0) }
            LaunchedEffect(list, rows.size, loading, hasMore) {
                snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
                    if (list.isScrollInProgress && hasMore && !loading && last >= list.layoutInfo.totalItemsCount - 5) load()
                }
            }
            Column(Modifier.fillMaxWidth().height((context.resources.displayMetrics.heightPixels / context.resources.displayMetrics.density * .88f).dp)
                .navigationBarsPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth()) { Text("History", Modifier.weight(1f).padding(vertical = 12.dp), fontSize = 24.sp, fontWeight = FontWeight.SemiBold); TextButton(onClick = ::dismiss) { Text("Close") } }
                TextField(input, { text ->
                    input = text; pendingSearch?.let(handler::removeCallbacks)
                    pendingSearch = Runnable { search(text.trim()) }.also { handler.postDelayed(it, 250) }
                }, colors = PiFieldColors(), placeholder = { Text("Search conversations") }, singleLine = true, modifier = tag("archiveSearch").fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { pendingSearch?.let(handler::removeCallbacks); search(input.trim()); keyboard?.hide() }))
                LazyColumn(state = list, modifier = tag("archiveList").fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    var prior: String? = null
                    rows.forEach { row ->
                        val day = date(row)
                        if (day != prior) { item(key = "date:$day") { DateHeader(dateLabel(day)) }; prior = day }
                        item(key = "session:${row.optString("id")}") {
                            SwipeAction(delete != null, "Delete", { confirm = row }) {
                                HistoryRow(row, false) { listener.resume(row.optString("id"), row.optString("title")) }
                            }
                        }
                    }
                    if (loading) {
                        if (rows.isEmpty()) item(key = "loading-header") { DateHeader(null) }
                        items(if (rows.isEmpty()) 6 else 2, key = { "loading:$it" }) { HistoryRow(null, true) {} }
                    }
                    if (hasMore && !loading) item(key = "more") { TextButton(onClick = ::load, modifier = Modifier.fillMaxWidth()) { Text("Load more") } }
                }
                if (error.isNotEmpty()) { Text(error, color = MaterialTheme.colorScheme.error); TextButton(onClick = ::load, modifier = Modifier.fillMaxWidth()) { Text("Retry") } }
                else if (!loading && rows.isEmpty()) Text(if (query.isEmpty()) "No closed conversations yet" else "No results", Modifier.padding(16.dp))
            }
            confirm?.let { row -> AlertDialog(onDismissRequest = { confirm = null }, title = { Text("Delete session from disk?") },
                text = { Text(row.optString("title") + "\n\nThe Pi history file will be permanently deleted. This cannot be undone.") },
                dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
                confirmButton = { TextButton(onClick = {
                    confirm = null
                    delete?.remove(row.optString("id"), Runnable { if (!closed) { generation++; loading = false; rows = emptyList(); offset = 0; hasMore = false; load() } },
                        Consumer { message -> if (!closed) error = message })
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) } }) }
        }
        setOnDismissListener { closed = true; generation++; handler.removeCallbacksAndMessages(null) }
        load()
    }
    @Composable private fun HistoryRow(row: JSONObject?, skeleton: Boolean, open: () -> Unit) {
        Row((if (skeleton) tag("archiveSkeleton") else tag("archiveRow").clickable(onClick = open))
            .fillMaxWidth().heightIn(min = 76.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                if (!skeleton) Icon(painterResource(R.drawable.ic_chat), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SkeletonText(if (skeleton) null else row?.optString("title").orEmpty(), 15.sp, .78f)
                SkeletonText(if (skeleton) null else "${row?.optString("workspaceName")} · ${row?.optInt("messageCount")} messages", 12.sp, .6f)
            }
            if (!skeleton) Text("›", fontSize = 24.sp)
        }
    }
    @Composable private fun DateHeader(label: String?) {
        Box(tag("archiveDateHeader").fillMaxWidth().heightIn(min = 40.dp).padding(top = 16.dp, bottom = 8.dp)) {
            SkeletonText(label, 12.sp, .22f)
        }
    }
    @Composable private fun SkeletonText(text: String?, size: TextUnit, fraction: Float) {
        val shade = MaterialTheme.colorScheme.surfaceVariant
        Text(text ?: " ", Modifier.fillMaxWidth().drawWithContent {
            if (text != null) drawContent() else {
                val height = this.size.height * .62f
                drawRoundRect(shade, Offset(0f, (this.size.height - height) / 2), Size(this.size.width * fraction, height), CornerRadius(height / 3))
            }
        }, fontSize = size, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (size == 12.sp) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
    }
    private fun search(value: String) { if (closed || value == query) return; query = value; generation++; loading = false; hasMore = false; rows = emptyList(); offset = 0; load() }
    private fun load() {
        if (loading || closed) return
        loading = true; error = ""
        val version = generation; val start = offset; val q = query
        AppExecutors.io().execute {
            var result: JSONObject? = null; var failure: String? = null
            try { result = loader.fetch(start, q) } catch (cause: Exception) { failure = cause.message ?: "Could not load history" }
            val data = result; val message = failure
            AppExecutors.main {
                if (closed || generation != version) return@main
                loading = false
                if (data == null) { hasMore = false; error = message ?: "Could not load history"; return@main }
                val items = data.optJSONArray("sessions")
                rows = (rows + (0 until (items?.length() ?: 0)).mapNotNull { items?.optJSONObject(it) })
                    .filter { it.optString("id").isNotEmpty() }.distinctBy { it.optString("id") }
                offset = data.optInt("nextOffset", start + 40); hasMore = data.optBoolean("hasMore") && offset > start
            }
        }
    }
    private fun date(row: JSONObject) = try { Instant.parse(row.optString("modified")).atZone(ZoneId.systemDefault()).toLocalDate().toString() } catch (_: Exception) { "" }
    private fun dateLabel(value: String) = try {
        val day = LocalDate.parse(value); val today = LocalDate.now()
        when (day) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> day.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "d MMMM" else "d MMMM yyyy", Locale.ENGLISH)) }
    } catch (_: Exception) { "Earlier" }
    private fun tag(name: String) = Modifier.testTag("${context.packageName}:id/$name").semantics { testTagsAsResourceId = true }
}
