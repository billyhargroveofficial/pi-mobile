package ru.billyhargrove.pimobile.features.chat

import org.junit.Assert.*
import org.junit.Test

class HistorySessionTest {
    private class Fixture {
        val reads = mutableListOf<String>()
        val notices = mutableListOf<String>()
        var user = false
        var busy = false
        var read: (String) -> String? = { before -> reads.add(before); "r${reads.size}" }
        val history = HistorySession({ read(it) }, { user }, { busy = it }, notices::add)
        fun snapshot(epoch: Long = 1, before: String = "a", more: Boolean = true, cached: Boolean = false) =
            history.snapshot(epoch, HistorySession.Cursor(before, more), cached)
        fun page(id: String = "r1", next: String = "b", more: Boolean = true, epoch: Long = 1) =
            history.received(id, epoch, HistorySession.Cursor(next, more))
        fun ack(id: String = "r1", ok: Boolean = true) = history.acknowledged(id, ok, "rejected")
    }
    @Test fun pageIsAcceptedOnceAndNextCursorWaitsForAck() {
        val f = Fixture(); f.snapshot(); f.history.ensureUserContext(); assertTrue(f.busy)
        assertFalse(f.page("foreign")); assertTrue(f.page()); assertFalse(f.page(next = "corrupt"))
        repeat(20) { f.history.ensureUserContext(); f.history.loadOlder() }
        assertEquals(listOf("a"), f.reads); assertTrue(f.busy)
        assertTrue(f.ack()); assertEquals(listOf("a", "b"), f.reads); assertTrue(f.busy)
        assertFalse(f.ack()); assertFalse(f.history.uncertain("r1", "late")); assertTrue(f.history.owns("r2"))
    }
    @Test fun canonicalUserOrTerminalPageStopsAutomaticContinuation() {
        val f = Fixture(); f.snapshot(); f.history.ensureUserContext(); f.page(); f.user = true; f.ack()
        assertFalse(f.busy); assertEquals(1, f.reads.size)
        assertTrue(f.history.loadOlder()); f.page("r2", "", false); f.user = false; f.ack("r2")
        repeat(20) { f.history.ensureUserContext(); f.history.loadOlder() }
        assertEquals(listOf("a", "b"), f.reads); assertFalse(f.busy)
    }
    @Test fun repeatedCursorCannotLoopEvenWithStreamingAndReaderGestures() {
        val f = Fixture(); f.snapshot(); f.history.ensureUserContext(); assertTrue(f.page(next = "a")); f.ack()
        repeat(100) { f.history.ensureUserContext(); f.history.loadOlder() }
        assertEquals(listOf("a"), f.reads); assertFalse(f.busy); assertEquals(listOf("History cursor did not advance"), f.notices)
    }
    @Test fun cursorCycleIsDetectedAcrossPages() {
        val f = Fixture(); f.snapshot(); f.history.ensureUserContext(); f.page(); f.ack()
        assertTrue(f.page("r2", "a")); f.ack("r2"); repeat(100) { f.history.ensureUserContext() }
        assertEquals(listOf("a", "b"), f.reads); assertFalse(f.busy)
    }
    @Test fun missingOrEmptyContinuationStillAllowsOnePageButCannotInventNextCursor() {
        for (next in listOf(null, HistorySession.Cursor("", true))) {
            val f = Fixture(); f.snapshot(); f.history.ensureUserContext()
            assertTrue(f.history.received("r1", 1, next)); f.ack(); repeat(100) { f.history.ensureUserContext(); f.history.loadOlder() }
            assertEquals(listOf("a"), f.reads); assertFalse(f.busy)
        }
    }
    @Test fun missingBodyAndWrongEpochNeverAdvanceAndDoNotAutoRetry() {
        for (invalidBody in listOf(true, false)) {
            val f = Fixture(); f.snapshot(); f.history.ensureUserContext()
            if (invalidBody) assertTrue(f.history.invalid("r1")) else assertFalse(f.page(epoch = 2))
            assertFalse(f.ack()); repeat(100) { f.history.ensureUserContext() }
            assertEquals(listOf("a"), f.reads); assertFalse(f.busy)
            assertTrue(f.history.loadOlder()); assertEquals(listOf("a", "a"), f.reads)
        }
    }
    @Test fun successWithoutDataAndRejectionRequireExplicitRetry() {
        for (ok in listOf(true, false)) {
            val f = Fixture(); f.snapshot(); f.history.ensureUserContext(); f.ack(ok = ok)
            repeat(100) { f.history.ensureUserContext() }; assertEquals(1, f.reads.size); assertFalse(f.busy)
            assertEquals(if (ok) "Pi returned no history" else "rejected", f.notices.single())
            assertTrue(f.history.loadOlder()); assertTrue(f.busy)
        }
    }
    @Test fun readFailureNullAndEmptyTicketReleaseLoadingWithoutAnAutomaticStorm() {
        for (fail in 0..2) {
            val f = Fixture(); var attempts = 0
            f.read = { attempts++; when (fail) { 0 -> null; 1 -> ""; else -> error("synthetic") } }
            f.snapshot(); repeat(100) { f.history.ensureUserContext() }
            assertEquals(1, attempts); assertFalse(f.busy); assertEquals(1, f.notices.size)
            assertFalse(f.history.loadOlder()); assertEquals(2, attempts)
        }
    }
    @Test fun cachedAbsentAndTerminalSnapshotsCannotRead() {
        val f = Fixture(); f.snapshot(cached = true); f.history.ensureUserContext(); assertFalse(f.history.loadOlder())
        f.snapshot(more = false); f.history.ensureUserContext(); assertFalse(f.history.loadOlder())
        f.snapshot(before = ""); f.history.ensureUserContext(); assertFalse(f.history.loadOlder())
        f.history.snapshot(1, null, false); assertFalse(f.history.loadOlder()); assertTrue(f.reads.isEmpty())
    }
    @Test fun authoritativeSnapshotRetiresOldTicketAndResetsCursorFenceEvenWithinEpoch() {
        val f = Fixture(); f.snapshot(); f.history.ensureUserContext(); f.page(next = "a"); f.ack()
        f.snapshot(); f.history.ensureUserContext(); assertEquals(listOf("a", "a"), f.reads)
        assertFalse(f.page()); assertFalse(f.ack()); assertTrue(f.history.owns("r2"))
        f.snapshot(epoch = 2, before = "new"); assertFalse(f.busy); assertFalse(f.page("r2"))
        f.history.ensureUserContext(); assertEquals(listOf("a", "a", "new"), f.reads)
    }
    @Test fun detachedLedgerPreservesLiveAndRetiresMissingTicketWithoutReplay() {
        val f = Fixture(); f.snapshot(); f.history.ensureUserContext(); f.history.reconcile(setOf("r1")); assertTrue(f.busy)
        f.history.reconcile(emptySet()); assertFalse(f.busy); repeat(20) { f.history.ensureUserContext() }
        assertEquals(1, f.reads.size); assertFalse(f.page()); assertFalse(f.ack())
        assertTrue(f.history.loadOlder()); assertFalse(f.history.uncertain("r1", "late")); assertTrue(f.busy)
    }
    @Test fun closeIgnoresAllLateCallbacksAndNewSnapshots() {
        val f = Fixture(); f.snapshot(); f.history.ensureUserContext(); f.history.close(); f.history.close()
        assertFalse(f.busy); assertFalse(f.page()); assertFalse(f.ack()); assertFalse(f.history.uncertain("r1", "late"))
        f.snapshot(epoch = 2); f.history.ensureUserContext(); assertFalse(f.history.loadOlder())
        assertEquals(1, f.reads.size); assertTrue(f.notices.isEmpty())
    }
    @Test fun reentrantCloseOrSnapshotDuringReadCannotResurrectTicket() {
        for (close in listOf(true, false)) {
            val f = Fixture(); f.snapshot(); f.read = { if (close) f.history.close() else f.snapshot(epoch = 2); "late-id" }
            assertFalse(f.history.loadOlder()); assertFalse(f.busy); assertFalse(f.history.owns("late-id"))
        }
    }
}
