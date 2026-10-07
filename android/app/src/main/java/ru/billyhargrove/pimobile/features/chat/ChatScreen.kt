package ru.billyhargrove.pimobile.features.chat

import android.graphics.Rect
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    orchestration: org.json.JSONObject?, onOrchestration: (String, String, String) -> Unit,
    onBack: () -> Unit, onAttach: () -> Unit, onVoice: () -> Unit,
    onEffort: (Rect) -> Unit, onDocument: (String) -> Unit
) {
    val context = LocalContext.current
    val prefix = "${context.packageName}:id/"
    val background = colorResource(R.color.bg)
    val density = LocalDensity.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    var deliveryVisible by remember { mutableStateOf(false) }
    var effortBounds by remember { mutableStateOf(Rect()) }
    val scope = rememberCoroutineScope()
    var topHeight by remember { mutableStateOf(100.dp) }
    var bottomHeight by remember { mutableStateOf(if (chat.readOnly) 32.dp else 140.dp) }
    var canvasHeight by remember { mutableStateOf(800.dp) }
    var dockHeight by remember { mutableStateOf(0.dp) }
    val keyboardBottom = with(density) { WindowInsets.ime.getBottom(density).toDp() }
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
    Box(Modifier.fillMaxSize().background(background).onSizeChanged { canvasHeight = with(density) { it.height.toDp() } }
        .semantics { testTagsAsResourceId = true }.testTag(prefix + "chatRoot")) {
        // Content scrolls UNDER both islands and the system bars. Measured padding
        // makes the first/last message reachable without shrinking the viewport.
        Box(Modifier.fillMaxSize()) {
            val actions = remember(chat, onDocument) { TranscriptActions(chat::thumbnail, chat::retry, chat::restore, onDocument) }
            PiTranscript(chat.items, list, chat.tailRevision, loader, chat::toggle,
                Modifier.fillMaxSize().testTag(prefix + "messageList"),
                actions = if (chat.readOnly) null else actions,
                contentPadding = PaddingValues(top = topHeight + 32.dp, bottom = bottomHeight + 48.dp),
                followTailEnabled = chat.followTail && restore == null)
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(topHeight + 40.dp)
                .background(Brush.verticalGradient(0f to background, .65f to background.copy(alpha = .92f), 1f to Color.Transparent))
                .testTag(prefix + "chatTopFade"))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottomHeight + 48.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, background.copy(alpha = .92f)),
                    endY = with(density) { 64.dp.toPx() })).testTag(prefix + "chatBottomFade"))
            if (chat.loading || chat.items.isEmpty()) Box(Modifier.fillMaxSize().padding(top = topHeight, bottom = bottomHeight)) {
                if (chat.loading) Loading(Modifier.align(Alignment.Center), prefix)
                else Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!keyboardVisible) Text("Ready when you are", fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium,
                        color = colorResource(R.color.text_primary))
                    Text("No messages yet", fontSize = 14.sp, color = colorResource(R.color.text_secondary),
                        modifier = Modifier.padding(top = if (keyboardVisible) 0.dp else 12.dp).testTag(prefix + "chatEmptyText"))
                }
            }
            if (chat.historyLoading) Surface(shape = CircleShape, shadowElevation = 3.dp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp).size(48.dp).testTag(prefix + "historySpinner")) {
                Box(contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp) }
            }
            if (list.canScrollForward && !chat.followTail) Box(Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = bottomHeight + 12.dp)) {
                ChatIcon(R.drawable.ic_arrow_down, "Jump to latest message", prefix + "jumpToLatest", soft = true) {
                    scope.launch {
                        if (chat.items.isNotEmpty()) {
                            list.scrollToItem(chat.items.lastIndex)
                            // Native Markdown can remeasure after the index jump. Follow its
                            // bounded measured extent across frames before marking follow-tail.
                            repeat(3) {
                                withFrameNanos { }
                                val layout = list.layoutInfo
                                val last = layout.visibleItemsInfo.lastOrNull()
                                val remaining = last?.let { it.offset + it.size + layout.afterContentPadding - layout.viewportEndOffset } ?: 0
                                if (remaining > 0) list.scrollBy(remaining.toFloat())
                            }
                            chat.readerSettled(!list.canScrollForward)
                        }
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()
            .onSizeChanged { topHeight = with(density) { it.height.toDp() } }.statusBarsPadding().padding(top = 6.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag(prefix + "chatTopBar"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChatIcon(R.drawable.ic_back, "Back to sessions", prefix + "backButton", soft = true, onClick = onBack)
                Column(Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                    .background(colorResource(R.color.surface_input)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(chat.title.ifEmpty { "Chat" }, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, color = colorResource(R.color.text_primary),
                        modifier = Modifier.testTag(prefix + "chatTitleText"))
                    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(Color(StatusUi.sessionDotColor(
                            context, chat.status, chat.connection == ConnectionState.CONNECTED && chat.status != SessionStatus.OFFLINE))))
                        Text(statusText(chat), fontSize = 11.sp, color = colorResource(R.color.text_secondary), maxLines = 1,
                            modifier = Modifier.padding(start = 6.dp).testTag(prefix + "chatStatusText"))
                    }
                }
                if (chat.status == SessionStatus.RUNNING && !chat.readOnly) ChatIcon(R.drawable.ic_stop, "Stop", prefix + "stopButton",
                    enabled = chat.canSend, tint = colorResource(R.color.danger), soft = true, onClick = chat::abort)
                else if (orchestration?.let { OrchestrationData.agents(it, null).isNotEmpty() || OrchestrationData.objects(it.optJSONArray("workflows")).isNotEmpty() } == true)
                    ChatIcon(R.drawable.ic_queue, "Activity history", prefix + "orchestrationHistory", onClick = { onOrchestration("", "", "Activity") })
            }
            if (chat.notice.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp)
                .testTag(prefix + "chatNotice").semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
                Text(chat.notice, fontSize = 12.sp, color = colorResource(R.color.text_secondary), modifier = Modifier.weight(1f))
                ChatIcon(R.drawable.ic_close, "Dismiss message", prefix + "dismissNotice", onClick = chat::dismissNotice)
            }
            if (chat.readOnly) Banner("Read-only inspection", prefix + "readOnlyBanner")
        }
        if (!chat.readOnly) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onSizeChanged { bottomHeight = with(density) { it.height.toDp() } }
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)).padding(bottom = 8.dp)) {
            Column(Modifier.fillMaxWidth().onSizeChanged { dockHeight = with(density) { it.height.toDp() } }) {
                orchestration?.let { ActiveOrchestration(it, onOrchestration, compact = keyboardVisible) }
                val activeDock = orchestration?.let { OrchestrationData.activeWorkflows(it).isNotEmpty() || OrchestrationData.activeStandalone(it).isNotEmpty() } == true
                if (!keyboardVisible || (!activeDock && chat.queue.isEmpty())) Working(chat.metadata, Modifier, prefix)
                MessageQueue(chat, prefix, compact = keyboardVisible)
                Skills(chat, prefix)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colorResource(R.color.surface_alt)).padding(6.dp)
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
                val expanded = keyboardVisible
                val focusManager = LocalFocusManager.current
                var hadKeyboard by remember { mutableStateOf(false) }
                LaunchedEffect(expanded) {
                    // A user-dismissed IME must not reopen when the compact composer recomposes.
                    if (hadKeyboard && !expanded) focusManager.clearFocus()
                    hadKeyboard = expanded
                }
                val actionSize = maxOf(48f, 24f * density.fontScale).dp
                val lineHeight = with(density) { 22.sp.toDp() }
                // Leave reading space even with active agents, Queue, IME and 2× type.
                // Line capacity changes, never the multiline IME mode or caret owner.
                val room = canvasHeight - topHeight - keyboardBottom - dockHeight - actionSize - 120.dp
                val lines = if (expanded) ((room - 20.dp) / lineHeight).toInt().coerceIn(1, 3) else 1
                val fieldHeight = if (expanded) lineHeight * lines + 20.dp else maxOf(48.dp, lineHeight + 20.dp)
                // Writing and actions never compete for the same horizontal space.
                val height by animateDpAsState(fieldHeight + actionSize,
                    if (ExpressiveMotion.enabled()) spring(dampingRatio = .85f, stiffness = 500f) else snap(), label = "composer")
                Box(Modifier.fillMaxWidth().height(height).testTag(prefix + "composerEditor")) {
                    BasicTextField(chat.composer, onValueChange = { chat.composer = it },
                        textStyle = TextStyle(color = colorResource(R.color.text_primary), fontSize = 17.sp, lineHeight = 22.sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = false)),
                        cursorBrush = SolidColor(colorResource(R.color.accent)), maxLines = lines, singleLine = false,
                        modifier = Modifier.fillMaxWidth().height(fieldHeight)
                            .padding(horizontal = 12.dp)
                            .padding(vertical = if (expanded) 10.dp else (fieldHeight - lineHeight) / 2)
                            .testTag(prefix + "composerInput"),
                        decorationBox = { inner ->
                            if (chat.composer.text.isEmpty()) Text("Message Pi", fontSize = 17.sp,
                                color = colorResource(R.color.text_secondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            inner()
                        })
                    Row(Modifier.fillMaxWidth().height(actionSize).align(Alignment.BottomCenter), verticalAlignment = Alignment.CenterVertically) {
                        ChatIcon(R.drawable.ic_add, "Attach file", prefix + "attachImageButton", enabled = !chat.preparing, onClick = onAttach)
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
                        Spacer(Modifier.weight(1f))
                        ChatIcon(if (chat.voice) R.drawable.ic_mic else R.drawable.ic_send,
                            if (chat.transcribing) "Transcribing…" else if (chat.voice) "Dictation" else "Send", prefix + "sendButton",
                            enabled = chat.canSend && !chat.transcribing,
                            tint = colorResource(R.color.on_accent), filled = true,
                            loading = chat.transcribing,
                            onClick = { if (chat.voice) onVoice() else chat.send(behavior) })
                    }
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
    tint: Color = colorResource(R.color.text_primary), filled: Boolean = false, soft: Boolean = false,
    loading: Boolean = false, onClick: () -> Unit) {
    Box(if (loading) Modifier.testTag("${LocalContext.current.packageName}:id/transcriptionSpinner") else Modifier) {
    IconButton(onClick, enabled = enabled, modifier = Modifier.size(48.dp).testTag(tag)
        .clip(CircleShape).background(if (soft) colorResource(R.color.surface_alt) else Color.Transparent)
        .clearAndSetSemantics {
            testTagsAsResourceId = true; contentDescription = description; role = Role.Button
            if (enabled) onClick { onClick(); true } else disabled()
        }) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (filled) colorResource(R.color.accent) else Color.Transparent),
            contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = tint, strokeWidth = 2.dp)
            else Icon(painterResource(icon), null, tint = if (enabled) tint else tint.copy(alpha = .38f), modifier = Modifier.size(22.dp))
        }
    }
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
    Row(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).testTag(prefix + "workingBadge"),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_timer), null, tint = colorResource(R.color.text_secondary), modifier = Modifier.size(14.dp))
        Text((if (active) "Working" else "Worked") + duration, fontSize = 12.sp,
            color = colorResource(R.color.text_secondary), modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun MessageQueue(chat: ChatSession, prefix: String, compact: Boolean) {
    if (chat.queue.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        .clip(RoundedCornerShape(20.dp)).background(colorResource(R.color.surface_alt))
        .testTag(prefix + "messageQueue")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = !expanded }
            .padding(horizontal = 12.dp).testTag(prefix + "queueHeader")
            .semantics {
                role = Role.Button; stateDescription = if (expanded) "Expanded" else "Collapsed"
                contentDescription = "Queue: ${chat.queue.size} messages"; liveRegion = LiveRegionMode.Polite
            },
            verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_queue), null, Modifier.size(16.dp), tint = colorResource(R.color.text_secondary))
            Text("Queue · ${chat.queue.size}", Modifier.weight(1f).padding(start = 8.dp), fontSize = 12.sp,
                fontWeight = FontWeight.Medium, color = colorResource(R.color.text_primary))
            Text(if (expanded) "−" else "+", Modifier.clearAndSetSemantics {}, fontSize = 18.sp, color = colorResource(R.color.text_secondary))
        }
        Column(Modifier.fillMaxWidth().heightIn(max = if (expanded) { if (compact) 72.dp else 128.dp } else 56.dp)
            .then(if (expanded) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
            (if (expanded) chat.queue else chat.queue.take(1)).forEachIndexed { index, message ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag(prefix + "queueRow"), verticalAlignment = Alignment.Top) {
                    Text("${index + 1}", Modifier.padding(end = 10.dp), fontSize = 11.sp, color = colorResource(R.color.text_secondary))
                    Text(message.text().ifBlank { "${message.images().size} images" }, Modifier.weight(1f), fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, color = colorResource(R.color.text_primary))
                    val state = when {
                        message.localState() == ChatMessage.LocalState.SENDING -> "Sending"
                        message.localState() == ChatMessage.LocalState.UNCERTAIN -> "Unknown"
                        chat.cached || chat.connection != ConnectionState.CONNECTED || chat.queueRestored(message.requestId()) -> "Last known"
                        else -> "Queued"
                    }
                    Text(state, Modifier.padding(start = 10.dp), fontSize = 10.sp, color = colorResource(R.color.text_secondary))
                }
            }
        }
    }
}

@Composable
private fun Skills(chat: ChatSession, prefix: String) {
    val text = chat.composer.text
    if (!text.matches(Regex("\\$[A-Za-z0-9_-]*"))) return
    val skills = chat.configuration?.optJSONArray("skills") ?: return
    val matches = (0 until skills.length()).mapNotNull { skills.optJSONObject(it) }
        .filter { it.optString("name").startsWith(text.drop(1), ignoreCase = true) }.take(5)
    Column(Modifier.padding(horizontal = 12.dp).heightIn(max = 144.dp).verticalScroll(rememberScrollState()).testTag(prefix + "skillSuggestions")) {
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
