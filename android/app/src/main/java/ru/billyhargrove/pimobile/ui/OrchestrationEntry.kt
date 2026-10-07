package ru.billyhargrove.pimobile.ui

import android.os.Handler
import android.os.Looper
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
import org.json.JSONObject
import ru.billyhargrove.pimobile.core.OrchestrationData
import ru.billyhargrove.pimobile.core.AgentMetrics
import ru.billyhargrove.pimobile.net.AppExecutors

/** Lifecycle discovery with an injected read-only fetcher; no app shell or navigation dependency. */
class OrchestrationEntry(private val fetch: () -> JSONObject?) {
    var snapshot by mutableStateOf<JSONObject?>(null); private set
    private val handler = Handler(Looper.getMainLooper())
    private var live = false
    private var busy = false
    private var generation = 0
    private val poll = Runnable { refresh() }
    fun start() { live = true; snapshot = null; refresh() }
    fun stop() { live = false; generation++; busy = false; handler.removeCallbacks(poll) }
    private fun refresh() {
        if (!live || busy) return
        busy = true
        val expected = generation
        AppExecutors.io().execute {
            val result = try { fetch() } catch (_: Exception) { null }
            AppExecutors.main {
                if (live && expected == generation) {
                    busy = false
                    if (result != null) render(result) else snapshot = null
                    handler.postDelayed(poll, 3000)
                }
            }
        }
    }
    fun render(data: JSONObject) { snapshot = data }
}

/** Two distinct live surfaces, not a permanent aggregate/history plaque. Read-only callbacks only. */
@Composable
fun ActiveOrchestration(data: JSONObject, open: (String, String, String) -> Unit, compact: Boolean = false) {
    val flows = remember(data) { OrchestrationData.activeWorkflows(data) }
    val standalone = remember(data) { OrchestrationData.activeStandalone(data) }
    val workflowMetrics = remember(data) {
        val agents = OrchestrationData.agents(data, null).groupBy { it.optString("workflowId") }
        flows.associate { it.optString("id") to activityMetrics(agents[it.optString("id")].orEmpty(), it) }
    }
    val standaloneMetrics = remember(data) { activityMetrics(standalone, null) }
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
                val phase = OrchestrationData.objects(flow.optJSONArray("phases")).firstOrNull { it.optString("status") == "running" }?.optString("title")
                CompactActivity("Workflow · ${phase ?: flow.optString("title", "Workflow")}",
                    workflowMetrics.getValue(flow.optString("id")).label(now), prefix + "activeWorkflow") {
                    open("workflow", flow.optString("id"), flow.optString("title", "Workflow"))
                }
            }
            if (standalone.isNotEmpty()) CompactActivity("Agents · ${standalone.size}", standaloneMetrics.label(now), prefix + "activeAgents") {
                val single = standalone.singleOrNull()
                if (single != null) open("agent", single.optString("id"), single.optString("name", "Agent")) else open("", "", "Agents")
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
                        .clickable { open("workflow", flow.optString("id"), flow.optString("title", "Workflow")) }
                        .testTag(prefix + "activeWorkflow").semantics { role = Role.Button }
                        .padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ActivityDot(flow.optString("status"))
                            Text(flow.optString("title", "Workflow"), Modifier.weight(1f).padding(start = 8.dp), fontSize = 13.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Workflow" + if (flows.size > 1) " · ${flows.size}" else "", Modifier.padding(start = 8.dp),
                                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val phase = OrchestrationData.objects(flow.optJSONArray("phases")).firstOrNull { it.optString("status") == "running" }?.optString("title")
                        Text((if (compact && !phase.isNullOrEmpty()) "$phase · " else "") + workflowMetrics.getValue(flow.optString("id")).label(now), Modifier.padding(top = 4.dp), fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!compact) Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val phases = OrchestrationData.objects(flow.optJSONArray("phases"))
                            if (phases.isEmpty()) Text("Waiting for phase details", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            phases.forEach { phase ->
                                val status = phase.optString("status")
                                Text("${phaseGlyph(status)} ${phase.optString("title")}" +
                                    if (phase.optInt("agentCount") > 0) " ${phase.optInt("completed")}/${phase.optInt("agentCount")}" else "",
                                    fontSize = 11.sp, color = if (status == "running") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (standalone.isNotEmpty()) Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable {
                val single = standalone.singleOrNull()
                if (single != null) open("agent", single.optString("id"), single.optString("name", "Agent"))
                else open("", "", "Agents")
            }.testTag(prefix + "activeAgents").semantics { role = Role.Button }
            .padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                standalone.take(3).forEach { agent ->
                    Box(Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        ActivityDot(agent.optString("status"))
                    }
                }
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(if (standalone.size == 1) standalone.first().optString("name", "Agent") else "${standalone.size} active agents",
                    fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(standaloneMetrics.label(now), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
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

private fun phaseGlyph(status: String) = when (status) { "completed" -> "✓"; "running" -> "●"; "failed" -> "!"; else -> "○" }

private data class ActivityMetrics(val static: String, val clock: JSONObject?) {
    fun label(now: Long) = listOfNotNull(static.takeIf(String::isNotEmpty),
        clock?.let { OrchestrationData.duration(it, now) }?.takeIf(String::isNotEmpty)).joinToString(" · ")
}
private fun activityMetrics(agents: List<JSONObject>, flow: JSONObject?): ActivityMetrics {
    val static = listOfNotNull(flow?.let { "${agents.count { OrchestrationData.active(it.optString("status")) }} active" },
        AgentMetrics.from(agents).label.takeIf(String::isNotEmpty)).joinToString(" · ")
    val clock = flow ?: agents.filter { it.optLong("startedAt") > 0 }.minByOrNull { it.optLong("startedAt") }
    return ActivityMetrics(static, clock)
}

@Composable
private fun ActivityDot(status: String) {
    val ink = when (status) { "running" -> MaterialTheme.colorScheme.onSurface; "failed" -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant }
    Box(Modifier.size(6.dp).clip(CircleShape).background(ink))
}
