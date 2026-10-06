package ru.billyhargrove.pimobile.features.chat

import android.graphics.Rect
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import ru.billyhargrove.pimobile.R
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.net.MediaLoader
import ru.billyhargrove.pimobile.ui.*

/** Main chat presentation. All mutations go through the session owner or explicit platform callbacks. */
@Composable
fun ChatScreen(
    chat: ChatSession, list: LazyListState, loader: MediaLoader,
    behavior: CommandBuilder.Behavior, onBehavior: (CommandBuilder.Behavior) -> Unit,
    orchestration: String?, onOrchestration: () -> Unit,
    onBack: () -> Unit, onAttach: () -> Unit, onVoice: () -> Unit,
    onEffort: (Rect) -> Unit, onDocument: (String) -> Unit
) {
    val context = LocalContext.current
    val prefix = "${context.packageName}:id/"
    val background = colorResource(R.color.bg)
    var deliveryVisible by remember { mutableStateOf(false) }
    var effortBounds by remember { mutableStateOf(Rect()) }
    LaunchedEffect(chat, list) {
        list.interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is DragInteraction.Start -> chat.readerDragged()
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    snapshotFlow { !list.isScrollInProgress }.first { it }
                    chat.readerSettled(!list.canScrollForward)
                }
            }
        }
    }
    LaunchedEffect(chat, list) {
        snapshotFlow { Triple(list.firstVisibleItemIndex, list.isScrollInProgress, chat.followTail) }
            .collect { (index, moving, following) -> if (moving && !following && index < 3) chat.loadOlder() }
    }
    val restore = chat.restoreViewport
    LaunchedEffect(restore, chat.items) {
        if (restore != null) {
            val position = chat.items.indexOfFirst { it.row.key == restore.key }
            if (position >= 0) list.scrollToItem(position, (-restore.offset).coerceAtLeast(0))
            chat.readerSettled(restore.follow); chat.viewportRestored()
        }
    }
    Column(Modifier.fillMaxSize().background(background).statusBarsPadding()
        .semantics { testTagsAsResourceId = true }.testTag(prefix + "chatRoot")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag(prefix + "chatTopBar"),
            verticalAlignment = Alignment.CenterVertically) {
            ChatIcon(R.drawable.ic_menu, "Open navigation", prefix + "backButton", soft = true, onClick = onBack)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(chat.title.ifEmpty { "Chat" }, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = colorResource(R.color.text_primary),
                    modifier = Modifier.testTag(prefix + "chatTitleText"))
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color(StatusUi.sessionDotColor(
                        context, chat.status, chat.connection == ConnectionState.CONNECTED && chat.status != SessionStatus.OFFLINE))))
                    Text(statusText(chat), fontSize = 12.sp, color = colorResource(R.color.text_secondary),
                        maxLines = 1, modifier = Modifier.padding(start = 6.dp).testTag(prefix + "chatStatusText"))
                }
            }
            if (chat.status == SessionStatus.RUNNING) ChatIcon(R.drawable.ic_stop, "Stop", prefix + "stopButton",
                enabled = chat.canSend, tint = colorResource(R.color.danger), soft = true, onClick = chat::abort)
        }
        if (orchestration != null) OutlinedButton(onClick = onOrchestration,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 48.dp).testTag(prefix + "orchestrationButton")) {
            Text("$orchestration  ›", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (chat.truncated) Banner("Older messages are available in history", prefix + "truncatedBanner")
        if (chat.readOnly) Banner("Read-only inspection", prefix + "readOnlyBanner")
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val actions = remember(chat, onDocument) { TranscriptActions(chat::thumbnail, chat::retry, chat::restore, onDocument) }
            PiTranscript(chat.items, list, chat.tailRevision, loader, chat::toggle,
                Modifier.fillMaxSize().testTag(prefix + "messageList"),
                actions = if (chat.readOnly) null else actions,
                contentPadding = PaddingValues(top = 40.dp, bottom = 64.dp),
                followTailEnabled = chat.followTail && restore == null)
            Box(Modifier.fillMaxWidth().height(40.dp).background(Brush.verticalGradient(listOf(background, Color.Transparent))))
            if (chat.loading) Loading(Modifier.align(Alignment.Center), prefix)
            else if (chat.items.isEmpty()) Text("No messages yet", fontSize = 16.sp, color = colorResource(R.color.text_secondary),
                modifier = Modifier.align(Alignment.Center).padding(36.dp).testTag(prefix + "chatEmptyText"))
            if (chat.historyLoading) Surface(shape = CircleShape, shadowElevation = 3.dp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp).size(48.dp).testTag(prefix + "historySpinner")) {
                Box(contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp) }
            }
            Working(chat.metadata, Modifier.align(Alignment.BottomCenter), prefix)
        }
        if (!chat.readOnly) {
            Skills(chat, prefix)
            Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                .clip(RoundedCornerShape(28.dp)).background(colorResource(R.color.surface_input))
                .border(0.7.dp, colorResource(R.color.outline_soft), RoundedCornerShape(28.dp)).padding(4.dp)
                .testTag(prefix + "composerContainer")) {
                if (chat.preparing) Text("Preparing attachments…", fontSize = 12.sp,
                    color = colorResource(R.color.text_secondary), modifier = Modifier.padding(6.dp))
                if (chat.attachments.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).testTag(prefix + "attachmentStrip")) {
                    chat.attachments.forEachIndexed { index, attachment ->
                        Row(Modifier.padding(end = 8.dp).widthIn(max = 260.dp).heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(16.dp)).background(colorResource(R.color.surface_alt)),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(if (attachment.payload().isFile) "📎 ${attachment.payload().fileName()}" else "Image ${index + 1}",
                                fontSize = 13.sp, color = colorResource(R.color.text_primary), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 12.dp).weight(1f, fill = false))
                            ChatIcon(R.drawable.ic_close, "Remove attachment ${index + 1}", prefix + "removeAttachment$index",
                                enabled = !chat.preparing, onClick = { chat.removeAttachment(index) })
                        }
                    }
                }
                val density = LocalDensity.current
                val expanded = WindowInsets.ime.getBottom(density) > 0
                val actionSize = maxOf(48f, 24f * density.fontScale).dp
                val fieldHeight = if (expanded) maxOf(64f, 38f * density.fontScale).dp
                    else maxOf(48f, 22f * density.fontScale + 20f).dp
                val height by animateDpAsState(if (expanded) fieldHeight + actionSize else fieldHeight,
                    spring(dampingRatio = .85f, stiffness = 500f), label = "composer")
                Box(Modifier.fillMaxWidth().height(height).testTag(prefix + "composerEditor")) {
                    BasicTextField(chat.composer, onValueChange = { chat.composer = it },
                        textStyle = TextStyle(color = colorResource(R.color.text_primary), fontSize = 17.sp),
                        cursorBrush = SolidColor(colorResource(R.color.accent)), maxLines = 5, singleLine = !expanded,
                        modifier = Modifier.fillMaxWidth().height(fieldHeight)
                            .padding(start = if (expanded) 8.dp else 48.dp, end = if (expanded) 8.dp else 144.dp)
                            .padding(vertical = 10.dp).testTag(prefix + "composerInput"),
                        decorationBox = { inner ->
                            if (chat.composer.text.isEmpty()) Text("Message Pi", fontSize = 17.sp,
                                color = colorResource(R.color.text_secondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            inner()
                        })
                    Row(Modifier.fillMaxWidth().height(actionSize).align(if (expanded) Alignment.BottomCenter else Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                        ChatIcon(R.drawable.ic_add, "Attach file", prefix + "attachImageButton", enabled = !chat.preparing, onClick = onAttach)
                        Spacer(Modifier.weight(1f))
                        ChatIcon(if (behavior == CommandBuilder.Behavior.STEER) R.drawable.ic_steer else R.drawable.ic_queue,
                            "Delivery: ${if (behavior == CommandBuilder.Behavior.STEER) "Steer" else "Queue"}", prefix + "deliveryButton",
                            onClick = { deliveryVisible = true })
                        Box(Modifier.onGloballyPositioned { coordinates ->
                            val bounds = coordinates.boundsInWindow()
                            effortBounds = Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
                        }) {
                            ChatIcon(R.drawable.ic_effort, "Effort: ${EffortSlider.label(chat.configuration?.optString("thinkingLevel", "off") ?: "unknown")}",
                                prefix + "effortButton", onClick = { onEffort(effortBounds) })
                        }
                        ChatIcon(if (chat.voice) R.drawable.ic_mic else R.drawable.ic_send,
                            if (chat.transcribing) "Transcribing…" else if (chat.voice) "Dictation" else "Send", prefix + "sendButton",
                            enabled = chat.canSend && !chat.transcribing,
                            tint = colorResource(R.color.on_accent), filled = true,
                            onClick = { if (chat.voice) onVoice() else chat.send(behavior) })
                    }
                }
            }
        }
    }
    if (deliveryVisible) DeliveryPicker(behavior, onDismiss = { deliveryVisible = false }) {
        onBehavior(it); deliveryVisible = false
    }
}

@Composable
private fun ChatIcon(icon: Int, description: String, tag: String, enabled: Boolean = true,
    tint: Color = colorResource(R.color.text_primary), filled: Boolean = false, soft: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick, enabled = enabled, modifier = Modifier.size(48.dp).testTag(tag)
        .clip(CircleShape).background(if (filled) colorResource(R.color.accent) else if (soft) colorResource(R.color.surface_alt) else Color.Transparent)
        .then(if (soft) Modifier.border(0.7.dp, colorResource(R.color.outline_soft), CircleShape) else Modifier)
        .clearAndSetSemantics {
            testTagsAsResourceId = true; contentDescription = description; role = Role.Button
            if (enabled) onClick { onClick(); true } else disabled()
        }) {
        Icon(painterResource(icon), null, tint = if (enabled) tint else tint.copy(alpha = .38f), modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun statusText(chat: ChatSession): String {
    val context = LocalContext.current
    val running = chat.status == SessionStatus.RUNNING
    val execution = ExecutionInfo(chat.configuration, running)
    val state = when {
        chat.cached -> "Cached · syncing"
        chat.connection != ConnectionState.CONNECTED -> StatusUi.connectionLabel(context, chat.connection)
        chat.status == SessionStatus.OFFLINE -> "Pi disconnected"
        running -> "Working"
        else -> "Connected"
    }
    return buildString {
        append(state)
        if (execution.model.isNotEmpty()) {
            append(" · ${execution.model} · ${EffortSlider.label(execution.effort)}")
            if (execution.available) append(" · ${if (execution.tier == "fast") "⚡ " else ""}${execution.tierLabel(running)}")
        }
    }
}

@Composable
private fun Banner(text: String, tag: String) {
    Text(text, fontSize = 12.sp, color = colorResource(R.color.text_secondary),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag(tag))
}

@Composable
private fun Loading(modifier: Modifier, prefix: String) {
    var waiting by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(20_000); waiting = true }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (!waiting) CircularProgressIndicator(Modifier.size(48.dp).testTag(prefix + "chatLoadingSpinner"), strokeWidth = 3.dp)
        Text(if (waiting) "Waiting for Pi…" else "Loading conversation…", fontSize = 14.sp,
            color = colorResource(R.color.text_secondary), modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun Working(frame: org.json.JSONObject, modifier: Modifier, prefix: String) {
    val id = frame.optString("activeTurnId")
    val active = id.isNotEmpty()
    val turns = frame.optJSONArray("turns")
    val metrics = (0 until (turns?.length() ?: 0)).mapNotNull { turns?.optJSONObject(it) }
    val metric = if (active) metrics.find { it.optString("id") == id } else metrics.maxByOrNull { it.optLong("startedAt") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active, metric) { if (active) while (true) { now = System.currentTimeMillis(); delay(1000) } }
    if (!active && metric == null) return
    val duration = if (metric != null && metric.optLong("startedAt") > 0)
        " · ${DurationText.format((metric.optLong("finishedAt").takeIf { it > 0 } ?: now) - metric.optLong("startedAt"))}" else ""
    val background = colorResource(R.color.bg)
    Row(modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, background, background)))
        .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 8.dp).testTag(prefix + "workingBadge"),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_timer), null, tint = colorResource(R.color.text_secondary), modifier = Modifier.size(14.dp))
        Text((if (active) "Working" else "Worked") + duration, fontSize = 12.sp,
            color = colorResource(R.color.text_secondary), modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun Skills(chat: ChatSession, prefix: String) {
    val text = chat.composer.text
    if (!text.matches(Regex("\\$[A-Za-z0-9_-]*"))) return
    val skills = chat.configuration?.optJSONArray("skills") ?: return
    val matches = (0 until skills.length()).mapNotNull { skills.optJSONObject(it) }
        .filter { it.optString("name").startsWith(text.drop(1), ignoreCase = true) }.take(5)
    Column(Modifier.padding(horizontal = 12.dp).testTag(prefix + "skillSuggestions")) {
        matches.forEach { skill ->
            Text("${skill.optString("name")} · ${skill.optString("description")}", fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = colorResource(R.color.text_primary),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { chat.selectSkill(skill.optString("name")) }
                    .padding(horizontal = 12.dp).wrapContentHeight(Alignment.CenterVertically))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeliveryPicker(current: CommandBuilder.Behavior, onDismiss: () -> Unit, onSelect: (CommandBuilder.Behavior) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colorResource(R.color.surface)) {
        Text("Message delivery", fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        listOf(CommandBuilder.Behavior.FOLLOW_UP, CommandBuilder.Behavior.STEER).forEach { mode ->
            val steer = mode == CommandBuilder.Behavior.STEER
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(20.dp))
                .background(if (current == mode) colorResource(R.color.accent_soft) else Color.Transparent)
                .clickable { onSelect(mode) }.heightIn(min = 80.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(if (steer) R.drawable.ic_steer else R.drawable.ic_queue), null, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(if (steer) "Steer" else "Queue", fontSize = 16.sp)
                    Text(if (steer) "Guide the current task" else "Send after the current task", fontSize = 12.sp, color = colorResource(R.color.text_secondary))
                }
                if (current == mode) Text("✓", modifier = Modifier.padding(start = 8.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
