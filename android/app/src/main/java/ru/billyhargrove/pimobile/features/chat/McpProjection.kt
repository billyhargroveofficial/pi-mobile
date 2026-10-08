package ru.billyhargrove.pimobile.features.chat

import org.json.JSONObject

/** Immutable display body prepared once per accepted MCP reply; native time formatting stays in the host. */
internal object McpProjection {
    data class Status(val text: String, val observedAt: Long)
    fun project(data: JSONObject): Status {
        val servers = data.optJSONArray("servers")
        val text = buildString {
            for (index in 0 until (servers?.length() ?: 0)) {
                val server = servers?.optJSONObject(index) ?: continue
                val label = when (server.optString("status")) {
                    "connected" -> "connected"; "cached" -> "cached, disconnected"; "not-connected" -> "not connected"
                    "needs-auth" -> "sign-in required"; "disabled" -> "disabled"; "blocked" -> "blocked"; else -> "error"
                }
                append("${server.optString("name")} — $label · ${server.optInt("toolCount")} tools\n\n")
            }
            if (isEmpty()) append("No MCP servers in this Pi session.")
        }
        return Status(text, data.optLong("observedAt"))
    }
}
