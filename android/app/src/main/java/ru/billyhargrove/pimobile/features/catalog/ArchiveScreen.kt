package ru.billyhargrove.pimobile.features.catalog

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

import ru.billyhargrove.pimobile.ui.PiFieldColors
import ru.billyhargrove.pimobile.ui.SwipeAction

@Composable internal fun ArchiveScreen(owner: ArchiveSession, height: Dp, prefix: String, dismiss: () -> Unit, resume: (String, String) -> Unit) {
    val list = rememberLazyListState(); val keyboard = LocalSoftwareKeyboardController.current
    val rows = owner.rows; val query = owner.query; val loading = owner.loading; val hasMore = owner.hasMore; val error = owner.error
    LaunchedEffect(query) { list.scrollToItem(0) }
    LaunchedEffect(list, rows.size, loading, hasMore) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (list.isScrollInProgress && hasMore && !loading && last >= list.layoutInfo.totalItemsCount - 5) owner.load()
        }
    }
    Column(Modifier.fillMaxWidth().height(height)
        .navigationBarsPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth()) { Text("History", Modifier.weight(1f).padding(vertical = 12.dp), fontSize = 24.sp, fontWeight = FontWeight.SemiBold); TextButton(onClick = dismiss) { Text("Close") } }
        TextField(owner.input, owner::changeInput, colors = PiFieldColors(), placeholder = { Text("Search conversations") }, singleLine = true, modifier = tag(prefix, "archiveSearch").fillMaxWidth(),
            shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { owner.submitSearch(); keyboard?.hide() }))
        LazyColumn(state = list, modifier = tag(prefix, "archiveList").fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(owner.entries, key = { it.key }) { entry ->
                when (entry) {
                    is ArchiveProjection.Entry.Header -> DateHeader(prefix, entry.label)
                    is ArchiveProjection.Entry.Session -> SwipeAction(owner.canDelete(entry.row), "Delete", { owner.requestDelete(entry.row) }) {
                        HistoryRow(prefix, entry.row, false) { resume(entry.row.id, entry.row.title) }
                    }
                }
            }
            if (loading) {
                if (rows.isEmpty()) item(key = "loading-header") { DateHeader(prefix, null) }
                items(if (rows.isEmpty()) 6 else 2, key = { "loading:$it" }) { HistoryRow(prefix, null, true) {} }
            }
            if (hasMore && !loading) item(key = "more") { TextButton(onClick = owner::load, modifier = Modifier.fillMaxWidth()) { Text("Load more") } }
        }
        if (error.isNotEmpty()) { Text(error, color = MaterialTheme.colorScheme.error); TextButton(onClick = owner::load, modifier = Modifier.fillMaxWidth()) { Text("Retry") } }
        else if (!loading && rows.isEmpty()) Text(if (query.isEmpty()) "No closed conversations yet" else "No results", Modifier.padding(16.dp))
    }
    owner.confirmation?.let { row -> AlertDialog(onDismissRequest = owner::cancelDelete, title = { Text("Delete session from disk?") },
        text = { Text(row.title + "\n\nThe Pi history file will be permanently deleted. This cannot be undone.") },
        dismissButton = { TextButton(onClick = owner::cancelDelete) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = owner::confirmDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) } }) }
}
@Composable private fun HistoryRow(prefix: String, row: ArchiveProjection.Row?, skeleton: Boolean, open: () -> Unit) {
    Row((if (skeleton) tag(prefix, "archiveSkeleton") else tag(prefix, "archiveRow").clickable(onClick = open))
        .fillMaxWidth().heightIn(min = 76.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            if (!skeleton) Icon(painterResource(R.drawable.ic_chat), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SkeletonText(if (skeleton) null else row?.title.orEmpty(), 15.sp, .78f)
            SkeletonText(if (skeleton) null else "${row?.workspace} · ${row?.messages} messages", 12.sp, .6f)
        }
        if (!skeleton) Text("›", fontSize = 24.sp)
    }
}
@Composable private fun DateHeader(prefix: String, label: String?) {
    Box(tag(prefix, "archiveDateHeader").fillMaxWidth().heightIn(min = 40.dp).padding(top = 16.dp, bottom = 8.dp)) {
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
private fun tag(prefix: String, name: String) = Modifier.testTag(prefix + name).semantics { testTagsAsResourceId = true }
