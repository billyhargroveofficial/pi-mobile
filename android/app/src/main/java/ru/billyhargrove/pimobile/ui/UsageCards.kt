package ru.billyhargrove.pimobile.ui

import android.content.Context
import android.os.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.*
import org.json.JSONObject
import java.text.DateFormat
import java.util.*
import kotlin.math.roundToInt
import ru.billyhargrove.pimobile.R
import ru.billyhargrove.pimobile.net.*

/** One real usage owner: lifecycle polling, compact summary, accessible unified details. */
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
        val providers = listOf("codex", "cursor", "grok")
        val prefix = "${context.packageName}:id/"
        // Three compact INFORMATION lines, one large accessible action. We do not
        // turn 28dp labels into three undersized, overlapping touch targets.
        Column(modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(prefix + "usageCards").semantics {
            testTagsAsResourceId = true; role = Role.Button
            contentDescription = "Usage limits. Show all provider details"
            customActions = providers.map { key -> CustomAccessibilityAction("Show ${name(key)} usage") { this@UsageCards.selected = key; true } }
        }.clickable { selected = "all" }.padding(vertical = 4.dp)) {
            for (provider in providers) {
                val data = provider(provider); val window = primary(data); val used = window?.optDouble("usedPercent")
                val stale = window != null && (timestamp(data?.optLong("updatedAt") ?: 0) <= 0 ||
                    System.currentTimeMillis() - timestamp(data?.optLong("updatedAt") ?: 0) > 15 * 60 * 1000 || data?.optString("status") != "ok")
                val reported = reported(data)
                val ink = MaterialTheme.colorScheme.onSurfaceVariant
                val summary = buildAnnotatedString {
                    if (used == null) append("Unavailable · check in Orca")
                    else {
                        reported.take(3).forEachIndexed { index, quota ->
                            if (index > 0) append(" · ")
                            val percent = quota.optDouble("usedPercent")
                            withStyle(SpanStyle(color = if (stale) ink else usageColor(percent))) { append("${percent.roundToInt()}%") }
                            append(" ${shortWindow(quota)}")
                        }
                        if (reported.size > 3) append(" · +${reported.size - 3} limits")
                        append(" · ")
                        append(if (stale) "cached" else resetLabel(window!!).removePrefix("Resets in ").replace("Reset not reported", "reset unknown"))
                    }
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 28.dp).testTag(prefix + "usage" + name(provider))
                    .semantics { testTagsAsResourceId = true; contentDescription = "${name(provider)}, $summary" },
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProviderIcon(provider)
                    Box(Modifier.width(40.dp)) {
                        if (used != null) Meter(used)
                        else Spacer(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    }
                    Text(summary, Modifier.weight(1f).padding(vertical = 3.dp), style = MaterialTheme.typography.bodySmall, color = ink)
                }
            }
        }
        selected?.let { choice ->
            ModalBottomSheet(onDismissRequest = { selected = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).navigationBarsPadding().verticalScroll(rememberScrollState())
                    .padding(20.dp).testTag(prefix + "usageDetails").semantics { testTagsAsResourceId = true }, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("Usage", style = MaterialTheme.typography.titleLarge)
                    Text("Reported by your connected Orca", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (provider in if (choice == "all") providers else listOf(choice)) {
                        val data = provider(provider)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ProviderIcon(provider)
                                Text(name(provider) + " usage", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
                            }
                            val quotas = reported(data)
                            if (quotas.isEmpty()) Text("Orca has not provided usage limits for this account. Open Orca to check the account connection.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            quotas.forEach { quota ->
                                val used = quota.optDouble("usedPercent")
                                Row { Text(windowName(quota), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text("${used.roundToInt()}% used", style = MaterialTheme.typography.bodyMedium, color = usageColor(used)) }
                                Meter(used)
                                val reset = timestamp(quota.optLong("resetsAt"))
                                Text(if (reset > 0) "Resets " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.ENGLISH).format(Date(reset)) else "Reset time not reported",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val updated = timestamp(data?.optLong("updatedAt") ?: 0)
                            if (updated > 0) Text("Updated " + DateFormat.getTimeInstance(DateFormat.SHORT, Locale.ENGLISH).format(Date(updated)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    @Composable private fun ProviderIcon(provider: String) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            val codex = provider == "codex"
            Icon(painterResource(when (provider) { "codex" -> R.drawable.ic_provider_codex; "cursor" -> R.drawable.ic_provider_cursor; else -> R.drawable.ic_provider_grok }),
                null, Modifier.size(if (codex) 28.dp else 14.dp), tint = if (codex) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    @Composable private fun Meter(used: Double) {
        val fraction = (used / 100).toFloat().coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp))
            .background(MaterialTheme.colorScheme.outlineVariant).clearAndSetSemantics {}) {
            if (fraction > 0f) Spacer(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(usageColor(used), RoundedCornerShape(1.dp)))
        }
    }
    @Composable private fun usageColor(used: Double) = when { used >= 95 -> MaterialTheme.colorScheme.error; used >= 75 -> androidx.compose.ui.res.colorResource(R.color.warning); else -> MaterialTheme.colorScheme.onSurface }
    private fun provider(name: String): JSONObject? { val array = value?.optJSONArray("providers"); return (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) }.firstOrNull { it.optString("provider") == name } }
    private fun windows(data: JSONObject?) = data?.optJSONArray("windows").let { array -> (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) } }
    private fun reported(data: JSONObject?) = windows(data).filter { it.optDouble("usedPercent", Double.NaN).let { used -> used.isFinite() && used >= 0 } }
    private fun primary(data: JSONObject?) = reported(data).maxByOrNull { it.optDouble("usedPercent") }
    private fun name(provider: String) = provider.replaceFirstChar { it.titlecase(Locale.ENGLISH) }
    private fun shortWindow(window: JSONObject) = when (window.optString("name")) {
        "weekly" -> "week"; "monthly" -> "month"; "Cursor Models" -> "models"; "Other Models" -> "other"; else -> windowName(window)
    }
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
