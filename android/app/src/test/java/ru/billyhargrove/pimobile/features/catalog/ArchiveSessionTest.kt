package ru.billyhargrove.pimobile.features.catalog

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ArchiveSessionTest {
    private class Wire : ArchiveSession.Transport {
        data class Read(val offset: Int, val query: String, val done: (Result<JSONObject>) -> Unit)
        val reads = mutableListOf<Read>(); val timers = mutableListOf<() -> Unit>()
        var scheduled: (() -> Unit)? = null
        override fun fetch(offset: Int, query: String, result: (Result<JSONObject>) -> Unit) { reads.add(Read(offset, query, result)) }
        override fun scheduleSearch(delayMs: Long, action: () -> Unit) { assertEquals(250L, delayMs); scheduled = action; timers.add(action) }
        override fun cancelSearch() { scheduled = null }
        fun done(index: Int, vararg ids: String, next: Int = 40, more: Boolean = false) = reads[index].done(Result.success(page(*ids, next = next, more = more)))
        fun fail(index: Int, message: String = "synthetic") = reads[index].done(Result.failure(Exception(message)))
    }
    private class Fixture(deletable: Boolean = false) {
        val wire = Wire(); val deletes = mutableListOf<Pair<String, (Result<Unit>) -> Unit>>()
        val owner = ArchiveSession(wire, ZoneId.of("UTC"), { LocalDate.of(2026, 10, 7) },
            if (deletable) { id, done -> deletes.add(id to done); Unit } else null)
        fun search(value: String) { owner.changeInput(value); owner.submitSearch() }
        fun deleteFirst() { owner.requestDelete(owner.rows.first()); owner.confirmDelete() }
    }
    @Test fun initialReadAndPaginationAreSingleFlightAndRetainFirstCopy() {
        val f = Fixture(); f.owner.load(); f.owner.load(); assertEquals(1, f.wire.reads.size)
        assertTrue(f.owner.loading); f.wire.done(0, "a", next = 42, more = true)
        f.owner.load(); f.owner.load(); assertEquals(42, f.wire.reads.last().offset); assertEquals(2, f.wire.reads.size)
        f.wire.done(1, "a", "b", next = 84)
        assertEquals(listOf("a", "b"), f.owner.rows.map { it.id }); assertFalse(f.owner.hasMore); assertFalse(f.owner.loading)
    }
    @Test fun searchGenerationsRejectStaleReplyWithoutClearingCurrentBusyState() {
        val f = Fixture(); f.owner.load(); f.search("  chosen  "); f.wire.done(0, "stale")
        assertTrue(f.owner.loading); assertTrue(f.owner.rows.isEmpty()); assertEquals("chosen", f.owner.query)
        assertEquals("chosen", f.wire.reads.last().query); assertEquals(0, f.wire.reads.last().offset)
        f.wire.done(1, "new"); assertEquals(listOf("new"), f.owner.rows.map { it.id })
    }
    @Test fun duplicateCompletionCannotAppendOrFinishNextPage() {
        val f = Fixture(); f.owner.load(); f.wire.done(0, "first", more = true); f.owner.load()
        f.wire.done(0, "duplicate"); f.wire.fail(0)
        assertEquals(listOf("first"), f.owner.rows.map { it.id }); assertTrue(f.owner.loading); assertEquals("", f.owner.error)
        f.wire.done(1, "second"); assertEquals(listOf("first", "second"), f.owner.rows.map { it.id })
    }
    @Test fun debounceOnlyCommitsLatestTextAndSubmitRetiresAllTimers() {
        val f = Fixture(); f.owner.load(); f.owner.changeInput("old"); val old = f.wire.scheduled!!
        f.owner.changeInput("chosen"); old(); assertEquals(1, f.wire.reads.size)
        val selected = f.wire.scheduled!!; f.owner.submitSearch(); selected(); assertEquals(2, f.wire.reads.size)
        assertEquals("chosen", f.wire.reads.last().query)
        f.owner.changeInput(" chosen "); assertNull(f.wire.scheduled); assertEquals(2, f.wire.reads.size)
    }
    @Test fun aPreviousSubmittedQueryCannotFireAfterSearchReturnsToSameText() {
        val f = Fixture(); f.owner.changeInput("a"); val stale = f.wire.scheduled!!; f.owner.submitSearch()
        f.search("b"); f.owner.changeInput("a"); stale(); assertEquals("b", f.owner.query)
        f.wire.scheduled!!(); assertEquals("a", f.owner.query); assertEquals(3, f.wire.reads.size)
    }
    @Test fun failureKeepsLoadedRowsAndRetryUsesSameCursorAndSearch() {
        val f = Fixture(); f.search("query"); f.wire.done(0, "a", next = 42, more = true)
        f.owner.load(); f.wire.fail(1); assertEquals("synthetic", f.owner.error)
        assertEquals(listOf("a"), f.owner.rows.map { it.id }); assertFalse(f.owner.hasMore)
        f.owner.load(); assertEquals(42, f.wire.reads.last().offset); assertEquals("query", f.wire.reads.last().query)
        f.wire.done(2, "b"); assertEquals("", f.owner.error); assertEquals(listOf("a", "b"), f.owner.rows.map { it.id })
    }
    @Test fun closeInvalidatesLoadingAndDebounceAndIgnoresRepeatedPublicActions() {
        val f = Fixture(); f.owner.load(); f.owner.changeInput("chosen"); val old = f.wire.scheduled!!
        f.owner.close(); f.wire.done(0, "stale"); old(); f.owner.load(); f.owner.submitSearch(); f.owner.changeInput("other")
        assertFalse(f.owner.loading); assertTrue(f.owner.rows.isEmpty()); assertEquals(1, f.wire.reads.size); assertNull(f.wire.scheduled)
    }
    @Test fun deleteNeedsConfirmationAndCannotRepeatWhileItsResultIsPending() {
        val f = Fixture(true); f.owner.load(); f.wire.done(0, "a"); val row = f.owner.rows.single()
        f.owner.requestDelete(row); assertEquals(row, f.owner.confirmation); assertTrue(f.deletes.isEmpty())
        f.owner.cancelDelete(); f.owner.confirmDelete(); assertTrue(f.deletes.isEmpty())
        f.owner.requestDelete(row); f.owner.confirmDelete(); f.owner.requestDelete(row); f.owner.confirmDelete()
        assertEquals(1, f.deletes.size); assertFalse(f.owner.canDelete(row)); assertNull(f.owner.confirmation)
    }
    @Test fun successfulDeleteReloadsCurrentQueryAndDuplicateAckDoesNotRestartIt() {
        val f = Fixture(true); f.owner.load(); f.wire.done(0, "a"); f.deleteFirst(); f.search("current")
        f.deletes.single().second(Result.success(Unit)); assertEquals(3, f.wire.reads.size)
        assertEquals("current", f.wire.reads.last().query); assertEquals(0, f.wire.reads.last().offset)
        f.wire.done(1, "deleted-stale"); assertTrue(f.owner.loading); assertTrue(f.owner.rows.isEmpty())
        f.deletes.single().second(Result.success(Unit)); assertEquals(3, f.wire.reads.size)
        f.wire.done(2, "current"); assertEquals(listOf("current"), f.owner.rows.map { it.id })
    }
    @Test fun lateDeleteFailureCannotOverwriteNewSearchOrClosedSheet() {
        val f = Fixture(true); f.owner.load(); f.wire.done(0, "a"); f.deleteFirst(); f.search("new")
        f.deletes.single().second(Result.failure(Exception("old delete"))); assertEquals("", f.owner.error)
        f.wire.done(1, "b"); f.deleteFirst(); f.owner.close(); f.deletes.last().second(Result.success(Unit))
        assertEquals(2, f.wire.reads.size)
    }
    @Test fun missingDeletePortAndStaleConfirmationCannotSendMutation() {
        val readOnly = Fixture(); readOnly.owner.load(); readOnly.wire.done(0, "a")
        readOnly.owner.requestDelete(readOnly.owner.rows.single()); assertNull(readOnly.owner.confirmation)
        val f = Fixture(true); f.owner.load(); f.wire.done(0, "a"); f.owner.requestDelete(f.owner.rows.single()); f.search("new")
        f.owner.confirmDelete(); assertTrue(f.deletes.isEmpty())
    }
    @Test fun deleteFailureAllowsExplicitRetryWithFreshConfirmation() {
        val f = Fixture(true); f.owner.load(); f.wire.done(0, "a"); f.deleteFirst()
        f.deletes.single().second(Result.failure(Exception("failed"))); assertEquals("failed", f.owner.error)
        assertTrue(f.owner.canDelete(f.owner.rows.single())); f.deleteFirst(); assertEquals(2, f.deletes.size)
        f.deletes.first().second(Result.success(Unit)); assertEquals(1, f.wire.reads.size)
    }
    @Test fun synchronousReadFailureCanBeRetried() {
        val owner = ArchiveSession(object : ArchiveSession.Transport {
            override fun fetch(offset: Int, query: String, result: (Result<JSONObject>) -> Unit) { throw IllegalStateException() }
            override fun scheduleSearch(delayMs: Long, action: () -> Unit) {}
            override fun cancelSearch() {}
        }, ZoneId.of("UTC"), { LocalDate.of(2026, 10, 7) })
        owner.load(); assertFalse(owner.loading); assertEquals("Could not load history", owner.error)
        owner.load(); assertFalse(owner.loading)
    }
    companion object {
        private fun page(vararg ids: String, next: Int = 40, more: Boolean = false) = JSONObject().put("sessions", JSONArray(ids.map { id ->
            JSONObject().put("id", id).put("title", id).put("modified", "2026-10-07T12:00:00Z")
        })).put("nextOffset", next).put("hasMore", more)
    }
}
