package ru.billyhargrove.pimobile.features.orchestration

import androidx.compose.runtime.*
import org.json.JSONObject
import ru.billyhargrove.pimobile.core.AgentMetrics
import ru.billyhargrove.pimobile.core.ChatMessage
import ru.billyhargrove.pimobile.core.OrchestrationData
import ru.billyhargrove.pimobile.core.SessionStatus
import ru.billyhargrove.pimobile.core.SnapshotParser
import ru.billyhargrove.pimobile.core.TranscriptPresentation

/** Read-only inspection owner. Transport completions and lifecycle calls run on the main thread. */
internal class OrchestrationSession(
    val target: Target,
    private val transport: Transport,
    private val readerAtTail: () -> Boolean
) {
    data class Target(val session: String, val workflow: String = "", val agent: String = "", val parentAgent: String? = null)
    /** No command/mutation API, credentials, Context or Activity crosses this seam. */
    interface Transport {
        fun available(): Boolean
        fun fetch(target: Target, before: Long, result: (Result<JSONObject>) -> Unit)
        fun schedule(delayMs: Long, action: () -> Unit)
        fun cancelPoll()
    }
    var busy by mutableStateOf(false); private set
    var loading by mutableStateOf(true); private set
    var subtitle by mutableStateOf(if (target.agent.isEmpty()) "Workflows & agents" else "Agent conversation · read-only"); private set
    var notice by mutableStateOf(""); private set
    var olderVisible by mutableStateOf(false); private set
    var childrenCount by mutableIntStateOf(0); private set
    var rows by mutableStateOf<List<OrchestrationProjection.Row>>(emptyList()); private set
    var workflowMetrics by mutableStateOf<Map<String, AgentMetrics>>(emptyMap()); private set
    var selectedWorkflow by mutableStateOf<JSONObject?>(null); private set
    var transcriptItems by mutableStateOf<List<TranscriptPresentation.Item>>(emptyList()); private set
    var followTailRevision by mutableIntStateOf(0); private set
    private var active = false
    private var loaded = false
    private var generation = 0
    private var request = 0
    private var before = 0L
    private var historyInitialized = false
    private var transcriptSource = ""
    private val transcript = LinkedHashMap<String, ChatMessage>()
    private val presentation = TranscriptPresentation()

    fun start() {
        if (active) return
        active = true
        refresh(false)
    }
    fun stop() {
        active = false
        generation++
        busy = false
        transport.cancelPoll()
    }
    fun refresh(history: Boolean = false) {
        if (!active || busy || !transport.available()) return
        transport.cancelPoll()
        busy = true
        val expectedGeneration = generation
        val expectedRequest = ++request
        transport.fetch(target, if (history) before else 0L) { result ->
            // A stopped screen, an old request or duplicate completion cannot publish or re-arm polling.
            if (!active || generation != expectedGeneration || request != expectedRequest || !busy) return@fetch
            busy = false
            loading = false
            result.fold({ data ->
                if (target.agent.isEmpty()) render(data) else renderAgent(data, history)
                loaded = true
            }, {
                notice = if (loaded) "Activity unavailable · showing last received details. Retrying…"
                    else "Activity unavailable. Update the gateway or try again."
            })
            transport.schedule(2500) {
                if (active && generation == expectedGeneration && request == expectedRequest) refresh(false)
            }
        }
    }
    fun render(data: JSONObject) {
        val projected = OrchestrationProjection.project(data, target.workflow, target.parentAgent)
        loading = false
        loaded = true
        workflowMetrics = projected.workflowMetrics
        selectedWorkflow = projected.workflow
        rows = projected.rows
        subtitle = projected.subtitle
        notice = projected.notice
    }
    fun renderAgent(data: JSONObject, history: Boolean) {
        loading = false
        val agent = data.optJSONObject("agent")
        if (agent == null) { notice = "Agent details unavailable"; return }
        subtitle = OrchestrationData.label(agent.optString("status")) + " · " + OrchestrationData.details(agent)
        if (target.agent.isEmpty()) return
        val source = data.optString("source", "live")
        val sourceChanged = source != transcriptSource
        if (sourceChanged) {
            transcript.clear(); presentation.reset(); before = 0; historyInitialized = false; transcriptSource = source
        }
        val hadMessages = transcript.isNotEmpty()
        val page = SnapshotParser.parseMessages(data.optJSONArray("messages"))
        if (history) {
            val merged = LinkedHashMap<String, ChatMessage>()
            for (message in page) merged[message.stableKey()] = message
            merged.putAll(transcript)
            transcript.clear(); transcript.putAll(merged)
        } else for (message in page) transcript[message.stableKey()] = message
        presentation.sessionStatus(if (OrchestrationData.active(agent.optString("status"))) SessionStatus.RUNNING else SessionStatus.IDLE)
        presentation.submit(ArrayList(transcript.values))
        transcriptItems = presentation.items()
        // The view owns scroll geometry; this owner only issues follow-tail revisions.
        if (!history && (sourceChanged || readerAtTail())) followTailRevision++
        if (history || !historyInitialized || !hadMessages) {
            historyInitialized = true
            before = data.optLong("before")
            olderVisible = data.optBoolean("hasMore")
        }
        notice = when {
            agent.optString("error").isNotEmpty() -> agent.optString("error")
            data.optBoolean("truncated") -> "Some large or older records were shortened or omitted."
            page.isEmpty() -> "No transcript yet · the agent may be queued or its log may have expired."
            source.startsWith("saved") -> "Saved transcript · read-only"
            else -> "Read-only · prompt, progress, tools & answer"
        }
        childrenCount = data.optInt("childCount")
        loaded = true
    }
    fun toggle(group: String) {
        presentation.toggle(group)
        transcriptItems = presentation.items()
    }
}
