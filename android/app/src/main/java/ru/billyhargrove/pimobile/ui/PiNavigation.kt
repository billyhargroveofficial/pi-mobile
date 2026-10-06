package ru.billyhargrove.pimobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch
import ru.billyhargrove.pimobile.R
import ru.billyhargrove.pimobile.core.CatalogRow
import ru.billyhargrove.pimobile.core.SessionStatus

/** Shared presentation only. Navigation and host actions remain callbacks to their owners. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PiNavigation(rows: List<CatalogRow>, onSession: (CatalogRow) -> Unit, onHistory: () -> Unit, onSettings: () -> Unit,
    historyEnabled: Boolean = true, currentSession: String = "", openInitially: Boolean = false,
    content: @Composable (open: () -> Unit, drawerOpen: Boolean) -> Unit) {
    val prefix = LocalContext.current.packageName + ":id/"
    val drawer = rememberDrawerState(DrawerValue.Closed); val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }; var searching by remember { mutableStateOf(false) }
    fun open() { keyboard?.hide(); scope.launch { drawer.open() } }
    fun navigate(action: () -> Unit) { scope.launch { drawer.close(); action() } }
    LaunchedEffect(openInitially) { if (openInitially) open() }
    BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }
    val visible = rows.filter { it.isHeader() || (it.title() + " " + it.subtitle()).contains(query, true) }
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet(modifier = Modifier.fillMaxWidth(.84f).testTag(prefix + "navigationDrawer").semantics { testTagsAsResourceId = true },
            drawerContainerColor = if (isSystemInDarkTheme()) Color(0xff212121) else Color(0xfff2f2f2)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Pi Mobile", Modifier.weight(1f), fontSize = 24.sp)
                    CircleIcon(android.R.drawable.ic_menu_search, "Search open conversations", prefix + "drawerSearchButton") { searching = !searching }
                }
                if (searching) OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Search open conversations") }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { navigate(onHistory) }, enabled = historyEnabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Icon(painterResource(R.drawable.ic_timer), null, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(16.dp)); Text("History", Modifier.weight(1f), fontSize = 18.sp)
                }
                TextButton(onClick = { navigate(onSettings) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Icon(painterResource(R.drawable.ic_settings), null, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(16.dp)); Text("Settings", Modifier.weight(1f), fontSize = 18.sp)
                }
                HorizontalDivider(Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text("Open in Orca", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(visible, key = { "drawer:" + it.key() }) { row ->
                        if (row.isHeader()) Text(row.title(), Modifier.padding(vertical = 14.dp).semantics { heading() }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else Row(Modifier.fillMaxWidth().clickable { navigate { onSession(row) } }.semantics { selected = row.sessionId() == currentSession && currentSession.isNotEmpty() }
                            .padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(row.title(), Modifier.weight(1f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (row.status() == SessionStatus.RUNNING && row.connected()) Spacer(Modifier.size(8.dp).background(colorResource(R.color.accent), CircleShape))
                        }
                    }
                    item { TextButton(onClick = { navigate(onHistory) }, enabled = historyEnabled) { Text("See saved conversations…", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${rows.count { it.isHeader() && it.key().startsWith("ws:") }} workspaces", Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CircleIcon(R.drawable.ic_settings, "Settings", prefix + "drawerSettingsButton") { navigate(onSettings) }
                }
            }
        }
    }) { content(::open, drawer.isOpen) }
}
