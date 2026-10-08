package ru.billyhargrove.pimobile.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ActiveWorkTest {
    private fun flow(id: String = "w", status: String = "running") = JSONObject().put("id", id).put("title", "Flow $id").put("status", status)
    private fun agent(id: String, status: String = "running", workflow: String = "") = JSONObject().put("id", id).put("name", "Agent $id")
        .put("status", status).put("workflowId", workflow)
    private fun data(flows: List<JSONObject> = emptyList(), agents: List<JSONObject> = emptyList(), live: Boolean = true) =
        JSONObject().put("liveAvailable", live).put("workflows", JSONArray(flows)).put("agents", JSONArray(agents))
    @Test fun nullEmptyAndInvalidRowsHaveNoHistoryOrActiveWork() {
        for (value in listOf(null, JSONObject(), JSONObject().put("liveAvailable", true).put("agents", JSONArray().put(2)).put("workflows", JSONArray().put(false)))) {
            val snapshot = ActiveWork.project(value); assertFalse(snapshot.active); assertFalse(snapshot.hasHistory)
        }
    }
    @Test fun savedFactsRetainHistoryButCannotBecomeLiveWork() {
        val snapshot = ActiveWork.project(data(listOf(flow()), listOf(agent("a")), false))
        assertTrue(snapshot.hasHistory); assertFalse(snapshot.active); assertTrue(snapshot.workflows.isEmpty()); assertTrue(snapshot.agents.isEmpty())
    }
    @Test fun activeStatesAndSourceOrderMatchExistingPolicy() {
        val statuses = listOf("running", "completed", "queued", "failed", "starting", "paused", "waiting", "cancelled", "unknown")
        val snapshot = ActiveWork.project(data(statuses.map { flow(it, it) }, statuses.map { agent(it, it) }))
        val expected = listOf("running", "queued", "starting", "waiting")
        assertEquals(expected, snapshot.workflows.map { it.status }); assertEquals(expected, snapshot.agents.map { it.status })
    }
    @Test fun activeWorkflowMembershipSuppressesOnlyItsOwnStandaloneAgents() {
        val snapshot = ActiveWork.project(data(listOf(flow("live"), flow("done", "completed")), listOf(
            agent("in-live", workflow = "live"), agent("in-done", workflow = "done"), agent("free"), agent("finished", "completed"))))
        assertEquals(listOf("in-done", "free"), snapshot.agents.map { it.target.id })
        assertEquals("2 active agents", snapshot.agentTitle); assertEquals(ActiveWork.Target("", "", "Agents"), snapshot.agentTarget)
    }
    @Test fun oneStandaloneAgentKeepsItsExactNavigationNameAndIdentity() {
        val snapshot = ActiveWork.project(data(agents = listOf(agent("only"))))
        assertEquals("Agent only", snapshot.agentTitle); assertEquals(ActiveWork.Target("agent", "only", "Agent only"), snapshot.agentTarget)
    }
    @Test fun phasesPreserveOrderCountsGlyphsAndFirstRunningTitle() {
        val phases = JSONArray().put(JSONObject().put("title", "Explore").put("status", "completed").put("agentCount", 2).put("completed", 2))
            .put(3).put(JSONObject().put("title", "Build").put("status", "running"))
            .put(JSONObject().put("title", "Verify").put("status", "failed"))
        val work = ActiveWork.project(data(listOf(flow().put("phases", phases)))).workflows.single()
        assertEquals(listOf("✓ Explore 2/2", "● Build", "! Verify"), work.phases.map { it.label }); assertEquals("Build", work.runningPhase)
        assertEquals(ActiveWork.Target("workflow", "w", "Flow w"), work.target)
    }
    @Test fun missingAndExplicitlyEmptyPhaseTitlesKeepTheirDifferentFallbacks() {
        assertNull(ActiveWork.project(data(listOf(flow()))).workflows.single().runningPhase)
        val work = flow().put("phases", JSONArray().put(JSONObject().put("status", "running")))
        assertEquals("", ActiveWork.project(data(listOf(work))).workflows.single().runningPhase)
    }
    @Test fun workflowMetricsIncludeRecordedCompletedCountersAndOnlyActualActiveCount() {
        val running = agent("a", workflow = "w").put("toolCalls", 0).put("outputTokens", 100)
        val done = agent("b", "completed", "w").put("toolCalls", 7).put("outputTokens", 221)
        val snapshot = ActiveWork.project(data(listOf(flow().put("startedAt", 1000)), listOf(running, done)))
        assertEquals("1 active · 7 tools · 321 tokens · 6s", snapshot.workflows.single().metrics.label(7000))
        assertEquals("0 active", ActiveWork.project(data(listOf(flow()))).workflows.single().metrics.label(7000))
    }
    @Test fun standaloneClockUsesTheEarliestPositiveStartAndPreservesUnknownCounters() {
        val snapshot = ActiveWork.project(data(agents = listOf(agent("later").put("startedAt", 5000), agent("earlier").put("startedAt", 1000), agent("missing"))))
        assertEquals("6s", snapshot.agentMetrics.label(7000)); assertEquals("", snapshot.agentMetrics.static)
        assertEquals("0 tools", ActiveWork.project(data(agents = listOf(agent("zero").put("toolCalls", -7)))).agentMetrics.static)
    }
    @Test fun durationKeepsFinishedFutureMissingAndHourFormatting() {
        assertEquals("1m 1s", ActiveWork.Metrics("", 1000, 62000).label(500000))
        assertEquals("0s", ActiveWork.Metrics("", 10000, 0).label(1000))
        assertEquals("1h 1m", ActiveWork.Metrics("", 1000, 0).label(3661000))
        assertEquals("7 tools", ActiveWork.Metrics("7 tools", 0, 0).label(1000))
    }
    @Test fun projectedFactsAndNestedListsCannotChangeAfterJsonMutation() {
        val phase = JSONObject().put("title", "Build").put("status", "running")
        val flow = flow().put("startedAt", 1000).put("phases", JSONArray().put(phase))
        val agent = agent("a").put("startedAt", 1000); val source = data(listOf(flow), listOf(agent))
        val snapshot = ActiveWork.project(source); phase.put("title", "Changed"); flow.put("title", "Changed").put("startedAt", 5000); agent.put("name", "Changed")
        source.put("liveAvailable", false)
        assertEquals("Build", snapshot.workflows.single().runningPhase); assertEquals("Flow w", snapshot.workflows.single().target.title)
        assertEquals("6s", snapshot.workflows.single().metrics.label(7000).substringAfter(" · ")); assertEquals("Agent a", snapshot.agentTitle)
        for (list in listOf(snapshot.workflows, snapshot.agents, snapshot.workflows.single().phases)) {
            try { (list as MutableList<*>).clear(); fail("Published lists must be read-only") } catch (_: UnsupportedOperationException) { }
        }
    }
    @Test fun duplicateWorkflowIdsRetainTheExistingLastClockForBothRows() {
        val snapshot = ActiveWork.project(data(listOf(flow().put("startedAt", 1000), flow().put("startedAt", 2000))))
        assertEquals(listOf("0 active · 5s", "0 active · 5s"), snapshot.workflows.map { it.metrics.label(7000) })
    }
    @Test fun tickerLabelsNeverReadSnapshotJsonAgain() {
        val flow = object : JSONObject() {
            var reads = 0
            override fun optLong(name: String?): Long { reads++; return super.optLong(name) }
            override fun optJSONArray(name: String?): JSONArray? { reads++; return super.optJSONArray(name) }
        }.apply { put("id", "w"); put("status", "running"); put("startedAt", 1000) }
        val snapshot = ActiveWork.project(data(listOf(flow))); val before = flow.reads
        repeat(40) { snapshot.workflows.single().metrics.label(7000L + it * 1000) }
        assertEquals(3, before); assertEquals(before, flow.reads)
    }
}
