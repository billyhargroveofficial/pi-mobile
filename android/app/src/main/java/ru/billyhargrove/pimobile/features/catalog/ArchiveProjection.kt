package ru.billyhargrove.pimobile.features.catalog

import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Immutable rows and stable list entries independent of Compose and Android. */
internal object ArchiveProjection {
    data class Row(val id: String, val title: String, val workspace: String, val messages: Int, val day: String)
    data class Page(val rows: List<Row>, val next: Int, val hasMore: Boolean)
    sealed interface Entry {
        val key: String
        data class Header(override val key: String, val label: String) : Entry
        data class Session(val row: Row) : Entry { override val key = "session:${row.id}" }
    }
    fun page(value: JSONObject, start: Int, zone: ZoneId): Page {
        val array = value.optJSONArray("sessions")
        val rows = (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) }
            .filter { it.optString("id").isNotEmpty() }.distinctBy { it.optString("id") }.map { row ->
                val day = try { Instant.parse(row.optString("modified")).atZone(zone).toLocalDate().toString() } catch (_: Exception) { "" }
                Row(row.optString("id"), row.optString("title"), row.optString("workspaceName"), maxOf(0, row.optInt("messageCount")), day)
            }
        val raw = if (value.has("nextOffset")) value.optDouble("nextOffset", Double.NaN) else start.toDouble() + 40
        val next = raw.takeIf { it.isFinite() && it >= 0 && it <= Int.MAX_VALUE && it % 1 == 0.0 }?.toInt() ?: start
        return Page(rows, next, value.optBoolean("hasMore") && next > start)
    }
    fun entries(rows: List<Row>, today: LocalDate): List<Entry> = buildList {
        var prior: String? = null
        rows.forEach { row ->
            if (row.day != prior) {
                // The first row disambiguates non-contiguous runs of the same date.
                add(Entry.Header("date:${row.day}:${row.id}", dateLabel(row.day, today))); prior = row.day
            }
            add(Entry.Session(row))
        }
    }
    private fun dateLabel(value: String, today: LocalDate) = try {
        val day = LocalDate.parse(value)
        when (day) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> day.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "d MMMM" else "d MMMM yyyy", Locale.ENGLISH)) }
    } catch (_: Exception) { "Earlier" }
}
