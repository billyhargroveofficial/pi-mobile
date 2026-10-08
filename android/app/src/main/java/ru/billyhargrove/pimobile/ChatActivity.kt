package ru.billyhargrove.pimobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.features.chat.ChatScreen
import ru.billyhargrove.pimobile.features.chat.ChatSession
import ru.billyhargrove.pimobile.features.chat.McpProjection
import ru.billyhargrove.pimobile.features.chat.TranscriptionSession
import ru.billyhargrove.pimobile.features.chat.AttachmentSession
import ru.billyhargrove.pimobile.features.chat.DocumentSession
import ru.billyhargrove.pimobile.features.chat.ConfigurationPanels
import ru.billyhargrove.pimobile.media.Attachment
import ru.billyhargrove.pimobile.media.AttachmentImporter
import ru.billyhargrove.pimobile.media.PickedAttachments
import ru.billyhargrove.pimobile.net.AppExecutors
import ru.billyhargrove.pimobile.net.PiClient
import ru.billyhargrove.pimobile.net.SpeechTranscriber
import ru.billyhargrove.pimobile.store.PendingMessages
import ru.billyhargrove.pimobile.ui.*

/** Composition/lifecycle and Android capabilities only. ChatSession owns transcript and command state. */
class ChatActivity : AppCompatActivity(), PiClient.Listener {
    companion object {
        @JvmStatic fun intent(context: Context, sessionId: String, title: String, readOnly: Boolean) =
            Intent(context, ChatActivity::class.java).putExtra("session_id", sessionId)
                .putExtra("session_title", title).putExtra("read_only", readOnly)
                .putExtra("connection_scope", PiApp.get(context).settings().connectionScope())
    }
    private lateinit var app: PiApp
    private var boundScope = ""
    private var boundBase = ""
    private var boundToken = ""
    private fun currentComputer() = boundScope == app.settings().connectionScope()
    private fun currentClient() = currentComputer() && boundScope == app.client().connectionScope()
    private fun acceptFrame() = currentComputer() && (app.client().state() != ConnectionState.CONNECTED || currentClient())
    private lateinit var root: ComposeView
    private val chatState = mutableStateOf<ChatSession?>(null)
    private val chat get() = requireNotNull(chatState.value)
    private val transcriptList = LazyListState()
    private lateinit var orchestration: OrchestrationEntry
    private var behavior by mutableStateOf(CommandBuilder.Behavior.FOLLOW_UP)
    private var dictation: DictationRecorder? = null
    private lateinit var transcription: TranscriptionSession
    private lateinit var attachments: AttachmentSession<Uri, Attachment>
    private var modelSheet: ModelSettingsSheet? = null
    private var effortPopup: EffortPopup? = null
    private var documentPreview: MarkdownPreview? = null
    private var mcpDialog: AlertDialog? = null
    private var effortBounds = Rect()
    private val configurationPanels = ConfigurationPanels({ !isFinishing && !isDestroyed }, {
        ConfigurationPanels.State(chat.configuration, chat.connection == ConnectionState.CONNECTED,
            chat.configurationPending, chat.readOnly, chat.canConfigureModel)
    }, ::showQuickEffort, ::showModelSettings, ::notice)
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) startDictation() else notice("Microphone permission is required for dictation")
    }
    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), ::picked)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        app = PiApp.get(this)
        val restoredScope = state?.getString("connection_scope") ?: intent.getStringExtra("connection_scope")
        if (state != null && restoredScope == null && app.settings().profiles().size > 1) { finish(); return }
        boundScope = restoredScope ?: app.settings().connectionScope()
        if (!currentComputer()) { finish(); return }
        boundBase = app.settings().baseUrl(); boundToken = app.settings().token()
        val session = intent.getStringExtra("session_id").orEmpty()
        val pending = PendingMessages(this, boundBase, session)
        val client = app.client()
        val transport = object : ChatSession.Transport {
            override fun catalog() = if (currentClient()) client.catalog() else Catalog.empty()
            override fun connection() = if (currentClient()) client.state() else ConnectionState.DISCONNECTED
            override fun configuration(session: String) = if (currentClient()) client.configuration(session) else null
            override fun viewport(session: String) = if (currentClient()) client.cachedViewport(session) else null
            override fun saveViewport(session: String, key: String, offset: Int, follow: Boolean) { if (currentClient()) client.saveViewport(session, key, offset, follow) }
            override fun subscribe(session: String) { if (currentClient()) client.subscribe(session) }
            override fun prompt(session: String, text: String, payloads: List<ImagePayload>, behavior: CommandBuilder.Behavior) =
                if (currentClient()) client.sendPrompt(session, text, payloads, behavior) else null
            override fun abort(session: String) = if (currentClient()) client.sendAbort(session) else null
            override fun configure(session: String, provider: String?, model: String?, effort: String?, tier: String?) =
                if (currentClient()) client.configure(session, provider, model, effort, tier) else null
            override fun read(session: String, kind: String, args: JSONObject) = if (currentClient()) client.readCommand(session, kind, args) else null
            override fun pendingRequests(session: String) = if (currentClient()) client.pendingRequestIds(session).toSet() else emptySet()
        }
        chatState.value = ChatSession(session, intent.getStringExtra("session_title").orEmpty(),
            intent.getBooleanExtra("read_only", false), transport, pending.load(), pending::save, ::effect)
        val owner = chat
        val importer = AttachmentImporter(PickedAttachments(contentResolver)::prepare,
            { AppExecutors.io().execute(it) }, { AppExecutors.main(it) })
        attachments = AttachmentSession(owner.readOnly, object : AttachmentSession.Port<Uri, Attachment> {
            override fun start(keys: List<Uri>, budget: Long, done: (AttachmentSession.Batch<Attachment>) -> Boolean): AttachmentSession.Control {
                val job = importer.start(keys, budget) { done(AttachmentSession.Batch(it.values, it.error)) }
                return object : AttachmentSession.Control { override fun cancel() = job.cancel() }
            }
        }, owner::beginPreparing, owner::prepared, { notice(getString(R.string.error_attach_limit)) },
            { notice(getString(R.string.error_attach_failed, it)) }, { !isFinishing && !isDestroyed })
        val speech = SpeechTranscriber(app.api())
        transcription = TranscriptionSession(owner.readOnly, object : TranscriptionSession.Port {
            override fun start(file: java.io.File, done: (Result<String>) -> Unit): TranscriptionSession.Control {
                check(currentComputer()) { "Computer changed; return to the catalog" }
                val job = speech.start(boundBase, boundToken, file, done)
                return object : TranscriptionSession.Control { override fun cancel() = job.cancel() }
            }
            override fun discard(file: java.io.File) { file.delete() }
        }, owner::transcriptionChanged, owner::dismissNotice, owner::insertDictation, ::notice, { !isFinishing && !isDestroyed && currentComputer() })
        state?.getString("composer_text")?.let { text ->
            chat.composer = TextFieldValue(text, TextRange(state.getInt("composer_selection_start", text.length).coerceIn(0, text.length),
                state.getInt("composer_selection_end", text.length).coerceIn(0, text.length)))
        }
        behavior = app.settings().behavior()
        orchestration = OrchestrationEntry {
            if (currentComputer() && boundToken.isNotEmpty()) app.api().fetchOrchestration(boundBase, boundToken, session) else null
        }
        val media = app.mediaLoader() // Freeze this window's loader to its original computer.
        root = ComposeView(this).apply {
            setContent { PiTheme {
                chatState.value?.let { current ->
                    ChatScreen(current, transcriptList, media, behavior,
                        { behavior = it; app.settings().setBehavior(it) }, orchestration.snapshot,
                        { kind, id, title -> if (currentComputer()) startActivity(OrchestrationActivity.intent(this@ChatActivity, session, kind, id, title)) },
                        { finish() }, { imagePicker.launch(arrayOf("*/*")) }, ::requestDictation, ::quickEffort, ::onDocument)
                }
            } }
        }
        setContentView(root)
        // The transcript owns the whole canvas; Compose floats controls above IME/nav.
        SystemInsets.apply(this, root, null, null, false, composeOwnsInsets = true)
        val enter = com.google.android.material.transition.platform.MaterialSharedAxis(com.google.android.material.transition.platform.MaterialSharedAxis.X, true)
        enter.duration = if (ExpressiveMotion.enabled()) 350 else 0
        window.enterTransition = enter
        val back = com.google.android.material.transition.platform.MaterialSharedAxis(com.google.android.material.transition.platform.MaterialSharedAxis.X, false)
        back.duration = enter.duration
        window.returnTransition = back
    }
    override fun onStart() {
        super.onStart()
        if (chatState.value == null || !currentComputer()) { finish(); return }
        app.client().setListener(this); chat.start(); orchestration.start()
    }
    override fun onSaveInstanceState(state: Bundle) {
        state.putString("connection_scope", boundScope)
        if (chatState.value == null) { super.onSaveInstanceState(state); return }
        state.putString("composer_text", chat.composer.text)
        state.putInt("composer_selection_start", chat.composer.selection.start)
        state.putInt("composer_selection_end", chat.composer.selection.end)
        super.onSaveInstanceState(state)
    }
    override fun onStop() {
        if (chatState.value == null) { super.onStop(); return }
        orchestration.stop(); dictation?.cancel(); dictation = null
        chat.items.getOrNull(transcriptList.firstVisibleItemIndex)?.let { chat.saveViewport(it.row.key, -transcriptList.firstVisibleItemScrollOffset) }
        app.client().clearListener(this)
        super.onStop()
    }
    override fun onDestroy() {
        if (chatState.value == null) { super.onDestroy(); return }
        chat.closeViewport()
        chat.closeConfiguration()
        chat.closeControls()
        chat.closeHistory()
        chat.closeDocuments()
        chat.closeComposer()
        attachments.close()
        transcription.close()
        configurationPanels.close()
        documentPreview?.dismiss()
        val mcp = mcpDialog; mcpDialog = null; mcp?.dismiss()
        super.onDestroy()
    }

    private fun quickEffort(bounds: Rect) {
        effortBounds = bounds
        configurationPanels.openQuick()
    }
    private fun openModelSettings() = configurationPanels.openModels()
    private fun showQuickEffort(value: ConfigurationPanels.Quick): ConfigurationPanels.QuickPanel {
        val popup = EffortPopup(root, { effortBounds }, value.model, value.effort, value.editable,
            ::openModelSettings, { chat.configure(null, null, it, null, false) }, value.tier,
            { chat.configure(null, null, null, it, false) })
        effortPopup = popup
        return object : ConfigurationPanels.QuickPanel {
            override val showing get() = popup.isShowing
            override val pending get() = popup.awaitingResult
            override fun completed(error: String?) = popup.completed(error)
            override fun update(configuration: JSONObject) = popup.updateConfiguration(configuration)
            override fun close() { if (effortPopup === popup) effortPopup = null; popup.dismiss() }
        }
    }
    private fun showModelSettings(value: ConfigurationPanels.Models): ConfigurationPanels.Panel {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken, 0)
        val sheet = ModelSettingsSheet(this, value.catalog, value.editable) { provider, model, effort, tier ->
            chat.configure(provider, model, effort, tier, true)
        }
        modelSheet = sheet; sheet.show()
        return object : ConfigurationPanels.Panel {
            override val showing get() = sheet.isShowing
            override val pending get() = sheet.awaitingResult
            override fun completed(error: String?) { if (error == null) sheet.applied() else sheet.failed(error) }
            override fun close() { if (modelSheet === sheet) modelSheet = null; sheet.dismiss() }
        }
    }
    private fun effect(value: ChatSession.Effect) {
        when (value) {
            is ChatSession.Effect.Notice -> Unit // The owner exposes persistent, dismissible inline feedback.
            is ChatSession.Effect.ConfigurationResult -> configurationPanels.completed(value.error)
            is ChatSession.Effect.Document -> {
                if (isFinishing || isDestroyed) return
                if (documentPreview?.let { it.isShowing && it.matches(value.path, value.text) } == true) return
                val document = DocumentSession.Document(value.path, value.text)
                val previous = documentPreview
                val next = MarkdownPreview(this, value.path, value.text) { link -> onDocument(document.resolve(link)) }
                next.onClosed {
                    if (documentPreview === next) { documentPreview = null; chat.cancelDocument() }
                }
                documentPreview = next; previous?.dismiss(); next.show()
            }
            is ChatSession.Effect.Mcp -> {
                if (isFinishing || isDestroyed) return
                val status = McpProjection.project(value.data)
                val text = status.text + if (status.observedAt > 0)
                    "Status reported by Pi: ${android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(status.observedAt))}" else ""
                val previous = mcpDialog
                val next = MaterialAlertDialogBuilder(this).setTitle("Session MCP servers").setMessage(text).setPositiveButton("Done", null).create()
                next.setOnDismissListener { if (mcpDialog === next) mcpDialog = null }
                mcpDialog = next; previous?.dismiss(); next.show()
            }
        }
    }
    fun onDocument(raw: String) {
        if (isFinishing || isDestroyed) return
        val uri = Uri.parse(raw)
        val scheme = uri.scheme
        if (scheme.equals("http", true) || scheme.equals("https", true)) {
            try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) { notice("No app available to open this link") }
            return
        }
        if (scheme != null && !scheme.equals("file", true)) { notice("This link type is not supported"); return }
        val path = Uri.decode((if (scheme == null) raw else uri.path ?: return).substringBefore('#'))
        if (!path.matches(Regex(".*\\.(md|markdown)$", RegexOption.IGNORE_CASE))) { notice("Preview supports .md and .markdown files"); return }
        chat.document(path)
    }
    private fun picked(uris: List<Uri>) {
        if (chat.preparing) return
        attachments.pick(uris, chat.attachments.size, ImageGuard.remainingBytes(chat.attachments.map { it.payload() }))
    }
    private fun requestDictation() {
        if (chat.readOnly) return
        if (chat.transcribing) { return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO) else startDictation()
    }
    private fun startDictation() {
        if (isFinishing || isDestroyed || chat.readOnly || chat.transcribing) return
        dictation = DictationRecorder(this, { file ->
            dictation = null; transcription.start(file)
        }, ::notice)
    }
    private fun notice(text: String) { chat.showNotice(text) }
    override fun onConnectionState(state: ConnectionState, detail: String) {
        if (acceptFrame()) chat.onConnectionState(state, detail)
        else chat.onConnectionState(ConnectionState.DISCONNECTED, "Computer changed; return to the catalog")
    }
    override fun onCatalog(catalog: Catalog) { if (acceptFrame()) chat.onCatalog(catalog) }
    override fun onSnapshot(snapshot: Snapshot) { if (acceptFrame()) chat.onSnapshot(snapshot) }
    override fun onMessages(update: MessagesUpdate) { if (acceptFrame()) chat.onMessages(update) }
    override fun onAck(ack: Ack) { if (acceptFrame()) chat.onAck(ack) }
    override fun onConfiguration(id: String, value: JSONObject) {
        if (!acceptFrame()) return
        chat.onConfiguration(id, value)
        if (id == chat.sessionId) configurationPanels.update(value)
    }
    override fun onTimelineMeta(frame: JSONObject) { if (acceptFrame()) chat.onTimelineMeta(frame) }
    override fun onData(request: String, id: String, data: JSONObject) { if (acceptFrame()) chat.onData(request, id, data) }
    override fun onCommandUncertain(request: String, id: String, reason: String) { if (acceptFrame()) chat.onCommandUncertain(request, id, reason) }
    override fun onProtocolError(message: String) { if (acceptFrame()) chat.onProtocolError(message) }
}
