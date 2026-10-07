package ru.billyhargrove.pimobile.features.catalog

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class UsageProjectionTest {
    private val now = 1700000000000L
    private fun quota(used: Any, name: String = "weekly", reset: Long = 0) = JSONObject().put("name", name).put("usedPercent", used).put("resetsAt", reset)
    private fun project(windows: JSONArray, updated: Long = now, status: String = "ok") = UsageProjection.project(JSONObject().put("providers",
        JSONArray().put(JSONObject().put("provider", "codex").put("windows", windows).put("updatedAt", updated).put("status", status))), now, TimeZone.getTimeZone("UTC"))
    @Test fun malformedQuotasAreOmittedWithoutTurningMissingIntoZero() {
        val rows = project(JSONArray().put("bad").put(JSONObject()).put(quota(-1)).put(quota("NaN")).put(quota("Infinity")).put(quota(0)).put(quota(150)))
        assertEquals(listOf("codex", "cursor", "grok"), rows.map { it.key })
        assertEquals(listOf(0.0, 150.0), rows.first().quotas.map { it.used }); assertEquals(150.0, rows.first().primary!!.used, 0.0)
        assertNull(rows[1].primary); assertTrue(rows[1].quotas.isEmpty())
    }
    @Test fun projectionCopiesJsonAndPreservesAllQuotaNamesAndLargestReset() {
        val session = quota(12, "session").put("windowMinutes", 300)
        val weekly = quota(95, reset = now + 3600001)
        val windows = JSONArray().put(session).put(weekly).put(quota(20, "Cursor Models")).put(quota(30, "Other Models"))
        val provider = project(windows).first()
        session.put("usedPercent", 100); weekly.put("name", "changed"); windows.put(quota(99))
        assertEquals(listOf("5-hour", "Weekly", "Cursor Models", "Other Models"), provider.quotas.map { it.name })
        assertEquals(listOf("5-hour", "week", "models", "other"), provider.quotas.map { it.shortName })
        assertEquals("2h", provider.resetSummary); assertEquals(12.0, provider.quotas.first().used, 0.0)
    }
    @Test fun freshnessAcceptsSecondsAndHasExactFifteenMinuteBoundary() {
        val windows = JSONArray().put(quota(0))
        assertFalse(project(windows, (now - 900000) / 1000).first().stale)
        assertTrue(project(windows, now - 900001).first().stale)
        assertTrue(project(windows, 0).first().stale); assertTrue(project(windows, now, "unavailable").first().stale)
    }
    @Test fun resetSummaryUsesCeilingAndDoesNotOverflowHugeTimestamp() {
        fun summary(reset: Long) = project(JSONArray().put(quota(10, reset = reset))).first().resetSummary
        assertEquals("reset unknown", summary(0)); assertEquals("Reset due", summary(now))
        assertEquals("1m", summary(now + 1)); assertEquals("1h", summary(now + 3600000)); assertEquals("2d", summary(now + 86400001))
        assertTrue(summary(Long.MAX_VALUE).endsWith("d"))
    }
    @Test fun firstProviderWinsAndNoProviderFallbackIsInvented() {
        val value = JSONObject().put("providers", JSONArray().put(1).put(JSONObject().put("provider", "codex").put("windows", JSONArray().put(quota(12))))
            .put(JSONObject().put("provider", "codex").put("windows", JSONArray().put(quota(99)))))
        assertEquals(12.0, UsageProjection.project(value, now).first().primary!!.used, 0.0)
        assertTrue(UsageProjection.project(JSONObject(), now).all { it.quotas.isEmpty() && it.updated.isEmpty() })
    }
}
