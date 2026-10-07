package ru.billyhargrove.pimobile.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AgentMetricsTest {
    @Test fun unknownCountersStayDistinctFromActualZeroAndPartiallyReportedSums() {
        assertEquals("", AgentMetrics.from(listOf(JSONObject())).label)
        assertEquals("0 tools", AgentMetrics.from(listOf(JSONObject().put("toolCalls", 0))).label)
        val metrics = AgentMetrics.from(listOf(JSONObject().put("toolCalls", 4), JSONObject().put("outputTokens", 20),
            JSONObject().put("toolCalls", -2)))
        assertEquals("4 tools · 20 tokens", metrics.label)
    }
    @Test fun counterOverflowCannotTurnPositiveActivityNegative() {
        assertEquals(Long.MAX_VALUE, AgentMetrics.from(listOf(JSONObject().put("toolCalls", Long.MAX_VALUE),
            JSONObject().put("toolCalls", 1))).tools)
    }
}
