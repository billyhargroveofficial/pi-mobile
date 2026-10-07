package ru.billyhargrove.pimobile.features.catalog

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ArchiveProjectionTest {
    private val zone = ZoneId.of("UTC"); private val today = LocalDate.of(2026, 10, 7)
    private fun row(id: String, modified: String = "2026-10-07T10:00:00Z") = JSONObject().put("id", id).put("title", id).put("modified", modified)
    @Test fun recurringDateAndUnknownGroupsHaveUniqueStableKeysAfterAppend() {
        val value = JSONObject().put("sessions", JSONArray().put(row("a")).put(row("b", "2026-10-06T10:00:00Z")).put(row("c"))
            .put(row("d", "bad")).put(row("e")).put(row("f", "bad")))
        val rows = ArchiveProjection.page(value, 0, zone).rows; val entries = ArchiveProjection.entries(rows, today)
        assertEquals(entries.size, entries.map { it.key }.distinct().size)
        assertEquals(listOf("Today", "Yesterday", "Today", "Earlier", "Today", "Earlier"), entries.filterIsInstance<ArchiveProjection.Entry.Header>().map { it.label })
        assertEquals(entries.take(6).map { it.key }, ArchiveProjection.entries(rows.take(3), today).map { it.key })
    }
    @Test fun parsingCopiesRowsFiltersMissingIdsAndDuplicateIdsAndUsesInjectedZone() {
        val source = row("a", "2026-10-06T23:30:00Z").put("messageCount", -1)
        val value = JSONObject().put("sessions", JSONArray().put(1).put(JSONObject()).put(source).put(row("a")))
        val page = ArchiveProjection.page(value, 0, ZoneId.of("Europe/Moscow")); source.put("title", "changed")
        assertEquals("a", page.rows.single().title); assertEquals("2026-10-07", page.rows.single().day); assertEquals(0, page.rows.single().messages)
    }
    @Test fun invalidOrNonAdvancingCursorCannotEnableMoreRequests() {
        for (cursor in listOf(-1, 20, 1.5, "bad", "Infinity", 4294967336L, JSONObject.NULL)) {
            val page = ArchiveProjection.page(JSONObject().put("nextOffset", cursor).put("hasMore", true), 40, zone)
            assertFalse("cursor=$cursor", page.hasMore); assertTrue(page.next >= 0)
        }
        val fallback = ArchiveProjection.page(JSONObject().put("hasMore", true), 40, zone)
        assertEquals(80, fallback.next); assertTrue(fallback.hasMore)
        assertFalse(ArchiveProjection.page(JSONObject().put("hasMore", true), Int.MAX_VALUE - 1, zone).hasMore)
    }
    @Test fun dateLabelsIncludeYearOnlyWhenNeeded() {
        val rows = ArchiveProjection.page(JSONObject().put("sessions", JSONArray().put(row("a", "2026-09-20T10:00:00Z")).put(row("b", "2025-09-20T10:00:00Z"))), 0, zone).rows
        assertEquals(listOf("20 September", "20 September 2025"), ArchiveProjection.entries(rows, today).filterIsInstance<ArchiveProjection.Entry.Header>().map { it.label })
    }
}
