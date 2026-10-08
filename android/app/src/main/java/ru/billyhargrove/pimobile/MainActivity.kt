package ru.billyhargrove.pimobile

import android.os.*
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import org.json.JSONObject
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.features.catalog.*
import ru.billyhargrove.pimobile.net.*
import ru.billyhargrove.pimobile.ui.*

/** Composition/lifecycle only: catalog state and screen have one feature owner. */
class MainActivity : AppCompatActivity(), PiClient.Listener {
    private lateinit var app: PiApp
    private lateinit var catalogState: CatalogSession
    private lateinit var usageCards: UsageCards
    private lateinit var updates: AppUpdates
    private var archiveSheet: ArchiveSheet? = null
    private val timer = Handler(Looper.getMainLooper())
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        app = PiApp.get(this); usageCards = UsageCards(this, app.api()); updates = AppUpdates(this)
        val settings = app.settings(); val client = app.client()
        catalogState = CatalogSession(object : CatalogSession.Transport {
            override fun catalog() = client.catalog()
            override fun hasToken() = settings.hasToken()
            override fun hasTokenFor(url: String) = settings.hasTokenFor(url)
            override fun computers() = settings.profiles()
            override fun activeComputerId() = settings.activeProfileId()
            override fun saveComputer(url: String, name: String, token: String) = settings.saveComputer(url, name, token)
            override fun selectComputer(id: String) = settings.selectComputer(id)
            override fun forgetComputer(id: String) = settings.forgetComputer(id)
            override fun connect(url: String, typedToken: String) {
                // Credentials were atomically selected/saved by the profile port, never borrowed from another address.
                val token = settings.token()
                if (token.isEmpty() || EndpointPolicy.normalize(url) != EndpointPolicy.normalize(settings.baseUrl())) {
                    client.disconnect(); error("Enter an access token for this computer")
                }
                try { client.connect(settings.baseUrl(), token) }
                catch (_: Exception) { client.disconnect(); error("Could not connect to this computer") }
            }
            override fun disconnect() = client.disconnect()
            override fun health(url: String, result: (Result<String>) -> Unit) {
                AppExecutors.io().execute { val response = runCatching { app.api().health(url) }; AppExecutors.main { result(response) } }
            }
            override fun refresh(result: (Result<Catalog>) -> Unit) {
                val url = settings.baseUrl(); val token = settings.token()
                AppExecutors.io().execute { val response = runCatching { app.api().fetchCatalog(url, token) }; AppExecutors.main { result(response) } }
            }
            override fun command(session: String, kind: String, args: JSONObject) = client.readCommand(session, kind, args)
            override fun timeout(delay: Long, action: () -> Unit) { timer.removeCallbacksAndMessages(null); timer.postDelayed(action, delay) }
            override fun cancelTimeout() { timer.removeCallbacksAndMessages(null) }
        }, settings.baseUrl(), settings.hasConnection(), ::effect)
        state?.getString("catalog_screen")?.let { screen -> CatalogSession.Screen.entries.find { it.name == screen }?.let { catalogState.screen = it } }
        if (intent.getBooleanExtra("open_settings", false)) catalogState.screen = CatalogSession.Screen.Settings
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { PiTheme { CatalogScreen(catalogState, usageCards, { updates.check(true) }, { BubbleColors.show(this) }, updates.status) } }
    }
    override fun onSaveInstanceState(state: Bundle) { state.putString("catalog_screen", catalogState.screen.name); super.onSaveInstanceState(state) }
    override fun onStart() {
        super.onStart(); catalogState.start(); app.client().setListener(this); updates.check(false)
        if (app.client().state() == ConnectionState.IDLE && app.settings().hasConnection()) app.client().connect(app.settings().baseUrl(), app.settings().token())
        if (intent.getBooleanExtra("open_history", false)) { intent.removeExtra("open_history"); catalogState.history() }
    }
    override fun onResume() { super.onResume(); if (::updates.isInitialized) updates.resume() }
    override fun onStop() { app.client().clearListener(this); catalogState.stop(); super.onStop() }
    override fun onDestroy() { archiveSheet?.dismiss(); updates.close(); catalogState.close(); super.onDestroy() }
    private fun effect(effect: CatalogSession.Effect) {
        when (effect) {
            is CatalogSession.Effect.OpenChat -> startActivity(ChatActivity.intent(this, effect.id, effect.title, false))
            CatalogSession.Effect.ComputerChanged -> { archiveSheet?.dismiss(); archiveSheet = null; app.mediaLoader().clearCache() }
            is CatalogSession.Effect.Usage -> if (effect.connected) usageCards.start(app.settings().baseUrl(), app.settings().token()) else usageCards.stop()
            CatalogSession.Effect.OpenHistory -> {
                archiveSheet?.dismiss()
                archiveSheet = ArchiveSheet(this, app, { id, title -> archiveSheet?.dismiss(); catalogState.requestResume(id, title) },
                    { id, success, failure -> catalogState.deleteConfirmed(id, success::run, failure::accept) }).apply { show() }
            }
        }
    }
    override fun onConnectionState(state: ConnectionState, detail: String) = catalogState.onConnectionState(state, detail)
    override fun onCatalog(catalog: Catalog) = catalogState.onCatalog(catalog)
    override fun onSnapshot(snapshot: Snapshot) = catalogState.onSnapshot(snapshot)
    override fun onAck(ack: Ack) = catalogState.onAck(ack)
    override fun onData(requestId: String, sessionId: String, data: JSONObject) = catalogState.onData(requestId, sessionId, data)
    override fun onCommandUncertain(requestId: String, sessionId: String, reason: String) = catalogState.onCommandUncertain(requestId, sessionId, reason)
    override fun onProtocolError(message: String) = catalogState.onProtocolError(message)
}
