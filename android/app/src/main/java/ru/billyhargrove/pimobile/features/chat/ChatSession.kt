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
    var items by mutableStateOf<List<TranscriptPresentation.Item>>(emptyList()); private set
    var navigationRows by mutableStateOf(SessionGrouping.build(transport.catalog())); private set
    var composer by mutableStateOf(TextFieldValue())
    var attachments by mutableStateOf<List<Attachment>>(emptyList()); private set
    var preparing by mutableStateOf(false); private set
    var transcribing by mutableStateOf(false); private set
    var loading by mutableStateOf(true); private set
    var cached by mutableStateOf(false); private set
    var truncated by mutableStateOf(false); private set
    var historyLoading by mutableStateOf(false); private set
    var followTail by mutableStateOf(true); private set
    var tailRevision by mutableIntStateOf(0); private set
    var restoreViewport by mutableStateOf<Viewport?>(null); private set
    var configurationPending by mutableStateOf(false); private set
    private val store = TranscriptStore()
    private val presentation = TranscriptPresentation()
    private val outbox = ChatOutbox(sessionId, readOnly, transport::prompt, initialReceipts)
    private var firstRendered = false
    private var historyRequest: String? = null
    private var historyEpoch = 0L
    private var historyBefore = ""
    private var hasMore = false
    private var documentRequest: String? = null
    private var controlRequest: String? = null
    private var controlDraft = ""
    private var controlKind = ""
    private var configurationRequest: String? = null
    private val abortRequests = mutableSetOf<String>()
    val canSend get() = !readOnly && connection == ConnectionState.CONNECTED && status != SessionStatus.OFFLINE && !preparing
    val voice get() = composer.text.isBlank() && attachments.isEmpty()
    val canConfigureModel get() = !readOnly && connection == ConnectionState.CONNECTED && status == SessionStatus.IDLE && !configurationPending

    init { render() }

    fun start() {
        connection = transport.connection()
        configuration = transport.configuration(sessionId)
        transport.subscribe(sessionId)
    }

    fun saveViewport(key: String, offset: Int) {
        if (key.isNotEmpty()) transport.saveViewport(sessionId, key, offset, followTail)
    }
    fun viewportRestored() { restoreViewport = null }
    fun readerDragged() { followTail = false; restoreViewport = null }
    fun readerSettled(atBottom: Boolean) { followTail = atBottom }
    fun toggle(group: String) { presentation.toggle(group); items = presentation.items() }
    fun thumbnail(request: String, index: Int) = outbox.thumbnail(request, index)
    fun transcriptionChanged(value: Boolean) { transcribing = value }
    fun beginPreparing(): Boolean {
        if (readOnly || preparing) return false
        preparing = true
        return true
    }
    fun prepared(values: List<Attachment>) {
        preparing = false
        if (!readOnly) attachments = attachments + values
    }
    fun removeAttachment(index: Int) { if (!readOnly && !preparing) attachments = attachments.filterIndexed { i, _ -> i != index } }
    fun insertDictation(text: String) {
        if (readOnly || text.isBlank()) return
        val at = composer.selection.start.coerceIn(0, composer.text.length)
        val inserted = (if (at > 0) " " else "") + text
        composer = TextFieldValue(composer.text.substring(0, at) + inserted + composer.text.substring(at), TextRange(at + inserted.length))
    }
    fun selectSkill(name: String) { if (!readOnly) setText("\$$name ") }

    fun send(behavior: CommandBuilder.Behavior): Boolean {
        if (readOnly) return false
        if (preparing) { notice("Wait for attachments to finish preparing"); return false }
        val text = composer.text
        if (handleControl(text.trim())) return false
        if (text.startsWith("$") && !capability("skills")) { notice("Update the Pi bridge when idle to use skills"); return false }
        return when (outbox.send(text, attachments, behavior).status) {
            ChatOutbox.SendStatus.WRITTEN -> { composer = TextFieldValue(); attachments = emptyList(); render(); tail(); true }
            ChatOutbox.SendStatus.EMPTY -> { notice("Write a message or attach a file"); false }
            else -> { notice("Not connected to this Pi session"); false }
        }
    }

    fun abort() {
        if (readOnly) return
        val request = transport.abort(sessionId)
        if (request == null) notice("Stop is unavailable while Pi is disconnected")
        else { abortRequests.add(request); notice("Stop requested") }
    }

    fun retry(message: ChatMessage) {
        when (outbox.retry(message.requestId()).status) {
            ChatOutbox.SendStatus.WRITTEN -> { notice("Retry sent. The original message may already have been accepted."); render(); tail() }
            ChatOutbox.SendStatus.MISSING_DRAFT -> notice("Restore the draft and reattach files. Check whether Pi already accepted the message before retrying.")
            ChatOutbox.SendStatus.NO_CONNECTION -> notice("Not connected to this Pi session")
            else -> Unit
        }
    }

    fun restore(message: ChatMessage) {
        val result = outbox.restore(message.requestId(), composer.text.isNotEmpty() || attachments.isNotEmpty() || preparing)
        when (result.status) {
            ChatOutbox.RestoreStatus.COMPOSER_OCCUPIED -> notice("Clear the current draft first")
            ChatOutbox.RestoreStatus.RESTORED, ChatOutbox.RestoreStatus.REATTACH_REQUIRED -> {
                setText(result.text)
                attachments = result.draft?.attachments.orEmpty()
                render()
                if (result.status == ChatOutbox.RestoreStatus.REATTACH_REQUIRED) notice("Check the chat before retrying. Select attachments again.")
            }
            else -> Unit
        }
    }

    fun configure(provider: String?, model: String?, effort: String?, tier: String?, modelChange: Boolean): Boolean {
        if (readOnly) return false
        if (configurationPending) { notice("Waiting for the previous change to be confirmed"); return false }
        if (modelChange && status != SessionStatus.IDLE) { effect(Effect.ConfigurationResult("Wait for the current task to finish")); return false }
        configurationRequest = transport.configure(sessionId, provider, model, effort, tier)
        configurationPending = configurationRequest != null
        if (!configurationPending) effect(Effect.ConfigurationResult("Pi is disconnected. Changes not sent."))
        return configurationPending
    }

    /** Paths are validated in the platform shell and confined again by the gateway. Read-only inspection may read. */
    fun document(path: String) {
        if (documentRequest != null) { notice("A file is already loading"); return }
        documentRequest = transport.read(sessionId, "document", JSONObject().put("path", path))
        if (documentRequest == null) notice("Pi must be connected to preview files")
    }

    fun loadOlder() {
        if (cached || !hasMore || historyRequest != null || historyBefore.isEmpty()) return
        historyRequest = transport.read(sessionId, "history", JSONObject().put("before", historyBefore).put("limit", 40))
        historyLoading = historyRequest != null
        if (!historyLoading) notice("Pi must be connected to load history")
    }

    private fun ensureUserContext() {
        if (!cached && hasMore && store.transcript().none { it.role() == ChatMessage.Role.USER }) loadOlder()
    }
    private fun capability(name: String): Boolean {
        val caps = configuration?.optJSONArray("capabilities") ?: return false
        return (0 until caps.length()).any { caps.optString(it) == name }
    }
    private fun handleControl(text: String): Boolean {
        val kind = when { text == "/mcp" -> "mcp"; text == "/name" || text.startsWith("/name ") -> "name"; else -> return false }
        if (attachments.isNotEmpty()) { notice("This command does not send attachments. Remove them first"); return true }
        if (controlRequest != null) { notice("The previous command is still pending"); return true }
        if (!capability(kind)) { notice("Update the Pi bridge after the current task finishes"); return true }
        val args = JSONObject()
        if (kind == "name") {
            val name = text.substring(5).trim()
            if (name.isEmpty()) { notice("Use /name New name"); return true }
            args.put("name", name)
        }
        controlRequest = transport.read(sessionId, kind, args)
        if (controlRequest == null) notice("Not connected to this Pi session")
        else { controlKind = kind; controlDraft = text }
        return true
    }

    override fun onConnectionState(state: ConnectionState, detail: String) { connection = state }
    override fun onCatalog(catalog: Catalog) {
        navigationRows = SessionGrouping.build(catalog)
        val session = catalog.findSession(sessionId) ?: return
        status = session.status()
        if (session.displayTitle().isNotEmpty()) title = session.displayTitle()
        presentation.sessionStatus(status); items = presentation.items()
    }
    override fun onConfiguration(id: String, value: JSONObject) { if (id == sessionId) configuration = value }
    override fun onTimelineMeta(frame: JSONObject) {
        if (frame.optString("sessionId") != sessionId) return
        cached = frame.optBoolean("cached")
        metadata = frame
        presentation.metadata(frame); items = presentation.items()
        if (frame.optString("type") == "snapshot") {
            historyEpoch = frame.optLong("epoch")
            historyRequest = null; historyLoading = false
            readHistory(frame)
        }
    }
    override fun onSnapshot(snapshot: Snapshot) {
        if (snapshot.sessionId() != sessionId) return
        val viewport = if (cached) transport.viewport(sessionId) else null
        if (viewport != null) {
            firstRendered = true; followTail = false
            restoreViewport = Viewport(viewport.optString("anchor"), viewport.optInt("offset"), viewport.optBoolean("follow"))
        }
        loading = false; status = snapshot.status(); truncated = snapshot.truncated()
        presentation.sessionStatus(status)
        render(store.replaceAll(snapshot.messages()))
        ensureUserContext()
    }
    override fun onMessages(update: MessagesUpdate) {
        if (update.sessionId() != sessionId) return
        if (update.hasStatus()) status = update.status()
        if (update.hasTruncated()) truncated = update.truncated()
        loading = false; presentation.sessionStatus(status)
        render(store.apply(update.messages(), update.removedIds()))
        ensureUserContext()
    }
    override fun onData(request: String, id: String, data: JSONObject) {
        if (id != sessionId) return
        when {
            request == controlRequest && data.optString("type") == "mcp" -> effect(Effect.Mcp(data))
            request == documentRequest && data.optString("type") == "document" -> effect(Effect.Document(data.optString("path"), data.optString("text")))
            request == historyRequest && data.optString("type") == "history" -> {
                if (data.optLong("epoch") != historyEpoch) { historyRequest = null; historyLoading = false; return }
                // Compose's stable item keys retain the reader's pixel anchor during prepend.
                store.prepend(SnapshotParser.parseMessages(data.optJSONArray("messages")))
                presentation.metadata(data); render(); readHistory(data)
            }
        }
    }
    override fun onAck(ack: Ack) {
        if (ack.sessionId() != sessionId) return
        val request = ack.requestId()
        when {
            request == controlRequest -> {
                controlRequest = null
                if (ack.ok()) {
                    if (composer.text.trim() == controlDraft) composer = TextFieldValue()
                    if (controlKind == "name") notice("Session renamed")
                } else notice(error(ack))
                controlDraft = ""
            }
            request == historyRequest -> { historyRequest = null; historyLoading = false; if (ack.ok()) ensureUserContext() else notice(error(ack)) }
            request == documentRequest -> { documentRequest = null; if (!ack.ok()) notice(error(ack)) }
            request == configurationRequest -> {
                configurationRequest = null; configurationPending = false
                effect(Effect.ConfigurationResult(if (ack.ok()) null else error(ack)))
            }
            abortRequests.remove(request) -> if (!ack.ok()) notice("Pi rejected the command: ${error(ack)}")
            else -> {
                val result = outbox.acknowledge(ack)
                if (!result.handled) return
                if (!ack.ok()) {
                    notice("Pi rejected the command: ${error(ack)}")
                    if (result.rejectedText != null && composer.text.isEmpty() && attachments.isEmpty() && !preparing) setText(result.rejectedText)
                }
                render()
            }
        }
    }
    override fun onCommandUncertain(requestId: String, sessionId: String, reason: String) {
        if (sessionId != this.sessionId) return
        when {
            requestId == controlRequest -> { controlRequest = null; notice("Command result unknown: $reason") }
            requestId == historyRequest -> { historyRequest = null; historyLoading = false; notice("History could not be loaded: $reason") }
            requestId == documentRequest -> { documentRequest = null; notice("File could not be loaded: $reason") }
            requestId == configurationRequest -> {
                configurationRequest = null; configurationPending = false
                effect(Effect.ConfigurationResult("Result unknown. Check the model in the terminal before retrying."))
            }
            abortRequests.remove(requestId) -> notice("Result unknown: $reason")
            outbox.uncertain(requestId, sessionId) -> { notice("Result unknown: $reason"); render() }
        }
    }
    override fun onProtocolError(message: String) { notice("Protocol error: $message") }

    private fun readHistory(frame: JSONObject) {
        val history = frame.optJSONObject("history") ?: return
        historyBefore = history.optString("before"); hasMore = history.optBoolean("hasMore")
    }
    private fun render(change: TranscriptStore.ChangeSet? = null) {
        presentation.submit(outbox.reconcile(store.transcript()))
        persist(outbox.localReceipts()); items = presentation.items()
        val first = !firstRendered && items.isNotEmpty()
        if (first || (followTail && change?.tailTouched() == true)) tailRevision++
        if (first) firstRendered = true
    }
    private fun tail() { followTail = true; restoreViewport = null; tailRevision++ }
    private fun setText(text: String) { composer = TextFieldValue(text, TextRange(text.length)) }
    private fun notice(text: String) { effect(Effect.Notice(text)) }
    private fun error(ack: Ack) = ack.error().ifEmpty { "no details" }
}
