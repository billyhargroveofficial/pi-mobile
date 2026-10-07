package ru.billyhargrove.pimobile.features.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import ru.billyhargrove.pimobile.BuildConfig
import ru.billyhargrove.pimobile.R
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.ui.*

/** Navigation and grouped settings present real Orca data and explicit owner callbacks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(state: CatalogSession, usage: UsageCards, checkUpdates: () -> Unit, appearance: () -> Unit, updateStatus: String = "") {
    val prefix = LocalContext.current.packageName + ":id/"
    fun tag(name: String) = Modifier.testTag(prefix + name).semantics { testTagsAsResourceId = true }
    BackHandler(state.screen == CatalogSession.Screen.Settings) { state.screen = CatalogSession.Screen.Catalog }
        Column(tag("mainRoot").fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(tag("mainTopBar").fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.screen == CatalogSession.Screen.Settings) CircleIcon(R.drawable.ic_back, "Back", prefix + "navigationButton") {
                    state.screen = CatalogSession.Screen.Catalog
                }
                Text(if (state.screen == CatalogSession.Screen.Settings) "Settings" else "Pi Mobile", Modifier.weight(1f), fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.screen == CatalogSession.Screen.Catalog) {
                    CircleIcon(R.drawable.ic_refresh, "Refresh sessions", prefix + "refreshButton", !state.refreshBusy) { state.refresh(); usage.refresh() }
                    CircleIcon(R.drawable.ic_settings, "Settings", prefix + "settingsButton") { state.screen = CatalogSession.Screen.Settings }
                }
            }
            if (state.message.isNotEmpty()) Text(state.message, tag("connectErrorText").fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }, color = if (state.messageError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            if (state.screen == CatalogSession.Screen.Settings) Settings(state, prefix, checkUpdates, appearance, updateStatus, Modifier.weight(1f))
            else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(tag("connectionDot").size(6.dp).background(Color(StatusUi.connectionDotColor(LocalContext.current, state.connection)), CircleShape))
                    Text(StatusUi.connectionLabel(LocalContext.current, state.connection), tag("connectionStatusText"), fontSize = 12.sp)
                    Text(state.connectionDetail, tag("connectionDetailText").weight(1f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyColumn(tag("catalogList").fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = "usage") { usage.Content() }
                    item(key = "summary") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val running = state.catalog.sessions().count { it.connected() && it.status() == SessionStatus.RUNNING }
                            Text(if (state.launching) "Opening conversation in Orca…" else "${state.catalog.workspaces().size} workspaces · ${state.catalog.sessions().size + state.catalog.terminals().size} tabs" + if (running > 0) " · $running running" else "",
                                tag("catalogSummary").weight(1f), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = state::history, enabled = !state.launching, modifier = tag("historyButton")) { Text("History") }
                        }
                    }
                    if (state.rows.isEmpty()) item(key = "empty") { Text("No open Pi tabs.\nResume a conversation from History.", tag("catalogEmptyText").padding(24.dp), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(state.rows, key = CatalogRow::key) { row ->
                        if (row.isHeader()) Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(row.title(), tag("groupTitle").weight(1f).semantics { heading() }, fontSize = 15.sp, maxLines = 2)
                            Text("${row.count()}", tag("groupCount").padding(12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (row.key().startsWith("ws:")) CircleIcon(R.drawable.ic_add, "New session: ${row.title()}", prefix + "newSessionButton", !state.launching) { state.newSession(row.key().substring(3), row.title()) }
                        } else SwipeAction(row.connected() && row.kind() == CatalogRow.Kind.SESSION, "Close", { state.requestClose(row) }) {
                            Row(tag("catalogRowRoot").fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                                .clickable { state.open(row) }.semantics { contentDescription = "${if (row.readOnly()) "Terminal" else "Pi session"}: ${row.title()}" }
                                .padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Spacer(tag("rowStatusDot").size(6.dp).background(Color(StatusUi.sessionDotColor(LocalContext.current, row.status(), row.connected())), CircleShape))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(row.title(), tag("rowTitle"), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(row.subtitle().substringAfterLast(" · "), tag("rowSubtitle"), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(row.badge(), tag("rowBadge"), fontSize = 10.sp, color = Color(StatusUi.sessionBadgeColor(LocalContext.current, row.status())))
                            }
                        }
                    }
                }
            }
        }
    state.confirmation?.let { prompt -> AlertDialog(onDismissRequest = state::cancelConfirmation, title = { Text(prompt.title) }, text = { Text(prompt.message) },
        dismissButton = { TextButton(onClick = state::cancelConfirmation) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = state::confirm) { Text(prompt.action, color = if (prompt.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) } }) }
}

@Composable
private fun Settings(state: CatalogSession, prefix: String, checkUpdates: () -> Unit, appearance: () -> Unit, updateStatus: String, modifier: Modifier) {
    fun tag(name: String) = Modifier.testTag(prefix + name).semantics { testTagsAsResourceId = true }
    var showToken by remember { mutableStateOf(false) }
    Column(modifier.then(tag("connectPanel")).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Connection", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(state.url, { state.url = it }, label = { Text("Server URL") }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = tag("connectUrlInput").fillMaxWidth())
            OutlinedTextField(state.typedToken, { state.typedToken = it }, label = { Text("Access token") }, placeholder = { Text(if (state.hasStoredToken) "Saved securely · leave blank to keep" else "Enter your access token") },
                singleLine = true, visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password), modifier = tag("connectTokenInput").fillMaxWidth(),
                trailingIcon = { TextButton(onClick = { showToken = !showToken }) { Text(if (showToken) "Hide" else "Show", fontSize = 11.sp) } })
            Button(onClick = state::connect, enabled = state.connection !in listOf(ConnectionState.CONNECTING, ConnectionState.RECONNECTING), modifier = tag("connectButton").fillMaxWidth().heightIn(min = 56.dp)) { Text("Connect") }
            if (LocalConfiguration.current.fontScale > 1.5f) Column {
                TextButton(onClick = state::health, enabled = !state.healthBusy, modifier = tag("healthCheckButton").fillMaxWidth().heightIn(min = 48.dp)) { Text(if (state.healthBusy) "Checking…" else "Check server") }
                TextButton(onClick = state::disconnect, enabled = state.connection !in listOf(ConnectionState.IDLE, ConnectionState.DISCONNECTED), modifier = tag("disconnectButton").fillMaxWidth().heightIn(min = 48.dp)) { Text("Disconnect", color = MaterialTheme.colorScheme.error) }
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = state::health, enabled = !state.healthBusy, modifier = tag("healthCheckButton").weight(1f).heightIn(min = 48.dp)) { Text(if (state.healthBusy) "Checking…" else "Check server") }
                TextButton(onClick = state::disconnect, enabled = state.connection !in listOf(ConnectionState.IDLE, ConnectionState.DISCONNECTED), modifier = tag("disconnectButton").weight(1f).heightIn(min = 48.dp)) { Text("Disconnect", color = MaterialTheme.colorScheme.error) }
            }
        }
        Spacer(Modifier.height(8.dp)); Text("App", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))) {
            SettingsRow("Message color", "Your side of the conversation", R.drawable.ic_chat, tag("bubbleColorSettings"), appearance)
            HorizontalDivider(color = MaterialTheme.colorScheme.background, thickness = 2.dp)
            SettingsRow("Check for updates", updateStatus.ifEmpty { "Installed · ${BuildConfig.VERSION_NAME}" }, R.drawable.ic_refresh, tag("checkUpdates"), checkUpdates)
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, icon: Int, modifier: Modifier, click: () -> Unit) {
    Row(modifier.fillMaxWidth().clickable(onClick = click).padding(20.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(painterResource(icon), null, Modifier.size(24.dp))
        Column(Modifier.weight(1f)) { Text(title, fontSize = 17.sp); Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(18.dp))
    }
}
