package ru.billyhargrove.pimobile.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.json.JSONObject
import ru.billyhargrove.pimobile.features.catalog.UsageSession
import ru.billyhargrove.pimobile.features.catalog.UsageScreen
import ru.billyhargrove.pimobile.net.AppExecutors
import ru.billyhargrove.pimobile.net.HttpApi

/** Platform adapter for the catalog usage owner and its production screen. */
class UsageCards(private val context: Context, api: HttpApi) {
    private val main = Handler(Looper.getMainLooper())
    private var tick: Runnable? = null
    private val owner = UsageSession(object : UsageSession.Transport {
        override fun fetch(base: String, token: String, result: (Result<JSONObject>) -> Unit) {
            AppExecutors.io().execute {
                val value = try { Result.success(api.fetchUsage(base, token)) } catch (cause: Exception) { Result.failure(cause) }
                AppExecutors.main { result(value) }
            }
        }
        override fun schedule(delayMs: Long, action: () -> Unit) {
            cancelPoll(); tick = Runnable { action() }.also { main.postDelayed(it, delayMs) }
        }
        override fun cancelPoll() { tick?.let(main::removeCallbacks); tick = null }
    }, System::currentTimeMillis)
    fun start(base: String, token: String) = owner.start(base, token)
    fun stop() = owner.stop()
    fun refresh() = owner.refresh()
    /** Deterministic preview uses the same projection and screen as authenticated data. */
    fun render(value: JSONObject?) = owner.render(value)
    @Composable fun Content(modifier: Modifier = Modifier) = UsageScreen(owner, "${context.packageName}:id/", modifier)
}
