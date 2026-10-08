package ru.billyhargrove.pimobile.core

import org.json.JSONArray
import org.json.JSONObject

/** Read-only runner facts; no inferred completion percentages. */
object OrchestrationData {
    @JvmStatic fun active(status: String?) = status in setOf("running", "queued", "starting", "waiting")
    @JvmStatic fun label(status: String?) = when (status) {
        "running" -> "Running"; "queued" -> "Queued"; "starting" -> "Starting"; "waiting" -> "Waiting"
        "completed" -> "Done"; "failed" -> "Failed"; "cancelled" -> "Stopped"; "paused" -> "Paused"; else -> "Unknown"
    }
    @JvmStatic fun objects(array: JSONArray?): List<JSONObject> = (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) }
    @JvmStatic fun agents(data: JSONObject, workflow: String?): List<JSONObject> = objects(data.optJSONArray("agents"))
        .filter { workflow == null || workflow == it.optString("workflowId") }
    @JvmStatic fun running(data: JSONObject) = agents(data, null).count { it.optString("status") == "running" }
    @JvmStatic fun summary(data: JSONObject): String {
        val agents = agents(data, null).size; val flows = objects(data.optJSONArray("workflows")).size; val running = running(data)
        return (if (flows > 0) "$flows workflow${if (flows == 1) "" else "s"} · " else "") +
            "$agents agent${if (agents == 1) "" else "s"}" + if (running > 0) " · $running running" else ""
    }
    @JvmStatic fun activeWorkflows(data: JSONObject): List<JSONObject> =
        if (!data.optBoolean("liveAvailable")) emptyList() else objects(data.optJSONArray("workflows")).filter { active(it.optString("status")) }
    @JvmStatic fun activeStandalone(data: JSONObject): List<JSONObject> {
        if (!data.optBoolean("liveAvailable")) return emptyList()
        val workflows = activeWorkflows(data).mapTo(hashSetOf()) { it.optString("id") }
        return agents(data, null).filter { active(it.optString("status")) && it.optString("workflowId") !in workflows }
    }
    @JvmStatic fun shortModel(model: String) = model.substringAfterLast('/')
    @JvmStatic fun duration(agent: JSONObject, now: Long): String {
        return duration(agent.optLong("startedAt"), agent.optLong("finishedAt"), now)
    }
    @JvmStatic fun duration(start: Long, end: Long, now: Long): String {
        if (start <= 0) return ""
        val seconds = (((if (end > 0) end else now) - start) / 1000).coerceAtLeast(0)
        return when {
            seconds >= 3600 -> "${seconds / 3600}h ${seconds % 3600 / 60}m"
            seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
    @JvmStatic fun metrics(agent: JSONObject, now: Long): String = buildList {
        if (agent.has("toolCalls")) add("${agent.optLong("toolCalls").coerceAtLeast(0)} tools")
        if (agent.has("outputTokens")) add("${agent.optLong("outputTokens").coerceAtLeast(0)} tokens")
        duration(agent, now).takeIf(String::isNotEmpty)?.let(::add)
    }.joinToString(" · ")
    @JvmStatic fun details(agent: JSONObject): String = buildList {
        shortModel(agent.optString("model")).takeIf(String::isNotEmpty)?.let(::add)
        agent.optString("thinkingLevel").takeIf(String::isNotEmpty)?.let(::add)
        duration(agent, System.currentTimeMillis()).takeIf(String::isNotEmpty)?.let(::add)
        agent.optLong("outputTokens").takeIf { it > 0 }?.let { add("$it output tokens") }
    }.joinToString(" · ")
}
