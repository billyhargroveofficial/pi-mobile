package ru.billyhargrove.pimobile.ui

import android.content.Context
import android.os.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import org.json.JSONObject
import java.text.DateFormat
import java.util.*
import kotlin.math.roundToInt
import ru.billyhargrove.pimobile.net.*

/** One real usage owner: lifecycle polling and three summaries of reported windows. */
class UsageCards(private val context: Context, private val api: HttpApi) {
    private val main = Handler(Looper.getMainLooper())
    private var base = ""; private var token = ""; private var active = false; private var busy = false; private var generation = 0
    private var value by mutableStateOf<JSONObject?>(null)
    private var visible by mutableStateOf(false)
    private var selected by mutableStateOf<String?>(null)
    private val tick = Runnable { refresh() }
    fun start(base: String, token: String) {
        if (active && this.base == base && this.token == token) return
        stop(); this.base = base; this.token = token; active = base.isNotEmpty() && token.isNotEmpty()
        if (active) { render(null); refresh() }
    }
    fun stop() { active = false; generation++; busy = false; main.removeCallbacks(tick); selected = null; visible = false; value = null }
    fun refresh() {
        if (!active || busy) return
        main.removeCallbacks(tick); busy = true
        val version = generation; val url = base; val secret = token
        AppExecutors.io().execute {
            val result = try { api.fetchUsage(url, secret) } catch (_: Exception) { null }
            main.post { if (active && generation == version) { busy = false; render(result); main.postDelayed(tick, 60000) } }
        }
    }
    /** Deterministic preview uses the same schema and renderer as authenticated data. */
    fun render(value: JSONObject?) { this.value = value; visible = true }
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable fun Content(modifier: Modifier = Modifier) {
        if (!visible) return
        val large = LocalDensity.current.fontScale > 1.25f
        Column(modifier.fillMaxWidth().testTag("${context.packageName}:id/usageCards").semantics { testTagsAsResourceId = true }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row { Text("Usage", Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("Tap for details", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row((if (large) Modifier.horizontalScroll(rememberScrollState()) else Modifier.fillMaxWidth())
                .height(IntrinsicSize.Min).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))) {
                for (provider in listOf("codex", "cursor", "grok")) {
                    val data = provider(provider); val window = primary(data); val used = window?.optDouble("usedPercent")
                    val name = name(provider); val id = "usage" + name
                    val stale = window != null && (timestamp(data?.optLong("updatedAt") ?: 0) <= 0 ||
                        System.currentTimeMillis() - timestamp(data?.optLong("updatedAt") ?: 0) > 15 * 60 * 1000 || data?.optString("status") != "ok")
                    val reset = if (stale) "Cached · tap to check" else window?.let(::resetLabel) ?: "Check in Orca"
                    Column((if (large) Modifier.width((156 * LocalDensity.current.fontScale / 1.35f).dp) else Modifier.weight(1f))
                        .fillMaxHeight().testTag("${context.packageName}:id/$id").semantics {
                            testTagsAsResourceId = true
                            contentDescription = name + if (used != null) ", ${used.roundToInt()} percent used, ${windowName(window)}, $reset. Show details" else ", usage unavailable. Show details"
                        }.clickable { selected = provider }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(name, fontSize = 13.sp)
                        Text(if (used != null) "${used.roundToInt()}%" else "—", Modifier.padding(top = 8.dp), fontSize = 28.sp, color = usageColor(used ?: 0.0))
                        Text(window?.let { windowName(it) + " used" } ?: "Unavailable", fontSize = 11.sp, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (used != null) Meter(used) else Spacer(Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Text(reset, fontSize = 10.sp, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        selected?.let { provider ->
            val data = provider(provider)
            ModalBottomSheet(onDismissRequest = { selected = null }) {
                Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(name(provider) + " usage", fontSize = 24.sp)
                    Text("Reported by your connected Orca", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val windows = windows(data).filter { it.optDouble("usedPercent", Double.NaN).isFinite() }
                    if (windows.isEmpty()) Text("Orca has not provided usage limits for this account. Open Orca to check the account connection.", fontSize = 15.sp)
                    windows.forEach { window ->
                        val used = window.optDouble("usedPercent")
                        Row { Text(windowName(window), Modifier.weight(1f), fontSize = 15.sp); Text("${used.roundToInt()}% used", fontSize = 15.sp, color = usageColor(used)) }
                        Meter(used)
                        val reset = timestamp(window.optLong("resetsAt"))
                        Text(if (reset > 0) "Resets " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.ENGLISH).format(Date(reset)) else "Reset time not reported", fontSize = 12.sp)
                    }
                    val updated = timestamp(data?.optLong("updatedAt") ?: 0)
                    if (updated > 0) Text("Updated " + DateFormat.getTimeInstance(DateFormat.SHORT, Locale.ENGLISH).format(Date(updated)), fontSize = 12.sp)
                }
            }
        }
    }
    @Composable private fun Meter(used: Double) = LinearProgressIndicator(progress = { (used / 100).toFloat().coerceIn(0f, 1f) }, color = usageColor(used),
        trackColor = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.fillMaxWidth().height(4.dp).clearAndSetSemantics {})
    @Composable private fun usageColor(used: Double) = when { used >= 95 -> MaterialTheme.colorScheme.error; used >= 75 -> androidx.compose.ui.res.colorResource(ru.billyhargrove.pimobile.R.color.warning); else -> MaterialTheme.colorScheme.onSurface }
    private fun provider(name: String): JSONObject? { val array = value?.optJSONArray("providers"); return (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) }.firstOrNull { it.optString("provider") == name } }
    private fun windows(data: JSONObject?) = data?.optJSONArray("windows").let { array -> (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) } }
    private fun primary(data: JSONObject?) = windows(data).filter { val used = it.optDouble("usedPercent", Double.NaN); used.isFinite() && used >= 0 }.maxByOrNull { it.optDouble("usedPercent") }
    private fun name(provider: String) = provider.replaceFirstChar { it.titlecase(Locale.ENGLISH) }
    private fun windowName(window: JSONObject?): String {
        val name = window?.optString("name", "Usage") ?: return "Usage"
        return when (name) { "session" -> { val minutes = window.optLong("windowMinutes"); if (minutes > 0 && minutes % 60 == 0L) "${minutes / 60}-hour" else "Session" }
            "weekly" -> "Weekly"; "monthly" -> "Monthly"; else -> name }
    }
    private fun timestamp(value: Long) = if (value in 1 until 100000000000L) value * 1000 else value
    private fun resetLabel(window: JSONObject): String {
        val reset = timestamp(window.optLong("resetsAt")); if (reset <= 0) return "Reset not reported"
        val minutes = maxOf(0, (reset - System.currentTimeMillis() + 59999) / 60000)
        return when { minutes == 0L -> "Reset due"; minutes >= 1440 -> "Resets in ${(minutes + 1439) / 1440}d"; minutes >= 60 -> "Resets in ${(minutes + 59) / 60}h"; else -> "Resets in ${minutes}m" }
    }
}
