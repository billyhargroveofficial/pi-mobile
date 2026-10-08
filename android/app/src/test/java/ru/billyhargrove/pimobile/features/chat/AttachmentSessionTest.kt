package ru.billyhargrove.pimobile.features.chat

import org.junit.Assert.*
import org.junit.Test
import ru.billyhargrove.pimobile.core.ImageGuard

class AttachmentSessionTest {
    private open class Port : AttachmentSession.Port<String, String> {
        class Job(val keys: List<String>, val budget: Long, val done: (AttachmentSession.Batch<String>) -> Boolean) : AttachmentSession.Control {
            var cancels = 0; var onCancel: () -> Unit = {}
            override fun cancel() { cancels++; onCancel() }
        }
        val jobs = mutableListOf<Job>()
        override fun start(keys: List<String>, budget: Long, done: (AttachmentSession.Batch<String>) -> Boolean) = Job(keys, budget, done).also(jobs::add)
    }
    private class Fixture(readOnly: Boolean = false, val port: Port = Port()) {
        var active = true; var busy = false; var limits = 0
        val values = mutableListOf<String>(); val errors = mutableListOf<String>(); var finishes = 0
        val owner = AttachmentSession(readOnly, port, { if (busy) false else { busy = true; true } },
            { finishes++; busy = false; values.addAll(it) }, { limits++ }, errors::add, { active })
        fun pick(keys: List<String> = listOf("one"), count: Int = 0, budget: Long = 100) = owner.pick(keys, count, budget)
        fun reply(values: List<String> = listOf("prepared"), error: String? = null) = port.jobs.last().done(AttachmentSession.Batch(values, error))
    }
    @Test fun acceptsSuccessfulPrefixAndFailureWithoutResubmitting() {
        val f = Fixture(); assertTrue(f.pick(listOf("a", "b", "c"))); assertTrue(f.busy)
        assertTrue(f.reply(listOf("a"), "unreadable b")); assertFalse(f.busy)
        assertEquals(listOf("a"), f.values); assertEquals(listOf("unreadable b"), f.errors); assertEquals(1, f.finishes)
    }
    @Test fun selectionUsesOnlyFreeSlotsAndExactRemainingBudget() {
        val f = Fixture(); assertTrue(f.pick(listOf("a", "b", "c"), 1, 4))
        assertEquals(listOf("a", "b"), f.port.jobs.single().keys); assertEquals(4L, f.port.jobs.single().budget); assertEquals(1, f.limits)
    }
    @Test fun fullComposerOnlyShowsLimitAndNeverBeginsWork() {
        val f = Fixture(); assertFalse(f.pick(count = 3)); assertEquals(1, f.limits); assertFalse(f.busy); assertTrue(f.port.jobs.isEmpty())
    }
    @Test fun readOnlyClosedInactiveAndEmptySelectionCannotPrepare() {
        val readOnly = Fixture(true); assertFalse(readOnly.pick()); assertTrue(readOnly.port.jobs.isEmpty())
        val closed = Fixture(); closed.owner.close(); assertFalse(closed.pick()); assertFalse(closed.busy)
        val inactive = Fixture(); inactive.active = false; assertFalse(inactive.pick()); assertTrue(inactive.port.jobs.isEmpty())
        val empty = Fixture(); assertFalse(empty.pick(emptyList())); assertEquals(0, empty.limits); assertFalse(empty.busy)
    }
    @Test fun singleFlightAndExternalPreparingGatePreventSecondWorker() {
        val f = Fixture(); assertTrue(f.pick()); assertFalse(f.pick(listOf("new"))); assertEquals(1, f.port.jobs.size)
        val other = Fixture(); other.busy = true; assertFalse(other.pick()); assertTrue(other.port.jobs.isEmpty())
    }
    @Test fun duplicateSuccessAndErrorCannotAppendOrClearNewBusyState() {
        val f = Fixture(); f.pick(); val old = f.port.jobs.single(); assertTrue(f.reply()); f.pick(listOf("new"))
        assertFalse(old.done(AttachmentSession.Batch(listOf("duplicate"), "old error")))
        assertTrue(f.busy); assertEquals(listOf("prepared"), f.values); assertTrue(f.errors.isEmpty())
        assertTrue(f.reply(listOf("new"))); assertEquals(listOf("prepared", "new"), f.values)
    }
    @Test fun cancelRetiresBeforeTransportAndDoesNotTouchExistingValues() {
        val f = Fixture(); f.values.add("old draft file"); f.pick(); val old = f.port.jobs.single()
        old.onCancel = { assertFalse(old.done(AttachmentSession.Batch(listOf("late"), "late error"))) }
        f.owner.cancel(); f.owner.cancel(); assertEquals(1, old.cancels); assertFalse(f.busy)
        assertEquals(listOf("old draft file"), f.values); assertTrue(f.errors.isEmpty()); assertTrue(f.pick(listOf("new")))
    }
    @Test fun closeBlocksPendingResultsAndFurtherPicks() {
        val f = Fixture(); f.pick(); f.owner.close(); f.owner.close()
        assertFalse(f.reply()); assertFalse(f.pick()); assertEquals(1, f.port.jobs.single().cancels); assertFalse(f.busy)
    }
    @Test fun finishBeforeDeliveryRejectsOwnershipAndClearsOnlyOldPreparation() {
        val f = Fixture(); f.pick(); f.active = false
        assertFalse(f.reply(listOf("must be discarded"), "hidden error")); assertFalse(f.busy); assertTrue(f.values.isEmpty()); assertTrue(f.errors.isEmpty())
    }
    @Test fun synchronousSuccessDoesNotCancelAlreadyTransferredValues() {
        val port = object : Port() {
            override fun start(keys: List<String>, budget: Long, done: (AttachmentSession.Batch<String>) -> Boolean): Job {
                val job = super.start(keys, budget, done); assertTrue(done(AttachmentSession.Batch(listOf("sync")))); return job
            }
        }
        val f = Fixture(port = port); assertTrue(f.pick()); assertFalse(f.busy); assertEquals(listOf("sync"), f.values); assertEquals(0, port.jobs.single().cancels)
    }
    @Test fun setupFailureSettlesBusyAndPreservesCurrentDraftValues() {
        val f = Fixture(port = object : Port() {
            override fun start(keys: List<String>, budget: Long, done: (AttachmentSession.Batch<String>) -> Boolean): Job = throw IllegalStateException("executor unavailable")
        }); f.values.add("existing"); assertTrue(f.pick()); assertFalse(f.busy)
        assertEquals(listOf("existing"), f.values); assertEquals(listOf("executor unavailable"), f.errors)
    }
    @Test fun cancelDuringFactoryStillCancelsControlReturnedAfterRetirement() {
        lateinit var f: Fixture
        val port = object : Port() {
            override fun start(keys: List<String>, budget: Long, done: (AttachmentSession.Batch<String>) -> Boolean): Job {
                val job = super.start(keys, budget, done); f.owner.close(); return job
            }
        }
        f = Fixture(port = port); f.pick(); assertEquals(1, port.jobs.single().cancels); assertFalse(f.reply()); assertFalse(f.busy)
    }
    @Test fun clampsMalformedBudgetWithoutIncreasingSmallPositiveBudget() {
        val f = Fixture(); f.pick(budget = -1); assertEquals(0L, f.port.jobs.last().budget); f.reply(emptyList())
        f.pick(budget = Long.MAX_VALUE); assertEquals(ImageGuard.MAX_TOTAL_BYTES, f.port.jobs.last().budget)
    }
}
