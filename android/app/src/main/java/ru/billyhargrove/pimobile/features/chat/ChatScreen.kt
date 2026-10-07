package ru.billyhargrove.pimobile.features.chat

import android.graphics.Rect
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
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
    val scope = rememberCoroutineScope()
    var topHeight by remember { mutableStateOf(100.dp) }
    var bottomHeight by remember { mutableStateOf(if (chat.readOnly) 32.dp else 140.dp) }
    var canvasHeight by remember { mutableStateOf(800.dp) }
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
    LaunchedEffect(chat.arrivalRevision) {
        val revision = chat.arrivalRevision
        delay(420); chat.arrivalsShown(revision)
    }
    LaunchedEffect(restore, chat.items) {
        if (restore != null) {
            val position = chat.items.indexOfFirst { it.row.key == restore.key || it.row.key == restore.key.removePrefix("progress:") }
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
                followTailEnabled = chat.followTail && restore == null, smoothFollowTail = chat.smoothTail,
                arrivalRevision = chat.arrivalRevision, arrivingKeys = chat.arrivingKeys)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(chat.newActivity,
                        enter = if (ExpressiveMotion.enabled()) fadeIn(tween(220)) else EnterTransition.None,
                        exit = if (ExpressiveMotion.enabled()) fadeOut(tween(140)) else ExitTransition.None) {
                        Text("New activity", fontSize = 12.sp, color = colorResource(R.color.text_secondary),
                            modifier = Modifier.padding(end = 8.dp).clip(CircleShape)
                                .background(colorResource(R.color.surface_alt)).padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag(prefix + "newActivity").semantics { liveRegion = LiveRegionMode.Polite })
                    }
                    PiIconButton(R.drawable.ic_arrow_down, "Jump to latest message", prefix + "jumpToLatest", soft = true) {
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
        }
        ChatHeader(chat, orchestration, onBack, onOrchestration, prefix,
            Modifier.align(Alignment.TopCenter).onSizeChanged { topHeight = with(density) { it.height.toDp() } })
        ChatComposer(chat, behavior, onBehavior, orchestration, onOrchestration, onAttach, onVoice, onEffort,
            keyboardVisible, canvasHeight, topHeight, prefix,
            Modifier.align(Alignment.BottomCenter).onSizeChanged { bottomHeight = with(density) { it.height.toDp() } })
    }
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
