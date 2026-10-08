package ru.billyhargrove.pimobile.features.orchestration

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.billyhargrove.pimobile.core.ActiveWork

/** Two distinct live surfaces, not a permanent aggregate/history plaque. Read-only callbacks only. */
@Composable
internal fun ActiveWorkScreen(snapshot: ActiveWork.Snapshot, open: (String, String, String) -> Unit, compact: Boolean = false) {
    val flows = snapshot.workflows
    val standalone = snapshot.agents
    if (flows.isEmpty() && standalone.isEmpty()) return
    val prefix = "${LocalContext.current.packageName}:id/"
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    if (compact) {
        // While typing, use one horizontal dock rather than stacking agent and
        // workflow panels above Queue. All entries remain individually inspectable.
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).horizontalScroll(rememberScrollState())
            .testTag(prefix + "activeOrchestration"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            flows.forEach { flow ->
                val phase = flow.runningPhase
                CompactActivity("Workflow · ${phase ?: flow.target.title}",
                    flow.metrics.label(now), prefix + "activeWorkflow") {
                    open(flow.target.kind, flow.target.id, flow.target.title)
                }
            }
            if (standalone.isNotEmpty()) CompactActivity("Agents · ${standalone.size}", snapshot.agentMetrics.label(now), prefix + "activeAgents") {
                val target = snapshot.agentTarget
                open(target.kind, target.id, target.title)
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(prefix + "activeOrchestration"),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (flows.isNotEmpty()) BoxWithConstraints(Modifier.fillMaxWidth()) {
            val pageWidth = maxWidth
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                flows.forEach { flow ->
                    Column(Modifier.width(pageWidth).clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { open(flow.target.kind, flow.target.id, flow.target.title) }
                        .testTag(prefix + "activeWorkflow").semantics { role = Role.Button }
                        .padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ActivityDot(flow.status)
                            Text(flow.target.title, Modifier.weight(1f).padding(start = 8.dp), fontSize = 13.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Workflow" + if (flows.size > 1) " · ${flows.size}" else "", Modifier.padding(start = 8.dp),
                                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(flow.metrics.label(now), Modifier.padding(top = 4.dp), fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!compact) Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val phases = flow.phases
                            if (phases.isEmpty()) Text("Waiting for phase details", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            phases.forEach { phase ->
                                val status = phase.status
                                Text(phase.label,
                                    fontSize = 11.sp, color = if (status == "running") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (standalone.isNotEmpty()) Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable {
                val target = snapshot.agentTarget
                open(target.kind, target.id, target.title)
            }.testTag(prefix + "activeAgents").semantics { role = Role.Button }
            .padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                standalone.take(3).forEach { agent ->
                    Box(Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        ActivityDot(agent.status)
                    }
                }
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(snapshot.agentTitle,
                    fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(snapshot.agentMetrics.label(now), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("›", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CompactActivity(title: String, metrics: String, tag: String, open: () -> Unit) {
    Column(Modifier.width(264.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(18.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = open).testTag(tag)
        .semantics { role = Role.Button }.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(metrics, Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()), fontSize = 10.sp,
            maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActivityDot(status: String) {
    val ink = when (status) { "running" -> MaterialTheme.colorScheme.onSurface; "failed" -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant }
    Box(Modifier.size(6.dp).clip(CircleShape).background(ink))
}
