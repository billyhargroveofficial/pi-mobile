package ru.billyhargrove.pimobile.features.chat

import org.junit.Assert.*
import org.junit.Test
import ru.billyhargrove.pimobile.core.Ack
import ru.billyhargrove.pimobile.core.ChatMessage
import ru.billyhargrove.pimobile.core.CommandBuilder
import ru.billyhargrove.pimobile.core.ImagePayload
import ru.billyhargrove.pimobile.core.ImageRef
import ru.billyhargrove.pimobile.media.Attachment

class ChatOutboxTest {
    private data class Call(val session: String, val text: String, val payloads: List<ImagePayload>, val behavior: CommandBuilder.Behavior)
    private class Sender : ChatOutbox.PromptSender {
        val calls = mutableListOf<Call>()
        var connected = true
        override fun send(sessionId: String, text: String, payloads: List<ImagePayload>, behavior: CommandBuilder.Behavior): String? {
            calls.add(Call(sessionId, text, payloads, behavior))
            return if (connected) "request-${calls.size}" else null
        }
    }
    private val sender = Sender()
    private fun outbox(readOnly: Boolean = false, initial: List<ChatMessage> = emptyList()) = ChatOutbox("session", readOnly, sender, initial)
    private fun image() = Attachment(ImagePayload(byteArrayOf(1, 2), "image/png"), null, "image.png")
    private fun file() = Attachment(ImagePayload.file(byteArrayOf(3, 4), "notes.md"), null, "notes.md")
    private fun send(outbox: ChatOutbox, text: String = "Prompt", attachments: List<Attachment> = emptyList()) =
        outbox.send(text, attachments, CommandBuilder.Behavior.STEER).requestId
    private fun state(outbox: ChatOutbox) = outbox.localReceipts().single().localState()

    @Test fun failedSocketWriteLeavesCallerDraftAndAttachmentsIntact() {
        sender.connected = false
        val outbox = outbox()
        val attachments = mutableListOf(file(), image())
        assertEquals(ChatOutbox.SendStatus.NO_CONNECTION, outbox.send(" Draft \n", attachments, CommandBuilder.Behavior.STEER).status)
        assertEquals(2, attachments.size)
        assertTrue(outbox.localReceipts().isEmpty())
        assertEquals(" Draft \n", sender.calls.single().text)
        assertEquals("session", sender.calls.single().session)
    }

    @Test fun readOnlyAndEmptyInputNeverCallSender() {
        val receipt = ChatMessage.local("cached", "Old prompt", null, ChatMessage.LocalState.UNCERTAIN)
        val reader = outbox(true, listOf(receipt))
        assertEquals(ChatOutbox.SendStatus.READ_ONLY, reader.send("Prompt", listOf(file()), CommandBuilder.Behavior.STEER).status)
        assertEquals(ChatOutbox.SendStatus.READ_ONLY, reader.retry("cached").status)
        assertEquals(ChatOutbox.RestoreStatus.READ_ONLY, reader.restore("cached", false).status)
        assertEquals(listOf(receipt), reader.localReceipts())
        assertEquals(ChatOutbox.SendStatus.EMPTY, outbox().send(" \n", emptyList(), CommandBuilder.Behavior.FOLLOW_UP).status)
        assertTrue(sender.calls.isEmpty())
    }

    @Test fun writtenReceiptRetainsAttachmentOrderAndOwnsItsDraftList() {
        val outbox = outbox()
        val attachments = mutableListOf(file(), image())
        val id = send(outbox, "Prompt", attachments)
        attachments.clear()
        val receipt = outbox.localReceipts().single()
        assertEquals("Prompt\n📎 notes.md", receipt.text())
        assertEquals("local:1", receipt.images().single().url())
        assertEquals(ChatMessage.LocalState.SENDING, receipt.localState())
        outbox.uncertain(id, "session")
        val restored = outbox.restore(id, false)
        assertEquals("Prompt", restored.text)
        assertEquals(listOf("notes.md", "image.png"), restored.draft!!.attachments.map { it.displayName() })
        assertEquals(CommandBuilder.Behavior.STEER, restored.draft.behavior)
        assertEquals(1, sender.calls.size)
    }

    @Test fun unknownAndForeignEventsCannotMutateReceipt() {
        val outbox = outbox()
        val id = send(outbox)
        assertFalse(outbox.acknowledge(Ack(id, "other-session", false, "Rejected")).handled)
        assertFalse(outbox.acknowledge(Ack("unknown", "session", false, "Rejected")).handled)
        assertFalse(outbox.uncertain(id, "other-session"))
        assertFalse(outbox.uncertain("unknown", "session"))
        assertEquals(ChatMessage.LocalState.SENDING, state(outbox))
        assertEquals(1, sender.calls.size)
    }

    @Test fun terminalAcceptanceCannotBeDowngradedByStaleTimeoutOrAck() {
        val outbox = outbox()
        val id = send(outbox)
        assertTrue(outbox.acknowledge(Ack(id, "session", true, "")).handled)
        assertFalse(outbox.uncertain(id, "session"))
        assertFalse(outbox.acknowledge(Ack(id, "session", false, "Late rejection")).handled)
        assertEquals(ChatOutbox.SendStatus.NOT_RETRYABLE, outbox.retry(id).status)
        assertEquals(ChatMessage.LocalState.ACCEPTED, state(outbox))
        assertEquals(1, sender.calls.size)
    }

    @Test fun uncertainReceiptCanReceiveItsLateAuthoritativeAckWithoutReplay() {
        val outbox = outbox()
        val id = send(outbox)
        assertTrue(outbox.uncertain(id, "session"))
        assertFalse(outbox.uncertain(id, "session"))
        assertEquals(ChatMessage.LocalState.UNCERTAIN, state(outbox))
        assertTrue(outbox.acknowledge(Ack(id, "session", true, "")).handled)
        assertEquals(ChatMessage.LocalState.ACCEPTED, state(outbox))
        assertEquals(1, sender.calls.size)
    }

    @Test fun occupiedComposerDoesNotConsumeRejectedDraft() {
        val outbox = outbox()
        val original = file()
        val id = send(outbox, "Retained prompt", listOf(original))
        val failure = outbox.acknowledge(Ack(id, "session", false, "Rejected"))
        assertEquals("Retained prompt", failure.rejectedText)
        assertFalse(outbox.uncertain(id, "session"))
        assertEquals(ChatOutbox.RestoreStatus.COMPOSER_OCCUPIED, outbox.restore(id, true).status)
        assertEquals(ChatMessage.LocalState.FAILED, state(outbox))
        val restored = outbox.restore(id, false)
        assertEquals(ChatOutbox.RestoreStatus.RESTORED, restored.status)
        assertSame(original, restored.draft!!.attachments.single())
        assertEquals("Retained prompt", restored.text)
        assertTrue(outbox.localReceipts().isEmpty())
        assertEquals(ChatOutbox.RestoreStatus.UNKNOWN, outbox.restore(id, false).status)
        assertEquals(1, sender.calls.size)
    }

    @Test fun manualRetryUsesOriginalBytesAndBehaviorWithFreshIdentity() {
        val outbox = outbox()
        val id = send(outbox, "Prompt", listOf(file(), image()))
        outbox.uncertain(id, "session")
        val retried = outbox.retry(id)
        assertEquals(ChatOutbox.SendStatus.WRITTEN, retried.status)
        assertNotEquals(id, retried.requestId)
        assertEquals(2, sender.calls.size)
        assertEquals(sender.calls[0], sender.calls[1])
        assertEquals(listOf(ChatMessage.LocalState.UNCERTAIN, ChatMessage.LocalState.SENDING), outbox.localReceipts().map { it.localState() })
    }

    @Test fun failedManualRetryKeepsOriginalReceiptAndRecoverableBytes() {
        val outbox = outbox()
        val id = send(outbox, attachments = listOf(file()))
        outbox.uncertain(id, "session")
        sender.connected = false
        assertEquals(ChatOutbox.SendStatus.NO_CONNECTION, outbox.retry(id).status)
        assertEquals(ChatMessage.LocalState.UNCERTAIN, state(outbox))
        assertEquals("notes.md", outbox.restore(id, false).draft!!.attachments.single().payload().fileName())
        assertEquals(2, sender.calls.size)
    }

    @Test fun reentryRequiresManualReattachmentAndNeverCallsSender() {
        val cached = ChatMessage.local("cached", "Prompt", listOf(ImageRef("local:1", "image/png")), ChatMessage.LocalState.SENDING)
        val outbox = outbox(initial = listOf(cached))
        assertEquals(ChatMessage.LocalState.UNCERTAIN, state(outbox))
        assertEquals(ChatOutbox.SendStatus.MISSING_DRAFT, outbox.retry("cached").status)
        assertEquals(ChatOutbox.RestoreStatus.COMPOSER_OCCUPIED, outbox.restore("cached", true).status)
        val restored = outbox.restore("cached", false)
        assertEquals(ChatOutbox.RestoreStatus.REATTACH_REQUIRED, restored.status)
        assertEquals("Prompt", restored.text)
        assertNull(restored.draft)
        assertTrue(sender.calls.isEmpty())
    }

    @Test fun canonicalImageEchoPrunesAcceptedDraftWhileRejectedBubbleRemains() {
        val outbox = outbox()
        val id = send(outbox, attachments = listOf(image()))
        outbox.acknowledge(Ack(id, "session", true, ""))
        val remote = ChatMessage.remote("remote", ChatMessage.Role.USER,
            "Prompt [Image: original 100x200, displayed at 50x100. Multiply coordinates by 2 to map to original image.]",
            listOf(ImageRef("/media", "image/png")), null)
        assertEquals(listOf(remote), outbox.reconcile(listOf(remote)))
        assertTrue(outbox.localReceipts().isEmpty())
        assertEquals(ChatOutbox.SendStatus.MISSING_DRAFT, outbox.retry(id).status)
        val rejected = send(outbox, attachments = listOf(image()))
        outbox.acknowledge(Ack(rejected, "session", false, "Rejected"))
        assertEquals(2, outbox.reconcile(listOf(remote)).size)
        assertEquals(ChatMessage.LocalState.FAILED, state(outbox))
    }

    @Test fun echoBeforeAckHidesBubbleButRetainsPendingIdentity() {
        val outbox = outbox()
        val id = send(outbox)
        val remote = ChatMessage.remote("remote", ChatMessage.Role.USER, "Prompt", null, null)
        assertEquals(listOf(remote), outbox.reconcile(listOf(remote)))
        assertEquals(ChatMessage.LocalState.SENDING, state(outbox))
        assertTrue(outbox.acknowledge(Ack(id, "session", true, "")).handled)
        assertEquals(listOf(remote), outbox.reconcile(listOf(remote)))
        assertTrue(outbox.localReceipts().isEmpty())
        assertEquals(1, sender.calls.size)
    }
}
