package ru.billyhargrove.pimobile.features.chat

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.billyhargrove.pimobile.R
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.ui.*

/** Header consumes session state; navigation remains a platform callback. */
@Composable
internal fun ChatHeader(chat: ChatSession, orchestration: ActiveWork.Snapshot, onBack: () -> Unit,
    onOrchestration: (String, String, String) -> Unit, prefix: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth().statusBarsPadding().padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag(prefix + "chatTopBar"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PiIconButton(R.drawable.ic_back, "Back to sessions", prefix + "backButton", soft = true, onClick = onBack)
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
            if (chat.status == SessionStatus.RUNNING && !chat.readOnly) PiIconButton(R.drawable.ic_stop, "Stop", prefix + "stopButton",
                enabled = chat.canSend, tint = colorResource(R.color.danger), soft = true, onClick = chat::abort)
            else if (orchestration.hasHistory)
                PiIconButton(R.drawable.ic_queue, "Activity history", prefix + "orchestrationHistory", onClick = { onOrchestration("", "", "Activity") })
        }
        if (chat.notice.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp)
            .testTag(prefix + "chatNotice").semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
            Text(chat.notice, fontSize = 12.sp, color = colorResource(R.color.text_secondary), modifier = Modifier.weight(1f))
            PiIconButton(R.drawable.ic_close, "Dismiss message", prefix + "dismissNotice", onClick = chat::dismissNotice)
        }
        if (chat.readOnly) Text("Read-only inspection", fontSize = 12.sp, color = colorResource(R.color.text_secondary),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag(prefix + "readOnlyBanner"))
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
