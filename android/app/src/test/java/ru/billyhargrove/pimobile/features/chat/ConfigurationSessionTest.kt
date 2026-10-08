package ru.billyhargrove.pimobile.features.chat

import org.junit.Assert.*
import org.junit.Test

class ConfigurationSessionTest {
    private val change = ConfigurationSession.Change("p", "m", "high", "fast")
    private class Fixture(readOnly: Boolean = false) {
        var idle = true
        var id: String? = "one"
        var failure: Exception? = null
        var duringSend: (() -> Unit)? = null
        var duringPending: ((Boolean) -> Unit)? = null
        var duringResult: ((String?) -> Unit)? = null
        val sent = mutableListOf<ConfigurationSession.Change>()
        val pending = mutableListOf<Boolean>()
        val results = mutableListOf<String?>()
        val notices = mutableListOf<String>()
        val owner = ConfigurationSession(readOnly, { idle }, { sent.add(it); duringSend?.invoke(); failure?.let { throw it }; id },
            { pending.add(it); duringPending?.invoke(it) }, { results.add(it); duringResult?.invoke(it) }, notices::add)
    }
    @Test fun changePreservesFieldsAndReservesOneTicketUntilOwnedAck() {
        val f = Fixture(); assertTrue(f.owner.apply(change, true)); assertFalse(f.owner.canSubmit)
        assertEquals(listOf(change), f.sent); assertEquals(listOf(true), f.pending)
        assertFalse(f.owner.apply(change.copy(effort = "low"), false)); assertEquals(1, f.sent.size)
        assertFalse(f.owner.acknowledged("foreign", true, "")); assertFalse(f.owner.uncertain("foreign"))
        assertTrue(f.owner.acknowledged("one", true, "")); assertEquals(listOf<String?>(null), f.results)
        assertEquals(listOf(true, false), f.pending); assertTrue(f.owner.canSubmit)
        assertFalse(f.owner.acknowledged("one", false, "duplicate")); assertFalse(f.owner.uncertain("one"))
    }
    @Test fun readOnlyCannotWriteOrEmitFeedback() {
        val f = Fixture(true); assertFalse(f.owner.canSubmit); assertFalse(f.owner.apply(change, true))
        f.owner.reconcile(emptySet()); f.owner.close(); assertTrue(f.sent.isEmpty()); assertTrue(f.pending.isEmpty()); assertTrue(f.results.isEmpty())
    }
    @Test fun onlyModelChangesRequireIdleAndTierEffortStayAvailableDuringWork() {
        val f = Fixture(); f.idle = false
        assertFalse(f.owner.apply(change, true)); assertTrue(f.sent.isEmpty()); assertTrue(f.pending.isEmpty())
        assertEquals(listOf("Wait for the current task to finish"), f.results)
        val effort = ConfigurationSession.Change(null, null, "high", null)
        assertTrue(f.owner.apply(effort, false)); assertEquals(listOf(effort), f.sent)
        f.owner.acknowledged("one", true, ""); f.id = "two"
        assertTrue(f.owner.apply(effort.copy(effort = null, tier = "fast"), false))
    }
    @Test fun rejectionRetiresBeforeExplicitRetryAndOldAckCannotSettleIt() {
        val f = Fixture(); f.owner.apply(change, false); assertTrue(f.owner.acknowledged("one", false, "Unsupported"))
        assertEquals(listOf("Unsupported"), f.results); f.id = "two"; assertTrue(f.owner.apply(change, false))
        assertFalse(f.owner.acknowledged("one", true, "")); assertFalse(f.owner.canSubmit)
        assertTrue(f.owner.acknowledged("two", true, "")); assertEquals(2, f.results.size); assertEquals(2, f.sent.size)
    }
    @Test fun missingLedgerIsUnknownAndNeverReplayedWhileLiveTicketStaysPending() {
        val f = Fixture(); f.owner.apply(change, false); repeat(10) { f.owner.reconcile(setOf("one")) }
        assertTrue(f.results.isEmpty()); assertEquals(listOf(true), f.pending)
        repeat(10) { f.owner.reconcile(emptySet()) }; assertEquals(1, f.sent.size); assertEquals(1, f.results.size)
        assertEquals("Result unknown. Check the model in the terminal before retrying.", f.results.single())
        assertFalse(f.owner.acknowledged("one", true, "")); assertTrue(f.owner.canSubmit)
    }
    @Test fun nullAndEmptyIdsReleasePendingAndDoNotInventAcceptance() {
        for (id in listOf(null, "")) {
            val f = Fixture(); f.id = id; assertFalse(f.owner.apply(change, false))
            assertEquals(listOf(true, false), f.pending); assertTrue(f.owner.canSubmit)
            assertEquals(listOf("Pi is disconnected. Changes not sent."), f.results)
        }
    }
    @Test fun setupFailureReleasesPendingAndAllowsExplicitRetry() {
        val f = Fixture(); f.failure = IllegalStateException("Synthetic write failed")
        assertFalse(f.owner.apply(change, false)); assertEquals(listOf(true, false), f.pending)
        assertEquals(listOf("Changes could not be sent: Synthetic write failed"), f.results)
        f.failure = null; assertTrue(f.owner.apply(change, false)); assertEquals(2, f.sent.size)
    }
    @Test fun closeIsLocalIdempotentAndSuppressesAllLateResultsAndWrites() {
        val f = Fixture(); f.owner.apply(change, false); f.owner.close(); f.owner.close()
        assertEquals(listOf(true, false), f.pending); assertTrue(f.results.isEmpty()); assertFalse(f.owner.canSubmit)
        assertFalse(f.owner.apply(change, false)); assertFalse(f.owner.acknowledged("one", false, "late")); assertFalse(f.owner.uncertain("one"))
        f.owner.reconcile(emptySet()); assertEquals(1, f.sent.size); assertTrue(f.results.isEmpty())
    }
    @Test fun reentrantWriteCannotSendAnotherChangeBeforeIdIsAssigned() {
        val f = Fixture(); f.duringSend = { assertFalse(f.owner.apply(change.copy(effort = "low"), false)) }
        assertTrue(f.owner.apply(change, false)); assertEquals(1, f.sent.size); assertEquals(listOf(true), f.pending)
        assertEquals(listOf("Waiting for the previous change to be confirmed"), f.notices)
    }
    @Test fun closeDuringPendingCallbackPreventsTheWrite() {
        val f = Fixture(); f.duringPending = { if (it) f.owner.close() }
        assertFalse(f.owner.apply(change, false)); assertTrue(f.sent.isEmpty()); assertEquals(listOf(true, false), f.pending); assertTrue(f.results.isEmpty())
    }
    @Test fun closeDuringWriteCannotResurrectIdOrReportItsFailure() {
        for (fails in listOf(false, true)) {
            val f = Fixture(); f.duringSend = f.owner::close; if (fails) f.failure = IllegalStateException("late")
            assertFalse(f.owner.apply(change, false)); assertEquals(listOf(true, false), f.pending); assertTrue(f.results.isEmpty())
            assertFalse(f.owner.acknowledged("one", true, "")); assertFalse(f.owner.canSubmit)
        }
    }
    @Test fun terminalCallbackCanSubmitNextChangeWithoutOldResultClearingIt() {
        val f = Fixture(); f.owner.apply(change, false)
        f.duringResult = { f.id = "two"; assertTrue(f.owner.apply(change.copy(tier = "standard"), false)) }
        f.owner.acknowledged("one", true, ""); f.duringResult = null
        assertEquals(listOf(true, false, true), f.pending); assertFalse(f.owner.canSubmit); assertEquals(2, f.sent.size)
        assertFalse(f.owner.uncertain("one")); assertTrue(f.owner.acknowledged("two", true, "")); assertTrue(f.owner.canSubmit)
    }
}
