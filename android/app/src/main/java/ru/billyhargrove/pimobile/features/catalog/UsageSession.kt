package ru.billyhargrove.pimobile.features.catalog

import androidx.compose.runtime.*
import org.json.JSONObject

/** Main-thread state owner; HTTP, timer and clock are injected platform ports. */
internal class UsageSession(private val transport: Transport, private val now: () -> Long) {
    interface Transport {
        fun fetch(base: String, token: String, result: (Result<JSONObject>) -> Unit)
        fun schedule(delayMs: Long, action: () -> Unit)
        fun cancelPoll()
    }
    var providers by mutableStateOf(UsageProjection.project(null, now())); private set
    var visible by mutableStateOf(false); private set
    var selected by mutableStateOf<String?>(null); private set
    var busy by mutableStateOf(false); private set
    private var base = ""; private var token = ""; private var active = false
    private var request: Any? = null; private var poll: Any? = null
    fun start(base: String, token: String) {
        if (active && this.base == base && this.token == token) return
        stop(); this.base = base; this.token = token; active = base.isNotEmpty() && token.isNotEmpty()
        if (active) { render(null); refresh() }
    }
    fun stop() {
        active = false; request = null; busy = false; cancelPoll(); base = ""; token = ""
        selected = null; visible = false; providers = UsageProjection.project(null, now())
    }
    fun refresh() {
        if (!active || busy) return
        cancelPoll(); busy = true
        val ticket = Any(); request = ticket
        val complete: (Result<JSONObject>) -> Unit = complete@ { result ->
            if (!active || request !== ticket) return@complete
            request = null; busy = false; render(result.getOrNull())
            val next = Any(); poll = next
            transport.schedule(60000) { if (active && poll === next) { poll = null; refresh() } }
        }
        try { transport.fetch(base, token, complete) } catch (cause: Exception) { complete(Result.failure(cause)) }
    }
    fun render(value: JSONObject?) { providers = UsageProjection.project(value, now()); visible = true }
    fun select(key: String?) { selected = key?.takeIf { it == "all" || providers.any { provider -> provider.key == it } } }
    private fun cancelPoll() { poll = null; transport.cancelPoll() }
}
