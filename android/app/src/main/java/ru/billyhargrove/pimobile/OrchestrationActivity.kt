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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.ComposeView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import ru.billyhargrove.pimobile.core.ChatMessage
import ru.billyhargrove.pimobile.core.OrchestrationData
import ru.billyhargrove.pimobile.core.SessionStatus
import ru.billyhargrove.pimobile.core.SnapshotParser
import ru.billyhargrove.pimobile.net.AppExecutors
import ru.billyhargrove.pimobile.ui.MessageAdapter
import ru.billyhargrove.pimobile.ui.SystemInsets

/** Read-only orchestration UI. The existing rich transcript renderer is hosted until its own migration. */
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
    private var list: RecyclerView? = null
    private var messages: MessageAdapter? = null
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
        if (agent.isNotEmpty()) messages = MessageAdapter(app.mediaLoader(), null, null)
        val root = ComposeView(this).apply { setContent { Screen() } }
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
    override fun onDestroy() { messages?.close(); super.onDestroy() }

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
        rows = buildList {
            if (workflow.isEmpty()) for (w in OrchestrationData.objects(shown.optJSONArray("workflows")))
                add(OrchestrationRow("workflow", w))
            for (a in OrchestrationData.agents(shown, null))
                if (if (workflow.isEmpty()) a.optString("workflowId").isEmpty() else workflow == a.optString("workflowId"))
                    add(OrchestrationRow("agent", a))
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
        val adapter = messages ?: return
        adapter.sessionStatus(if (OrchestrationData.active(a.optString("status"))) SessionStatus.RUNNING else SessionStatus.IDLE)
        val source = data.optString("source", "live")
        if (source != transcriptSource) {
            transcript.clear(); before = 0; historyInitialized = false; transcriptSource = source
        }
        val hadMessages = transcript.isNotEmpty()
        val page = SnapshotParser.parseMessages(data.optJSONArray("messages"))
        val view = list
        val layout = view?.layoutManager as? LinearLayoutManager
        val nearBottom = view?.canScrollVertically(1) != true
        val first = layout?.findFirstVisibleItemPosition() ?: -1
        val anchor = adapter.keyAt(first)
        val offset = (layout?.findViewByPosition(first)?.top ?: 0) - (view?.paddingTop ?: 0)
        if (history) {
            val merged = LinkedHashMap<String, ChatMessage>()
            for (m in page) merged[m.stableKey()] = m
            merged.putAll(transcript)
            transcript.clear(); transcript.putAll(merged)
        } else for (m in page) transcript[m.stableKey()] = m
        adapter.submit(ArrayList(transcript.values))
        if (history) {
            val pos = adapter.positionOf(anchor)
            if (pos >= 0) layout?.scrollToPositionWithOffset(pos, offset)
        } else if (nearBottom && adapter.size() > 0) view?.scrollToPosition(adapter.size() - 1)
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
        val context = LocalContext.current
        val bg = color(R.color.bg)
        Column(Modifier.fillMaxSize().background(bg).semantics { testTagsAsResourceId = true }) {
            // Compose owns the header, notices, stage cards and navigation. Only transcript remains a View.
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                HeaderAction(R.drawable.ic_back, "Back") { finish() }
                Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                    Text(intent.getStringExtra("title").orEmpty(), color = color(R.color.text_primary),
                        fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
            if (agent.isEmpty()) LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("orchestrationList"),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                items(rows, key = { it.kind + ":" + it.data.optString("id") }) { row -> OrchestrationCard(row) }
            } else AndroidView(
                factory = {
                    RecyclerView(context).apply {
                        id = R.id.orchestrationList
                        layoutManager = LinearLayoutManager(context)
                        itemAnimator = null
                        clipToPadding = false
                        setPadding(0, dp(8), 0, dp(24))
                        adapter = messages
                        list = this
                    }
                },
                modifier = Modifier.fillMaxWidth().weight(1f)
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
        OutlinedButton(onClick = action, enabled = enabled,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = color(R.color.text_primary)),
            border = BorderStroke(1.dp, color(R.color.outline_soft)),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp)
                .testTag("$packageName:id/$tag")) {
            Text(label)
        }
    }

    @Composable private fun OrchestrationCard(row: OrchestrationRow) {
        val x = row.data
        val flow = row.kind == "workflow"
        val status = x.optString("status")
        val title = x.optString("title", x.optString("name", "Agent"))
        val description = if (x.optString("error").isEmpty()) x.optString("description") else x.optString("error")
        val meta = if (flow) "${x.optInt("completed")} of ${x.optInt("agentCount")} agents done" else buildString {
            if (x.optString("parentId").isNotEmpty()) append("↳ Child agent · ")
            if (x.optString("phase").isNotEmpty()) append(x.optString("phase"))
            val details = OrchestrationData.details(x)
            if (details.isNotEmpty()) { if (x.optString("phase").isNotEmpty()) append(" · "); append(details) }
        }
        val ink = color(R.color.text_primary)
        val secondary = color(R.color.text_secondary)
        val statusColor = color(when (status) {
            "failed" -> R.color.danger; "running" -> R.color.dot_ok; "queued" -> R.color.dot_warn
            else -> R.color.text_secondary
        })
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .background(color(R.color.surface), RoundedCornerShape(24.dp))
            .clickable {
                startActivity(intent(this@OrchestrationActivity, session, row.kind, x.optString("id"), title)
                    .putExtra("agentSummary", x.toString()))
            }.semantics { contentDescription = "${if (flow) "Workflow" else "Agent"}: $title, ${OrchestrationData.label(status)}" }
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp)) {
            Text("${if (flow) "WORKFLOW" else "AGENT"}   ·   ${OrchestrationData.label(status)}", color = statusColor, fontSize = 11.sp)
            Text(title, Modifier.padding(top = 8.dp), color = ink, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (description.isNotEmpty() && description != title)
                Text(description, Modifier.padding(top = 6.dp), color = if (x.optString("error").isNotEmpty()) color(R.color.danger) else secondary,
                    fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (flow) {
                val phases = OrchestrationData.objects(x.optJSONArray("phases"))
                if (phases.isNotEmpty()) Column(Modifier.padding(top = 12.dp)) {
                    for (phase in phases) {
                        val ps = phase.optString("status")
                        val count = phase.optInt("agentCount")
                        val text = "${when (ps) { "completed" -> "✓  "; "running" -> "●  "; else -> "○  " }}${phase.optString("title")}" +
                            if (count > 0) "   ·   ${phase.optInt("completed")}/$count agents done" else ""
                        Text(text, Modifier.padding(vertical = 4.dp), color = if (ps == "running") ink else secondary, fontSize = 12.sp)
                    }
                }
            }
            if (meta.isNotEmpty()) Text(meta, Modifier.padding(top = 10.dp), color = secondary, fontSize = 11.sp)
            Text(if (flow) "View stages & agents  ›" else if (x.optBoolean("canInspect")) "Open conversation  ›" else "View details  ›",
                Modifier.padding(top = 14.dp), color = ink, fontSize = 12.sp)
        }
    }

    @Composable private fun color(id: Int): Color = Color(LocalContext.current.getColor(id))
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private data class OrchestrationRow(val kind: String, val data: JSONObject)
}
