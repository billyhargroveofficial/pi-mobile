package ru.billyhargrove.pimobile.features.chat

import androidx.compose.ui.text.input.TextFieldValue
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.media.Attachment

class ChatSessionTest {
    private class Wire : ChatSession.Transport {
        var writes = 0
        var fail = false
        var subscriptions = 0
        var saved: Triple<String, Int, Boolean>? = null
        var cached: JSONObject? = null
        var liveRequests: Set<String>? = null
        override fun pendingRequests(session: String) = liveRequests
        val commands = mutableListOf<Pair<String, JSONObject>>()
        override fun connection() = ConnectionState.CONNECTED
        override fun configuration(session: String): JSONObject? = null
        override fun viewport(session: String) = cached
        override fun saveViewport(session: String, key: String, offset: Int, follow: Boolean) { saved = Triple(key, offset, follow) }
        override fun subscribe(session: String) { subscriptions++ }
        override fun prompt(session: String, text: String, payloads: List<ImagePayload>, behavior: CommandBuilder.Behavior): String? {
            writes++; return if (fail) null else "p$writes"
        }
        override fun abort(session: String): String { writes++; return "a$writes" }
        override fun configure(session: String, provider: String?, model: String?, effort: String?, tier: String?): String { writes++; return "c$writes" }
        override fun read(session: String, kind: String, args: JSONObject): String { commands.add(kind to args); return "r${commands.size}" }
    }
    private class Fixture(readOnly: Boolean = false, initial: List<ChatMessage> = emptyList()) {
        val wire = Wire()
        val effects = mutableListOf<ChatSession.Effect>()
        var persisted = emptyList<ChatMessage>()
        val chat = ChatSession("s", "Title", readOnly, wire, initial, { persisted = it }, effects::add)
    }
    private fun message(id: String, role: ChatMessage.Role = ChatMessage.Role.USER, text: String = id) = ChatMessage.remote(id, role, text, null, null)
    private fun snapshot(vararg rows: ChatMessage, id: String = "s") = Snapshot(id, SessionStatus.IDLE, true, rows.toList(), false)
    private fun meta(epoch: Long, more: Boolean = true, cached: Boolean = false) = JSONObject().put("sessionId", "s").put("type", "snapshot")
        .put("epoch", epoch).put("cached", cached).put("history", JSONObject().put("before", "cursor").put("hasMore", more))

    @Test fun foreignFramesAndUnknownAckCannotMutateSession() {
        val f = Fixture(); f.chat.onSnapshot(snapshot(message("original")))
        f.chat.onSnapshot(snapshot(message("foreign"), id = "other"))
        f.chat.onTimelineMeta(JSONObject().put("sessionId", "other").put("cached", true))
        f.chat.onAck(Ack("unknown", "s", false, "no")); f.chat.onCommandUncertain("unknown", "s", "timeout")
        assertEquals("original", f.chat.items.single().row.message!!.id()); assertFalse(f.chat.cached); assertTrue(f.effects.isEmpty())
    }
    @Test fun writeFailurePreservesTextFilesAndNoReceipt() {
        val f = Fixture(); f.wire.fail = true; f.chat.composer = TextFieldValue("draft")
        val file = Attachment(ImagePayload.file(byteArrayOf(1), "a.md"), null, "a.md"); f.chat.prepared(listOf(file))
        assertFalse(f.chat.send(CommandBuilder.Behavior.STEER)); assertEquals("draft", f.chat.composer.text)
        assertSame(file, f.chat.attachments.single()); assertTrue(f.persisted.isEmpty()); assertEquals(1, f.wire.writes)
    }
    @Test fun readOnlyBlocksEveryMutationIncludingControlsAndRetry() {
        val local = ChatMessage.local("old", "old", null, ChatMessage.LocalState.UNCERTAIN)
        val f = Fixture(true, listOf(local)); f.chat.composer = TextFieldValue("/name renamed")
        f.chat.send(CommandBuilder.Behavior.FOLLOW_UP); f.chat.abort(); f.chat.retry(local); f.chat.restore(local)
        f.chat.configure("p", "m", "high", "fast", false); assertFalse(f.chat.beginPreparing())
        assertEquals(0, f.wire.writes); assertTrue(f.wire.commands.isEmpty()); assertEquals(listOf(local), f.persisted)
        f.chat.document("README.md"); assertEquals("document", f.wire.commands.single().first)
    }
    @Test fun controlsAreCapabilityGatedAndClearOnlyTheirOwnDraft() {
        val f = Fixture(); f.chat.composer = TextFieldValue("/name New name")
        f.chat.send(CommandBuilder.Behavior.FOLLOW_UP); assertTrue(f.wire.commands.isEmpty())
        f.chat.onConfiguration("s", JSONObject().put("capabilities", JSONArray().put("name")))
        f.chat.send(CommandBuilder.Behavior.FOLLOW_UP); assertEquals("New name", f.wire.commands.single().second.getString("name")); assertEquals(0, f.wire.writes)
        f.chat.composer = TextFieldValue("later draft"); f.chat.onAck(Ack("r1", "s", true, "")); assertEquals("later draft", f.chat.composer.text)
    }
    @Test fun skillsAndControlAttachmentsCannotEscapeGuard() {
        val f = Fixture(); f.chat.composer = TextFieldValue("\$skill"); f.chat.send(CommandBuilder.Behavior.FOLLOW_UP)
        f.chat.onConfiguration("s", JSONObject().put("capabilities", JSONArray().put("mcp")))
        f.chat.composer = TextFieldValue("/mcp"); f.chat.prepared(listOf(Attachment(ImagePayload.file(byteArrayOf(1), "a.md"), null, "a.md")))
        f.chat.send(CommandBuilder.Behavior.FOLLOW_UP); assertEquals(0, f.wire.writes); assertTrue(f.wire.commands.isEmpty())
    }
    @Test fun staleHistoryPageCannotEnterNewEpoch() {
        val f = Fixture(); f.chat.onTimelineMeta(meta(1)); f.chat.onSnapshot(snapshot(message("tail"))); f.chat.loadOlder()
        f.chat.onTimelineMeta(meta(2)); f.chat.onData("r1", "s", JSONObject().put("type", "history").put("epoch", 1).put("messages", JSONArray().put(JSONObject().put("id", "old").put("role", "user").put("text", "old"))))
        assertEquals(listOf("tail"), f.chat.items.map { it.row.message!!.id() }); assertFalse(f.chat.historyLoading)
    }
    @Test fun historyPrependKeepsReaderAndAckCompletesLoading() {
        val f = Fixture(); f.chat.onTimelineMeta(meta(1)); f.chat.onSnapshot(snapshot(message("tail"))); f.chat.readerDragged()
        val revision = f.chat.tailRevision; f.chat.loadOlder(); assertTrue(f.chat.historyLoading)
        f.chat.onData("r1", "s", JSONObject().put("type", "history").put("epoch", 1).put("messages", JSONArray().put(JSONObject().put("id", "old").put("role", "user").put("text", "old")))
            .put("history", JSONObject().put("hasMore", false)))
        assertEquals(listOf("old", "tail"), f.chat.items.map { it.row.message!!.id() }); assertEquals(revision, f.chat.tailRevision)
        f.chat.onAck(Ack("r1", "s", true, "")); assertFalse(f.chat.historyLoading)
    }
    @Test fun longToolTailFetchesUserContextUntilPromptArrives() {
        val f = Fixture(); f.chat.onTimelineMeta(meta(1)); f.chat.onSnapshot(snapshot(message("assistant", ChatMessage.Role.ASSISTANT)))
        assertEquals("history", f.wire.commands.single().first)
        f.chat.onData("r1", "s", JSONObject().put("type", "history").put("epoch", 1).put("messages", JSONArray().put(JSONObject().put("id", "user").put("role", "user").put("text", "prompt"))))
        f.chat.onAck(Ack("r1", "s", true, "")); assertEquals(1, f.wire.commands.size)
    }
    @Test fun cachedSnapshotRestoresExistingViewportWithoutFetchingOrReplay() {
        val f = Fixture(initial = listOf(ChatMessage.local("pending", "draft", null, ChatMessage.LocalState.SENDING)))
        f.wire.cached = JSONObject().put("anchor", "tail").put("offset", -42).put("follow", false)
        f.chat.onTimelineMeta(meta(1, cached = true)); val revision = f.chat.tailRevision
        f.chat.onSnapshot(snapshot(message("tail", ChatMessage.Role.ASSISTANT)))
        assertEquals(ChatSession.Viewport("tail", -42, false), f.chat.restoreViewport); assertEquals(revision, f.chat.tailRevision)
        f.chat.start(); assertEquals(0, f.wire.writes); assertTrue(f.wire.commands.isEmpty()); assertEquals(ChatMessage.LocalState.UNCERTAIN, f.persisted.single().localState())
    }
    @Test fun streamingUpdatesFollowOnlyWhenReaderIsAtTail() {
        val f = Fixture(); f.chat.onSnapshot(snapshot(message("tail", ChatMessage.Role.ASSISTANT))); val revision = f.chat.tailRevision
        f.chat.readerDragged()
        f.chat.onMessages(MessagesUpdate("s", listOf(message("tail", ChatMessage.Role.ASSISTANT, "changed")), emptyList(), SessionStatus.UNKNOWN, false, false, false, false, false))
        assertEquals(revision, f.chat.tailRevision); f.chat.readerSettled(true)
        f.chat.onMessages(MessagesUpdate("s", listOf(message("tail", ChatMessage.Role.ASSISTANT, "more")), emptyList(), SessionStatus.UNKNOWN, false, false, false, false, false))
        assertTrue(f.chat.tailRevision > revision)
    }
    @Test fun configurationNeedsIdleForModelAndMatchingAckForCompletion() {
        val f = Fixture(); assertFalse(f.chat.configure("p", "m", "high", null, true)); assertEquals(0, f.wire.writes)
        f.chat.onSnapshot(snapshot()); assertTrue(f.chat.configure("p", "m", "high", "fast", true)); assertTrue(f.chat.configurationPending)
        f.chat.onAck(Ack("c1", "other", true, "")); assertTrue(f.chat.configurationPending)
        f.chat.onCommandUncertain("c1", "s", "timeout"); assertFalse(f.chat.configurationPending); assertEquals(1, f.wire.writes)
    }
    @Test fun successiveConfigurationAcksDoNotEmitSuccessNoticesOrReplay() {
        val f = Fixture(); f.chat.onSnapshot(snapshot())
        assertTrue(f.chat.configure(null, null, "high", null, false))
        assertFalse(f.chat.configure(null, null, "low", null, false))
        f.chat.dismissNotice(); f.effects.clear()
        f.chat.onAck(Ack("c1", "s", true, ""))
        assertFalse(f.chat.configurationPending); assertEquals("", f.chat.notice)
        assertEquals(listOf(ChatSession.Effect.ConfigurationResult(null)), f.effects)
        assertTrue(f.chat.configure(null, null, "low", null, false))
        f.chat.onAck(Ack("c2", "s", true, "")); assertEquals(2, f.wire.writes)
        f.chat.onCommandUncertain("c2", "s", "late timeout"); assertEquals(2, f.wire.writes)
    }
    @Test fun noticesArePersistentDismissibleStateAndTranscriptionNeverSends() {
        val f = Fixture(); f.chat.onProtocolError("Synthetic error")
        assertEquals("Protocol error: Synthetic error", f.chat.notice)
        f.chat.dismissNotice(); assertEquals("", f.chat.notice)
        f.chat.showNotice("Platform error"); assertEquals("Platform error", f.chat.notice)
        f.chat.transcriptionChanged(true); assertTrue(f.chat.transcribing)
        f.chat.insertDictation("Recognized words"); f.chat.transcriptionChanged(false)
        assertEquals("Recognized words", f.chat.composer.text); assertEquals(0, f.wire.writes)
    }
    @Test fun acceptedReceiptCannotDowngradeAndEchoIsCanonical() {
        val f = Fixture(); f.chat.composer = TextFieldValue("prompt"); f.chat.send(CommandBuilder.Behavior.FOLLOW_UP)
        f.chat.onAck(Ack("p1", "s", true, "")); f.chat.onCommandUncertain("p1", "s", "late")
        assertEquals(ChatMessage.LocalState.ACCEPTED, f.persisted.single().localState())
        f.chat.onSnapshot(snapshot(message("echo", text = "prompt"))); assertTrue(f.persisted.isEmpty()); assertEquals("echo", f.chat.items.single().row.message!!.id())
    }
    @Test fun returningAfterDetachedAcksOrTimeoutsUnblocksRequestsWithoutReplay() {
        val f = Fixture(); f.chat.onTimelineMeta(meta(1)); f.chat.onSnapshot(snapshot(message("tail")))
        f.chat.composer = TextFieldValue("prompt"); f.chat.send(CommandBuilder.Behavior.FOLLOW_UP)
        f.chat.loadOlder(); f.chat.document("README.md"); f.chat.configure(null, null, "high", null, false)
        f.wire.liveRequests = emptySet(); val writes = f.wire.writes
        f.chat.start()
        assertFalse(f.chat.configurationPending); assertFalse(f.chat.historyLoading)
        assertEquals(ChatMessage.LocalState.UNCERTAIN, f.persisted.single().localState())
        assertEquals(writes, f.wire.writes)
        // Clearing a missing document ticket allows an explicit later read.
        f.chat.document("other.md"); assertEquals(3, f.wire.commands.size)
        f.chat.onAck(Ack("p1", "s", true, ""))
        assertEquals(ChatMessage.LocalState.ACCEPTED, f.persisted.single().localState())
    }
    @Test fun returningWithStillLiveTicketsDoesNotInventAnUncertainResult() {
        val f = Fixture(); f.chat.onSnapshot(snapshot()); f.chat.composer = TextFieldValue("prompt")
        f.chat.send(CommandBuilder.Behavior.FOLLOW_UP); f.chat.configure(null, null, "high", null, false)
        f.wire.liveRequests = setOf("p1", "c2"); f.chat.start()
        assertTrue(f.chat.configurationPending); assertEquals(ChatMessage.LocalState.SENDING, f.persisted.single().localState())
        assertEquals(2, f.wire.writes)
    }
    @Test fun returningAnimatesChangedRowsOnceWithoutFollowingAReaderAboveTheTail() {
        val f = Fixture(); f.chat.onTimelineMeta(meta(1, more = false))
        f.chat.onSnapshot(snapshot(message("u"), message("a", ChatMessage.Role.ASSISTANT, "old")))
        f.chat.readerDragged(); val tail = f.chat.tailRevision
        f.chat.start(); f.chat.onTimelineMeta(meta(1, more = false, cached = true))
        f.chat.onSnapshot(snapshot(message("u"), message("a", ChatMessage.Role.ASSISTANT, "caught up"), message("b", ChatMessage.Role.ASSISTANT)))
        assertEquals(setOf("a", "b"), f.chat.arrivingKeys); assertEquals(tail, f.chat.tailRevision)
        val arrival = f.chat.arrivalRevision; f.chat.arrivalsShown(arrival)
        f.chat.onTimelineMeta(JSONObject().put("sessionId", "s").put("type", "messages"))
        f.chat.onMessages(MessagesUpdate("s", listOf(message("b", ChatMessage.Role.ASSISTANT, "stream")), emptyList(), SessionStatus.UNKNOWN, false, false, false, false, false))
        f.chat.arrivalsShown(f.chat.arrivalRevision)
        f.chat.onMessages(MessagesUpdate("s", listOf(message("b", ChatMessage.Role.ASSISTANT, "next chunk")), emptyList(), SessionStatus.UNKNOWN, false, false, false, false, false))
        assertTrue(f.chat.arrivingKeys.isEmpty()); assertEquals(tail, f.chat.tailRevision)
    }
    @Test fun oldArrivalCompletionCannotClearANewerBatchAndHistoryDoesNotAnimate() {
        val f = Fixture(); f.chat.onTimelineMeta(meta(1)); f.chat.onSnapshot(snapshot(message("u")))
        f.chat.readerDragged()
        fun append(id: String) = f.chat.onMessages(MessagesUpdate("s", listOf(message(id)), emptyList(), SessionStatus.UNKNOWN, false, false, false, false, false))
        append("a"); val first = f.chat.arrivalRevision; append("b")
        f.chat.arrivalsShown(first); assertEquals(setOf("b"), f.chat.arrivingKeys)
        f.chat.arrivalsShown(f.chat.arrivalRevision); f.chat.loadOlder()
        f.chat.onData("r1", "s", JSONObject().put("type", "history").put("epoch", 1).put("messages", JSONArray().put(JSONObject().put("id", "old").put("role", "user").put("text", "old"))))
        assertTrue(f.chat.arrivingKeys.isEmpty()); assertFalse(f.chat.followTail)
    }
    @Test fun newEpochResetsOldDisclosureButKeepsTheNewActiveTurnMetadata() {
        val f = Fixture(); f.chat.onTimelineMeta(meta(1, more = false))
        f.chat.onSnapshot(snapshot(message("old")))
        f.chat.onTimelineMeta(meta(2, more = false).put("activeTurnId", "new-turn").put("status", "running"))
        val user = message("new-user").withPresentation("new-turn", "final", "", "")
        val tool = message("new-tool", ChatMessage.Role.TOOL_RESULT).withPresentation("new-turn", "work", "", "")
        f.chat.onSnapshot(Snapshot("s", SessionStatus.RUNNING, true, listOf(user, tool), false))
        assertEquals("new-turn", f.chat.metadata.optString("activeTurnId"))
        assertTrue(f.chat.items.first { it.row.header }.expanded)
        assertFalse(f.chat.items.any { it.row.key == "old" })
    }
}
