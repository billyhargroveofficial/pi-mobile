package ru.billyhargrove.pimobile.core

import org.json.JSONObject

/** Static counters shared by activity docks and workflow details; unknown stays unknown. */
data class AgentMetrics(val tools: Long?, val tokens: Long?) {
    val label = listOfNotNull(tools?.let { "$it tools" }, tokens?.let { "$it tokens" }).joinToString(" · ")

    companion object {
        private fun add(total: Long?, value: Long): Long {
            val safe = value.coerceAtLeast(0)
            return if ((total ?: 0) > Long.MAX_VALUE - safe) Long.MAX_VALUE else (total ?: 0) + safe
        }
        fun from(agents: List<JSONObject>): AgentMetrics {
            var tools: Long? = null
            var tokens: Long? = null
            for (agent in agents) {
                if (agent.has("toolCalls")) tools = add(tools, agent.optLong("toolCalls"))
                if (agent.has("outputTokens")) tokens = add(tokens, agent.optLong("outputTokens"))
            }
            return AgentMetrics(tools, tokens)
        }
    }
}
