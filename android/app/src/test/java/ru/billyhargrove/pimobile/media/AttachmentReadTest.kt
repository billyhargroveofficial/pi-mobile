package ru.billyhargrove.pimobile.media

import java.io.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import ru.billyhargrove.pimobile.core.ImageGuard

class AttachmentReadTest {
    private class Stream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closes = 0
        override fun close() { closes++; super.close() }
    }
    private fun fails(message: String, action: () -> Unit) {
        val error = assertThrows(IOException::class.java, action); assertEquals(message, error.message)
    }
    @Test fun exactBoundAndSequentialReadsCloseTheirOwnStreamOnce() {
        val reader = AttachmentRead(); val a = Stream(byteArrayOf(1, 2, 3)); val b = Stream(byteArrayOf(4))
        assertArrayEquals(byteArrayOf(1, 2, 3), reader.read(3, "too big") { a })
        assertArrayEquals(byteArrayOf(4), reader.read(1, "too big") { b }); reader.cancel()
        assertEquals(1, a.closes); assertEquals(1, b.closes)
    }
    @Test fun stopsAfterFirstByteBeyondBudgetAndCloses() {
        val input = Stream(ByteArray(100)); fails("too big") { AttachmentRead().read(4, "too big") { input } }
        assertEquals(95, input.available()); assertEquals(1, input.closes)
    }
    @Test fun zeroNegativeAndExcessiveBudgetsRejectBeforeOpening() {
        var opens = 0
        for (budget in listOf(0L, -1L, ImageGuard.MAX_TOTAL_BYTES + 2))
            fails("Attachments must total no more than 10 MB") { AttachmentRead().read(budget, "too big") { opens++; Stream(byteArrayOf(1)) } }
        assertEquals(0, opens)
    }
    @Test fun emptyAndMissingStreamReportFailure() {
        val input = Stream(byteArrayOf()); fails("Empty file") { AttachmentRead().read(1, "too big") { input } }; assertEquals(1, input.closes)
        fails("Could not open file") { AttachmentRead().read(1, "too big") { null } }
    }
    @Test fun readFailureStillClosesWithoutMaskingOriginalFailure() {
        var closes = 0
        val input = object : InputStream() {
            override fun read(): Int = throw IOException("provider failed")
            override fun close() { closes++; throw IOException("close failed") }
        }
        fails("provider failed") { AttachmentRead().read(1, "too big") { input } }; assertEquals(1, closes)
    }
    @Test fun cancelBeforeOpenNeverReads() {
        val reader = AttachmentRead(); reader.cancel(); reader.cancel(); var opens = 0
        fails("Cancelled") { reader.read(1, "too big") { opens++; Stream(byteArrayOf(1)) } }; assertEquals(0, opens)
    }
    @Test fun cancelDuringOpenClosesReturnedStreamBeforeReading() {
        val reader = AttachmentRead(); val input = Stream(byteArrayOf(1))
        fails("Cancelled") { reader.read(1, "too big") { reader.cancel(); input } }
        assertEquals(1, input.closes); assertEquals(1, input.available())
    }
    @Test fun cancelUnblocksActiveStreamAndSuppressesItsBytes() {
        val reader = AttachmentRead(); val entered = CountDownLatch(1); val closed = CountDownLatch(1); var closes = 0; var failure: Throwable? = null
        val input = object : InputStream() {
            override fun read(): Int { entered.countDown(); check(closed.await(3, TimeUnit.SECONDS)); return 7 }
            override fun close() { closes++; closed.countDown() }
        }
        val thread = Thread { try { reader.read(1, "too big") { input } } catch (cause: Throwable) { failure = cause } }
        thread.start()
        try { assertTrue(entered.await(3, TimeUnit.SECONDS)); reader.cancel(); reader.cancel(); thread.join(3000)
            assertFalse(thread.isAlive); assertEquals("Cancelled", failure?.message); assertEquals(1, closes)
        } finally { reader.cancel(); closed.countDown(); thread.join(3000) }
    }
    @Test fun zeroByteReadCannotSpinAndStillObeysExactCap() {
        val input = object : ByteArrayInputStream(byteArrayOf(1, 2)) {
            override fun read(buffer: ByteArray, offset: Int, length: Int) = 0
        }
        assertArrayEquals(byteArrayOf(1, 2), AttachmentRead().read(2, "too big") { input })
    }
}
