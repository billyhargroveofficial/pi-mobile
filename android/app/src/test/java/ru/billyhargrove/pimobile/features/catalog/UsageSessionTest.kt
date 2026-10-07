package ru.billyhargrove.pimobile.features.catalog

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UsageSessionTest {
    private class Wire : UsageSession.Transport {
        data class Read(val base: String, val token: String, val done: (Result<JSONObject>) -> Unit)
        val reads = mutableListOf<Read>(); val timers = mutableListOf<() -> Unit>()
        var scheduled: (() -> Unit)? = null
        override fun fetch(base: String, token: String, result: (Result<JSONObject>) -> Unit) { reads.add(Read(base, token, result)) }
        override fun schedule(delayMs: Long, action: () -> Unit) { assertEquals(60000L, delayMs); scheduled = action; timers.add(action) }
        override fun cancelPoll() { scheduled = null }
        fun done(index: Int, used: Int) = reads[index].done(Result.success(data(used)))
    }
    private fun owner(wire: Wire) = UsageSession(wire) { 1700000000000 }
    @Test fun startsOnceWithExactCredentialsAndPollsOnlyAfterCompletion() {
        val wire = Wire(); val owner = owner(wire)
        owner.start("host-a", "secret-a"); owner.start("host-a", "secret-a"); owner.refresh()
        assertEquals(1, wire.reads.size); assertEquals("secret-a", wire.reads.single().token); assertTrue(owner.busy)
        wire.done(0, 36); assertFalse(owner.busy); assertEquals(36.0, owner.providers.first().primary!!.used, 0.0)
        wire.scheduled!!(); assertEquals(2, wire.reads.size)
    }
    @Test fun endpointChangeRejectsOldReplyAndDuplicateCompletionCannotFinishAnotherRead() {
        val wire = Wire(); val owner = owner(wire)
        owner.start("host-a", "a"); owner.start("host-b", "b"); wire.done(0, 90)
        assertTrue(owner.busy); assertNull(owner.providers.first().primary)
        wire.done(1, 12); owner.refresh(); wire.done(1, 90)
        assertTrue(owner.busy); assertEquals(12.0, owner.providers.first().primary!!.used, 0.0)
        wire.done(2, 15); assertEquals(2, wire.timers.size)
    }
    @Test fun tokenChangeOnSameHostAlsoInvalidatesReply() {
        val wire = Wire(); val owner = owner(wire)
        owner.start("host", "a"); owner.start("host", "b"); wire.done(0, 90); wire.done(1, 0)
        assertEquals("b", wire.reads.last().token); assertEquals(0.0, owner.providers.first().primary!!.used, 0.0)
    }
    @Test fun retiredTimerCannotRunAfterManualRefreshOrRestart() {
        val wire = Wire(); val owner = owner(wire)
        owner.start("host", "a"); wire.done(0, 36); val retired = wire.scheduled!!
        owner.refresh(); wire.done(1, 37); retired(); assertEquals(2, wire.reads.size)
        val oldLifecycle = wire.scheduled!!; owner.stop(); owner.start("host", "a"); wire.done(2, 38)
        oldLifecycle(); assertEquals(3, wire.reads.size); wire.scheduled!!(); assertEquals(4, wire.reads.size)
    }
    @Test fun stopClearsDetailsAndIgnoresInFlightReplies() {
        val wire = Wire(); val owner = owner(wire)
        owner.start("host", "a"); owner.select("all"); owner.stop(); wire.done(0, 90); owner.refresh()
        assertFalse(owner.visible); assertNull(owner.selected); assertFalse(owner.busy); assertNull(wire.scheduled)
        assertEquals(1, wire.reads.size)
    }
    @Test fun unavailableFailureIsHonestAndStillSchedulesRecovery() {
        val wire = Wire(); val owner = owner(wire)
        owner.start("host", "a"); wire.done(0, 36); owner.refresh()
        wire.reads[1].done(Result.failure(Exception("synthetic")))
        assertTrue(owner.visible); assertNull(owner.providers.first().primary); assertFalse(owner.busy)
        wire.scheduled!!(); wire.done(2, 40); assertEquals(40.0, owner.providers.first().primary!!.used, 0.0)
    }
    @Test fun missingCredentialsNeverReadOrScheduleAndSelectionIsAllowlisted() {
        val wire = Wire(); val owner = owner(wire)
        owner.start("", "a"); owner.start("host", ""); owner.refresh()
        assertTrue(wire.reads.isEmpty()); assertTrue(wire.timers.isEmpty()); assertFalse(owner.visible)
        owner.render(data(0)); owner.select("not-a-provider"); assertNull(owner.selected)
        owner.select("cursor"); assertEquals("cursor", owner.selected)
    }
    @Test fun synchronousTransportFailureDoesNotLeavePollingBusy() {
        var timer: (() -> Unit)? = null
        val owner = UsageSession(object : UsageSession.Transport {
            override fun fetch(base: String, token: String, result: (Result<JSONObject>) -> Unit) { throw IllegalStateException("synthetic") }
            override fun schedule(delayMs: Long, action: () -> Unit) { timer = action }
            override fun cancelPoll() { timer = null }
        }) { 0 }
        owner.start("host", "a"); assertFalse(owner.busy); assertNotNull(timer)
    }
    companion object {
        private fun data(used: Int) = JSONObject().put("providers", JSONArray().put(JSONObject().put("provider", "codex").put("status", "ok")
            .put("updatedAt", 1700000000000).put("windows", JSONArray().put(JSONObject().put("name", "weekly").put("usedPercent", used)))))
    }
}
