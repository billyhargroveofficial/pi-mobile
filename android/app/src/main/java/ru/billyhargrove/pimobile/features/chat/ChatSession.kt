package ru.billyhargrove.pimobile.features.chat

import androidx.compose.runtime.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.json.JSONObject
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.media.Attachment
import ru.billyhargrove.pimobile.net.PiClient

/** Main-thread session owner. Transport is injected; rendering never owns a socket or replays a command. */
class ChatSession(
    val sessionId: String,
    title: String,
    val readOnly: Boolean,
    private val transport: Transport,
    initialReceipts: List<ChatMessage>,
    private val persist: (List<ChatMessage>) -> Unit,
    private val effect: (Effect) -> Unit
) : PiClient.Listener {
    interface Transport {
        fun catalog(): Catalog = Catalog.empty()
        fun connection(): ConnectionState
        fun configuration(session: String): JSONObject?
        fun viewport(session: String): JSONObject?
        fun saveViewport(session: String, key: String, offset: Int, follow: Boolean)
        fun subscribe(session: String)
        fun prompt(session: String, text: String, payloads: List<ImagePayload>, behavior: CommandBuilder.Behavior): String?
        fun abort(session: String): String?
        fun configure(session: String, provider: String?, model: String?, effort: String?, tier: String?): String?
        fun read(session: String, kind: String, args: JSONObject): String?
        /** Null is only for transports without a ledger (e.g. a stateless preview). */
        fun pendingRequests(session: String): Set<String>? = null
    }
    sealed interface Effect {
        data class Notice(val text: String) : Effect
        data class Document(val path: String, val text: String) : Effect
        data class Mcp(val data: JSONObject) : Effect
        data class ConfigurationResult(val error: String?) : Effect
    }
    data class Viewport(val key: String, val offset: Int, val follow: Boolean)

    var title by mutableStateOf(title); private set
    var connection by mutableStateOf(transport.connection()); private set
    var status by mutableStateOf(SessionStatus.UNKNOWN); private set
    var configuration by mutableStateOf<JSONObject?>(null); private set
    var metadata by mutableStateOf(JSONObject()); private set
    val items get() = transcript.items
    var queue by mutableStateOf<List<ChatMessage>>(emptyList()); private set
    var notice by mutableStateOf(""); private set
    private val draft = ComposerSession<TextFieldValue, Attachment>(readOnly, object : ComposerSession.Editor<TextFieldValue> {
        override fun text(value: TextFieldValue) = value.text
        override fun selectionStart(value: TextFieldValue) = value.selection.start
        override fun create(text: String, caret: Int) = TextFieldValue(text, TextRange(caret))
    })
    var composer: TextFieldValue
        get() = draft.value
        set(value) { draft.value = value }
    val attachments get() = draft.attachments
    val preparing get() = draft.preparing
    val transcribing get() = draft.transcribing
    var loading by mutableStateOf(true); private set
    var cached by mutableStateOf(false); private set
    var truncated by mutableStateOf(false); private set
    var historyLoading by mutableStateOf(false); private set
    val followTail get() = viewport.followTail
    val tailRevision get() = viewport.tailRevision
    val smoothTail get() = viewport.smoothTail
    val arrivalRevision get() = viewport.arrivalRevision
    val arrivingKeys get() = viewport.arrivingKeys
    val newActivity get() = viewport.newActivity
    var restoreViewport by mutableStateOf<Viewport?>(null); private set
    var configurationPending by mutableStateOf(false); private set
    private val outbox = ChatOutbox(sessionId, readOnly, transport::prompt, initialReceipts)
    private val viewport = ViewportSession({ transport.viewport(sessionId)?.let {
        ViewportSession.Position(it.optString("anchor"), it.optInt("offset"), it.optBoolean("follow"))
    } }, { transport.saveViewport(sessionId, it.key, it.offset, it.follow) }, {
        restoreViewport = it?.let { position -> Viewport(position.key, position.offset, position.follow) }
    })
    private val transcript = TranscriptSession(outbox::reconcile, {
        outbox.persistReceipts(persist); queue = outbox.queuedMessages()
    }, viewport::published, viewport::rendered)
    private var resetPresentation = false
    private val history = HistorySession(
        { before -> transport.read(sessionId, "history", JSONObject().put("before", before).put("limit", 40)) },
        { transcript.hasUserContext }, { historyLoading = it }, ::notice)
    private val documents = DocumentSession(
        { path -> transport.read(sessionId, "document", JSONObject().put("path", path)) },
        { document -> effect(Effect.Document(document.path, document.text)) }, ::notice)
    private val controls = ControlSession(readOnly, ::capability, { command ->
        when (command) {
            ControlSession.Command.Mcp -> transport.read(sessionId, "mcp", JSONObject())
            is ControlSession.Command.Rename -> transport.read(sessionId, "name", JSONObject().put("name", command.name))
        }
    }, { transport.abort(sessionId) }, draft::clearMatchingText, ::notice)
    private val configurationChanges = ConfigurationSession(readOnly, { status == SessionStatus.IDLE }, { change ->
        transport.configure(sessionId, change.provider, change.model, change.effort, change.tier)
    }, { configurationPending = it }, { effect(Effect.ConfigurationResult(it)) }, ::notice)
    val canSend get() = draft.editable && connection == ConnectionState.CONNECTED && status != SessionStatus.OFFLINE && !preparing
    val voice get() = draft.voice
    val canConfigureModel get() = connection == ConnectionState.CONNECTED && status == SessionStatus.IDLE && configurationChanges.canSubmit

    init { render() }

    fun start() {
        viewport.start()
        connection = transport.connection()
        // The Activity detaches its listener while stopped. ACKs/timeouts can finish
        // there; recover missing tickets as unknown, never as accepted or replayed.
        transport.pendingRequests(sessionId)?.let { live ->
            documents.reconcile(live)
            history.reconcile(live)
            controls.reconcile(live)
            configurationChanges.reconcile(live)
            val owned = outbox.localReceipts().filter { it.localState() == ChatMessage.LocalState.SENDING }.map { it.requestId() }
            owned.filter { it !in live }.forEach { onCommandUncertain(it, sessionId, "The result arrived while this screen was inactive") }
        }
        configuration = transport.configuration(sessionId)
        transport.subscribe(sessionId)
    }

    fun saveViewport(key: String, offset: Int) = viewport.save(key, offset)
    fun viewportRestored() = viewport.viewportRestored()
    fun arrivalsShown(revision: Int) = viewport.arrivalsShown(revision)
    fun readerDragged() = viewport.readerDragged()
    fun readerSettled(atBottom: Boolean) = viewport.readerSettled(atBottom)
    fun closeViewport() = viewport.close()
    fun toggle(group: String) = transcript.toggle(group)
    fun thumbnail(request: String, index: Int) = outbox.thumbnail(request, index)
    fun queueRestored(request: String) = outbox.queueRestored(request)
    fun transcriptionChanged(value: Boolean) = draft.transcriptionChanged(value)
    fun showNotice(text: String) { notice = text }
    fun dismissNotice() { notice = "" }
    fun beginPreparing() = draft.beginPreparing()
    fun prepared(values: List<Attachment>) = draft.prepared(values)
    fun removeAttachment(index: Int) = draft.removeAttachment(index)
    fun insertDictation(text: String) = draft.insertDictation(text)
    fun selectSkill(name: String) = draft.selectSkill(name)
    fun closeComposer() = draft.close()

    fun send(behavior: CommandBuilder.Behavior): Boolean {
        if (!draft.editable) return false
        if (preparing) { notice("Wait for attachments to finish preparing"); return false }
        val submitted = draft.submission() ?: return false
        val text = submitted.text
        if (controls.handle(text, submitted.attachments.isNotEmpty())) return false
        if (text.startsWith("$") && !capability("skills")) { notice("Update the Pi bridge when idle to use skills"); return false }
        return when (outbox.send(text, submitted.attachments, behavior, status == SessionStatus.RUNNING).status) {
            ChatOutbox.SendStatus.WRITTEN -> { draft.sent(submitted); render(); tail(); true }
            ChatOutbox.SendStatus.EMPTY -> { notice("Write a message or attach a file"); false }
            else -> { notice("Not connected to this Pi session"); false }
        }
    }

    fun abort() = controls.abort()
    fun closeControls() = controls.close()

    fun retry(message: ChatMessage) {
        when (outbox.retry(message.requestId()).status) {
            ChatOutbox.SendStatus.WRITTEN -> { notice("Retry sent. The original message may already have been accepted."); render(); tail() }
            ChatOutbox.SendStatus.MISSING_DRAFT -> notice("Restore the draft and reattach files. Check whether Pi already accepted the message before retrying.")
            ChatOutbox.SendStatus.NO_CONNECTION -> notice("Not connected to this Pi session")
            else -> Unit
        }
    }

    fun restore(message: ChatMessage) {
        if (!draft.editable) return
        val result = outbox.restore(message.requestId(), draft.occupied)
        when (result.status) {
            ChatOutbox.RestoreStatus.COMPOSER_OCCUPIED -> notice("Clear the current draft first")
            ChatOutbox.RestoreStatus.RESTORED, ChatOutbox.RestoreStatus.REATTACH_REQUIRED -> {
                draft.restore(result.text, result.draft?.attachments.orEmpty())
                render()
                if (result.status == ChatOutbox.RestoreStatus.REATTACH_REQUIRED) notice("Check the chat before retrying. Select attachments again.")
            }
            else -> Unit
        }
    }

    fun configure(provider: String?, model: String?, effort: String?, tier: String?, modelChange: Boolean) =
        configurationChanges.apply(ConfigurationSession.Change(provider, model, effort, tier), modelChange)
    fun closeConfiguration() = configurationChanges.close()

    /** Paths are validated in the platform shell and confined again by the gateway. Read-only inspection may read. */
    fun document(path: String) {
        documents.open(path)
    }
    fun cancelDocument() = documents.cancel()
    fun closeDocuments() = documents.close()

    fun loadOlder() { history.loadOlder() }
    fun closeHistory() = history.close()
    private fun capability(name: String): Boolean {
        val caps = configuration?.optJSONArray("capabilities") ?: return false
        return (0 until caps.length()).any { caps.optString(it) == name }
    }

    override fun onConnectionState(state: ConnectionState, detail: String) { connection = state }
    override fun onCatalog(catalog: Catalog) {
        val session = catalog.findSession(sessionId) ?: return
        status = session.status()
        if (session.displayTitle().isNotEmpty()) title = session.displayTitle()
        transcript.status(status)
    }
    override fun onConfiguration(id: String, value: JSONObject) { if (id == sessionId) configuration = value }
    override fun onTimelineMeta(frame: JSONObject) {
        if (frame.optString("sessionId") != sessionId) return
        cached = frame.optBoolean("cached")
        history.cached = cached
        val snapshot = frame.optString("type") == "snapshot"
        if (snapshot && history.epoch != 0L && history.epoch != frame.optLong("epoch")) resetPresentation = true
        metadata = frame
        transcript.metadata(frame, viewport.catchingUp)
        if (snapshot) {
            history.snapshot(frame.optLong("epoch"), historyCursor(frame), cached)
        }
    }
    override fun onSnapshot(snapshot: Snapshot) {
        if (snapshot.sessionId() != sessionId) return
        val resetMetadata = metadata.takeIf { resetPresentation }
        resetPresentation = false
        if (cached) viewport.restoreCached()
        loading = false; status = snapshot.status(); truncated = snapshot.truncated()
        transcript.snapshot(snapshot.messages(), status, resetMetadata, viewport.catchingUp, !cached)
        viewport.snapshotRendered(cached)
        history.ensureUserContext()
    }
    override fun onMessages(update: MessagesUpdate) {
        if (update.sessionId() != sessionId) return
        if (update.hasStatus()) status = update.status()
        if (update.hasTruncated()) truncated = update.truncated()
        loading = false
        transcript.messages(update.messages(), update.removedIds(), status, viewport.catchingUp)
        viewport.messagesRendered()
        history.ensureUserContext()
    }
    override fun onData(request: String, id: String, data: JSONObject) {
        if (id != sessionId) return
        when {
            data.optString("type") == "mcp" && controls.receivedMcp(request) -> effect(Effect.Mcp(data))
            documents.owns(request) && data.optString("type") == "document" -> documents.received(request, data.optString("path"), data.optString("text"))
            history.owns(request) && data.optString("type") == "history" -> {
                val messages = data.optJSONArray("messages")
                if (messages == null) { history.invalid(request); return }
                if (!history.received(request, data.optLong("epoch"), historyCursor(data))) return
                // Compose's stable item keys retain the reader's pixel anchor during prepend.
                transcript.prepend(SnapshotParser.parseMessages(messages), data)
            }
        }
    }
    override fun onAck(ack: Ack) {
        if (ack.sessionId() != sessionId) return
        val request = ack.requestId()
        when {
            controls.acknowledged(request, ack.ok(), error(ack)) -> Unit
            history.acknowledged(request, ack.ok(), error(ack)) -> Unit
            documents.acknowledged(request, ack.ok(), error(ack)) -> Unit
            configurationChanges.acknowledged(request, ack.ok(), error(ack)) -> Unit
            else -> {
                val result = outbox.acknowledge(ack)
                if (!result.handled) return
                if (!ack.ok()) {
                    notice("Pi rejected the command: ${error(ack)}")
                    result.rejectedText?.let { draft.restore(it) }
                }
                render()
            }
        }
    }
    override fun onCommandUncertain(requestId: String, sessionId: String, reason: String) {
        if (sessionId != this.sessionId) return
        when {
            controls.uncertain(requestId, reason) -> Unit
            history.uncertain(requestId, reason) -> Unit
            documents.uncertain(requestId, reason) -> Unit
            configurationChanges.uncertain(requestId) -> Unit
            outbox.uncertain(requestId, sessionId) -> { notice("Result unknown: $reason"); render() }
        }
    }
    override fun onProtocolError(message: String) { notice("Protocol error: $message") }

    private fun historyCursor(frame: JSONObject) = frame.optJSONObject("history")?.let {
        HistorySession.Cursor(it.optString("before"), it.optBoolean("hasMore"))
    }
    private fun render() = transcript.render()
    private fun tail() = viewport.tail()
    private fun notice(text: String) { showNotice(text); effect(Effect.Notice(text)) }
    private fun error(ack: Ack) = ack.error().ifEmpty { "no details" }
}
