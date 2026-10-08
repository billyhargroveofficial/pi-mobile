package ru.billyhargrove.pimobile.core

import org.json.JSONObject
import java.util.Collections

/** Copied read-only facts shared by the chat header/dock and the public activity facade. */
object ActiveWork {
    data class Target(val kind: String, val id: String, val title: String)
    data class Phase(val status: String, val title: String, val label: String)
    data class Metrics(val static: String, val startedAt: Long, val finishedAt: Long) {
        fun label(now: Long) = listOfNotNull(static.takeIf(String::isNotEmpty),
            OrchestrationData.duration(startedAt, finishedAt, now).takeIf(String::isNotEmpty)).joinToString(" · ")
    }
    data class Workflow(val target: Target, val status: String, val phases: List<Phase>, val metrics: Metrics) {
        val runningPhase = phases.firstOrNull { it.status == "running" }?.title
    }
    data class Agent(val target: Target, val status: String)
    data class Snapshot(val hasHistory: Boolean, val workflows: List<Workflow>, val agents: List<Agent>, val agentMetrics: Metrics) {
        val active = workflows.isNotEmpty() || agents.isNotEmpty()
        val agentTarget = agents.singleOrNull()?.target ?: Target("", "", "Agents")
        val agentTitle = agents.singleOrNull()?.target?.title ?: "${agents.size} active agents"
    }
    private val noMetrics = Metrics("", 0, 0)
    private val empty = Snapshot(false, emptyList(), emptyList(), noMetrics)
    fun project(data: JSONObject?): Snapshot {
        if (data == null) return empty
        val allFlows = OrchestrationData.objects(data.optJSONArray("workflows"))
        val allAgents = OrchestrationData.objects(data.optJSONArray("agents"))
        val history = allFlows.isNotEmpty() || allAgents.isNotEmpty()
        if (!data.optBoolean("liveAvailable")) return Snapshot(history, emptyList(), emptyList(), noMetrics)
        val flows = allFlows.filter { OrchestrationData.active(it.optString("status")) }
        val ids = flows.mapTo(hashSetOf()) { it.optString("id") }
        val standalone = allAgents.filter { OrchestrationData.active(it.optString("status")) && it.optString("workflowId") !in ids }
        val byWorkflow = allAgents.groupBy { it.optString("workflowId") }
        // Preserve the existing last-clock-wins behavior for duplicate workflow IDs.
        val workflowMetrics = flows.associate { it.optString("id") to metrics(byWorkflow[it.optString("id")].orEmpty(), it) }
        val projected = flows.map { flow ->
            val phases = OrchestrationData.objects(flow.optJSONArray("phases")).map { phase ->
                val status = phase.optString("status"); val title = phase.optString("title")
                val glyph = when (status) { "completed" -> "✓"; "running" -> "●"; "failed" -> "!"; else -> "○" }
                Phase(status, title, "$glyph $title" + if (phase.optInt("agentCount") > 0) " ${phase.optInt("completed")}/${phase.optInt("agentCount")}" else "")
            }
            Workflow(Target("workflow", flow.optString("id"), flow.optString("title", "Workflow")), flow.optString("status"),
                Collections.unmodifiableList(phases), workflowMetrics.getValue(flow.optString("id")))
        }
        val agents = standalone.map { Agent(Target("agent", it.optString("id"), it.optString("name", "Agent")), it.optString("status")) }
        return Snapshot(history, Collections.unmodifiableList(projected), Collections.unmodifiableList(agents), metrics(standalone, null))
    }
    private fun metrics(agents: List<JSONObject>, flow: JSONObject?): Metrics {
        val static = listOfNotNull(flow?.let { "${agents.count { OrchestrationData.active(it.optString("status")) }} active" },
            AgentMetrics.from(agents).label.takeIf(String::isNotEmpty)).joinToString(" · ")
        val clock = flow ?: agents.filter { it.optLong("startedAt") > 0 }.minByOrNull { it.optLong("startedAt") }
        return Metrics(static, clock?.optLong("startedAt") ?: 0, clock?.optLong("finishedAt") ?: 0)
    }
}
