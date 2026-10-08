package ru.billyhargrove.pimobile.features.chat

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class McpProjectionTest {
    private fun server(name: String, status: String, count: Int = 2) = JSONObject().put("name", name).put("status", status).put("toolCount", count)
    @Test fun knownStatusesRetainReportedNamesCountsAndOrder() {
        val labels = linkedMapOf("connected" to "connected", "cached" to "cached, disconnected", "not-connected" to "not connected",
            "needs-auth" to "sign-in required", "disabled" to "disabled", "blocked" to "blocked", "failed" to "error", "new-status" to "error")
        val rows = JSONArray(); labels.keys.forEachIndexed { i, s -> rows.put(server("s$i", s, i)) }
        val projected = McpProjection.project(JSONObject().put("servers", rows).put("observedAt", 1234L))
        assertEquals(labels.values.mapIndexed { i, label -> "s$i — $label · $i tools\n\n" }.joinToString(""), projected.text)
        assertEquals(1234L, projected.observedAt)
    }
    @Test fun emptyMissingOrMalformedServerListsUseTheExistingEmptyMessage() {
        for (rows in listOf(null, JSONArray(), JSONArray().put("bad").put(3).put(JSONObject.NULL))) {
            val data = JSONObject(); if (rows != null) data.put("servers", rows)
            val projected = McpProjection.project(data); assertEquals("No MCP servers in this Pi session.", projected.text); assertEquals(0L, projected.observedAt)
        }
    }
    @Test fun immutableProjectionCannotChangeWithTheWireObjectAndNeverChangesInput() {
        val server = server("one", "connected"); val data = JSONObject().put("servers", JSONArray().put(server)).put("observedAt", 123L)
        val before = data.toString(); val projected = McpProjection.project(data); assertEquals(before, data.toString())
        server.put("name", "changed").put("status", "blocked"); data.put("observedAt", 456L)
        assertEquals("one — connected · 2 tools\n\n", projected.text); assertEquals(123L, projected.observedAt)
    }
    @Test fun duplicateServersAndMissingFieldsKeepExistingDisplaySemantics() {
        val same = server("same", "connected"); val rows = JSONArray().put(same).put(same).put(JSONObject())
        val projected = McpProjection.project(JSONObject().put("servers", rows).put("observedAt", -1L))
        assertEquals("same — connected · 2 tools\n\nsame — connected · 2 tools\n\n — error · 0 tools\n\n", projected.text); assertEquals(-1L, projected.observedAt)
    }
}
