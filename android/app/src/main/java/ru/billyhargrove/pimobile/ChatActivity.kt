package ru.billyhargrove.pimobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
import ru.billyhargrove.pimobile.media.Attachment
import ru.billyhargrove.pimobile.media.ImagePreparer
import ru.billyhargrove.pimobile.net.AppExecutors
import ru.billyhargrove.pimobile.net.AttachmentPreparer
import ru.billyhargrove.pimobile.net.PiClient
import ru.billyhargrove.pimobile.store.PendingMessages
import ru.billyhargrove.pimobile.ui.*

/** Composition/lifecycle and Android capabilities only. ChatSession owns transcript and command state. */
class ChatActivity : AppCompatActivity(), PiClient.Listener {
    companion object {
        @JvmStatic fun intent(context: Context, sessionId: String, title: String, readOnly: Boolean) =
            Intent(context, ChatActivity::class.java).putExtra("session_id", sessionId)
                .putExtra("session_title", title).putExtra("read_only", readOnly)
    }
    private lateinit var app: PiApp
    private lateinit var root: ComposeView
    private val chatState = mutableStateOf<ChatSession?>(null)
    private val chat get() = requireNotNull(chatState.value)
    private val transcriptList = LazyListState()
    private lateinit var orchestration: OrchestrationEntry
    private var behavior by mutableStateOf(CommandBuilder.Behavior.FOLLOW_UP)
    private var dictation: DictationRecorder? = null
    private var modelSheet: ModelSettingsSheet? = null
    private var effortPopup: EffortPopup? = null
    private var documentPreview: MarkdownPreview? = null
    private var effortBounds = Rect()
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) startDictation() else toast("Microphone permission is required for dictation")
    }
    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), ::picked)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        app = PiApp.get(this)
        val session = intent.getStringExtra("session_id").orEmpty()
        val pending = PendingMessages(this, app.settings().baseUrl(), session)
        val client = app.client()
        val transport = object : ChatSession.Transport {
            override fun catalog() = client.catalog()
            override fun connection() = client.state()
            override fun configuration(session: String) = client.configuration(session)
            override fun viewport(session: String) = client.cachedViewport(session)
            override fun saveViewport(session: String, key: String, offset: Int, follow: Boolean) = client.saveViewport(session, key, offset, follow)
            override fun subscribe(session: String) = client.subscribe(session)
            override fun prompt(session: String, text: String, payloads: List<ImagePayload>, behavior: CommandBuilder.Behavior) = client.sendPrompt(session, text, payloads, behavior)
            override fun abort(session: String) = client.sendAbort(session)
            override fun configure(session: String, provider: String?, model: String?, effort: String?, tier: String?) = client.configure(session, provider, model, effort, tier)
            override fun read(session: String, kind: String, args: JSONObject) = client.readCommand(session, kind, args)
        }
        chatState.value = ChatSession(session, intent.getStringExtra("session_title").orEmpty(),
            intent.getBooleanExtra("read_only", false), transport, pending.load(), pending::save, ::effect)
        state?.getString("composer_text")?.let { text ->
            chat.composer = TextFieldValue(text, TextRange(state.getInt("composer_selection_start", text.length).coerceIn(0, text.length),
                state.getInt("composer_selection_end", text.length).coerceIn(0, text.length)))
        }
        behavior = app.settings().behavior()
        orchestration = OrchestrationEntry {
            if (app.settings().hasToken()) app.api().fetchOrchestration(app.settings().baseUrl(), app.settings().token(), session) else null
        }
        root = ComposeView(this).apply {
            setContent { PiTheme {
                chatState.value?.let { current ->
                    PiNavigation(current.navigationRows, { row ->
                        if (row.readOnly()) toast("Extension required: this terminal has no Pi bridge")
                        else if (row.sessionId() != current.sessionId) startActivity(intent(this@ChatActivity, row.sessionId(), row.title(), false))
                    }, { startActivity(Intent(this@ChatActivity, MainActivity::class.java).putExtra("open_history", true)) },
                        { startActivity(Intent(this@ChatActivity, MainActivity::class.java).putExtra("open_settings", true)) },
                        currentSession = current.sessionId) { openMenu, _ ->
                        ChatScreen(current, transcriptList, app.mediaLoader(), behavior,
                        { behavior = it; app.settings().setBehavior(it) }, orchestration.summary,
                        { startActivity(OrchestrationActivity.intent(this@ChatActivity, session, "", "", "Orchestration")) },
                        openMenu, { imagePicker.launch(arrayOf("*/*")) }, ::requestDictation, ::quickEffort, ::onDocument)
                    }
                }
            } }
        }
        setContentView(root)
        // Compose owns the top inset; the platform root follows IME/nav at the bottom.
        SystemInsets.apply(this, root, null, null, false)
        val enter = com.google.android.material.transition.platform.MaterialSharedAxis(com.google.android.material.transition.platform.MaterialSharedAxis.X, true)
        enter.duration = if (ExpressiveMotion.enabled()) 350 else 0
        window.enterTransition = enter
        val back = com.google.android.material.transition.platform.MaterialSharedAxis(com.google.android.material.transition.platform.MaterialSharedAxis.X, false)
        back.duration = enter.duration
        window.returnTransition = back
    }
    override fun onStart() { super.onStart(); app.client().setListener(this); chat.start(); orchestration.start() }
    override fun onSaveInstanceState(state: Bundle) {
        state.putString("composer_text", chat.composer.text)
        state.putInt("composer_selection_start", chat.composer.selection.start)
        state.putInt("composer_selection_end", chat.composer.selection.end)
        super.onSaveInstanceState(state)
    }
    override fun onStop() {
        orchestration.stop(); dictation?.cancel(); dictation = null
        chat.items.getOrNull(transcriptList.firstVisibleItemIndex)?.let { chat.saveViewport(it.row.key, -transcriptList.firstVisibleItemScrollOffset) }
        app.client().clearListener(this)
        super.onStop()
    }
    override fun onDestroy() {
        modelSheet?.dismiss(); effortPopup?.dismiss(); documentPreview?.dismiss()
        super.onDestroy()
    }

    private fun quickEffort(bounds: Rect) {
        effortBounds = bounds
        if (chat.configurationPending) return
        val config = chat.configuration
        if (config == null || chat.connection != ConnectionState.CONNECTED) { toast("Connect to Pi first"); return }
        val models = config.optJSONArray("models")
        val selected = (0 until (models?.length() ?: 0)).mapNotNull { models?.optJSONObject(it) }
            .find { "${it.optString("provider")}/${it.optString("id")}" == config.optString("model") }
        if (selected == null) { openModelSettings(); return }
        effortPopup?.dismiss()
        effortPopup = EffortPopup(root, { effortBounds }, selected, config.optString("thinkingLevel", "off"), !chat.readOnly,
            ::openModelSettings, { chat.configure(null, null, it, null, false) }, config.optString("serviceTier", "standard"),
            { chat.configure(null, null, null, it, false) })
    }
    private fun openModelSettings() {
        if (chat.configurationPending) return
        effortPopup?.dismiss()
        val config = chat.configuration
        if (config?.optJSONArray("models") == null) { toast("Run /reload in Pi when idle to load models and effort levels."); return }
        modelSheet?.dismiss()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken, 0)
        modelSheet = ModelSettingsSheet(this, config, chat.canConfigureModel) { provider, model, effort, tier ->
            chat.configure(provider, model, effort, tier, true)
        }
        modelSheet?.show()
    }
    private fun effect(value: ChatSession.Effect) {
        when (value) {
            is ChatSession.Effect.Notice -> toast(value.text)
            is ChatSession.Effect.ConfigurationResult -> {
                if (value.error == null) { modelSheet?.dismiss(); toast("Session settings updated") }
                else if (modelSheet?.isShowing == true) modelSheet?.failed(value.error) else toast(value.error)
            }
            is ChatSession.Effect.Document -> {
                documentPreview?.dismiss()
                documentPreview = MarkdownPreview(this, value.path, value.text) { link ->
                    val parent = value.path.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
                    onDocument(if (link.startsWith('/') || link.contains(':')) link else parent + link)
                }
                documentPreview?.show()
            }
            is ChatSession.Effect.Mcp -> {
                val servers = value.data.optJSONArray("servers")
                val text = buildString {
                    for (index in 0 until (servers?.length() ?: 0)) {
                        val server = servers?.optJSONObject(index) ?: continue
                        val label = when (server.optString("status")) {
                            "connected" -> "connected"; "cached" -> "cached, disconnected"; "not-connected" -> "not connected"
                            "needs-auth" -> "sign-in required"; "disabled" -> "disabled"; "blocked" -> "blocked"; else -> "error"
                        }
                        append("${server.optString("name")} — $label · ${server.optInt("toolCount")} tools\n\n")
                    }
                    if (isEmpty()) append("No MCP servers in this Pi session.")
                    val observed = value.data.optLong("observedAt")
                    if (observed > 0) append("Status reported by Pi: ${android.text.format.DateFormat.getTimeFormat(this@ChatActivity).format(java.util.Date(observed))}")
                }
                MaterialAlertDialogBuilder(this).setTitle("Session MCP servers").setMessage(text).setPositiveButton("Done", null).show()
            }
        }
    }
    fun onDocument(raw: String) {
        val uri = Uri.parse(raw)
        val scheme = uri.scheme
        if (scheme.equals("http", true) || scheme.equals("https", true)) {
            try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) { toast("No app available to open this link") }
            return
        }
        if (scheme != null && !scheme.equals("file", true)) { toast("This link type is not supported"); return }
        val path = Uri.decode((if (scheme == null) raw else uri.path ?: return).substringBefore('#'))
        if (!path.matches(Regex(".*\\.(md|markdown)$", RegexOption.IGNORE_CASE))) { toast("Preview supports .md and .markdown files"); return }
        chat.document(path)
    }
    private fun picked(uris: List<Uri>) {
        if (uris.isEmpty() || chat.readOnly || chat.preparing) return
        val free = ImageGuard.MAX_IMAGES - chat.attachments.size
        if (uris.size > free) toast(getString(R.string.error_attach_limit))
        if (free <= 0 || !chat.beginPreparing()) return
        val owner = chat
        val selected = uris.take(free)
        val remaining = ImageGuard.remainingBytes(owner.attachments.map { it.payload() })
        AppExecutors.io().execute {
            val staged = mutableListOf<Attachment>()
            var failure: String? = null
            var budget = remaining
            for (uri in selected) try {
                val payload = AttachmentPreparer.prepare(contentResolver, uri, budget)
                budget -= payload.size()
                staged.add(Attachment(payload, if (payload.isFile) null else ImagePreparer.thumbnail(payload.bytes(), 320),
                    ImagePreparer.displayName(contentResolver, uri)))
            } catch (error: Exception) { failure = error.message ?: "could not read file"; break }
            AppExecutors.main {
                if (!isDestroyed) { owner.prepared(staged); failure?.let { toast(getString(R.string.error_attach_failed, it)) } }
            }
        }
    }
    private fun requestDictation() {
        if (chat.readOnly) return
        if (chat.transcribing) { toast("The previous recording is still being transcribed"); return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO) else startDictation()
    }
    private fun startDictation() {
        if (isFinishing || isDestroyed || chat.readOnly) return
        val owner = chat
        dictation = DictationRecorder(this, { file ->
            dictation = null; owner.transcriptionChanged(true); toast("Transcribing on your computer…")
            val url = app.settings().baseUrl(); val token = app.settings().token()
            AppExecutors.io().execute {
                var text: String? = null; var failure: String? = null
                try { text = app.api().transcribe(url, token, file) } catch (error: Exception) { failure = error.message }
                finally { file.delete() }
                AppExecutors.main {
                    owner.transcriptionChanged(false)
                    if (!isFinishing && !isDestroyed) {
                        when { failure != null -> toast(failure!!); text.isNullOrBlank() -> toast("No speech detected"); else -> owner.insertDictation(text!!) }
                    }
                }
            }
        }, ::toast)
    }
    private fun toast(text: String) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
    override fun onConnectionState(state: ConnectionState, detail: String) = chat.onConnectionState(state, detail)
    override fun onCatalog(catalog: Catalog) = chat.onCatalog(catalog)
    override fun onSnapshot(snapshot: Snapshot) = chat.onSnapshot(snapshot)
    override fun onMessages(update: MessagesUpdate) = chat.onMessages(update)
    override fun onAck(ack: Ack) = chat.onAck(ack)
    override fun onConfiguration(id: String, value: JSONObject) = chat.onConfiguration(id, value)
    override fun onTimelineMeta(frame: JSONObject) = chat.onTimelineMeta(frame)
    override fun onData(request: String, id: String, data: JSONObject) = chat.onData(request, id, data)
    override fun onCommandUncertain(request: String, id: String, reason: String) = chat.onCommandUncertain(request, id, reason)
    override fun onProtocolError(message: String) = chat.onProtocolError(message)
}
