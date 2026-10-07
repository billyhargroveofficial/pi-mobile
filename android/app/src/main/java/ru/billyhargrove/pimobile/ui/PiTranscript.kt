package ru.billyhargrove.pimobile.ui

import android.graphics.Bitmap
import android.widget.TextView
import java.util.Locale
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ru.billyhargrove.pimobile.R
import ru.billyhargrove.pimobile.core.ChatMessage
import ru.billyhargrove.pimobile.core.ToolArguments
import ru.billyhargrove.pimobile.core.TranscriptPresentation
import ru.billyhargrove.pimobile.net.MediaLoader

/** Optional main-chat actions. Omitting them leaves agent inspection read-only. */
class TranscriptActions(
    val thumbnail: (String, Int) -> Bitmap?,
    val retry: (ChatMessage) -> Unit,
    val restore: (ChatMessage) -> Unit,
    val document: (String) -> Unit
)

/** Callers own fetching, history, state and navigation. No transport or commands in rendering. */
@Composable
fun PiTranscript(
    items: List<TranscriptPresentation.Item>,
    state: LazyListState,
    followTailRevision: Int,
    loader: MediaLoader,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    actions: TranscriptActions? = null,
    contentPadding: PaddingValues = PaddingValues(top = 8.dp, bottom = 24.dp),
    followTailEnabled: Boolean = true
) {
    val context = LocalContext.current
    val currentActions by rememberUpdatedState(actions)
    val markdown = remember(context) { MarkdownRenderer(context) { currentActions?.document?.invoke(it) } }
    DisposableEffect(markdown) { onDispose { markdown.close() } }
    LaunchedEffect(followTailRevision) {
        if (followTailEnabled && items.isNotEmpty() && !state.isScrollInProgress) {
            state.scrollToItem(items.lastIndex)
            state.scrollBy(Float.MAX_VALUE)
        }
    }
    // Receipt belongs to the newest USER submission, not each historical bubble.
    // A newer unacknowledged message hides the previous receipt instead of implying delivery.
    val lastUserKey = items.lastOrNull { it.row.message?.role() == ChatMessage.Role.USER }?.row?.message?.stableKey()
    LazyColumn(modifier, state = state, contentPadding = contentPadding) {
        items(items, key = { it.row.key }, contentType = { if (it.row.header) "tools" else "message" }) { item ->
            if (item.row.header) ToolGroup(item, markdown) { onToggle(item.row.group) }
            else item.row.message?.let { MessageBubble(it, markdown, loader, actions, it.stableKey() == lastUserKey) }
        }
    }
}

@Composable
private fun ToolGroup(item: TranscriptPresentation.Item, markdown: MarkdownRenderer, onToggle: () -> Unit) {
    val context = LocalContext.current
    val tagPrefix = "${context.packageName}:id/"
    val background = colorResource(R.color.bg)
    // Keep scroll state while collapsed and while an offscreen LazyColumn item is disposed.
    val logState = rememberLazyListState()
    val wasAtBottom = rememberSaveable { mutableStateOf(true) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(logState) {
        snapshotFlow { logState.layoutInfo.totalItemsCount to !logState.canScrollForward }.collect { (count, atBottom) ->
            if (count > 0) wasAtBottom.value = atBottom
        }
    }
    LaunchedEffect(item.tools) {
        if (item.tools.isNotEmpty() && (!initialized || wasAtBottom.value) && !logState.isScrollInProgress) {
            logState.scrollToItem(item.tools.lastIndex)
            logState.scrollBy(Float.MAX_VALUE)
            initialized = true
        }
    }
    val lastTool = item.tools.lastOrNull { it.role() == ChatMessage.Role.TOOL_RESULT && it.toolName().isNotBlank() }?.toolName() ?: "Activity"
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp).testTag(tagPrefix + "toolSegment")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggle)
            .testTag(tagPrefix + "workHeader").semantics {
                contentDescription = if (item.expanded) "Collapse tools" else "Expand tools"
                stateDescription = "${if (item.expanded) "Expanded" else "Collapsed"}, $lastTool, ${item.row.count} calls"
            }, verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(lastTool, modifier = Modifier.weight(1f, fill = false).clipToBounds(), transitionSpec = {
                if (!ExpressiveMotion.enabled()) EnterTransition.None togetherWith ExitTransition.None
                else (slideInVertically(tween(180)) { it } + fadeIn(tween(180))) togetherWith
                    (slideOutVertically(tween(180)) { -it } + fadeOut(tween(140)))
            }, label = "lastToolRoller") { name ->
                Text(name, Modifier.testTag(tagPrefix + "workToolName"), fontSize = 12.sp, lineHeight = 16.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = colorResource(R.color.text_secondary))
            }
            Text("${item.row.count}", Modifier.padding(start = 8.dp).testTag(tagPrefix + "workToolCount"),
                fontSize = 12.sp, lineHeight = 16.sp, color = colorResource(R.color.text_secondary))
        }
        AnimatedVisibility(item.expanded) {
            Box {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 200.dp).testTag(tagPrefix + "workLogList"),
                    state = logState, contentPadding = PaddingValues(bottom = 6.dp)) {
                    items(item.tools, key = { it.stableKey() }) {
                        if (it.role() == ChatMessage.Role.TOOL_RESULT) ToolLine(it)
                        else Box(Modifier.padding(top = 5.dp, bottom = 7.dp)) {
                            NativeMarkdown(it.text(), markdown, colorResource(R.color.text_primary), 13f)
                        }
                    }
                }
                if (logState.canScrollBackward) Box(Modifier.fillMaxWidth().height(24.dp)
                    .background(Brush.verticalGradient(listOf(background, Color.Transparent))))
            }
        }
    }
}

@Composable
private fun ToolLine(message: ChatMessage) {
    val context = LocalContext.current
    val preview = ToolArguments.format(message.toolName(), message.preview().ifEmpty { message.text().substringBefore('\n') })
    Row(Modifier.fillMaxWidth().heightIn(min = 20.dp).testTag("${context.packageName}:id/toolLine")
        .semantics(mergeDescendants = true) { contentDescription = "${message.toolName()}, ${message.toolStatus()}, $preview" },
        verticalAlignment = Alignment.CenterVertically) {
        ToolGlyph(message.toolName(), message.toolStatus() == "error")
        Text(message.toolName(), fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            color = colorResource(R.color.text_primary), maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 5.dp).widthIn(max = 105.dp))
        Text(preview, fontSize = 11.sp, color = colorResource(R.color.text_secondary),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp).weight(1f).testTag("${context.packageName}:id/workArguments"))
    }
}

@Composable
private fun ToolGlyph(tool: String, error: Boolean) {
    val ink = colorResource(if (error) R.color.danger else R.color.text_secondary)
    Canvas(Modifier.size(13.dp)) {
        val stroke = Stroke(width = size.width * 1.8f / 24, cap = StrokeCap.Round)
        fun point(x: Float, y: Float) = Offset(size.width * x / 24, size.height * y / 24)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(ink, point(x1, y1), point(x2, y2), stroke.width, cap = StrokeCap.Round)
        fun rectangle(x: Float, y: Float, w: Float, h: Float, radius: Float) = drawRoundRect(
            ink, point(x, y), androidx.compose.ui.geometry.Size(size.width * w / 24, size.height * h / 24),
            androidx.compose.ui.geometry.CornerRadius(size.width * radius / 24), style = stroke)
        val name = tool.lowercase(Locale.ROOT)
        if (name.contains("bash") || name.contains("shell")) {
            rectangle(2f, 4f, 20f, 16f, 3f)
            line(6f, 9f, 9f, 12f); line(9f, 12f, 6f, 15f); line(12f, 15f, 17f, 15f)
        } else if (name.contains("web") || name.contains("search") || name.contains("fetch")) {
            drawCircle(ink, size.width * 9 / 24, center, style = stroke)
            drawOval(ink, point(8f, 3f), androidx.compose.ui.geometry.Size(size.width * 8 / 24, size.height * 18 / 24), style = stroke)
            line(3f, 12f, 21f, 12f)
        } else if (name == "read") {
            rectangle(5f, 2f, 14f, 20f, 2f)
            line(8f, 8f, 16f, 8f); line(8f, 12f, 16f, 12f); line(8f, 16f, 13f, 16f)
        } else if (name.contains("edit") || name.contains("write")) {
            line(5f, 19f, 18f, 6f); line(5f, 19f, 9f, 18f); line(5f, 19f, 6f, 15f); line(16f, 4f, 20f, 8f)
        } else if (name.contains("mcp")) {
            rectangle(5f, 8f, 14f, 9f, 3f)
            line(9f, 3f, 9f, 8f); line(15f, 3f, 15f, 8f); line(12f, 17f, 12f, 22f)
        } else {
            rectangle(3f, 4f, 18f, 16f, 3f)
            line(7f, 9f, 17f, 9f); line(7f, 14f, 14f, 14f)
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, markdown: MarkdownRenderer, loader: MediaLoader, actions: TranscriptActions?, latestUser: Boolean) {
    val context = LocalContext.current
    val user = message.role() == ChatMessage.Role.USER
    val bubble = if (user) Color(BubbleColors.color(context)) else colorResource(R.color.bubble_assistant)
    val ink = if (user) Color(BubbleColors.foreground(BubbleColors.color(context))) else colorResource(R.color.text_primary)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = if (user) 8.dp else 10.dp)) {
        val maxBubbleWidth = minOf(584.dp, if (user) maxWidth - 24.dp else maxWidth)
        Column(Modifier.align(if (user) Alignment.CenterEnd else Alignment.CenterStart).widthIn(max = maxBubbleWidth)
            .then(if (user) Modifier else Modifier.fillMaxWidth().testTag("${context.packageName}:id/assistantMessage"))) {
            // The assistant is document content; only user messages carry a bubble surface.
            val container = if (user) Modifier.widthIn(max = maxBubbleWidth)
                .clip(RoundedCornerShape(20.dp)).background(bubble).padding(horizontal = 16.dp, vertical = 12.dp)
                else Modifier.fillMaxWidth()
            Column(container.testTag("${context.packageName}:id/messageBubble")) {
            if (message.text().isNotEmpty()) {
                if (user) SelectionContainer { Text(message.text(), color = ink, fontSize = 16.sp, lineHeight = 23.sp) }
                else NativeMarkdown(message.text(), markdown, ink)
            }
            message.images().take(3).forEachIndexed { index, image ->
                val local = if (image.url().startsWith("local:")) actions?.thumbnail?.invoke(
                    message.requestId(), image.url().substringAfter(':').toIntOrNull() ?: index) else null
                TranscriptImage(image.url(), index, loader, minOf(240.dp, maxBubbleWidth - 24.dp), local)
            }
            }
            if (user && latestUser && message.localState() in setOf(ChatMessage.LocalState.NONE, ChatMessage.LocalState.ACCEPTED)) Text("read",
                color = colorResource(R.color.text_secondary), fontSize = 10.sp, lineHeight = 12.sp,
                modifier = Modifier.align(Alignment.End).padding(top = 2.dp, end = 8.dp)
                    .testTag("${context.packageName}:id/messageRead")
                    .semantics { contentDescription = "Accepted by Pi; not task completion" })
            else if (message.isLocal && message.localState() != ChatMessage.LocalState.ACCEPTED) {
                Text(StatusUi.localStateLabel(context, message.localState()), fontSize = 11.sp,
                    color = Color(StatusUi.localStateColor(context, message.localState())),
                    modifier = Modifier.align(Alignment.End).padding(top = 6.dp))
                if (actions != null && message.localState() in setOf(ChatMessage.LocalState.FAILED, ChatMessage.LocalState.UNCERTAIN)) {
                    Row {
                        TextButton(onClick = { actions.retry(message) }) { Text("Retry manually", fontSize = 12.sp) }
                        TextButton(onClick = { actions.restore(message) }) { Text("Restore", fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

/** The only View leaf: vetted Markwon tables/LaTeX, no HTML, JS or remote image plugin. */
@Composable
private fun NativeMarkdown(text: String, markdown: MarkdownRenderer, ink: Color, textSizeSp: Float = 16f) {
    AndroidView(factory = { context -> TextView(context).apply {
        id = R.id.messageText
        textSize = textSizeSp
        setTextIsSelectable(true)
        setLineSpacing(5 * resources.displayMetrics.scaledDensity, 1f)
        includeFontPadding = false
    } }, update = { view -> view.setTextColor(ink.toArgb()); markdown.render(view, text) },
        modifier = Modifier.widthIn(max = 560.dp))
}

@Composable
private fun TranscriptImage(url: String, index: Int, loader: MediaLoader, maxWidth: androidx.compose.ui.unit.Dp, localBitmap: Bitmap? = null) {
    val context = LocalContext.current
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    var error by remember(url) { mutableStateOf("") }
    DisposableEffect(url, loader) {
        var active = true
        if (!url.startsWith("local:")) loader.load(url, object : MediaLoader.Callback {
            override fun onLoaded(resolvedUrl: String, value: Bitmap) { if (active) bitmap = value }
            override fun onFailed(resolvedUrl: String, message: String) { if (active) error = message }
        })
        onDispose { active = false }
    }
    val loaded = localBitmap ?: bitmap
    if (loaded != null) {
        val aspect = loaded.width.toFloat() / loaded.height
        val width = minOf(maxWidth, 280.dp * aspect)
        Image(loaded.asImageBitmap(), context.getString(R.string.cd_message_image, index + 1),
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp).size(width, width / aspect)
                .clip(RoundedCornerShape(16.dp)).clickable { ImageViewer.show(context, url, loaded) })
    } else Text(if (url.startsWith("local:")) "Select attachment again to restore preview" else if (error.isEmpty()) "Loading image…" else "Image unavailable: $error",
        color = colorResource(R.color.text_secondary), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
}
