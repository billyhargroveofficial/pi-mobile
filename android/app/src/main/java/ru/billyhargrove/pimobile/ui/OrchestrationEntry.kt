package ru.billyhargrove.pimobile.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import org.json.JSONObject
import ru.billyhargrove.pimobile.core.OrchestrationData
import ru.billyhargrove.pimobile.net.AppExecutors

/** Lifecycle discovery with an injected read-only fetcher; no app shell or navigation dependency. */
class OrchestrationEntry(private val fetch: () -> JSONObject?) {
    var summary by mutableStateOf<String?>(null); private set
    private val handler = Handler(Looper.getMainLooper())
    private var live = false
    private var busy = false
    private var generation = 0
    private val poll = Runnable { refresh() }
    fun start() { live = true; refresh() }
    fun stop() { live = false; generation++; busy = false; handler.removeCallbacks(poll) }
    private fun refresh() {
        if (!live || busy) return
        busy = true
        val expected = generation
        AppExecutors.io().execute {
            val result = try { fetch() } catch (_: Exception) { null }
            AppExecutors.main {
                if (live && expected == generation) {
                    busy = false
                    if (result != null) render(result) else if (summary != null) summary = "Orchestration · reconnecting"
                    handler.postDelayed(poll, 3000)
                }
            }
        }
    }
    fun render(data: JSONObject) {
        summary = if (OrchestrationData.agents(data, null).isNotEmpty() || OrchestrationData.objects(data.optJSONArray("workflows")).isNotEmpty())
            OrchestrationData.summary(data) else null
    }
}
