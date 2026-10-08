package ru.billyhargrove.pimobile.features.chat

import org.junit.Assert.*
import org.junit.Test
import ru.billyhargrove.pimobile.core.*

class ReceiptPublicationTest {
    private var writes=0
    private fun outbox(initial: List<ChatMessage> = emptyList()) = ChatOutbox("s",false,{_,_,_,_->"r${++writes}"},initial)
    private fun user(id: String,text: String = "same") = ChatMessage.remote(id,ChatMessage.Role.USER,text,null,null)
    private fun assistant(text: String) = ChatMessage.remote("a",ChatMessage.Role.ASSISTANT,text,null,null)
    private fun send(outbox: ChatOutbox,queued: Boolean = true) = outbox.send("same",emptyList(),CommandBuilder.Behavior.FOLLOW_UP,queued)

    @Test fun initialAndChangedReceiptsPublishOnceButHundredsOfStreamFramesNeverRepublish() {
        val out=outbox();val saved=mutableListOf<List<ChatMessage>>();val save:(List<ChatMessage>)->Unit={saved.add(it)}
        out.persistReceipts(save);out.persistReceipts(save);assertEquals(1,saved.size)
        out.reconcile(listOf(user("old")));send(out);out.persistReceipts(save);assertEquals(2,saved.size)
        val receipts=out.localReceipts();val queue=out.queuedMessages()
        repeat(200) { out.reconcile(listOf(user("old"),assistant("stream $it")));out.persistReceipts(save);assertSame(receipts,out.localReceipts());assertSame(queue,out.queuedMessages()) }
        assertEquals(2,saved.size);assertEquals(1,writes)
        assertTrue(out.acknowledge(Ack("r1","s",true,"")).handled);out.persistReceipts(save);assertEquals(3,saved.size)
        assertEquals(ChatMessage.LocalState.SENDING,receipts.single().localState());assertEquals(ChatMessage.LocalState.ACCEPTED,out.localReceipts().single().localState())
    }
    @Test fun terminalAndForeignResultsNeverRepublishAndUncertainRemainsExplicit() {
        val out=outbox();send(out);var saves=0;val save:(List<ChatMessage>)->Unit={saves++};out.persistReceipts(save)
        assertFalse(out.acknowledge(Ack("r1","other",true,"")).handled);assertFalse(out.uncertain("r1","other"));out.persistReceipts(save);assertEquals(1,saves)
        assertTrue(out.uncertain("r1","s"));out.persistReceipts(save);assertEquals(2,saves)
        assertTrue(out.acknowledge(Ack("r1","s",false,"rejected")).handled);out.persistReceipts(save);assertEquals(3,saves)
        assertFalse(out.uncertain("r1","s"));assertFalse(out.acknowledge(Ack("r1","s",true,"")).handled);out.persistReceipts(save);assertEquals(3,saves)
    }
    @Test fun consumedSendingEchoHidesTheBubbleButRetainsAckIdentityUntilTerminalCleanupIsSaved() {
        val out=outbox();send(out);val saved=mutableListOf<List<ChatMessage>>();val save:(List<ChatMessage>)->Unit={saved.add(it)};out.persistReceipts(save)
        out.reconcile(listOf(user("echo")));out.persistReceipts(save);assertEquals(2,saved.size);assertTrue(out.queuedMessages().isEmpty());assertEquals(ChatMessage.LocalState.SENDING,out.localReceipts().single().localState())
        assertTrue(out.acknowledge(Ack("r1","s",true,"")).handled);out.reconcile(listOf(user("echo")));out.persistReceipts(save)
        assertEquals(3,saved.size);assertTrue(saved.last().isEmpty());assertEquals(1,writes)
    }
    @Test fun restoreAndExplicitRetryInvalidateSnapshotsWithoutChangingEarlierSavedLists() {
        val out=outbox();send(out,false);out.uncertain("r1","s");val old=out.localReceipts();var saves=0
        out.persistReceipts { saves++ };assertEquals(ChatOutbox.SendStatus.WRITTEN,out.retry("r1").status);out.persistReceipts { saves++ }
        assertEquals(2,saves);assertEquals(1,old.size);assertEquals(2,out.localReceipts().size);assertEquals(ChatOutbox.RestoreStatus.RESTORED,out.restore("r1",false).status)
        out.persistReceipts { saves++ };assertEquals(3,saves);assertEquals(listOf("r2"),out.localReceipts().map { it.requestId() })
    }
    @Test fun receiptAndQueueListSnapshotsCannotBeModifiedByTheirConsumers() {
        val out=outbox();send(out);val receipts=out.localReceipts();val queue=out.queuedMessages()
        for(list in listOf(receipts,queue)) try { (list as MutableList<ChatMessage>).clear();fail("Snapshot must be read-only") } catch(expected: UnsupportedOperationException) {}
        assertSame(receipts,out.localReceipts());assertSame(queue,out.queuedMessages());assertEquals(1,out.localReceipts().size)
    }
    @Test fun sameRevisionReentryCannotPublishTwice() {
        val out=outbox();var calls=0;lateinit var save:(List<ChatMessage>)->Unit
        save={calls++;out.persistReceipts(save)};out.persistReceipts(save);assertEquals(1,calls)
    }
    @Test fun newerRevisionSavedReentrantlyIsNotMarkedDirtyWhenTheOlderCallReturnsOrFails() {
        val out=outbox();var calls=0;lateinit var save:(List<ChatMessage>)->Unit
        save={calls++;if(calls==1) { send(out);out.persistReceipts(save);throw IllegalStateException("old save failed") } }
        try { out.persistReceipts(save);fail("Save exception must propagate") } catch(expected: IllegalStateException) {}
        out.persistReceipts(save);assertEquals(2,calls);assertEquals("r1",out.localReceipts().single().requestId())
    }
    @Test fun failedSaveCanRetryWithoutSendingAnotherPrompt() {
        val out=outbox();send(out);var attempts=0
        try { out.persistReceipts { attempts++;throw IllegalStateException("storage") };fail("Save exception must propagate") } catch(expected: IllegalStateException) {}
        out.persistReceipts { attempts++ };out.persistReceipts { attempts++ };assertEquals(2,attempts);assertEquals(1,writes)
    }
    @Test fun reopenedSendingReceiptBecomesUnknownOnceAndNeverReplays() {
        val old=ChatMessage.local("old","same",null,ChatMessage.LocalState.SENDING).withQueue(listOf("u"));val out=outbox(listOf(old));var calls=0
        out.persistReceipts { calls++;assertEquals(ChatMessage.LocalState.UNCERTAIN,it.single().localState());assertEquals(listOf("u"),it.single().queueBaseline()) }
        out.reconcile(listOf(user("u"),assistant("stream")));out.persistReceipts { calls++ };assertEquals(1,calls);assertEquals(0,writes);assertTrue(out.queueRestored("old"))
    }
}
