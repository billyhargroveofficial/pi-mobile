package ru.billyhargrove.pimobile.core

import org.json.JSONObject
import java.util.Collections

/** Shared pure projection; source topology is independent of status, settlement and manual disclosure. */
class TranscriptPresentation {
    data class Item(val row: WorkTimeline.Row, val tools: List<ChatMessage>, val expanded: Boolean)

    private var source: List<ChatMessage> = emptyList()
    private var messages: List<ChatMessage> = emptyList()
    private var timeline: List<WorkTimeline.Row> = emptyList()
    private var visibleRows: List<WorkTimeline.Row> = emptyList()
    private var tools: Map<String, List<ChatMessage>> = emptyMap()
    private val collapsed = mutableSetOf<String>()
    private val completed = mutableSetOf<String>()
    private val settled = mutableSetOf<String>()
    private var activeTurn = ""
    private var idleKnown = false
    private var items: List<Item> = emptyList()

    fun items(): List<Item> = items

    fun submit(source: List<ChatMessage>) = update(source)
    fun metadata(frame: JSONObject) = update(source, metadata = frame)
    fun sessionStatus(status: SessionStatus) = update(source, status = status)

    /** Metadata applies first; an explicit snapshot/update status is authoritative for that publication. */
    fun update(source: List<ChatMessage>, status: SessionStatus? = null, metadata: JSONObject? = null) {
        var changed = false
        if (this.source != source) {
            this.source = source.toList()
            var fallback = "legacy"
            messages = this.source.map { message ->
                if (message.role() == ChatMessage.Role.USER) fallback = message.stableKey()
                if (message.turnId() == "legacy")
                    message.withPresentation(fallback, message.phase(), message.preview(), message.documentPath())
                else message
            }
            timeline = WorkTimeline.build(messages, emptySet())
            visibleRows = timeline.filter { !it.work() || it.header || it.progress }
            tools = timeline.filter { it.work() && !it.header && !it.progress }.groupBy { it.group }
                .mapValues { (_, rows) -> Collections.unmodifiableList(rows.mapNotNull { it.message }) }
            changed = true
        }
        if (metadata?.has("activeTurnId") == true) {
            val next = metadata.optString("activeTurnId")
            if (next != activeTurn) { activeTurn = next; changed = true }
        }
        val idle = when {
            status != null -> status == SessionStatus.IDLE || status == SessionStatus.OFFLINE
            metadata?.has("status") == true -> metadata.optString("status") in setOf("idle", "offline")
            else -> idleKnown
        }
        if (idle != idleKnown) { idleKnown = idle; changed = true }
        val turns = metadata?.optJSONArray("turns")
        if (turns != null) for (index in 0 until turns.length()) {
            val turn = turns.optJSONObject(index) ?: continue
            if (turn.optLong("finishedAt") > 0 && completed.add(turn.optString("id"))) changed = true
        }
        if (changed) rebuild()
    }

    fun toggle(group: String) {
        if (!collapsed.remove(group)) collapsed.add(group)
        rebuild()
    }

    fun reset() {
        source = emptyList()
        messages = emptyList()
        timeline = emptyList()
        visibleRows = emptyList()
        tools = emptyMap()
        collapsed.clear()
        completed.clear()
        settled.clear()
        activeTurn = ""
        idleKnown = false
        items = emptyList()
    }

    private fun rebuild() {
        for (message in messages) {
            if (message.role() == ChatMessage.Role.ASSISTANT && message.phase() != "work" && message.turnId() != activeTurn)
                completed.add(message.turnId())
        }
        for (row in timeline) {
            // Settle only once: a subsequent snapshot must preserve a user's manual expansion.
            if (row.header && (idleKnown || row.turn in completed) && settled.add(row.group)) collapsed.add(row.group)
        }
        val next = visibleRows.map { row ->
            Item(row, tools[row.group].orEmpty(), row.group !in collapsed)
        }
        if (next != items) items = Collections.unmodifiableList(next)
    }
}
