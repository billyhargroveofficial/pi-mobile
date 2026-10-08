package ru.billyhargrove.pimobile.features.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.billyhargrove.pimobile.R
import ru.billyhargrove.pimobile.core.ConnectionProfile

/** Safe metadata only. Saving/switching/forgetting and credentials never belong to the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComputerPicker(computers: List<ConnectionProfile>, activeId: String?, connected: Boolean, error: String,
    onSelect: (String) -> Unit, onEdit: (String) -> Unit, onAdd: () -> Unit, onDismiss: () -> Unit) {
    val prefix = LocalContext.current.packageName + ":id/"
    fun tag(name: String) = Modifier.testTag(prefix + name).semantics { testTagsAsResourceId = true }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(tag("computerPicker").fillMaxWidth().heightIn(max = 600.dp).navigationBarsPadding()) {
            Text("Computers", tag("computerPickerTitle").padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            Text("Switch the mobile connection. Work on each computer keeps running.", Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (error.isNotEmpty()) Text(error, Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            LazyColumn(tag("computerList").fillMaxWidth().weight(1f, fill = false)) {
                if (computers.isEmpty()) item { Text("No saved computers yet", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(computers, key = ConnectionProfile::id) { computer ->
                    Row(tag("computerRow").fillMaxWidth().heightIn(min = 64.dp).padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable(role = Role.Button) { onSelect(computer.id) }.padding(vertical = 12.dp)
                            .semantics { contentDescription = "Use computer: ${computer.name}"; stateDescription = when {
                                computer.id == activeId && connected -> "Connected"; computer.id == activeId -> "Selected"
                                !computer.hasToken -> "Token needed"; else -> "Saved" } }) {
                            Text(computer.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(computer.baseUrl, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (computer.id == activeId || !computer.hasToken) Text(if (!computer.hasToken) "Token needed" else if (connected) "Connected" else "Selected",
                                Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onEdit(computer.id) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "Edit computer: ${computer.name}" }) {
                            Icon(painterResource(R.drawable.ic_settings), null, Modifier.size(22.dp))
                        }
                    }
                }
            }
            TextButton(onClick = onAdd, modifier = tag("addComputerButton").fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp)) { Text("Add computer") }
            Spacer(Modifier.height(8.dp))
        }
    }
}
