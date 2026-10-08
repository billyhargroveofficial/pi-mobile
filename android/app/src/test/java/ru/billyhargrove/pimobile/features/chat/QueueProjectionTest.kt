package ru.billyhargrove.pimobile.features.chat

import org.junit.Assert.*
import org.junit.Test
import ru.billyhargrove.pimobile.core.ChatMessage

class QueueProjectionTest {
    private fun user(id: String, text: String = "same") = ChatMessage.remote(id, ChatMessage.Role.USER, text, null, null)
    private fun assistant(id: String) = ChatMessage.remote(id, ChatMessage.Role.ASSISTANT, "same", null, null)
    private fun queued(id: String, keys: List<String> = emptyList(), text: String = "same") =
        ChatMessage.local(id, text, null, ChatMessage.LocalState.ACCEPTED).withQueue(keys)
    private class Counted(private val rows: List<ChatMessage>) : AbstractList<ChatMessage>() {
        var reads = 0
        override val size get() = rows.size
        override fun get(index: Int): ChatMessage { reads++; return rows[index] }
    }

    @Test fun canonicalTailReadsOnlyTheBoundedSuffixAndKeepsOrder() {
        val rows = Counted((1..6000).map { user("u$it") })
        assertEquals((4501..6000).map { "u$it" }, QueueProjection.tailKeys(rows));assertEquals(1500,rows.reads)
        assertTrue(QueueProjection.tailKeys(emptyList()).isEmpty());assertEquals(listOf("a","b"),QueueProjection.tailKeys(listOf(user("a"),user("b"))))
    }
    @Test fun unqueuedReceiptsNeedNoCanonicalWalkOrReplacement() {
        val rows=Counted((1..6000).map { user("u$it") });val receipts=listOf(ChatMessage.local("r","same",null,ChatMessage.LocalState.SENDING))
        assertSame(receipts,QueueProjection.reconcile(rows,receipts));assertEquals(0,rows.reads)
    }
    @Test fun assistantEchoAndAnOldIdenticalUserCannotConsumeQueue() {
        val receipt=queued("r",listOf("old"));val receipts=listOf(receipt)
        assertSame(receipts,QueueProjection.reconcile(listOf(user("old"),assistant("new")),receipts));assertTrue(receipt.queued())
    }
    @Test fun oneNewUserConsumesOnlyOneIdenticalReceiptAndRepeatedSnapshotCannotConsumeAnother() {
        val receipts=listOf(queued("a",listOf("old")),queued("b",listOf("old")))
        val rows=listOf(user("old"),user("echo"));val projected=QueueProjection.reconcile(rows,receipts)
        assertFalse(projected[0].queued());assertEquals(listOf("old","echo"),projected[1].queueBaseline())
        assertTrue(receipts.all { it.queued() });assertEquals(listOf("old"),receipts[1].queueBaseline())
        assertSame(projected,QueueProjection.reconcile(rows,projected))
    }
    @Test fun twoDistinctNewUsersConsumeTwoInReceiptOrderWithoutChangingAcceptanceState() {
        val receipts=listOf(queued("a"),queued("b"),queued("c"));val result=QueueProjection.reconcile(listOf(user("e1"),user("e2")),receipts)
        assertEquals(listOf(false,false,true),result.map { it.queued() });assertEquals(listOf("a","b","c"),result.map { it.requestId() })
        assertTrue(result.all { it.localState()==ChatMessage.LocalState.ACCEPTED });assertEquals(setOf("e1","e2"),result.last().queueBaseline().toSet())
    }
    @Test fun unrelatedNewUsersAndStreamingDoNotAllocateNewReceipts() {
        val receipts=listOf(queued("r"));assertSame(receipts,QueueProjection.reconcile(listOf(user("u","other"),assistant("a")),receipts))
    }
    @Test fun canonicalUsersAreCollectedInOneWalkRegardlessOfQueueLength() {
        val rows=Counted((1..6000).map { assistant("a$it") }+user("u","other"));val receipts=(1..40).map { queued("r$it") }
        assertSame(receipts,QueueProjection.reconcile(rows,receipts));assertEquals(6001,rows.reads)
    }
    @Test fun consumedKeysNormalizeAndBoundLaterReceiptBaselineWithoutMutatingInput() {
        val initial=listOf(queued("a"),queued("b",(1..1600).map { "old$it" }+"old1600"))
        val result=QueueProjection.reconcile(listOf(user("echo")),initial)
        assertEquals(1500,result[1].queueBaseline().size);assertEquals("echo",result[1].queueBaseline().last())
        assertEquals(1601,initial[1].queueBaseline().size)
    }
}
