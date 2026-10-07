package ru.billyhargrove.pimobile.features.chat

import android.graphics.Rect
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
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
import ru.billyhargrove.pimobile.R
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.ui.*

/** Draft/caret/bytes stay with ChatSession. This component owns only local modal/focus geometry. */
@Composable
internal fun ChatComposer(chat: ChatSession, behavior: CommandBuilder.Behavior,
    onBehavior: (CommandBuilder.Behavior) -> Unit, orchestration: org.json.JSONObject?,
    onOrchestration: (String, String, String) -> Unit, onAttach: () -> Unit, onVoice: () -> Unit,
    onEffort: (Rect) -> Unit, keyboardVisible: Boolean, canvasHeight: androidx.compose.ui.unit.Dp,
    topHeight: androidx.compose.ui.unit.Dp, prefix: String, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val keyboardBottom = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    var dockHeight by remember { mutableStateOf(0.dp) }
    var deliveryVisible by remember { mutableStateOf(false) }
    var effortBounds by remember { mutableStateOf(Rect()) }
    if (!chat.readOnly) {
        Column(modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)).padding(bottom = 8.dp)) {
            ChatWorkDock(chat, orchestration, onOrchestration, keyboardVisible, prefix,
                Modifier.onSizeChanged { dockHeight = with(density) { it.height.toDp() } })
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
                            PiIconButton(R.drawable.ic_close, "Remove attachment ${index + 1}", prefix + "removeAttachment$index",
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
                        PiIconButton(R.drawable.ic_add, "Attach file", prefix + "attachImageButton", enabled = !chat.preparing, onClick = onAttach)
                        PiIconButton(if (behavior == CommandBuilder.Behavior.STEER) R.drawable.ic_steer else R.drawable.ic_queue,
                            "Delivery: ${if (behavior == CommandBuilder.Behavior.STEER) "Steer" else "Queue"}", prefix + "deliveryButton",
                            onClick = { deliveryVisible = true })
                        Box(Modifier.onGloballyPositioned { coordinates ->
                            val bounds = coordinates.boundsInWindow()
                            effortBounds = Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
                        }) {
                            PiIconButton(R.drawable.ic_effort, "Effort: ${EffortSlider.label(chat.configuration?.optString("thinkingLevel", "off") ?: "unknown")}",
                                prefix + "effortButton", onClick = { onEffort(effortBounds) })
                        }
                        Spacer(Modifier.weight(1f))
                        PiIconButton(if (chat.voice) R.drawable.ic_mic else R.drawable.ic_send,
                            if (chat.transcribing) "Transcribing…" else if (chat.voice) "Dictation" else "Send", prefix + "sendButton",
                            enabled = chat.canSend && !chat.transcribing,
                            tint = colorResource(R.color.on_accent), filled = true,
                            loading = chat.transcribing, loadingTag = prefix + "transcriptionSpinner",
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
