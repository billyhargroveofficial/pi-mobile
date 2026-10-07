package ru.billyhargrove.pimobile.features.orchestration

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OrchestrationSessionTest {
    private class Wire : OrchestrationSession.Transport {
        data class Read(val target: OrchestrationSession.Target, val before: Long, val complete: (Result<JSONObject>) -> Unit)
        var configured = true
        var cancellations = 0
        val reads = mutableListOf<Read>()
        val polls = mutableListOf<() -> Unit>()
        var scheduled: (() -> Unit)? = null
        override fun available() = configured
        override fun fetch(target: OrchestrationSession.Target, before: Long, result: (Result<JSONObject>) -> Unit) { reads.add(Read(target, before, result)) }
        override fun schedule(delayMs: Long, action: () -> Unit) {
            assertEquals(2500L, delayMs); scheduled = action; polls.add(action)
        }
        override fun cancelPoll() { cancellations++; scheduled = null }
        fun succeed(index: Int, data: JSONObject) = reads[index].complete(Result.success(data))
        fun fail(index: Int) = reads[index].complete(Result.failure(Exception("synthetic; do not expose raw errors")))
    }
    private class Fixture(agent: String = "", workflow: String = "") {
        val wire = Wire()
        var atTail = true
        val target = OrchestrationSession.Target("session-a", workflow, agent)
        val owner = OrchestrationSession(target, wire) { atTail }
    }
    private fun activity(id: String = "w") = JSONObject().put("available", true).put("liveAvailable", true)
        .put("workflows", JSONArray().put(JSONObject().put("id", id).put("title", id).put("status", "running")))
    private fun message(id: String, role: String = "user", text: String = id) = JSONObject().put("id", id).put("role", role).put("text", text).put("turnId", "turn").put("phase", "work")
    private fun page(vararg messages: JSONObject, source: String = "live", status: String = "running", before: Long = 100, more: Boolean = true) = JSONObject()
        .put("agent", JSONObject().put("id", "agent-a").put("status", status)).put("source", source)
        .put("messages", JSONArray(messages.toList())).put("before", before).put("hasMore", more)
    private fun ids(owner: OrchestrationSession) = owner.transcriptItems.mapNotNull { it.row.message?.id() }

    @Test fun targetIsScopedAndStartDoesNotOverlapOrMultiplyPolling() {
        val f = Fixture(workflow = "w"); f.owner.start(); f.owner.start(); f.owner.refresh()
        assertEquals(1, f.wire.reads.size); assertTrue(f.owner.busy); assertEquals(f.target, f.wire.reads.single().target)
        assertEquals(0L, f.wire.reads.single().before)
        f.wire.succeed(0, activity()); assertFalse(f.owner.busy); assertFalse(f.owner.loading)
        assertEquals(1, f.wire.polls.size); f.wire.scheduled!!.invoke(); assertEquals(2, f.wire.reads.size)
    }
    @Test fun missingCredentialsNeverFetchOrScheduleAndManualRefreshCanRecover() {
        val f = Fixture(); f.wire.configured = false; f.owner.start(); f.owner.refresh()
        assertTrue(f.wire.reads.isEmpty()); assertTrue(f.wire.polls.isEmpty()); assertFalse(f.owner.busy)
        f.wire.configured = true; f.owner.refresh(); assertEquals(1, f.wire.reads.size)
    }
    @Test fun stopRejectsInFlightCompletionAndCancelsPoll() {
        val f = Fixture(); f.owner.start(); f.owner.stop(); f.wire.succeed(0, activity("stale"))
        assertTrue(f.owner.rows.isEmpty()); assertFalse(f.owner.busy); assertNull(f.wire.scheduled)
        f.owner.refresh(); assertEquals(1, f.wire.reads.size)
    }
    @Test fun restartRejectsOldResponseWithoutClearingNewRequestsBusyState() {
        val f = Fixture(); f.owner.start(); f.owner.stop(); f.owner.start()
        f.wire.succeed(0, activity("stale")); assertTrue(f.owner.busy); assertTrue(f.owner.rows.isEmpty())
        f.wire.succeed(1, activity("fresh")); assertEquals("fresh", f.owner.rows.single().data.optString("id")); assertFalse(f.owner.busy)
    }
    @Test fun duplicateCompletionCannotPublishOrScheduleTwice() {
        val f = Fixture(); f.owner.start(); f.wire.succeed(0, activity("first")); f.wire.succeed(0, activity("duplicate"))
        assertEquals("first", f.owner.rows.single().data.optString("id")); assertEquals(1, f.wire.polls.size)
        f.wire.scheduled!!.invoke(); f.wire.fail(0); assertTrue(f.owner.busy); assertEquals(1, f.wire.polls.size)
    }
    @Test fun cancelledPollCannotRunAcrossStopRestart() {
        val f = Fixture(); f.owner.start(); f.wire.succeed(0, activity()); val stalePoll = f.wire.scheduled!!
        f.owner.stop(); f.owner.start(); f.wire.succeed(1, activity()); stalePoll()
        assertEquals(2, f.wire.reads.size); assertFalse(f.owner.busy)
    }
    @Test fun replacedPollCannotRunAfterManualRefresh() {
        val f = Fixture(); f.owner.start(); f.wire.succeed(0, activity()); val stalePoll = f.wire.scheduled!!
        f.owner.refresh(); f.wire.succeed(1, activity()); stalePoll()
        assertEquals(2, f.wire.reads.size); f.wire.scheduled!!.invoke(); assertEquals(3, f.wire.reads.size)
    }
    @Test fun failureUsesHonestUnavailableNoticeAndRetainsLastSnapshot() {
        val f = Fixture(); f.owner.start(); f.wire.fail(0)
        assertFalse(f.owner.loading); assertEquals("Activity unavailable. Update the gateway or try again.", f.owner.notice)
        f.owner.refresh(); f.wire.succeed(1, activity("retained")); f.owner.refresh(); f.wire.fail(2)
        assertEquals("retained", f.owner.rows.single().data.optString("id"))
        assertEquals("Activity unavailable · showing last received details. Retrying…", f.owner.notice)
        assertEquals(3, f.wire.polls.size)
    }
    @Test fun historyCursorIsNotReplacedByTailPollAndPagingCannotOverlap() {
        val f = Fixture("agent-a"); f.owner.start(); f.wire.succeed(0, page(message("tail"), before = 123))
        f.owner.refresh(); f.wire.succeed(1, page(message("new"), before = 999, more = false))
        assertTrue(f.owner.olderVisible); f.owner.refresh(true); f.owner.refresh(true)
        assertEquals(3, f.wire.reads.size); assertEquals(123L, f.wire.reads[2].before)
        f.wire.succeed(2, page(message("old"), before = 42)); f.owner.refresh(true)
        assertEquals(42L, f.wire.reads[3].before)
    }
    @Test fun historyPrependsDeduplicatesAndRetainsNewestCopyWithoutFollowingTail() {
        val f = Fixture("agent-a"); f.owner.renderAgent(page(message("tail", text = "newest")), false)
        val revision = f.owner.followTailRevision
        f.owner.renderAgent(page(message("old"), message("tail", text = "older copy"), more = false), true)
        assertEquals(listOf("old", "tail"), ids(f.owner)); assertEquals("newest", f.owner.transcriptItems.last().row.message!!.text())
        assertEquals(revision, f.owner.followTailRevision); assertFalse(f.owner.olderVisible)
    }
    @Test fun repeatedTailSnapshotsUpdateInPlaceAndOnlyFollowAnIdleTailReader() {
        val f = Fixture("agent-a"); f.owner.renderAgent(page(message("tail")), false)
        val revision = f.owner.followTailRevision; f.atTail = false
        f.owner.renderAgent(page(message("tail", text = "changed"), message("later")), false)
        assertEquals(listOf("tail", "later"), ids(f.owner)); assertEquals(revision, f.owner.followTailRevision)
        f.atTail = true; f.owner.renderAgent(page(message("later")), false)
        assertEquals(revision + 1, f.owner.followTailRevision)
    }
    @Test fun sourceReplacementClearsHistoryAndForcesInitialTailWithoutClaimingLive() {
        val f = Fixture("agent-a"); f.owner.renderAgent(page(message("live")), false)
        f.owner.renderAgent(page(message("older")), true); f.atTail = false
        val revision = f.owner.followTailRevision
        f.owner.renderAgent(page(message("saved"), source = "saved-log", status = "completed", before = 7, more = false), false)
        assertEquals(listOf("saved"), ids(f.owner)); assertFalse(f.owner.olderVisible)
        assertEquals(revision + 1, f.owner.followTailRevision); assertEquals("Saved transcript · read-only", f.owner.notice)
        f.owner.start(); f.wire.succeed(0, page(message("saved"), source = "saved-log", status = "completed", before = 99))
        f.owner.refresh(true); assertEquals(7L, f.wire.reads.last().before)
    }
    @Test fun settlementAndRepeatedCompletionPreserveManualToolDisclosure() {
        val f = Fixture("agent-a"); val tool = message("tool", "toolResult").put("toolName", "read")
        f.owner.renderAgent(page(message("user"), tool), false)
        assertTrue(f.owner.transcriptItems.single { it.row.header }.expanded)
        f.owner.renderAgent(page(message("user"), tool, status = "completed"), false)
        val header = f.owner.transcriptItems.single { it.row.header }; assertFalse(header.expanded)
        f.owner.toggle(header.row.group); f.owner.renderAgent(page(message("user"), tool, status = "completed"), false)
        assertTrue(f.owner.transcriptItems.single { it.row.header }.expanded)
        f.owner.renderAgent(page(message("other", "toolResult").put("toolName", "read"), source = "saved", status = "completed"), false)
        assertFalse(f.owner.transcriptItems.single { it.row.header }.expanded)
    }
    @Test fun summaryAndMissingAgentDoNotEraseTranscriptAndErrorHasPriority() {
        val f = Fixture("agent-a"); f.owner.renderAgent(page(message("tail")).put("childCount", 2), false)
        f.owner.renderAgent(JSONObject(), false); assertEquals("Agent details unavailable", f.owner.notice)
        assertEquals(listOf("tail"), ids(f.owner)); assertEquals(2, f.owner.childrenCount)
        val failed = page(message("tail"), source = "saved").put("truncated", true)
        failed.getJSONObject("agent").put("error", "Scoped agent error")
        f.owner.renderAgent(failed, false); assertEquals("Scoped agent error", f.owner.notice)
    }
    @Test fun emptyAndTruncatedPagesNeverInventProgressOrLoseExistingMessages() {
        val f = Fixture("agent-a"); f.owner.renderAgent(page(message("tail")), false)
        f.owner.renderAgent(page(), false); assertEquals(listOf("tail"), ids(f.owner))
        assertEquals("No transcript yet · the agent may be queued or its log may have expired.", f.owner.notice)
        f.owner.renderAgent(page().put("truncated", true), false)
        assertEquals("Some large or older records were shortened or omitted.", f.owner.notice)
    }
}
