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
        var viewportReads = 0
        var liveRequests: Set<String>? = null
        var configurationId: String? = "auto"
        var configurationFailure: Exception? = null
        var configuring: (() -> Unit)? = null
        var prompting: (() -> Unit)? = null
        val changes = mutableListOf<List<String?>>()
        override fun pendingRequests(session: String) = liveRequests
        val commands = mutableListOf<Pair<String, JSONObject>>()
        override fun connection() = ConnectionState.CONNECTED
        override fun configuration(session: String): JSONObject? = null
        override fun viewport(session: String): JSONObject? { viewportReads++; return cached }
        override fun saveViewport(session: String, key: String, offset: Int, follow: Boolean) { saved = Triple(key, offset, follow) }
        override fun subscribe(session: String) { subscriptions++ }
        override fun prompt(session: String, text: String, payloads: List<ImagePayload>, behavior: CommandBuilder.Behavior): String? {
            writes++; prompting?.invoke(); return if (fail) null else "p$writes"
        }
        override fun abort(session: String): String { writes++; return "a$writes" }
        override fun configure(session: String, provider: String?, model: String?, effort: String?, tier: String?): String? {
            writes++; changes.add(listOf(provider, model, effort, tier)); configuring?.invoke(); configurationFailure?.let { throw it }
            return if (configurationId == "auto") "c$writes" else configurationId
        }
        override fun read(session: String, kind: String, args: JSONObject): String { commands.add(kind to args); return "r${commands.size}" }
    }
    private class Fixture(readOnly: Boolean = false, initial: List<ChatMessage> = emptyList()) {
        val wire = Wire()
        val effects = mutableListOf<ChatSession.Effect>()
        var persisted = emptyList<ChatMessage>()
        var saves = 0
        val chat = ChatSession("s", "Title", readOnly, wire, initial, { saves++;persisted = it }, effects::add)
    }
    private fun message(id: String, role: ChatMessage.Role = ChatMessage.Role.USER, text: String = id) = ChatMessage.remote(id, role, text, null, null)
    private fun snapshot(vararg rows: ChatMessage, id: String = "s") = Snapshot(id, SessionStatus.IDLE, true, rows.toList(), false)
    private fun meta(epoch: Long, more: Boolean = true, cached: Boolean = false) = JSONObject().put("sessionId", "s").put("type", "snapshot")
        .put("epoch", epoch).put("cached", cached).put("history", JSONObject().put("before", "cursor").put("hasMore", more))

    @Test fun noOpTranscriptFramesKeepRowsDraftReceiptCheckpointAndReaderSignals() {
        val f = Fixture(); f.chat.onSnapshot(snapshot(message("u"), message("a", ChatMessage.Role.ASSISTANT)))
        f.chat.composer = TextFieldValue("Next draft"); f.chat.readerDragged(); f.chat.readerSettled(false)
        val items = f.chat.items; val tail = f.chat.tailRevision; val arrivals = f.chat.arrivalRevision; val saves = f.saves
        repeat(40) {
            f.chat.onTimelineMeta(JSONObject().put("sessionId", "s").put("activeTurnId", "").put("observedAt", it))
            f.chat.onMessages(MessagesUpdate("s", emptyList(), emptyList(), SessionStatus.IDLE, true, false, false, false, false))
        }
        assertSame(items, f.chat.items); assertEquals("Next draft", f.chat.composer.text)
        assertEquals(tail, f.chat.tailRevision); assertEquals(arrivals, f.chat.arrivalRevision); assertEquals(saves, f.saves); assertEquals(0, f.wire.writes)
    }
    @Test fun omittedDeltaStatusAndTruncationKeepTheirPreviouslyObservedValues() {
        val f = Fixture(); f.chat.onSnapshot(Snapshot("s", SessionStatus.RUNNING, true, listOf(message("u")), true))
        f.chat.onMessages(MessagesUpdate("s", listOf(message("a", ChatMessage.Role.ASSISTANT)), emptyList(), SessionStatus.OFFLINE, false, false, false, false, false))
        assertEquals(SessionStatus.RUNNING, f.chat.status); assertTrue(f.chat.truncated); assertEquals(listOf("u", "a"), f.chat.items.map { it.row.message!!.id() })
        f.chat.onMessages(MessagesUpdate("s", emptyList(), listOf("a"), SessionStatus.IDLE, true, true, true, false, true))
        assertEquals(SessionStatus.IDLE, f.chat.status); assertFalse(f.chat.truncated); assertEquals(listOf("u"), f.chat.items.map { it.row.message!!.id() })
    }
    @Test fun unchangedSnapshotKeepsDisclosureButNewEpochResetsItUsingTheAuthoritativeStatus() {
        val f = Fixture(); val rows = listOf(message("u"), message("t", ChatMessage.Role.TOOL_RESULT))
        f.chat.onTimelineMeta(meta(1, false)); f.chat.onSnapshot(Snapshot("s", SessionStatus.IDLE, true, rows, false))
        val group = f.chat.items.last().row.group; f.chat.toggle(group); val expanded = f.chat.items
        f.chat.onSnapshot(Snapshot("s", SessionStatus.IDLE, true, rows, false)); assertSame(expanded, f.chat.items)
        f.chat.onTimelineMeta(meta(2, false).put("status", "running")); f.chat.onSnapshot(Snapshot("s", SessionStatus.IDLE, true, rows, false))
        assertFalse(f.chat.items.last().expanded); assertEquals(SessionStatus.IDLE, f.chat.status); assertNotSame(expanded.last().row, f.chat.items.last().row)
    }

    @Test fun successfulSendDoesNotEraseADraftChangedInsideTheTransport() {
        val f = Fixture(); f.chat.composer = TextFieldValue("Submitted draft")
        val next = Attachment(ImagePayload.file(byteArrayOf(2), "next.md"), null, "next.md")
        f.wire.prompting = { f.chat.composer = TextFieldValue("Next draft"); f.chat.prepared(listOf(next)) }
        assertTrue(f.chat.send(CommandBuilder.Behavior.STEER))
        assertEquals("Next draft", f.chat.composer.text); assertSame(next, f.chat.attachments.single())
        assertEquals("Submitted draft", f.persisted.single().text()); assertEquals(1, f.wire.writes)
    }
    @Test fun composerFacadeRetainsTheFullEditorAndInsertDictationDoesNotSend() {
        val f = Fixture(); val value = TextFieldValue("abcd", androidx.compose.ui.text.TextRange(2, 4), androidx.compose.ui.text.TextRange(0, 2))
        f.chat.composer = value; assertSame(value, f.chat.composer)
        f.chat.insertDictation("words"); assertEquals("ab wordscd", f.chat.composer.text)
        assertEquals(androidx.compose.ui.text.TextRange(8), f.chat.composer.selection); assertNull(f.chat.composer.composition)
        assertEquals(0, f.wire.writes); assertTrue(f.persisted.isEmpty())
    }
    @Test fun closedComposerDoesNotAcceptLatePreparationDictationRestoreOrPrompt() {
        val local = ChatMessage.local("old", "old", null, ChatMessage.LocalState.UNCERTAIN)
        val f = Fixture(initial = listOf(local)); f.chat.composer = TextFieldValue("kept")
        f.chat.beginPreparing(); f.chat.transcriptionChanged(true); f.chat.closeComposer()
        f.chat.prepared(listOf(Attachment(ImagePayload.file(byteArrayOf(1), "late.md"), null, "late.md")))
        f.chat.insertDictation("late"); f.chat.composer = TextFieldValue("late"); f.chat.selectSkill("late")
        assertFalse(f.chat.send(CommandBuilder.Behavior.STEER)); f.chat.restore(local)
        assertEquals("kept", f.chat.composer.text); assertFalse(f.chat.canSend)
        assertFalse(f.chat.preparing); assertFalse(f.chat.transcribing); assertTrue(f.chat.attachments.isEmpty())
        assertEquals("old", f.persisted.single().requestId()); assertEquals(0, f.wire.writes)
    }

    @Test fun emptyReceiptStreamingAndMetadataNeverInvokePersistenceAgain() {
        val f=Fixture();assertEquals(1,f.saves)
        repeat(100) { f.chat.onMessages(MessagesUpdate("s",listOf(message("a",ChatMessage.Role.ASSISTANT,"stream $it")),emptyList(),SessionStatus.RUNNING,true,false,false,false,false)) }
        f.chat.onTimelineMeta(meta(1,false));f.chat.onSnapshot(snapshot(message("a",ChatMessage.Role.ASSISTANT,"snapshot")))
        assertEquals(1,f.saves);assertTrue(f.persisted.isEmpty());assertEquals(0,f.wire.writes);assertEquals("snapshot",f.chat.items.last().row.message!!.text())
    }
    @Test fun queuedStreamingPersistsOnlySendAckAndDistinctCanonicalConsumption() {
        val f=Fixture();val old=message("old",text="same");f.chat.onSnapshot(Snapshot("s",SessionStatus.RUNNING,true,listOf(old),false))
        repeat(2) { f.chat.composer=TextFieldValue("same");assertTrue(f.chat.send(CommandBuilder.Behavior.FOLLOW_UP)) }
        assertEquals(3,f.saves);assertEquals(2,f.chat.queue.size)
        f.chat.onAck(Ack("p1","s",true,""));f.chat.onAck(Ack("p2","s",true,""));assertEquals(5,f.saves);assertEquals(2,f.chat.queue.size)
        val new=message("echo",text="same");f.chat.onSnapshot(Snapshot("s",SessionStatus.RUNNING,true,listOf(old,new),false));assertEquals(6,f.saves);assertEquals("p2",f.chat.queue.single().requestId())
        repeat(40) { f.chat.onSnapshot(Snapshot("s",SessionStatus.RUNNING,true,listOf(old,new,message("a",ChatMessage.Role.ASSISTANT,"stream $it")),false)) }
        assertEquals(6,f.saves);assertEquals(2,f.wire.writes);assertEquals(listOf("p2"),f.persisted.map { it.requestId() })
        assertTrue("echo" in f.persisted.single().queueBaseline())
    }
    @Test fun foreignAndRepeatedReceiptResultsDoNotReachThePersistencePort() {
        val f=Fixture();f.chat.composer=TextFieldValue("same");f.chat.send(CommandBuilder.Behavior.STEER);val before=f.saves
        f.chat.onAck(Ack("p1","other",true,""));f.chat.onAck(Ack("unknown","s",true,""));f.chat.onCommandUncertain("p1","other","timeout");assertEquals(before,f.saves)
        f.chat.onAck(Ack("p1","s",true,""));f.chat.onAck(Ack("p1","s",false,"late"));f.chat.onCommandUncertain("p1","s","late")
        assertEquals(before+1,f.saves);assertEquals(ChatMessage.LocalState.ACCEPTED,f.persisted.single().localState());assertEquals(1,f.wire.writes)
    }
    @Test fun missingDetachedPromptSettlesAndPersistsUnknownOnceWithoutReplay() {
        val f=Fixture();f.chat.composer=TextFieldValue("same");f.chat.send(CommandBuilder.Behavior.STEER);val before=f.saves;f.wire.liveRequests=emptySet()
        f.chat.start();f.chat.start();assertEquals(before+1,f.saves);assertEquals(ChatMessage.LocalState.UNCERTAIN,f.persisted.single().localState());assertEquals(1,f.wire.writes)
        assertEquals("",f.chat.composer.text)
    }

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
    @Test fun scopedDocumentDataOpensOnlyOnceAndDoesNotModifyReadOnlyDraftOrFiles() {
        val f = Fixture(true); f.chat.composer = TextFieldValue("Retained draft"); f.chat.document("docs/one.md")
        val data = JSONObject().put("type", "document").put("path", "docs/one.md").put("text", "**body**")
        f.chat.onData("r1", "foreign", data); f.chat.onData("foreign", "s", data); assertTrue(f.effects.isEmpty())
        f.chat.onData("r1", "s", data); f.chat.onData("r1", "s", data); f.chat.onAck(Ack("r1", "s", true, ""))
        f.chat.onCommandUncertain("r1", "s", "late")
        assertEquals(listOf(ChatSession.Effect.Document("docs/one.md", "**body**")), f.effects)
        assertEquals("Retained draft", f.chat.composer.text); assertTrue(f.chat.attachments.isEmpty()); assertEquals(0, f.wire.writes); assertEquals("", f.chat.notice)
    }
    @Test fun dismissingDocumentRetiresOnlyItsReadAndKeepsOtherCommandOwnership() {
        val f = Fixture(); f.chat.onSnapshot(snapshot()); f.chat.document("one.md"); f.chat.configure(null, null, "high", null, false)
        f.chat.cancelDocument(); f.chat.document("two.md")
        f.chat.onData("r1", "s", JSONObject().put("type", "document").put("text", "late")); assertTrue(f.effects.isEmpty())
        assertTrue(f.chat.configurationPending); f.chat.onAck(Ack("c1", "s", true, "")); assertFalse(f.chat.configurationPending)
        f.chat.onData("r2", "s", JSONObject().put("type", "document").put("text", "fresh"))
        assertEquals(ChatSession.Effect.Document("two.md", "fresh"), f.effects.last()); assertEquals(1, f.wire.writes)
    }
    @Test fun closedDocumentOwnerCannotReadOrEmitDialogWhileOtherChatStateRemainsUsable() {
        val f = Fixture(); f.chat.document("one.md"); f.chat.closeDocuments(); f.chat.document("two.md")
        f.chat.onData("r1", "s", JSONObject().put("type", "document").put("text", "late")); f.chat.onAck(Ack("r1", "s", false, "late error"))
        assertTrue(f.effects.isEmpty()); assertEquals(1, f.wire.commands.size); assertEquals("", f.chat.notice)
        f.chat.showNotice("Other notice"); assertEquals("Other notice", f.chat.notice)
    }
    @Test fun wrongDocumentPayloadTypeAndSuccessWithoutBodyProduceFeedbackWithoutPreview() {
        val f = Fixture(); f.chat.document("one.md"); f.chat.onData("r1", "s", JSONObject().put("type", "mcp")); f.chat.onAck(Ack("r1", "s", true, ""))
        assertTrue(f.effects.none { it is ChatSession.Effect.Document }); assertEquals("Pi returned no document", f.chat.notice)
    }
    private fun page(before: String = "next", role: String = "assistant", text: String = "old", id: String = "old") = JSONObject()
        .put("type", "history").put("epoch", 1).put("messages", JSONArray().put(JSONObject().put("id", id).put("role", role).put("text", text)))
        .put("history", JSONObject().put("before", before).put("hasMore", true))
    @Test fun duplicateHistoryCannotReplaceAcceptedContentOrAdvanceToAForeignCursor() {
        val f = Fixture(true); f.chat.onTimelineMeta(meta(1)); f.chat.onSnapshot(snapshot(message("tail"))); f.chat.readerDragged()
        f.chat.loadOlder(); val revision = f.chat.tailRevision; val arrivals = f.chat.arrivalRevision
        f.chat.onData("r1", "foreign", page(text = "foreign")); f.chat.onData("r1", "s", page())
        f.chat.onData("r1", "s", page("bad", text = "duplicate")); f.chat.onAck(Ack("r1", "s", true, ""))
        assertEquals(listOf("old", "tail"), f.chat.items.map { it.row.message!!.text() })
        assertEquals(revision, f.chat.tailRevision); assertEquals(arrivals, f.chat.arrivalRevision); assertFalse(f.chat.followTail)
        f.chat.loadOlder(); assertEquals("next", f.wire.commands.last().second.getString("before")); assertEquals(40, f.wire.commands.last().second.getInt("limit")); assertEquals(0, f.wire.writes)
    }
    @Test fun stalledHistoryCursorAndMissingBodyCannotMakeStreamingTriggerAReadStorm() {
        for (body in listOf(page("cursor"), JSONObject().put("type", "history").put("epoch", 1))) {
            val f = Fixture(); f.chat.onTimelineMeta(meta(1)); f.chat.onSnapshot(snapshot(message("tail", ChatMessage.Role.ASSISTANT)))
            f.chat.onData("r1", "s", body); f.chat.onAck(Ack("r1", "s", true, ""))
            repeat(100) { f.chat.onMessages(MessagesUpdate("s", listOf(message("tail", ChatMessage.Role.ASSISTANT, "chunk $it")), emptyList(), SessionStatus.UNKNOWN, false, false, false, false, false)) }
            assertEquals(1, f.wire.commands.size); assertFalse(f.chat.historyLoading); assertEquals(0, f.wire.writes)
            assertEquals(if (body.has("messages")) "History cursor did not advance" else "Pi returned no history", f.chat.notice)
        }
    }
    @Test fun canonicalRoleChangesAndRemovalDriveContextWhileLocalReceiptsDoNot() {
        val local = ChatMessage.local("pending", "optimistic", null, ChatMessage.LocalState.SENDING)
        val f = Fixture(initial = listOf(local)); f.chat.onTimelineMeta(meta(1)); f.chat.onSnapshot(snapshot(message("tail", ChatMessage.Role.ASSISTANT)))
        assertEquals(1, f.wire.commands.size)
        f.chat.onData("r1", "s", page(role = "user")); f.chat.onAck(Ack("r1", "s", true, "")); assertEquals(1, f.wire.commands.size)
        f.chat.onMessages(MessagesUpdate("s", listOf(message("old", ChatMessage.Role.ASSISTANT)), emptyList(), SessionStatus.UNKNOWN, false, false, false, false, false))
        assertEquals(2, f.wire.commands.size); assertEquals("next", f.wire.commands.last().second.getString("before"))
    }
    @Test fun absentSnapshotCursorDoesNotReuseThePreviousSnapshotsPage() {
        val f = Fixture(); f.chat.onTimelineMeta(meta(1)); f.chat.onSnapshot(snapshot(message("u")))
        f.chat.onTimelineMeta(JSONObject().put("sessionId", "s").put("type", "snapshot").put("epoch", 2))
        f.chat.onSnapshot(snapshot(message("new", ChatMessage.Role.ASSISTANT))); f.chat.loadOlder()
        assertTrue(f.wire.commands.isEmpty()); assertFalse(f.chat.historyLoading)
    }
    @Test fun closedHistoryCannotPrependOrLoadAfterActivityDestruction() {
        val f = Fixture(); f.chat.onTimelineMeta(meta(1)); f.chat.onSnapshot(snapshot(message("u"))); f.chat.loadOlder(); f.chat.closeHistory()
        f.chat.onData("r1", "s", page()); f.chat.onAck(Ack("r1", "s", false, "late")); f.chat.onCommandUncertain("r1", "s", "late")
        f.chat.loadOlder(); assertEquals(listOf("u"), f.chat.items.map { it.row.message!!.id() }); assertFalse(f.chat.historyLoading)
        assertEquals(1, f.wire.commands.size); assertEquals("", f.chat.notice)
    }
    @Test fun userContextContinuationUsesOnlyOwnedProgressingPagesAndWaitsForAck() {
        val f = Fixture(); f.chat.onTimelineMeta(meta(1)); f.chat.onSnapshot(snapshot(message("tail", ChatMessage.Role.ASSISTANT)))
        f.chat.onData("r1", "s", page()); assertEquals(1, f.wire.commands.size); assertTrue(f.chat.historyLoading)
        f.chat.onAck(Ack("r1", "foreign", true, "")); assertEquals(1, f.wire.commands.size)
        f.chat.onAck(Ack("r1", "s", true, "")); assertEquals(2, f.wire.commands.size)
        f.chat.onData("r2", "s", page("last", "user", "prompt", "prompt")); f.chat.onAck(Ack("r2", "s", true, ""))
        assertEquals(2, f.wire.commands.size); assertFalse(f.chat.historyLoading); assertEquals(0, f.wire.writes)
    }
    @Test fun onlyAnOwnedMcpCommandAcceptsItsDataOnceAndClearsItsOwnDraft() {
        val f = Fixture(); f.chat.onConfiguration("s", JSONObject().put("capabilities", JSONArray().put("mcp").put("name")))
        f.chat.composer = TextFieldValue(" /mcp "); assertFalse(f.chat.send(CommandBuilder.Behavior.FOLLOW_UP))
        val data = JSONObject().put("type", "mcp").put("servers", JSONArray())
        f.chat.onData("r1", "foreign", data); f.chat.onData("foreign", "s", data)
        f.chat.onData("r1", "s", JSONObject().put("type", "document")); assertTrue(f.effects.isEmpty())
        f.chat.onData("r1", "s", data); f.chat.onData("r1", "s", data); assertEquals(1, f.effects.filterIsInstance<ChatSession.Effect.Mcp>().size)
        f.chat.onAck(Ack("r1", "s", true, "")); assertEquals("", f.chat.composer.text)
        f.chat.composer = TextFieldValue("/name Name"); f.chat.send(CommandBuilder.Behavior.STEER); f.chat.onData("r2", "s", data)
        assertEquals(1, f.effects.filterIsInstance<ChatSession.Effect.Mcp>().size); assertEquals(0, f.wire.writes)
    }
    @Test fun missingMcpBodyLeavesDraftForExplicitRetryAndDoesNotInventAWindow() {
        val f = Fixture(); f.chat.onConfiguration("s", JSONObject().put("capabilities", JSONArray().put("mcp"))); f.chat.composer = TextFieldValue("/mcp")
        f.chat.send(CommandBuilder.Behavior.FOLLOW_UP); f.chat.onAck(Ack("r1", "s", true, ""))
        assertEquals("/mcp", f.chat.composer.text); assertEquals("Pi returned no MCP status", f.chat.notice); assertTrue(f.effects.none { it is ChatSession.Effect.Mcp })
        f.chat.send(CommandBuilder.Behavior.FOLLOW_UP); assertEquals(2, f.wire.commands.size); assertEquals(0, f.wire.writes)
    }
    @Test fun repeatedStopIsSingleFlightAndAckDoesNotInventIdleOrChangeDraft() {
        val f = Fixture(); f.chat.onSnapshot(Snapshot("s", SessionStatus.RUNNING, true, listOf(message("u")), false)); f.chat.composer = TextFieldValue("Retained draft")
        repeat(10) { f.chat.abort() }; assertEquals(1, f.wire.writes); assertEquals("Stop is already requested", f.chat.notice)
        f.chat.onAck(Ack("a1", "foreign", true, "")); f.chat.abort(); assertEquals(1, f.wire.writes)
        f.chat.onAck(Ack("a1", "s", true, "")); f.chat.onCommandUncertain("a1", "s", "late"); assertEquals(SessionStatus.RUNNING, f.chat.status)
        assertEquals("Retained draft", f.chat.composer.text); f.chat.abort(); assertEquals(2, f.wire.writes)
        f.chat.onAck(Ack("a2", "s", false, "denied")); assertEquals("Pi rejected the command: denied", f.chat.notice)
    }
    @Test fun detachedControlAndStopSettleUnknownWithoutReplayOrDraftConsumption() {
        val f = Fixture(); f.chat.onConfiguration("s", JSONObject().put("capabilities", JSONArray().put("mcp"))); f.chat.composer = TextFieldValue("/mcp")
        f.chat.send(CommandBuilder.Behavior.FOLLOW_UP); f.chat.abort(); f.wire.liveRequests = setOf("r1", "a1"); f.chat.start()
        f.chat.send(CommandBuilder.Behavior.FOLLOW_UP); f.chat.abort(); assertEquals(1, f.wire.commands.size); assertEquals(1, f.wire.writes)
        f.wire.liveRequests = emptySet(); f.chat.start(); assertEquals("/mcp", f.chat.composer.text)
        f.chat.onData("r1", "s", JSONObject().put("type", "mcp")); f.chat.onAck(Ack("a1", "s", true, ""))
        assertTrue(f.effects.none { it is ChatSession.Effect.Mcp }); assertEquals(1, f.wire.writes); assertEquals(1, f.wire.commands.size)
    }
    @Test fun closedControlsIgnoreLateEffectsWhileOtherCapabilitiesKeepTheirTickets() {
        val f = Fixture(); f.chat.onTimelineMeta(meta(1)); f.chat.onSnapshot(snapshot(message("tail")))
        f.chat.onConfiguration("s", JSONObject().put("capabilities", JSONArray().put("mcp"))); f.chat.composer = TextFieldValue("/mcp")
        f.chat.send(CommandBuilder.Behavior.FOLLOW_UP); f.chat.abort(); f.chat.loadOlder(); f.chat.document("one.md"); f.chat.configure(null, null, "high", null, false)
        f.effects.clear(); f.chat.closeControls(); f.chat.onData("r1", "s", JSONObject().put("type", "mcp")); f.chat.onAck(Ack("r1", "s", true, "")); f.chat.onCommandUncertain("a1", "s", "late")
        f.chat.send(CommandBuilder.Behavior.FOLLOW_UP); f.chat.abort(); assertTrue(f.effects.isEmpty()); assertEquals("/mcp", f.chat.composer.text)
        assertTrue(f.chat.historyLoading); assertTrue(f.chat.configurationPending); assertEquals(2, f.wire.writes); assertEquals(3, f.wire.commands.size)
        f.chat.onData("r3", "s", JSONObject().put("type", "document").put("text", "body")); assertEquals(ChatSession.Effect.Document("one.md", "body"), f.effects.last())
        f.chat.onAck(Ack("c2", "s", true, "")); assertFalse(f.chat.configurationPending)
    }
    @Test fun configurationWritesKeepTypedFieldsAndDoNotOptimisticallyReplaceReportsOrDraft() {
        val f = Fixture(); f.chat.onSnapshot(snapshot(message("u"))); f.chat.composer = TextFieldValue("Retained draft")
        val reported = JSONObject().put("model", "old/model").put("thinkingLevel", "low"); f.chat.onConfiguration("s", reported)
        assertTrue(f.chat.configure("new", "model", "high", "fast", true))
        assertEquals(listOf("new", "model", "high", "fast"), f.wire.changes.single()); assertSame(reported, f.chat.configuration)
        f.chat.onAck(Ack("c1", "foreign", true, "")); assertTrue(f.chat.configurationPending)
        f.chat.onAck(Ack("c1", "s", true, "")); assertFalse(f.chat.configurationPending)
        assertSame(reported, f.chat.configuration); assertEquals("Retained draft", f.chat.composer.text); assertEquals(SessionStatus.IDLE, f.chat.status)
    }
    @Test fun partialAndForeignConfigurationReportsCannotSettleThePendingChange() {
        val f = Fixture(); f.chat.onSnapshot(snapshot()); f.chat.configure(null, null, "high", null, false)
        val partial = JSONObject().put("serviceTier", "fast"); f.chat.onConfiguration("s", partial)
        f.chat.onConfiguration("foreign", JSONObject().put("thinkingLevel", "low")); assertSame(partial, f.chat.configuration)
        assertTrue(f.chat.configurationPending); assertTrue(f.effects.isEmpty())
        f.chat.onAck(Ack("c1", "s", false, "Rejected")); assertEquals(ChatSession.Effect.ConfigurationResult("Rejected"), f.effects.single())
    }
    @Test fun configurationSetupFailureAndMissingIdsReturnFeedbackWithoutStuckPending() {
        for (id in listOf(null, "")) {
            val f = Fixture(); f.wire.configurationId = id
            assertFalse(f.chat.configure(null, null, "high", null, false)); assertFalse(f.chat.configurationPending)
            assertEquals(listOf(ChatSession.Effect.ConfigurationResult("Pi is disconnected. Changes not sent.")), f.effects)
        }
        val f = Fixture(); f.wire.configurationFailure = IllegalStateException("Synthetic write")
        assertFalse(f.chat.configure(null, null, "high", null, false)); assertFalse(f.chat.configurationPending)
        assertEquals(listOf(ChatSession.Effect.ConfigurationResult("Changes could not be sent: Synthetic write")), f.effects)
        f.wire.configurationFailure = null; assertTrue(f.chat.configure(null, null, null, "fast", false))
    }
    @Test fun configurationIsReservedBeforeTransportAndClosedBeforeALateReturn() {
        val f = Fixture(); f.wire.configuring = { assertTrue(f.chat.configurationPending); assertFalse(f.chat.configure(null, null, "low", null, false)) }
        assertTrue(f.chat.configure(null, null, "high", null, false)); assertEquals(1, f.wire.writes)
        f.chat.onAck(Ack("c1", "s", true, "")); f.effects.clear(); f.wire.configuring = f.chat::closeConfiguration
        assertFalse(f.chat.configure(null, null, "low", null, false)); assertFalse(f.chat.configurationPending)
        f.chat.onAck(Ack("c2", "s", false, "late")); assertTrue(f.effects.isEmpty()); assertEquals(2, f.wire.writes)
    }
    @Test fun closingConfigurationRetiresOnlyItsTicketAndBlocksLatePanelEffects() {
        val f = Fixture(); f.chat.onSnapshot(snapshot()); f.chat.document("one.md"); f.chat.configure(null, null, "high", null, false)
        f.chat.composer = TextFieldValue("Retained draft"); f.chat.closeConfiguration(); f.effects.clear()
        assertFalse(f.chat.configurationPending); assertFalse(f.chat.canConfigureModel)
        assertFalse(f.chat.configure("p", "m", null, null, true)); f.chat.onAck(Ack("c1", "s", false, "late")); f.chat.onCommandUncertain("c1", "s", "late")
        assertTrue(f.effects.isEmpty()); assertEquals("Retained draft", f.chat.composer.text); assertEquals(1, f.wire.writes)
        f.chat.onData("r1", "s", JSONObject().put("type", "document").put("text", "body")); assertTrue(f.effects.single() is ChatSession.Effect.Document)
    }
    @Test fun delayedCachedSnapshotCannotRestoreAnAnchorAfterTheReaderAlreadyMoved() {
        val f = Fixture(); f.chat.onSnapshot(snapshot(message("u"),message("a",ChatMessage.Role.ASSISTANT)))
        f.wire.cached=JSONObject().put("anchor","u").put("offset",-40).put("follow",true)
        f.chat.readerDragged(); f.chat.readerSettled(false); val tail=f.chat.tailRevision
        f.chat.onTimelineMeta(meta(1,cached=true)); f.chat.onSnapshot(snapshot(message("u"),message("a",ChatMessage.Role.ASSISTANT)))
        assertNull(f.chat.restoreViewport); assertFalse(f.chat.followTail); assertEquals(tail,f.chat.tailRevision); assertEquals(0,f.wire.viewportReads)
        f.chat.saveViewport("a",-620); assertEquals(Triple("a",-620,false),f.wire.saved); assertEquals(0,f.wire.writes)
    }
    @Test fun initialCachedViewportKeepsPublicFacadeAndConsumesTheSavedAnchorOnce() {
        val f=Fixture(); f.wire.cached=JSONObject().put("anchor","a").put("offset",-480).put("follow",false)
        f.chat.onTimelineMeta(meta(1,cached=true)); f.chat.onSnapshot(snapshot(message("u"),message("a",ChatMessage.Role.ASSISTANT)))
        assertEquals(ChatSession.Viewport("a",-480,false),f.chat.restoreViewport); assertFalse(f.chat.followTail); val tail=f.chat.tailRevision
        f.chat.readerSettled(false); f.chat.viewportRestored(); f.wire.cached=JSONObject().put("anchor","u").put("follow",true)
        f.chat.onSnapshot(snapshot(message("u"),message("a",ChatMessage.Role.ASSISTANT)))
        assertNull(f.chat.restoreViewport); assertEquals(1,f.wire.viewportReads); assertEquals(tail,f.chat.tailRevision)
    }
    @Test fun closedViewportDoesNotScrollDiscloseRestoreOrSaveWhileCanonicalStateStillUpdates() {
        val f=Fixture(); f.chat.onSnapshot(snapshot(message("u"))); f.chat.readerDragged(); f.chat.closeViewport()
        val tail=f.chat.tailRevision; val arrival=f.chat.arrivalRevision
        f.chat.onMessages(MessagesUpdate("s",listOf(message("late",ChatMessage.Role.ASSISTANT)),emptyList(),SessionStatus.RUNNING,true,false,false,false,false))
        f.chat.readerSettled(true); f.chat.viewportRestored(); f.chat.saveViewport("late",1)
        assertEquals(listOf("u","late"),f.chat.items.map { it.row.message!!.id() }); assertEquals(SessionStatus.RUNNING,f.chat.status)
        assertEquals(tail,f.chat.tailRevision); assertEquals(arrival,f.chat.arrivalRevision); assertTrue(f.chat.arrivingKeys.isEmpty()); assertNull(f.chat.restoreViewport); assertNull(f.wire.saved)
    }
    @Test fun viewportDelegationKeepsCatchUpToolsAndNewRowsWithNoHistoryDisclosure() {
        val f=Fixture(); val user=message("u").withPresentation("turn","final","","")
        val tool=message("tool",ChatMessage.Role.TOOL_RESULT,"old").withPresentation("turn","work","","")
        f.chat.onTimelineMeta(meta(1,more=false).put("activeTurnId","turn")); f.chat.onSnapshot(snapshot(user,tool)); f.chat.readerDragged(); f.chat.start()
        f.chat.onMessages(MessagesUpdate("s",listOf(message("tool",ChatMessage.Role.TOOL_RESULT,"updated").withPresentation("turn","work","","")),emptyList(),SessionStatus.UNKNOWN,false,false,false,false,false))
        assertTrue(f.chat.arrivingKeys.any { it.startsWith("work-header:") })
        f.chat.arrivalsShown(f.chat.arrivalRevision)
        f.chat.start(); f.chat.onMessages(MessagesUpdate("s",listOf(message("new",ChatMessage.Role.ASSISTANT).withPresentation("turn","final","","")),emptyList(),SessionStatus.UNKNOWN,false,false,false,false,false))
        assertTrue("new" in f.chat.arrivingKeys); assertTrue(f.chat.newActivity); assertFalse(f.chat.followTail)
    }
}
