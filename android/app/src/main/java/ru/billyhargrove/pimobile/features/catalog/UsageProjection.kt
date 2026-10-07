package ru.billyhargrove.pimobile.features.catalog

import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Copies the reported schema once; UI never rescans or retains mutable JSON. */
internal object UsageProjection {
    data class Quota(val name: String, val shortName: String, val used: Double, val reset: String)
    data class Provider(val key: String, val name: String, val quotas: List<Quota>, val primary: Quota?,
        val stale: Boolean, val resetSummary: String, val updated: String)
    private val keys = listOf("codex", "cursor", "grok")
    fun project(value: JSONObject?, now: Long, zone: TimeZone = TimeZone.getDefault()): List<Provider> {
        val time = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.ENGLISH).apply { timeZone = zone }
        val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.ENGLISH).apply { timeZone = zone }
        val array = value?.optJSONArray("providers")
        val providers = (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) }
            .distinctBy { it.optString("provider") }.associateBy { it.optString("provider") }
        return keys.map { key ->
            val data = providers[key]; val windows = data?.optJSONArray("windows")
            val reported = (0 until (windows?.length() ?: 0)).mapNotNull { windows?.optJSONObject(it) }
                .filter { it.optDouble("usedPercent", Double.NaN).let { used -> used.isFinite() && used >= 0 } }
            val quotas = reported.map { window ->
                val reset = timestamp(window.optLong("resetsAt"))
                Quota(windowName(window), shortWindow(window), window.optDouble("usedPercent"),
                    if (reset > 0) "Resets ${date.format(Date(reset))}" else "Reset time not reported")
            }
            val primaryIndex = reported.indices.maxByOrNull { reported[it].optDouble("usedPercent") }
            val updated = timestamp(data?.optLong("updatedAt") ?: 0)
            Provider(key, key.replaceFirstChar { it.titlecase(Locale.ENGLISH) }, quotas, primaryIndex?.let { quotas[it] },
                updated <= 0 || (now > updated && now - updated > 15 * 60 * 1000) || data?.optString("status") != "ok",
                primaryIndex?.let { resetSummary(timestamp(reported[it].optLong("resetsAt")), now) }.orEmpty(),
                if (updated > 0) "Updated ${time.format(Date(updated))}" else "")
        }
    }
    private fun windowName(window: JSONObject): String = when (val name = window.optString("name", "Usage")) {
        "session" -> window.optLong("windowMinutes").let { minutes -> if (minutes > 0 && minutes % 60 == 0L) "${minutes / 60}-hour" else "Session" }
        "weekly" -> "Weekly"; "monthly" -> "Monthly"; else -> name
    }
    private fun shortWindow(window: JSONObject): String = when (window.optString("name")) {
        "weekly" -> "week"; "monthly" -> "month"; "Cursor Models" -> "models"; "Other Models" -> "other"; else -> windowName(window)
    }
    private fun timestamp(value: Long) = if (value in 1 until 100000000000L) value * 1000 else value
    private fun resetSummary(reset: Long, now: Long): String {
        if (reset <= 0) return "reset unknown"
        val delta = if (reset > now) reset - now else 0
        // Ceiling division without overflowing a timestamp near Long.MAX_VALUE.
        val minutes = delta / 60000 + if (delta % 60000 > 0) 1 else 0
        return when { minutes == 0L -> "Reset due"; minutes >= 1440 -> "${(minutes + 1439) / 1440}d"; minutes >= 60 -> "${(minutes + 59) / 60}h"; else -> "${minutes}m" }
    }
}
