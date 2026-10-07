package ru.billyhargrove.pimobile.features.orchestration

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import ru.billyhargrove.pimobile.core.OrchestrationData

/** Snapshot projection only: no fetching, navigation, clocks or transcript ownership. */
internal object OrchestrationProjection {
    data class Row(val kind: String, val data: JSONObject)
    data class WorkflowMetrics(val tools: Long?, val tokens: Long?) {
        val label = listOfNotNull(tools?.let { "$it tools" }, tokens?.let { "$it tokens" }).joinToString(" · ")
    }
    data class Snapshot(val data: JSONObject, val workflow: JSONObject?, val rows: List<Row>, val subtitle: String, val notice: String,
        val workflowMetrics: Map<String, WorkflowMetrics>)

    private fun metrics(agents: List<JSONObject>): WorkflowMetrics {
        var tools: Long? = null
        var tokens: Long? = null
        for (agent in agents) {
            if (agent.has("toolCalls")) tools = (tools ?: 0) + agent.optLong("toolCalls").coerceAtLeast(0)
            if (agent.has("outputTokens")) tokens = (tokens ?: 0) + agent.optLong("outputTokens").coerceAtLeast(0)
        }
        return WorkflowMetrics(tools, tokens)
    }

    fun project(data: JSONObject, workflowId: String, parentAgent: String?): Snapshot {
        val shown = if (parentAgent == null) data else try {
            val agents = OrchestrationData.agents(data, null)
            val descendants = mutableSetOf(parentAgent)
            do {
                var changed = false
                for (agent in agents) {
                    if (agent.optString("parentId") in descendants && descendants.add(agent.optString("id"))) changed = true
                }
            } while (changed)
            val children = JSONArray()
            for (agent in agents) if (agent.optString("id") != parentAgent && agent.optString("id") in descendants)
                children.put(JSONObject(agent.toString()).put("workflowId", ""))
            JSONObject(data.toString()).put("workflows", JSONArray()).put("agents", children)
        } catch (_: JSONException) { data }
        val workflows = OrchestrationData.objects(shown.optJSONArray("workflows"))
        val workflow = workflows.find { it.optString("id") == workflowId }
        val allAgents = OrchestrationData.agents(shown, null)
        val agents = if (workflowId.isEmpty()) allAgents else allAgents.filter { it.optString("workflowId") == workflowId }
        // Aggregate once per accepted snapshot, not for every Compose elapsed-time tick.
        val byWorkflow = allAgents.groupBy { it.optString("workflowId") }
        val workflowMetrics = workflows.associate { it.optString("id") to metrics(byWorkflow[it.optString("id")].orEmpty()) }
        val rows = buildList {
            if (workflowId.isEmpty()) {
                for (flow in workflows) add(Row("workflow", flow))
                for (agent in agents) if (agent.optString("workflowId").isEmpty()) add(Row("agent", agent))
            } else {
                val phases = OrchestrationData.objects(workflow?.optJSONArray("phases"))
                val assigned = hashSetOf<String>()
                phases.forEachIndexed { index, phase ->
                    add(Row("phase", JSONObject(phase.toString()).put("id", "phase:$index")))
                    agents.filter { it.optString("phase") == phase.optString("title") }.forEach {
                        assigned.add(it.optString("id")); add(Row("agent", it))
                    }
                }
                val remaining = agents.filter { it.optString("id") !in assigned }
                if (remaining.isNotEmpty() && phases.isNotEmpty()) add(Row("phase", JSONObject().put("id", "unassigned").put("title", "Other agents")))
                remaining.forEach { add(Row("agent", it)) }
            }
        }
        val subtitle = if (workflowId.isEmpty()) OrchestrationData.summary(shown) else "Workflow · ${agents.size} agents"
        val notice = when {
            data.optBoolean("truncated") -> "Some activity is omitted by server safety limits."
            rows.isEmpty() -> if (data.optBoolean("available")) "No agents have been recorded here yet." else "No saved subagent activity found for this session."
            !data.optBoolean("liveAvailable") -> "Saved activity · live workflow phases require the observer and an idle Pi reload."
            else -> "Read-only · updates automatically"
        }
        return Snapshot(shown, workflow, rows, subtitle, notice, workflowMetrics)
    }
}
