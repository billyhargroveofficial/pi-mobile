package ru.billyhargrove.pimobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.ui.platform.ComposeView
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import ru.billyhargrove.pimobile.core.ChatMessage
import ru.billyhargrove.pimobile.core.OrchestrationData
import ru.billyhargrove.pimobile.core.SessionStatus
import ru.billyhargrove.pimobile.core.SnapshotParser
import ru.billyhargrove.pimobile.net.AppExecutors
import ru.billyhargrove.pimobile.core.TranscriptPresentation
import ru.billyhargrove.pimobile.ui.PiTranscript
import ru.billyhargrove.pimobile.ui.PiTheme
import ru.billyhargrove.pimobile.ui.SystemInsets

/** Read-only orchestration UI with a shared Compose transcript. No command controls. */
class OrchestrationActivity : AppCompatActivity() {
    companion object {
        @JvmStatic fun intent(c: Context, session: String, kind: String, id: String, title: String): Intent =
            Intent(c, OrchestrationActivity::class.java).putExtra("session", session)
                .putExtra("workflow", if (kind == "workflow") id else "")
                .putExtra("agent", if (kind == "agent") id else "")
                .putExtra("title", title)
    }

    private lateinit var app: PiApp
    private var session = ""
    private var workflow = ""
    private var agent = ""
    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    private var busy by mutableStateOf(false)
    private var loaded = false
    private var generation = 0
    private var subtitle by mutableStateOf("")
    private var notice by mutableStateOf("")
    private var loading by mutableStateOf(true)
    private var olderVisible by mutableStateOf(false)
    private var childrenCount by mutableIntStateOf(0)
    private var rows by mutableStateOf<List<OrchestrationRow>>(emptyList())
    private var activityData by mutableStateOf(JSONObject())
    private var selectedWorkflow by mutableStateOf<JSONObject?>(null)
    private val presentation = TranscriptPresentation()
    private val transcriptList = LazyListState()
    private var transcriptItems by mutableStateOf<List<TranscriptPresentation.Item>>(emptyList())
    private var followTailRevision by mutableIntStateOf(0)
    private var before = 0L
    private var historyInitialized = false
    private var transcriptSource = ""
    private val transcript = LinkedHashMap<String, ChatMessage>()
    private val poll = Runnable { refresh(false) }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        app = PiApp.get(this)
        session = intent.getStringExtra("session").orEmpty()
        workflow = intent.getStringExtra("workflow").orEmpty()
        agent = intent.getStringExtra("agent").orEmpty()
        subtitle = if (agent.isEmpty()) "Workflows & agents" else "Agent conversation · read-only"
        val root = ComposeView(this).apply { setContent { PiTheme { Screen() } } }
        setContentView(root)
        // Compose owns the navigation-bar spacer; do not add a second bottom inset to the host View.
        SystemInsets.apply(this, root, null, null, true)
        if (agent.isNotEmpty() && intent.hasExtra("agentSummary")) try {
            renderAgent(JSONObject().put("agent", JSONObject(intent.getStringExtra("agentSummary")!!))
                .put("messages", JSONArray()), false)
        } catch (_: JSONException) { }
    }

    override fun onStart() { super.onStart(); started = true; refresh(false) }
    override fun onStop() { stopUpdates(); super.onStop() }
    fun stopUpdates() {
        started = false
        generation++
        busy = false
        handler.removeCallbacks(poll)
    }

    private fun refresh(history: Boolean) {
        if (!started || busy || !app.settings().hasToken()) return
        handler.removeCallbacks(poll)
        busy = true
        val expected = generation
        val base = app.settings().baseUrl()
        val token = app.settings().token()
        val cursor = if (history) before else 0L
        AppExecutors.io().execute {
            val value = try {
                if (agent.isEmpty()) app.api().fetchOrchestration(base, token, session)
                else app.api().fetchAgent(base, token, session, agent, cursor)
            } catch (_: Exception) { null }
            AppExecutors.main {
                if (!started || generation != expected) return@main
                busy = false
                loading = false
                if (value == null) {
                    notice = if (loaded) "Activity unavailable · showing last received details. Retrying…"
                        else "Activity unavailable. Update the gateway or try again."
                } else {
                    if (agent.isEmpty()) render(value) else renderAgent(value, history)
                    loaded = true
                }
                handler.postDelayed(poll, 2500)
            }
        }
    }

    fun render(data: JSONObject) {
        loading = false
        loaded = true
        val childFilter = intent.getStringExtra("parentAgent")
        val shown = if (childFilter == null) data else try {
            val copy = JSONObject(data.toString())
            val descendants = mutableSetOf(childFilter)
            do {
                var changed = false
                for (a in OrchestrationData.agents(data, null)) {
                    if (a.optString("parentId") in descendants && descendants.add(a.optString("id"))) changed = true
                }
            } while (changed)
            val children = JSONArray()
            for (a in OrchestrationData.agents(data, null)) {
                if (a.optString("id") != childFilter && a.optString("id") in descendants)
                    children.put(JSONObject(a.toString()).put("workflowId", ""))
            }
            copy.put("workflows", JSONArray()).put("agents", children)
        } catch (_: JSONException) { data }
        activityData = shown
        selectedWorkflow = OrchestrationData.objects(shown.optJSONArray("workflows")).find { it.optString("id") == workflow }
        rows = buildList {
            if (workflow.isEmpty()) {
                for (w in OrchestrationData.objects(shown.optJSONArray("workflows"))) add(OrchestrationRow("workflow", w))
                for (a in OrchestrationData.agents(shown, null)) if (a.optString("workflowId").isEmpty()) add(OrchestrationRow("agent", a))
            } else {
                val agents = OrchestrationData.agents(shown, workflow)
                val phases = OrchestrationData.objects(selectedWorkflow?.optJSONArray("phases"))
                val assigned = hashSetOf<String>()
                phases.forEachIndexed { index, phase ->
                    add(OrchestrationRow("phase", JSONObject(phase.toString()).put("id", "phase:$index")))
                    agents.filter { it.optString("phase") == phase.optString("title") }.forEach {
                        assigned.add(it.optString("id")); add(OrchestrationRow("agent", it))
                    }
                }
                val remaining = agents.filter { it.optString("id") !in assigned }
                if (remaining.isNotEmpty() && phases.isNotEmpty()) add(OrchestrationRow("phase", JSONObject().put("id", "unassigned").put("title", "Other agents")))
                remaining.forEach { add(OrchestrationRow("agent", it)) }
            }
        }
        subtitle = if (workflow.isEmpty()) OrchestrationData.summary(shown)
            else "Workflow · ${OrchestrationData.agents(shown, workflow).size} agents"
        notice = when {
            data.optBoolean("truncated") -> "Some activity is omitted by server safety limits."
            rows.isEmpty() -> if (data.optBoolean("available")) "No agents have been recorded here yet."
                else "No saved subagent activity found for this session."
            !data.optBoolean("liveAvailable") -> "Saved activity · live workflow phases require the observer and an idle Pi reload."
            else -> "Read-only · updates automatically"
        }
    }

    fun renderAgent(data: JSONObject, history: Boolean) {
        loading = false
        val a = data.optJSONObject("agent")
        if (a == null) { notice = "Agent details unavailable"; return }
        subtitle = OrchestrationData.label(a.optString("status")) + " · " + OrchestrationData.details(a)
        if (agent.isEmpty()) return
        val source = data.optString("source", "live")
        val sourceChanged = source != transcriptSource
        if (sourceChanged) {
            transcript.clear(); presentation.reset(); before = 0; historyInitialized = false; transcriptSource = source
        }
        val hadMessages = transcript.isNotEmpty()
        val page = SnapshotParser.parseMessages(data.optJSONArray("messages"))
        val nearBottom = !transcriptList.canScrollForward && !transcriptList.isScrollInProgress
        if (history) {
            val merged = LinkedHashMap<String, ChatMessage>()
            for (m in page) merged[m.stableKey()] = m
            merged.putAll(transcript)
            transcript.clear(); transcript.putAll(merged)
        } else for (m in page) transcript[m.stableKey()] = m
        presentation.sessionStatus(if (OrchestrationData.active(a.optString("status"))) SessionStatus.RUNNING else SessionStatus.IDLE)
        presentation.submit(ArrayList(transcript.values))
        transcriptItems = presentation.items()
        // Stable LazyColumn keys retain the visible message and offset when history prepends.
        if (!history && (sourceChanged || nearBottom)) followTailRevision++
        if (history || !historyInitialized || !hadMessages) {
            historyInitialized = true
            before = data.optLong("before")
            olderVisible = data.optBoolean("hasMore")
        }
        notice = when {
            a.optString("error").isNotEmpty() -> a.optString("error")
            data.optBoolean("truncated") -> "Some large or older records were shortened or omitted."
            page.isEmpty() -> "No transcript yet · the agent may be queued or its log may have expired."
            source.startsWith("saved") -> "Saved transcript · read-only"
            else -> "Read-only · prompt, progress, tools & answer"
        }
        childrenCount = data.optInt("childCount")
        loaded = true
    }

    @Composable private fun Screen() {
        val bg = color(R.color.bg)
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        val ticking = rows.any { OrchestrationData.active(it.data.optString("status")) }
        LaunchedEffect(ticking) { if (ticking) while (true) { now = System.currentTimeMillis(); delay(1000) } }
        Column(Modifier.fillMaxSize().background(bg).semantics { testTagsAsResourceId = true }) {
            // Compose owns screen/list geometry; Markwon is a bounded text rendering leaf.
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                HeaderAction(R.drawable.ic_back, "Back") { finish() }
                Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                    Text(intent.getStringExtra("title").orEmpty(), color = color(R.color.text_primary),
                        fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, color = color(R.color.text_secondary), fontSize = 12.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                HeaderAction(R.drawable.ic_refresh, "Refresh orchestration") { refresh(false) }
            }
            if (notice.isNotEmpty()) Text(notice, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                color = color(R.color.text_secondary), fontSize = 12.sp)
            if (loading) Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(32.dp).semantics { contentDescription = "Loading orchestration" })
            }
            if (agent.isNotEmpty() && childrenCount > 0) OutlineAction("View child agents · $childrenCount", "agentChildren") {
                startActivity(intent(this@OrchestrationActivity, session, "", "", "Child agents").putExtra("parentAgent", agent))
            }
            if (agent.isNotEmpty() && olderVisible) OutlineAction("Load earlier activity", "agentLoadOlder", !busy) { refresh(true) }
            if (agent.isEmpty()) LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("$packageName:id/orchestrationList"),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                selectedWorkflow?.let { flow -> item(key = "workflow-summary") { WorkflowCard(flow, now, false) } }
                items(rows, key = { it.kind + ":" + it.data.optString("id") }) { row ->
                    when (row.kind) { "phase" -> PhaseHeader(row.data); "workflow" -> WorkflowCard(row.data, now, true); else -> AgentRow(row.data, now) }
                }
            } else PiTranscript(
                items = transcriptItems,
                state = transcriptList,
                followTailRevision = followTailRevision,
                loader = app.mediaLoader(),
                onToggle = { group -> presentation.toggle(group); transcriptItems = presentation.items() },
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("$packageName:id/orchestrationList")
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
                .testTag("$packageName:id/$tag")) {
            Text(label)
        }
    }

    @Composable private fun WorkflowCard(flow: JSONObject, now: Long, interactive: Boolean) {
        val title = flow.optString("title", "Workflow")
        val status = flow.optString("status")
        val agents = OrchestrationData.agents(activityData, flow.optString("id"))
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            .background(color(R.color.surface), RoundedCornerShape(20.dp))
            .then(if (interactive) Modifier.clickable { startActivity(intent(this, session, "workflow", flow.optString("id"), title)) } else Modifier)
            .semantics { contentDescription = "Workflow: $title, ${OrchestrationData.label(status)}" }.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusMark(status)
                Text("Workflow · ${OrchestrationData.label(status)}", Modifier.padding(start = 8.dp), color = color(R.color.text_secondary), fontSize = 11.sp)
            }
            Text(title, Modifier.padding(top = 8.dp), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text("${flow.optInt("completed")} of ${flow.optInt("agentCount")} agents done", Modifier.padding(top = 6.dp), fontSize = 12.sp, color = color(R.color.text_secondary))
            val metrics = buildList {
                if (agents.any { it.has("toolCalls") }) add("${agents.sumOf { it.optLong("toolCalls").coerceAtLeast(0) }} tools")
                if (agents.any { it.has("outputTokens") }) add("${agents.sumOf { it.optLong("outputTokens").coerceAtLeast(0) }} tokens")
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
            .testTag("$packageName:id/workflowPhase"), verticalAlignment = Alignment.CenterVertically) {
            StatusMark(phase.optString("status"))
            Text(phase.optString("title"), Modifier.weight(1f).padding(start = 10.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (phase.optInt("agentCount") > 0) Text("${phase.optInt("completed")}/${phase.optInt("agentCount")}", fontSize = 11.sp, color = color(R.color.text_secondary))
        }
    }

    @Composable private fun AgentRow(agent: JSONObject, now: Long) {
        val title = agent.optString("name", "Agent")
        val status = agent.optString("status")
        Column(Modifier.fillMaxWidth().padding(horizontal = if (workflow.isEmpty()) 20.dp else 34.dp)
            .clickable { startActivity(intent(this, session, "agent", agent.optString("id"), title).putExtra("agentSummary", agent.toString())) }
            .semantics { contentDescription = "Agent: $title, ${OrchestrationData.label(status)}" }
            .padding(vertical = 12.dp).testTag("$packageName:id/orchestrationAgent")) {
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
    private data class OrchestrationRow(val kind: String, val data: JSONObject)
}
