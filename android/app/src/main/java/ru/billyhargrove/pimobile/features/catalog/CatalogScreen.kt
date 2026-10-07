package ru.billyhargrove.pimobile.features.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.font.FontWeight
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
    val pages = rememberSaveableStateHolder()
    AnimatedContent(state.screen, modifier = tag("mainRoot").fillMaxSize().background(MaterialTheme.colorScheme.background).clipToBounds(),
        transitionSpec = {
            if (!ExpressiveMotion.enabled()) EnterTransition.None togetherWith ExitTransition.None
            else if (targetState == CatalogSession.Screen.Settings)
                slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it } togetherWith
                    slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it / 4 } + fadeOut(tween(220))
            else slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it / 4 } + fadeIn(tween(220)) togetherWith
                slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { it }
        }, label = "catalogSettingsNavigation") { screen ->
        pages.SaveableStateProvider(screen.name) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(tag("mainTopBar").fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (screen == CatalogSession.Screen.Settings) CircleIcon(R.drawable.ic_back, "Back", prefix + "navigationButton") {
                    state.screen = CatalogSession.Screen.Catalog
                }
                if (screen == CatalogSession.Screen.Settings) Text("Settings", tag("mainHostTitle").weight(1f),
                    style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                else {
                    // Connection belongs to HOST identity, not the right-hand action cluster.
                    Row(tag("hostIdentity").weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val connectionInk = Color(StatusUi.connectionDotColor(LocalContext.current, state.connection))
                    val connectionDescription = StatusUi.connectionLabel(LocalContext.current, state.connection)
                    val statusModifier = tag("connectionIndicator").size(16.dp).semantics {
                        contentDescription = connectionDescription
                    }
                    if (state.connection in setOf(ConnectionState.CONNECTING, ConnectionState.RECONNECTING))
                        CircularProgressIndicator(statusModifier, strokeWidth = 1.5.dp, color = connectionInk)
                    else Canvas(statusModifier) {
                        val width = size.width * .09f
                        drawCircle(connectionInk, size.minDimension * .42f, style = Stroke(width))
                        fun point(x: Float, y: Float) = Offset(size.width * x, size.height * y)
                        if (state.connection == ConnectionState.CONNECTED) {
                            drawLine(connectionInk, point(.27f, .5f), point(.44f, .66f), width, StrokeCap.Round)
                            drawLine(connectionInk, point(.44f, .66f), point(.73f, .34f), width, StrokeCap.Round)
                        } else if (state.connection == ConnectionState.ERROR) {
                            drawLine(connectionInk, point(.33f, .33f), point(.67f, .67f), width, StrokeCap.Round)
                            drawLine(connectionInk, point(.33f, .67f), point(.67f, .33f), width, StrokeCap.Round)
                        } else drawLine(connectionInk, point(.22f, .78f), point(.78f, .22f), width, StrokeCap.Round)
                    }
                    Text(state.hostLabel(), tag("mainHostTitle").weight(1f), style = MaterialTheme.typography.titleLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    // Two equally sized 48dp targets / 22dp glyph boxes on one 4dp rhythm.
                    Row(tag("mainHeaderActions"), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircleIcon(R.drawable.ic_refresh, "Refresh sessions", prefix + "refreshButton", !state.refreshBusy) { state.refresh(); usage.refresh() }
                        CircleIcon(R.drawable.ic_settings, "Settings", prefix + "settingsButton") { state.screen = CatalogSession.Screen.Settings }
                    }
                }
            }
            if (state.message.isNotEmpty()) Text(state.message, tag("connectErrorText").fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }, color = if (state.messageError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            if (screen == CatalogSession.Screen.Settings) Settings(state, prefix, checkUpdates, appearance, updateStatus, Modifier.weight(1f))
            else {
                LazyColumn(tag("catalogList").fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    item(key = "usage") { usage.Content() }
                    item(key = "summary") {
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Conversations", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
                                val running = state.catalog.sessions().count { it.connected() && it.status() == SessionStatus.RUNNING }
                                Text(if (state.launching) "Opening conversation in Orca…" else "${state.catalog.workspaces().size} workspaces · ${state.catalog.sessions().size + state.catalog.terminals().size} tabs" + if (running > 0) " · $running running" else "",
                                    tag("catalogSummary").padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = state::history, enabled = !state.launching, modifier = tag("historyButton")) {
                                Text("History", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (state.rows.isEmpty()) item(key = "empty") { Text("No open Pi tabs.\nResume a conversation from History.", tag("catalogEmptyText").padding(24.dp), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(state.rows, key = CatalogRow::key) { row ->
                        if (row.isHeader()) Row(Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text((if (row.key().startsWith("ws:")) "Workspace · " else "") + row.title(), tag("groupTitle").weight(1f).semantics { heading() },
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            Text("${row.count()}", tag("groupCount").padding(12.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (row.key().startsWith("ws:")) CircleIcon(R.drawable.ic_add, "New session: ${row.title()}", prefix + "newSessionButton", !state.launching) { state.newSession(row.key().substring(3), row.title()) }
                        } else SwipeAction(row.connected() && row.kind() == CatalogRow.Kind.SESSION, "Close", { state.requestClose(row) }) {
                            // Opaque CANVAS color hides the destructive swipe underlay
                            // at rest without reinstating a card/bubble surface.
                            Row(tag("catalogRowRoot").fillMaxWidth().background(MaterialTheme.colorScheme.background).heightIn(min = 48.dp)
                                .clickable { state.open(row) }.semantics {
                                    contentDescription = "${if (row.readOnly()) "Terminal" else "Pi session"}: ${row.title()}"
                                    stateDescription = when { row.readOnly() -> "Extension required"; !row.connected() -> "Disconnected"
                                        row.status() == SessionStatus.RUNNING -> "Running"; else -> "Idle" }
                                }.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(row.title(), tag("rowTitle"), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (row.readOnly()) Text("Extension required", Modifier.padding(top = 2.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (row.connected() && row.status() == SessionStatus.RUNNING)
                                    CircularProgressIndicator(tag("rowRunningSpinner").size(14.dp).clearAndSetSemantics {}, strokeWidth = 1.5.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
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
        Text("Connection", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(state.url, { state.url = it }, colors = PiFieldColors(), label = { Text("Server URL") }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = tag("connectUrlInput").fillMaxWidth())
            TextField(state.typedToken, { state.typedToken = it }, colors = PiFieldColors(), label = { Text("Access token") }, placeholder = { Text(if (state.hasStoredToken) "Saved securely · leave blank to keep" else "Enter your access token") },
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
        Spacer(Modifier.height(8.dp)); Text("App", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))) {
            SettingsRow("Message color", "Your side of the conversation", R.drawable.ic_chat, tag("bubbleColorSettings"), appearance)
            SettingsRow("Check for updates", updateStatus.ifEmpty { "Installed · ${BuildConfig.VERSION_NAME}" }, R.drawable.ic_refresh, tag("checkUpdates"), checkUpdates)
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, icon: Int, modifier: Modifier, click: () -> Unit) {
    Row(modifier.fillMaxWidth().clickable(onClick = click).padding(20.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(painterResource(icon), null, Modifier.size(24.dp))
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyLarge); Text(subtitle, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(18.dp))
    }
}
