package ru.billyhargrove.pimobile.core

import org.json.JSONObject

/** One presentation policy for both renderers during the bounded Compose migration. */
class TranscriptPresentation {
    data class Item(val row: WorkTimeline.Row, val tools: List<ChatMessage>, val expanded: Boolean)

    private var messages: List<ChatMessage> = emptyList()
    private val collapsed = mutableSetOf<String>()
    private val completed = mutableSetOf<String>()
    private val settled = mutableSetOf<String>()
    private var activeTurn = ""
    private var idleKnown = false
    private var items: List<Item> = emptyList()

    fun items(): List<Item> = items

    fun submit(source: List<ChatMessage>) {
        var fallback = "legacy"
        messages = source.map { message ->
            if (message.role() == ChatMessage.Role.USER) fallback = message.stableKey()
            if (message.turnId() == "legacy")
                message.withPresentation(fallback, message.phase(), message.preview(), message.documentPath())
            else message
        }
        rebuild()
    }

    fun metadata(frame: JSONObject) {
        if (frame.has("activeTurnId")) activeTurn = frame.optString("activeTurnId")
        if (frame.has("status")) idleKnown = frame.optString("status") in setOf("idle", "offline")
        val turns = frame.optJSONArray("turns")
        if (turns != null) for (index in 0 until turns.length()) {
            val turn = turns.optJSONObject(index) ?: continue
            if (turn.optLong("finishedAt") > 0) completed.add(turn.optString("id"))
        }
        rebuild()
    }

    fun sessionStatus(status: SessionStatus) {
        idleKnown = status == SessionStatus.IDLE || status == SessionStatus.OFFLINE
        rebuild()
    }

    fun toggle(group: String) {
        if (!collapsed.remove(group)) collapsed.add(group)
        rebuild()
    }

    fun reset() {
        messages = emptyList()
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
        val timeline = WorkTimeline.build(messages, emptySet())
        for (row in timeline) {
            // Settle only once: a subsequent snapshot must preserve a user's manual expansion.
            if (row.header && (idleKnown || row.turn in completed) && settled.add(row.group)) collapsed.add(row.group)
        }
        val tools = timeline.filter { it.work() && !it.header && !it.progress }.groupBy { it.group }
        items = timeline.filter { !it.work() || it.header || it.progress }.map { row ->
            Item(row, tools[row.group].orEmpty().mapNotNull { it.message }, row.group !in collapsed)
        }
    }
}
