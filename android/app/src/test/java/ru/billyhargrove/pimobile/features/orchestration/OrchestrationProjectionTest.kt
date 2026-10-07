package ru.billyhargrove.pimobile.features.orchestration

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OrchestrationProjectionTest {
    private fun agent(id: String, workflow: String = "", parent: String = "", phase: String = "") = JSONObject()
        .put("id", id).put("name", id).put("workflowId", workflow).put("parentId", parent).put("phase", phase).put("status", "running")
    private fun data(vararg agents: JSONObject) = JSONObject().put("available", true).put("liveAvailable", true)
        .put("workflows", JSONArray().put(JSONObject().put("id", "w").put("title", "Workflow")))
        .put("agents", JSONArray(agents.toList()))
    private fun ids(projected: OrchestrationProjection.Snapshot) = projected.rows.map { it.data.optString("id") }

    @Test fun overviewSeparatesWorkflowsFromStandaloneAgentsAndKeepsWireOrder() {
        val projected = OrchestrationProjection.project(data(agent("scoped", "w"), agent("one"), agent("two")), "", null)
        assertEquals(listOf("w", "one", "two"), ids(projected))
        assertEquals(listOf("workflow", "agent", "agent"), projected.rows.map { it.kind })
        assertEquals("Read-only · updates automatically", projected.notice)
    }
    @Test fun workflowPreservesPhaseOrderAndGroupsOnlyItsAgentsWithUnassignedLast() {
        val data = data(agent("build", "w", phase = "Build"), agent("unknown", "w"), agent("explore", "w", phase = "Explore"), agent("foreign", "other"))
        data.getJSONArray("workflows").getJSONObject(0).put("phases", JSONArray()
            .put(JSONObject().put("title", "Explore")).put(JSONObject().put("title", "Build")))
        val projected = OrchestrationProjection.project(data, "w", null)
        assertEquals(listOf("phase:0", "explore", "phase:1", "build", "unassigned", "unknown"), ids(projected))
        assertEquals("Workflow · 3 agents", projected.subtitle); assertEquals("w", projected.workflow!!.optString("id"))
    }
    @Test fun unknownWorkflowDoesNotLeakOtherBranches() {
        val projected = OrchestrationProjection.project(data(agent("scoped", "w"), agent("free")), "missing", null)
        assertNull(projected.workflow); assertTrue(projected.rows.isEmpty()); assertEquals("Workflow · 0 agents", projected.subtitle)
    }
    @Test fun descendantsAreTransitiveOrderIndependentAndInputRemainsUntouched() {
        val data = data(agent("grandchild", "w", "child"), agent("unrelated", "w", "other"), agent("child", "w", "parent"), agent("parent", "w"))
        val original = data.toString()
        val projected = OrchestrationProjection.project(data, "", "parent")
        assertEquals(listOf("grandchild", "child"), ids(projected)); assertEquals(0, projected.data.getJSONArray("workflows").length())
        assertTrue(projected.rows.all { it.data.optString("workflowId").isEmpty() }); assertEquals(original, data.toString())
    }
    @Test fun legacyCloneFailureFallbackKeepsTheReceivedSnapshot() {
        val malformedClone = object : JSONObject() { override fun toString() = "not-json" }
        malformedClone.put("agents", JSONArray().put(agent("child", parent = "parent"))).put("available", true)
        val projected = OrchestrationProjection.project(malformedClone, "", "parent")
        assertSame(malformedClone, projected.data); assertEquals(listOf("child"), ids(projected))
    }
    @Test fun cyclicParentChainTerminatesAndNeverIncludesParentOrAnotherBranch() {
        val projected = OrchestrationProjection.project(data(agent("parent", "w", "child"), agent("child", "w", "parent"), agent("unrelated")), "", "parent")
        assertEquals(listOf("child"), ids(projected))
    }
    @Test fun unavailableEmptyAndSavedActivityHaveDistinctHonestNotices() {
        val empty = JSONObject().put("workflows", JSONArray()).put("agents", JSONArray())
        assertEquals("No saved subagent activity found for this session.", OrchestrationProjection.project(empty, "", null).notice)
        empty.put("available", true)
        assertEquals("No agents have been recorded here yet.", OrchestrationProjection.project(empty, "", null).notice)
        val saved = data(agent("a")).put("liveAvailable", false)
        assertEquals("Saved activity · live workflow phases require the observer and an idle Pi reload.", OrchestrationProjection.project(saved, "", null).notice)
    }
    @Test fun metricsAreScopedAndMissingCountersAreNotInventedZeroes() {
        val data = data(agent("a", "w").put("toolCalls", 2).put("outputTokens", 10), agent("b", "w").put("toolCalls", 3),
            agent("foreign", "other").put("toolCalls", 99).put("outputTokens", 999))
        val summary = OrchestrationProjection.project(data, "", null).workflowMetrics.getValue("w")
        assertEquals(5L, summary.tools); assertEquals(10L, summary.tokens); assertEquals("5 tools · 10 tokens", summary.label)
        val unknown = OrchestrationProjection.project(data(agent("a", "w")), "", null).workflowMetrics.getValue("w")
        assertNull(unknown.tools); assertNull(unknown.tokens); assertEquals("", unknown.label)
    }
    @Test fun knownZeroAndNegativeCountersPreserveExistingDisplayPolicy() {
        val summary = OrchestrationProjection.project(data(agent("a", "w").put("toolCalls", -2).put("outputTokens", 0)), "", null).workflowMetrics.getValue("w")
        assertEquals("0 tools · 0 tokens", summary.label)
        val partial = OrchestrationProjection.project(data(agent("a", "w").put("outputTokens", 12)), "", null).workflowMetrics.getValue("w")
        assertNull(partial.tools); assertEquals("12 tokens", partial.label)
    }
    @Test fun newSnapshotReplacesAggregatesInsteadOfAccumulatingOldCounters() {
        val data = data(agent("a", "w").put("toolCalls", 2))
        val first = OrchestrationProjection.project(data, "", null).workflowMetrics.getValue("w")
        data.getJSONArray("agents").getJSONObject(0).put("toolCalls", 5)
        val second = OrchestrationProjection.project(data, "", null).workflowMetrics.getValue("w")
        assertEquals("2 tools", first.label); assertEquals("5 tools", second.label)
    }
    @Test fun truncationTakesPriorityOverEmptyOrSavedNotices() {
        val data = JSONObject().put("truncated", true).put("available", false)
        assertEquals("Some activity is omitted by server safety limits.", OrchestrationProjection.project(data, "", null).notice)
    }
}
