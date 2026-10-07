package ru.billyhargrove.pimobile.features.orchestration

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.delay
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import ru.billyhargrove.pimobile.R
import ru.billyhargrove.pimobile.core.OrchestrationData
import ru.billyhargrove.pimobile.net.MediaLoader
import ru.billyhargrove.pimobile.ui.PiTranscript

/** Presentation only. Platform navigation and read-only fetching belong to the caller/owner. */
@Composable
internal fun OrchestrationScreen(
    owner: OrchestrationSession,
    title: String,
    transcriptList: LazyListState,
    loader: MediaLoader,
    onBack: () -> Unit,
    onChildren: () -> Unit,
    onWorkflow: (JSONObject) -> Unit,
    onAgent: (JSONObject) -> Unit
) {
    val agent = owner.target.agent
    val workflow = owner.target.workflow
    val rows = owner.rows
    val subtitle = owner.subtitle
    val notice = owner.notice
    val loading = owner.loading
    val childrenCount = owner.childrenCount
    val olderVisible = owner.olderVisible
    val busy = owner.busy
    val selectedWorkflow = owner.selectedWorkflow
    val bg = color(R.color.bg)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val ticking = rows.any { OrchestrationData.active(it.data.optString("status")) }
    LaunchedEffect(ticking) { if (ticking) while (true) { now = System.currentTimeMillis(); delay(1000) } }
    Column(Modifier.fillMaxSize().background(bg).semantics { testTagsAsResourceId = true }) {
        // Compose owns screen/list geometry; Markwon is a bounded text rendering leaf.
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            HeaderAction(R.drawable.ic_back, "Back") { onBack() }
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                Text(title, color = color(R.color.text_primary),
                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = color(R.color.text_secondary), fontSize = 12.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            HeaderAction(R.drawable.ic_refresh, "Refresh orchestration") { owner.refresh(false) }
        }
        if (notice.isNotEmpty()) Text(notice, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            color = color(R.color.text_secondary), fontSize = 12.sp)
        if (loading) Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(32.dp).semantics { contentDescription = "Loading orchestration" })
        }
        if (agent.isNotEmpty() && childrenCount > 0) OutlineAction("View child agents · $childrenCount", "agentChildren") {
            onChildren()
        }
        if (agent.isNotEmpty() && olderVisible) OutlineAction("Load earlier activity", "agentLoadOlder", !busy) { owner.refresh(true) }
        if (agent.isEmpty()) LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("${LocalContext.current.packageName}:id/orchestrationList"),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
            selectedWorkflow?.let { flow -> item(key = "workflow-summary") { WorkflowCard(flow, now, false, owner.workflowMetrics[flow.optString("id")], onWorkflow) } }
            items(rows, key = { it.kind + ":" + it.data.optString("id") }) { row ->
                when (row.kind) { "phase" -> PhaseHeader(row.data); "workflow" -> WorkflowCard(row.data, now, true, owner.workflowMetrics[row.data.optString("id")], onWorkflow); else -> AgentRow(row.data, now, workflow, onAgent) }
            }
        } else PiTranscript(
            items = owner.transcriptItems,
            state = transcriptList,
            followTailRevision = owner.followTailRevision,
            loader = loader,
            onToggle = owner::toggle,
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("${LocalContext.current.packageName}:id/orchestrationList")
        )
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable private fun HeaderAction(icon: Int, description: String, action: () -> Unit) {
    Box(Modifier.size(48.dp).clickable(onClick = action).semantics { contentDescription = description },
        contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = color(R.color.accent))
    }
}

@Composable private fun OutlineAction(label: String, tag: String, enabled: Boolean = true, action: () -> Unit) {
    TextButton(onClick = action, enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = color(R.color.text_primary)),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp)
            .testTag("${LocalContext.current.packageName}:id/$tag")) {
        Text(label)
    }
}

@Composable private fun WorkflowCard(flow: JSONObject, now: Long, interactive: Boolean, summary: OrchestrationProjection.WorkflowMetrics?, onOpen: (JSONObject) -> Unit) {
    val title = flow.optString("title", "Workflow")
    val status = flow.optString("status")
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
        .background(color(R.color.surface), RoundedCornerShape(20.dp))
        .then(if (interactive) Modifier.clickable { onOpen(flow) } else Modifier)
        .semantics { contentDescription = "Workflow: $title, ${OrchestrationData.label(status)}" }.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusMark(status)
            Text("Workflow · ${OrchestrationData.label(status)}", Modifier.padding(start = 8.dp), color = color(R.color.text_secondary), fontSize = 11.sp)
        }
        Text(title, Modifier.padding(top = 8.dp), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text("${flow.optInt("completed")} of ${flow.optInt("agentCount")} agents done", Modifier.padding(top = 6.dp), fontSize = 12.sp, color = color(R.color.text_secondary))
        val metrics = buildList {
            summary?.label?.takeIf(String::isNotEmpty)?.let(::add)
            OrchestrationData.duration(flow, now).takeIf(String::isNotEmpty)?.let(::add)
        }.joinToString(" · ")
        if (metrics.isNotEmpty()) Text(metrics, Modifier.padding(top = 4.dp), fontSize = 11.sp, color = color(R.color.text_secondary))
        if (interactive) Column(Modifier.padding(top = 12.dp)) {
            OrchestrationData.objects(flow.optJSONArray("phases")).forEach { phase ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusMark(phase.optString("status"))
                    Text(phase.optString("title"), Modifier.weight(1f).padding(start = 10.dp), fontSize = 12.sp,
                        fontWeight = if (phase.optString("status") == "running") FontWeight.Medium else FontWeight.Normal)
                    if (phase.optInt("agentCount") > 0) Text("${phase.optInt("completed")}/${phase.optInt("agentCount")}", fontSize = 11.sp, color = color(R.color.text_secondary))
                }
            }
            Text("View stages & agents  ›", Modifier.padding(top = 10.dp), fontSize = 12.sp)
        }
        if (flow.optString("error").isNotEmpty()) Text(flow.optString("error"), Modifier.padding(top = 8.dp), fontSize = 12.sp, color = color(R.color.danger))
    }
}

@Composable private fun PhaseHeader(phase: JSONObject) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)
        .testTag("${LocalContext.current.packageName}:id/workflowPhase"), verticalAlignment = Alignment.CenterVertically) {
        StatusMark(phase.optString("status"))
        Text(phase.optString("title"), Modifier.weight(1f).padding(start = 10.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (phase.optInt("agentCount") > 0) Text("${phase.optInt("completed")}/${phase.optInt("agentCount")}", fontSize = 11.sp, color = color(R.color.text_secondary))
    }
}

@Composable private fun AgentRow(agent: JSONObject, now: Long, workflow: String, onOpen: (JSONObject) -> Unit) {
    val title = agent.optString("name", "Agent")
    val status = agent.optString("status")
    Column(Modifier.fillMaxWidth().padding(horizontal = if (workflow.isEmpty()) 20.dp else 34.dp)
        .clickable { onOpen(agent) }
        .semantics { contentDescription = "Agent: $title, ${OrchestrationData.label(status)}" }
        .padding(vertical = 12.dp).testTag("${LocalContext.current.packageName}:id/orchestrationAgent")) {
        Row(verticalAlignment = Alignment.Top) {
            Text(title, Modifier.weight(1f), fontSize = 15.sp,
                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(OrchestrationData.label(status), Modifier.padding(start = 12.dp, top = 2.dp), fontSize = 10.sp, color = color(R.color.text_secondary))
        }
        val model = listOf(if (agent.optString("parentId").isNotEmpty()) "↳ Child agent" else "", OrchestrationData.shortModel(agent.optString("model")), agent.optString("thinkingLevel")).filter(String::isNotEmpty).joinToString(" · ")
        if (model.isNotEmpty()) Text(model, Modifier.padding(top = 4.dp), fontSize = 11.sp, color = color(R.color.text_secondary))
        val metrics = OrchestrationData.metrics(agent, now)
        if (metrics.isNotEmpty()) Text(metrics, Modifier.padding(top = 4.dp), fontSize = 11.sp, color = color(R.color.text_secondary))
        if (agent.optString("error").isNotEmpty()) Text(agent.optString("error"), Modifier.padding(top = 4.dp), fontSize = 12.sp, color = color(R.color.danger))
    }
}

@Composable private fun StatusMark(status: String) {
    if (status == "completed") Text("✓", fontSize = 12.sp, color = color(R.color.text_secondary), modifier = Modifier.width(12.dp))
    else Box(Modifier.size(8.dp).background(color(if (status == "running") R.color.dot_ok else if (status == "failed") R.color.danger else R.color.outline), CircleShape))
}

@Composable private fun color(id: Int): Color = Color(LocalContext.current.getColor(id))
