package ru.billyhargrove.pimobile.features.chat

import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.store.PendingMessageCodec

/** Queue is a local receipt view, never another sender or a fabricated cancellation API. */
class ChatQueueTest {
    private class Wire : ChatSession.Transport {
        var writes = 0
        var fail = false
        override fun connection() = ConnectionState.CONNECTED
        override fun configuration(session: String) = null
        override fun viewport(session: String) = null
        override fun saveViewport(session: String, key: String, offset: Int, follow: Boolean) {}
        override fun subscribe(session: String) {}
        override fun prompt(session: String, text: String, payloads: List<ImagePayload>, behavior: CommandBuilder.Behavior): String? { writes++; return if (fail) null else "p$writes" }
        override fun abort(session: String) = null
        override fun configure(session: String, provider: String?, model: String?, effort: String?, tier: String?) = null
        override fun read(session: String, kind: String, args: org.json.JSONObject) = null
    }
    private class Fixture(initial: List<ChatMessage> = emptyList()) {
        val wire = Wire()
        var saved = emptyList<ChatMessage>()
        val chat = ChatSession("s", "Queue", false, wire, initial, { saved = it }, {})
        fun frame(status: SessionStatus = SessionStatus.RUNNING, vararg messages: ChatMessage) = chat.onSnapshot(Snapshot("s", status, true, messages.toList(), false))
        fun send(text: String = "Next task", behavior: CommandBuilder.Behavior = CommandBuilder.Behavior.FOLLOW_UP) {
            chat.composer = TextFieldValue(text); assertTrue(chat.send(behavior))
        }
    }
    private fun user(id: String, text: String = "Next task") = ChatMessage.remote(id, ChatMessage.Role.USER, text, null, null)

    @Test fun busyQueueStaysOutsideTranscriptAndAckDoesNotConsumeIt() {
        val f = Fixture(); f.frame(); f.send()
        assertEquals(ChatMessage.LocalState.SENDING, f.chat.queue.single().localState()); assertTrue(f.chat.items.isEmpty())
        f.chat.onAck(Ack("p1", "s", true, ""))
        assertEquals(ChatMessage.LocalState.ACCEPTED, f.chat.queue.single().localState()); assertTrue(f.chat.items.isEmpty())
        f.frame(SessionStatus.IDLE); assertEquals(1, f.chat.queue.size); assertEquals(1, f.wire.writes)
    }
    @Test fun steerAndIdleFollowUpRemainOrdinaryMessages() {
        val steer = Fixture(); steer.frame(); steer.send(behavior = CommandBuilder.Behavior.STEER)
        assertTrue(steer.chat.queue.isEmpty()); assertEquals(1, steer.chat.items.size)
        val idle = Fixture(); idle.frame(SessionStatus.IDLE); idle.send()
        assertTrue(idle.chat.queue.isEmpty()); assertEquals(1, idle.chat.items.size)
    }
    @Test fun identicalOldPromptDoesNotEatNewQueueAndEachEchoConsumesOnce() {
        val f = Fixture(); val old = user("old")
        f.frame(SessionStatus.RUNNING, old); f.send(); f.send()
        f.chat.onAck(Ack("p1", "s", true, "")); f.chat.onAck(Ack("p2", "s", true, ""))
        f.frame(SessionStatus.RUNNING, old); assertEquals(2, f.chat.queue.size)
        val one = user("new-1"); f.frame(SessionStatus.RUNNING, old, one)
        assertEquals("p2", f.chat.queue.single().requestId())
        repeat(4) { f.frame(SessionStatus.RUNNING, old, one); assertEquals("p2", f.chat.queue.single().requestId()) }
        f.frame(SessionStatus.RUNNING, old, one, user("new-2")); assertTrue(f.chat.queue.isEmpty())
        assertEquals(3, f.chat.items.size); assertEquals(2, f.wire.writes)
    }
    @Test fun foreignAckCannotAcceptQueueAndRejectionRestoresDraftOutsideQueue() {
        val f = Fixture(); f.frame(); f.send()
        f.chat.onAck(Ack("p1", "foreign", true, "")); assertEquals(ChatMessage.LocalState.SENDING, f.chat.queue.single().localState())
        f.chat.onAck(Ack("p1", "s", false, "Rejected")); assertTrue(f.chat.queue.isEmpty())
        assertEquals("Next task", f.chat.composer.text); assertEquals(ChatMessage.LocalState.FAILED, f.chat.items.single().row.message!!.localState())
    }
    @Test fun timeoutAndDisconnectNeverReplayOrPretendCancelled() {
        val f = Fixture(); f.frame(); f.send()
        f.chat.onCommandUncertain("p1", "s", "Timeout"); f.chat.onConnectionState(ConnectionState.DISCONNECTED, "")
        assertEquals(ChatMessage.LocalState.UNCERTAIN, f.chat.queue.single().localState()); assertEquals(1, f.wire.writes)
        f.chat.onConnectionState(ConnectionState.CONNECTED, ""); f.chat.start(); assertEquals(1, f.wire.writes)
        f.chat.onAck(Ack("p1", "s", true, "")); assertEquals(ChatMessage.LocalState.ACCEPTED, f.chat.queue.single().localState())
    }
    @Test fun queueAndBaselineSurvivePrivateCodecReentryWithoutReplay() {
        val f = Fixture(); f.frame(SessionStatus.RUNNING, user("old")); f.send(); f.chat.onAck(Ack("p1", "s", true, ""))
        val decoded = PendingMessageCodec.decode(PendingMessageCodec.encode(f.saved))
        assertTrue(decoded.single().queued()); assertEquals(listOf("old"), decoded.single().queueBaseline())
        val restored = Fixture(decoded); restored.frame(SessionStatus.RUNNING, user("old"))
        assertEquals(1, restored.chat.queue.size); assertEquals(0, restored.wire.writes)
        assertTrue("Restored receipts are last known, never a claimed live Pi queue", restored.chat.queueRestored("p1"))
        restored.frame(SessionStatus.RUNNING, user("old"), user("new")); assertTrue(restored.chat.queue.isEmpty())
    }
    @Test fun echoBeforeAckRetainsRequestIdentityButNotQueueRow() {
        val f = Fixture(); f.frame(); f.send(); f.frame(SessionStatus.RUNNING, user("echo"))
        assertTrue(f.chat.queue.isEmpty()); assertEquals(ChatMessage.LocalState.SENDING, f.saved.single().localState())
        f.chat.onAck(Ack("p1", "s", true, "")); assertTrue(f.saved.isEmpty()); assertTrue(f.chat.queue.isEmpty())
    }
    @Test fun failedSocketWriteKeepsDraftAndDoesNotCreateQueue() {
        val f = Fixture(); f.frame(); f.wire.fail = true; f.chat.composer = TextFieldValue("draft")
        assertFalse(f.chat.send(CommandBuilder.Behavior.FOLLOW_UP)); assertTrue(f.chat.queue.isEmpty())
        assertEquals("draft", f.chat.composer.text); assertTrue(f.saved.isEmpty())
    }
}
