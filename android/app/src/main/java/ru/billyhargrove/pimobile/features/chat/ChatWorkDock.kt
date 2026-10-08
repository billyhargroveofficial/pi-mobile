package ru.billyhargrove.pimobile.features.chat

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.billyhargrove.pimobile.R
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.ui.*

/** Activity, queued receipts and skills are presentation of existing owners; no IO or sends. */
@Composable
internal fun ChatWorkDock(chat: ChatSession, orchestration: ActiveWork.Snapshot,
    onOrchestration: (String, String, String) -> Unit, keyboardVisible: Boolean, prefix: String,
    modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        ActiveOrchestration(orchestration, onOrchestration, compact = keyboardVisible)
        val activeDock = orchestration.active
        if (!keyboardVisible || (!activeDock && chat.queue.isEmpty())) Working(chat.metadata, Modifier, prefix)
        MessageQueue(chat, prefix, compact = keyboardVisible)
        Skills(chat, prefix)
    }
}

@Composable
private fun Working(frame: org.json.JSONObject, modifier: Modifier, prefix: String) {
    val id = frame.optString("activeTurnId")
    val active = id.isNotEmpty()
    val metric = remember(frame) {
        val turns = frame.optJSONArray("turns")
        val metrics = (0 until (turns?.length() ?: 0)).mapNotNull { turns?.optJSONObject(it) }
        if (active) metrics.find { it.optString("id") == id } else metrics.maxByOrNull { it.optLong("startedAt") }
    }
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
