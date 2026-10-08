package ru.billyhargrove.pimobile.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import org.json.JSONObject
import ru.billyhargrove.pimobile.core.ActiveWork
import ru.billyhargrove.pimobile.features.orchestration.ActiveWorkScreen
import ru.billyhargrove.pimobile.net.AppExecutors

/** Lifecycle discovery with an injected read-only fetcher; no app shell or navigation dependency. */
class OrchestrationEntry(private val fetch: () -> JSONObject?) {
    var snapshot by mutableStateOf<JSONObject?>(null); private set
    private val handler = Handler(Looper.getMainLooper())
    private var live = false
    private var busy = false
    private var generation = 0
    private val poll = Runnable { refresh() }
    fun start() { live = true; snapshot = null; refresh() }
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
                    if (result != null) render(result) else snapshot = null
                    handler.postDelayed(poll, 3000)
                }
            }
        }
    }
    fun render(data: JSONObject) { snapshot = data }
}

/** Compatibility facade; callers may share the copied snapshot with other chat surfaces. */
@Composable
fun ActiveOrchestration(data: JSONObject, open: (String, String, String) -> Unit, compact: Boolean = false) {
    ActiveOrchestration(remember(data) { ActiveWork.project(data) }, open, compact)
}

@Composable
fun ActiveOrchestration(snapshot: ActiveWork.Snapshot, open: (String, String, String) -> Unit, compact: Boolean = false) {
    ActiveWorkScreen(snapshot, open, compact)
}
