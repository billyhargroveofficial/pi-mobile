package ru.billyhargrove.pimobile.media

import java.io.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import ru.billyhargrove.pimobile.core.ImagePayload

class AttachmentImporterTest {
    private class Fixture {
        val workers = ArrayDeque<() -> Unit>(); val mains = ArrayDeque<() -> Unit>()
        val values = mutableListOf<Attachment>(); val discarded = mutableListOf<Attachment>(); val results = mutableListOf<AttachmentImporter.Batch>()
        val budgets = mutableListOf<Long>(); val keys = mutableListOf<String>(); var accept = true; var schedulingFailure = false
        var prepare: (String, Long, AttachmentRead) -> Attachment = { key, _, _ -> attachment(2, key) }
        val importer = AttachmentImporter<String>({ key, budget, reader -> keys.add(key); budgets.add(budget); prepare(key, budget, reader).also(values::add) },
            { if (schedulingFailure) throw IOException("executor unavailable"); workers.addLast(it) }, mains::addLast, discarded::add)
        fun start(keys: List<String> = listOf("a", "b"), budget: Long = 4) = importer.start(keys, budget) { results.add(it); accept }
        fun worker() = workers.removeFirst()()
        fun main() { while (mains.isNotEmpty()) mains.removeFirst()() }
    }
    companion object { private fun attachment(size: Int, name: String) = Attachment(ImagePayload.file(ByteArray(size) { 5 }, name), null, name) }
    @Test fun decreasingBudgetAndOneMainTransferSurviveDuplicateExecutorDelivery() {
        val f = Fixture(); val control = f.start(); val worker = f.workers.first(); f.worker(); worker()
        assertTrue(f.results.isEmpty()); assertEquals(listOf(4L, 2L), f.budgets)
        val main = f.mains.first(); f.main(); main(); control.cancel()
        assertEquals(listOf("a", "b"), f.results.single().values.map { it.displayName() }); assertTrue(f.discarded.isEmpty()); assertEquals(2, f.keys.size)
    }
    @Test fun partialFailureRetainsSuccessfulPrefixAndNeverOpensRemainingKeys() {
        val f = Fixture(); f.prepare = { key, _, _ -> if (key == "b") throw IOException("bad b") else attachment(1, key) }
        f.start(listOf("a", "b", "c")); f.worker(); f.main()
        assertEquals(listOf("a", "b"), f.keys); assertEquals(listOf("a"), f.results.single().values.map { it.displayName() })
        assertEquals("bad b", f.results.single().error); assertTrue(f.discarded.isEmpty())
    }
    @Test fun exhaustedBudgetNeverOpensAnotherFile() {
        val f = Fixture(); f.start(budget = 2); f.worker(); f.main(); assertEquals(listOf("a"), f.keys)
        assertEquals(1, f.results.single().values.size); assertEquals("Attachments must total no more than 10 MB", f.results.single().error)
    }
    @Test fun faultyOversizedAndEmptyImporterCannotExceedWireBudget() {
        for (size in listOf(0, 5)) {
            val f = Fixture(); f.prepare = { key, _, _ -> attachment(size, key) }; f.start(); f.worker(); f.main()
            assertTrue(f.results.single().values.isEmpty()); assertEquals(1, f.discarded.size); assertEquals(listOf("a"), f.keys)
        }
    }
    @Test fun zeroBudgetFailsBeforePlatformWork() {
        val f = Fixture(); f.start(budget = 0); f.worker(); f.main(); assertTrue(f.keys.isEmpty()); assertTrue(f.results.single().values.isEmpty())
    }
    @Test fun queuedCancelNeverReadsOrDelivers() {
        val f = Fixture(); val control = f.start(); control.cancel(); control.cancel(); f.worker(); f.main()
        assertTrue(f.keys.isEmpty()); assertTrue(f.results.isEmpty()); assertTrue(f.discarded.isEmpty())
    }
    @Test fun cancelAfterWorkerReleasesStagedValuesOnceAndSuppressesQueuedDelivery() {
        val f = Fixture(); val control = f.start(); f.worker(); control.cancel(); control.cancel(); f.main()
        assertEquals(f.values, f.discarded); assertTrue(f.results.isEmpty())
    }
    @Test fun inactiveOwnerRejectsTransferAndReleasesStagedValues() {
        val f = Fixture(); f.accept = false; f.start(); f.worker(); f.main(); assertEquals(f.values, f.discarded)
    }
    @Test fun activeCancelClosesBlockedStreamAndReleasesOnlyItsSuccessfulPrefix() {
        val f = Fixture(); val entered = CountDownLatch(1); val closed = CountDownLatch(1); var closes = 0
        f.prepare = { key, budget, reader ->
            if (key == "a") attachment(1, key) else {
                reader.read(budget, "too big") { object : InputStream() {
                    override fun read(): Int { entered.countDown(); check(closed.await(3, TimeUnit.SECONDS)); return 7 }
                    override fun close() { closes++; closed.countDown() }
                } }; attachment(1, key)
            }
        }
        val control = f.start(); val worker = f.workers.removeFirst(); val thread = Thread { worker() }; thread.start()
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS)); control.cancel(); thread.join(3000); assertFalse(thread.isAlive); f.main()
            assertEquals(1, closes); assertEquals(listOf("a"), f.discarded.map { it.displayName() }); assertTrue(f.results.isEmpty())
        } finally { control.cancel(); closed.countDown(); thread.join(3000) }
    }
    @Test fun schedulingFailureReportsWithoutOpeningFilesAndCanBeCancelled() {
        val f = Fixture(); f.schedulingFailure = true; f.start(); f.main(); assertEquals("executor unavailable", f.results.single().error); assertTrue(f.keys.isEmpty())
        val next = Fixture(); next.schedulingFailure = true; val control = next.start(); control.cancel(); next.main(); assertTrue(next.results.isEmpty())
    }
    @Test fun cancellingOneJobPreservesAcceptedAndOtherQueuedAttachments() {
        val f = Fixture(); f.start(listOf("accepted")); f.worker(); f.main()
        val old = f.start(listOf("cancelled")); f.start(listOf("new")); old.cancel(); f.worker(); f.worker(); f.main()
        assertEquals(listOf("accepted", "new"), f.results.flatMap { it.values }.map { it.displayName() }); assertTrue(f.discarded.isEmpty())
    }
    @Test fun refusedMainSchedulingReleasesUndeliveredThumbnails() {
        val discarded = mutableListOf<Attachment>(); val value = attachment(1, "a"); var callback = false
        val importer = AttachmentImporter<String>({ _, _, _ -> value }, { it() }, { throw IOException("main closed") }, discarded::add)
        importer.start(listOf("a"), 1) { callback = true; true }
        assertEquals(listOf(value), discarded); assertFalse(callback)
    }
    @Test fun fatalPlatformErrorStillReleasesPreparedPrefix() {
        val f = Fixture(); f.prepare = { key, _, _ -> if (key == "b") throw AssertionError("fatal provider") else attachment(1, key) }
        f.start(); assertThrows(AssertionError::class.java) { f.worker() }
        assertEquals(listOf("a"), f.discarded.map { it.displayName() }); assertTrue(f.results.isEmpty())
    }
    @Test fun ownerFailureBeforeAcceptanceReleasesUndeliveredThumbnails() {
        val discarded = mutableListOf<Attachment>(); val value = attachment(1, "a")
        val importer = AttachmentImporter<String>({ _, _, _ -> value }, { it() }, { it() }, discarded::add)
        importer.start(listOf("a"), 1) { throw IllegalStateException("owner unavailable") }
        assertEquals(listOf(value), discarded)
    }
}
