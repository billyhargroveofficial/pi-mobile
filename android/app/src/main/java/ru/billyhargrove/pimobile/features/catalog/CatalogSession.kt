package ru.billyhargrove.pimobile.features.catalog

import androidx.compose.runtime.*
import org.json.JSONObject
import java.util.UUID
import ru.billyhargrove.pimobile.BuildConfig
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.net.PiClient

/** Catalog/lifecycle command owner. Composables cannot send unconfirmed host mutations. */
class CatalogSession(private val transport: Transport, initialUrl: String, configured: Boolean, private val effect: (Effect) -> Unit) : PiClient.Listener {
    interface Transport {
        fun catalog(): Catalog
        fun connect(url: String, typedToken: String)
        fun disconnect()
        fun hasToken(): Boolean
        fun health(url: String, result: (Result<String>) -> Unit)
        fun refresh(result: (Result<Catalog>) -> Unit)
        fun command(session: String, kind: String, args: JSONObject): String?
        fun timeout(delay: Long, action: () -> Unit)
        fun cancelTimeout()
    }
    sealed interface Effect {
        data class OpenChat(val id: String, val title: String) : Effect
        data object OpenHistory : Effect
        data class Usage(val connected: Boolean) : Effect
    }
    enum class Screen { Catalog, Settings }
    data class Confirmation(val title: String, val message: String, val action: String, val danger: Boolean = false, val accept: () -> Unit)
    private data class Action(val session: String, val success: () -> Unit, val failure: (String) -> Unit)
    var screen by mutableStateOf(if (configured) Screen.Catalog else Screen.Settings)
    var url by mutableStateOf(initialUrl)
    // Last attempted/confirmed endpoint is separate from an unsubmitted Settings draft.
    var connectionEndpoint by mutableStateOf(initialUrl); private set
    fun hostLabel(): String = try {
        val endpoint = java.net.URI(connectionEndpoint.trim())
        endpoint.host?.let { host ->
            val address = if (host.contains(':') && !host.startsWith('[')) "[$host]" else host
            address + if (endpoint.port >= 0) ":${endpoint.port}" else ""
        } ?: "Host not configured"
    } catch (_: Exception) { "Host not configured" }
    var typedToken by mutableStateOf("")
    var hasStoredToken by mutableStateOf(transport.hasToken()); private set
    var catalog by mutableStateOf(transport.catalog()); private set
    var rows by mutableStateOf(SessionGrouping.build(catalog)); private set
    var connection by mutableStateOf(ConnectionState.IDLE); private set
    var connectionDetail by mutableStateOf(""); private set
    var message by mutableStateOf(""); private set
    var messageError by mutableStateOf(false); private set
    var healthBusy by mutableStateOf(false); private set
    var refreshBusy by mutableStateOf(false); private set
    var confirmation by mutableStateOf<Confirmation?>(null); private set
    var launching by mutableStateOf(false); private set
    private var active = false; private var generation = 0
    private var connectRequested = false
    private var launchId: String? = null; private var launchTitle = ""; private var launchRequest: String? = null
    private val actions = mutableMapOf<String, Action>()
    fun start() { active = true; onCatalog(transport.catalog()); effect(Effect.Usage(connection == ConnectionState.CONNECTED)) }
    fun stop() { active = false; generation++; healthBusy = false; refreshBusy = false; effect(Effect.Usage(false)) }
    fun close() { stop(); transport.cancelTimeout(); typedToken = ""; actions.clear() }
    fun connect() {
        val value = url.trim(); val result = EndpointPolicy.validate(value, BuildConfig.DEBUG)
        if (result != EndpointPolicy.Result.OK) { show(PiClient.describeResult(result), true); return }
        val typed = typedToken.trim()
        if (typed.isEmpty() && !transport.hasToken()) { show("Enter your access token", true); return }
        generation++; healthBusy = false; refreshBusy = false
        try { connectRequested = true; connectionEndpoint = value; transport.connect(value, typed); typedToken = ""; hasStoredToken = transport.hasToken(); show("", false) }
        catch (error: Exception) { connectRequested = false; show(error.message ?: "Could not save connection", true) }
    }
    fun disconnect() { generation++; connectRequested = false; transport.disconnect(); show("", false) }
    fun health() {
        val value = url.trim(); val result = EndpointPolicy.validate(value, BuildConfig.DEBUG)
        if (result != EndpointPolicy.Result.OK) { show(PiClient.describeResult(result), true); return }
        if (healthBusy) return
        healthBusy = true; val version = generation
        transport.health(value) { result ->
            if (!active || generation != version) return@health
            healthBusy = false
            result.fold({ body -> show("Server available" + body.replace('\n', ' ').trim().take(80).let { if (it.isEmpty()) "" else " · $it" }, false) },
                { error -> show(error.message ?: "Server unavailable", true) })
        }
    }
    fun refresh() {
        if (!transport.hasToken()) { show("Connect to your computer first", true); return }
        if (refreshBusy) return
        refreshBusy = true; val version = generation
        transport.refresh { result ->
            if (!active || generation != version) return@refresh
            refreshBusy = false
            result.fold(::onCatalog) { error -> show(error.message ?: "Could not refresh sessions", true) }
        }
    }
    fun history() { if (connection != ConnectionState.CONNECTED) show("Connect to your computer first", true) else if (!launching) effect(Effect.OpenHistory) }
    fun open(row: CatalogRow) {
        if (row.readOnly()) show("Extension required: this terminal has no Pi bridge", true)
        else effect(Effect.OpenChat(row.sessionId(), row.title()))
    }
    fun newSession(workspace: String, title: String) {
        if (launching) return
        confirmation = Confirmation("New session", "Start a new Pi in “$title”? This creates an Orca tab without sending a prompt.", "Create") {
            launch(UUID.randomUUID().toString(), "New conversation", "new", JSONObject().put("workspaceId", workspace))
        }
    }
    fun requestResume(id: String, title: String) {
        if (launching) return
        confirmation = Confirmation("Resume conversation?", "$title\n\nThis opens the saved conversation in Orca. No prompt will be sent.", "Open") { launch(id, title, "resume", JSONObject()) }
    }
    fun requestClose(row: CatalogRow) {
        if (!row.connected() || row.kind() != CatalogRow.Kind.SESSION) return
        val running = row.status() == SessionStatus.RUNNING
        confirmation = Confirmation(if (running) "Interrupt Pi and close the tab?" else "Close Pi tab?",
            row.title() + "\n\n" + (if (running) "The current task will be interrupted. " else "") + "Conversation history will remain on disk.", "Close", true) {
            command(row.sessionId(), "close", JSONObject().put("confirm", true).put("force", running), { show("Tab closed", false) }, { show(it, true) })
        }
    }
    fun cancelConfirmation() { confirmation = null }
    fun confirm() { val action = confirmation?.accept ?: return; confirmation = null; action() }
    /** Called only by the history sheet after its explicit permanent-deletion confirmation. */
    fun deleteConfirmed(id: String, success: () -> Unit, failure: (String) -> Unit) = command(id, "delete", JSONObject().put("confirm", true), success, failure)
    private fun command(id: String, kind: String, args: JSONObject, success: () -> Unit, failure: (String) -> Unit) {
        val request = transport.command(id, kind, args)
        if (request == null) failure("Not connected to your computer") else actions[request] = Action(id, success, failure)
    }
    private fun launch(id: String, title: String, kind: String, args: JSONObject) {
        if (launching) return
        launchId = id; launchTitle = title; launchRequest = transport.command(id, kind, args)
        if (launchRequest == null) { clearLaunch(); show("Not connected to your computer", true); return }
        launching = true; show(if (kind == "new") "Creating Pi tab…" else "Opening conversation in Orca…", false)
        transport.timeout(45000) { if (launchId == id) { clearLaunch(); show("Pi has not connected yet. Check Orca; no automatic retry.", true) } }
    }
    private fun clearLaunch() { launchId = null; launchRequest = null; launchTitle = ""; launching = false; transport.cancelTimeout() }
    override fun onConnectionState(state: ConnectionState, detail: String) {
        connection = state; connectionDetail = detail
        if (state in setOf(ConnectionState.CONNECTING, ConnectionState.CONNECTED)) {
            val endpoint = try { java.net.URI(detail) } catch (_: Exception) { null }
            if (endpoint?.scheme in setOf("https", "http") && endpoint?.host != null) connectionEndpoint = detail
        }
        if (active) effect(Effect.Usage(state == ConnectionState.CONNECTED))
        if (state == ConnectionState.CONNECTED && connectRequested && typedToken.isEmpty()) { screen = Screen.Catalog; connectRequested = false }
    }
    override fun onCatalog(catalog: Catalog) {
        this.catalog = catalog; rows = SessionGrouping.build(catalog)
        val id = launchId ?: return
        if (!active || catalog.findSession(id)?.connected() != true) return
        val title = launchTitle; clearLaunch(); effect(Effect.OpenChat(id, title))
    }
    override fun onSnapshot(snapshot: Snapshot) {}
    override fun onAck(ack: Ack) {
        val action = actions[ack.requestId()]
        if (action != null && action.session == ack.sessionId()) {
            actions.remove(ack.requestId()); if (ack.ok()) action.success() else action.failure(ack.error()); return
        }
        if (ack.requestId() == launchRequest && ack.sessionId() == launchId && !ack.ok()) { clearLaunch(); show(ack.error(), true) }
    }
    override fun onData(requestId: String, sessionId: String, data: JSONObject) {
        if (requestId == launchRequest && sessionId == launchId && data.optString("type") in listOf("resume", "new")) {
            show("Tab opened in Orca. Waiting for Pi…", false); onCatalog(transport.catalog())
        }
    }
    override fun onCommandUncertain(requestId: String, sessionId: String, reason: String) {
        val action = actions[requestId]
        if (action != null && action.session == sessionId) { actions.remove(requestId); action.failure("Result unknown. Refresh before retrying."); return }
        if (requestId == launchRequest && sessionId == launchId) { clearLaunch(); show("Launch result unknown. Check Orca; no automatic retry.", true) }
    }
    override fun onProtocolError(message: String) { show(message, true) }
    private fun show(text: String, error: Boolean) { message = text; messageError = error }
}
