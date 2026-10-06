package ru.billyhargrove.pimobile

import androidx.activity.compose.setContent
import org.json.JSONObject
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.features.catalog.*
import ru.billyhargrove.pimobile.ui.*

/** Actual catalog screen with a credential-free synthetic command owner. */
object CatalogFixture {
    class Transport(var value: Catalog) : CatalogSession.Transport {
        var commands = 0
        override fun catalog() = value
        override fun hasToken() = true
        override fun connect(url: String, typedToken: String) {}
        override fun disconnect() {}
        override fun health(url: String, result: (Result<String>) -> Unit) { result(Result.success("")) }
        override fun refresh(result: (Result<Catalog>) -> Unit) { result(Result.success(value)) }
        override fun command(session: String, kind: String, args: JSONObject): String { commands++; return "catalog-$commands" }
        override fun timeout(delay: Long, action: () -> Unit) {}
        override fun cancelTimeout() {}
    }
    @JvmStatic fun install(activity: MainActivity, catalog: Catalog): CatalogSession {
        PiApp.get(activity).client().clearListener(activity)
        PiApp.get(activity).client().disconnect()
        val state = CatalogSession(Transport(catalog), "https://example.invalid", true, {})
        val field = MainActivity::class.java.getDeclaredField("catalogState").apply { isAccessible = true }; field.set(activity, state)
        val usage = usage(activity); usage.stop()
        state.start(); state.onConnectionState(ConnectionState.CONNECTED, "Synthetic preview")
        activity.setContent { PiTheme { CatalogScreen(state, usage, {}, { BubbleColors.show(activity) }) } }
        return state
    }
    @JvmStatic fun usage(activity: MainActivity): UsageCards = MainActivity::class.java.getDeclaredField("usageCards")
        .apply { isAccessible = true }.get(activity) as UsageCards
}
