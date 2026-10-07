package ru.billyhargrove.pimobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.platform.ComposeView
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import ru.billyhargrove.pimobile.features.orchestration.OrchestrationScreen
import ru.billyhargrove.pimobile.features.orchestration.OrchestrationSession
import ru.billyhargrove.pimobile.net.AppExecutors
import ru.billyhargrove.pimobile.ui.PiTheme
import ru.billyhargrove.pimobile.ui.SystemInsets

/** Android composition/navigation shell. Inspection has no command transport. */
class OrchestrationActivity : AppCompatActivity() {
    companion object {
        @JvmStatic fun intent(c: Context, session: String, kind: String, id: String, title: String): Intent =
            Intent(c, OrchestrationActivity::class.java).putExtra("session", session)
                .putExtra("workflow", if (kind == "workflow") id else "")
                .putExtra("agent", if (kind == "agent") id else "")
                .putExtra("title", title)
    }
    private val handler = Handler(Looper.getMainLooper())
    private val transcriptList = LazyListState()
    private lateinit var owner: OrchestrationSession

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val app = PiApp.get(this)
        val target = OrchestrationSession.Target(intent.getStringExtra("session").orEmpty(),
            intent.getStringExtra("workflow").orEmpty(), intent.getStringExtra("agent").orEmpty(),
            intent.getStringExtra("parentAgent"))
        val transport = object : OrchestrationSession.Transport {
            private var poll: Runnable? = null
            override fun available() = app.settings().hasToken()
            override fun fetch(target: OrchestrationSession.Target, before: Long, result: (Result<JSONObject>) -> Unit) {
                // Capture connection credentials before leaving the main thread; never expose them to feature state.
                val base = app.settings().baseUrl()
                val token = app.settings().token()
                AppExecutors.io().execute {
                    val value = runCatching {
                        if (target.agent.isEmpty()) app.api().fetchOrchestration(base, token, target.session)
                        else app.api().fetchAgent(base, token, target.session, target.agent, before)
                    }
                    AppExecutors.main { result(value) }
                }
            }
            override fun schedule(delayMs: Long, action: () -> Unit) {
                cancelPoll()
                poll = Runnable(action).also { handler.postDelayed(it, delayMs) }
            }
            override fun cancelPoll() { poll?.let(handler::removeCallbacks); poll = null }
        }
        owner = OrchestrationSession(target, transport) { !transcriptList.canScrollForward && !transcriptList.isScrollInProgress }
        val root = ComposeView(this).apply { setContent { PiTheme {
            OrchestrationScreen(owner, intent.getStringExtra("title").orEmpty(), transcriptList, app.mediaLoader(),
                onBack = { finish() },
                onChildren = { startActivity(intent(this@OrchestrationActivity, target.session, "", "", "Child agents").putExtra("parentAgent", target.agent)) },
                onWorkflow = { flow -> startActivity(intent(this@OrchestrationActivity, target.session, "workflow", flow.optString("id"), flow.optString("title", "Workflow"))) },
                onAgent = { agent -> startActivity(intent(this@OrchestrationActivity, target.session, "agent", agent.optString("id"), agent.optString("name", "Agent")).putExtra("agentSummary", agent.toString())) })
        } } }
        setContentView(root)
        // Compose owns the navigation-bar spacer; do not add a second bottom inset to the host View.
        SystemInsets.apply(this, root, null, null, true)
        if (target.agent.isNotEmpty() && intent.hasExtra("agentSummary")) try {
            owner.renderAgent(JSONObject().put("agent", JSONObject(intent.getStringExtra("agentSummary")!!)).put("messages", JSONArray()), false)
        } catch (_: JSONException) { }
    }
    override fun onStart() { super.onStart(); owner.start() }
    override fun onStop() { owner.stop(); super.onStop() }

    // Existing read-only fixture/facade entry points; no duplicate state or fetching policy.
    fun stopUpdates() = owner.stop()
    fun render(data: JSONObject) = owner.render(data)
    fun renderAgent(data: JSONObject, history: Boolean) = owner.renderAgent(data, history)
}
