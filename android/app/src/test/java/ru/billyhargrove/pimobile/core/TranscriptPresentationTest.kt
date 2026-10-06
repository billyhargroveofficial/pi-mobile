package ru.billyhargrove.pimobile.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TranscriptPresentationTest {
    private fun message(id: String, role: ChatMessage.Role, phase: String = "work", turn: String = "turn") =
        ChatMessage.remote(id, role, id, null, if (role == ChatMessage.Role.TOOL_RESULT) "read" else null)
            .withPresentation(turn, phase, "{}", "")

    @Test fun independentToolsSettleOnceAndProgressAlwaysRemainsVisible() {
        val model = TranscriptPresentation()
        model.metadata(JSONObject().put("activeTurnId", "turn"))
        model.submit(listOf(message("u", ChatMessage.Role.USER), message("t1", ChatMessage.Role.TOOL_RESULT),
            message("p", ChatMessage.Role.ASSISTANT), message("t2", ChatMessage.Role.TOOL_RESULT)))
        assertEquals(listOf("u", "work-header:tools:turn:t1", "progress:p", "work-header:tools:turn:t2"), model.items().map { it.row.key })
        assertTrue(model.items().filter { it.row.header }.all { it.expanded })
        val done = JSONObject().put("status", "idle").put("activeTurnId", "")
        model.metadata(done)
        val headers = model.items().filter { it.row.header }
        assertTrue(headers.all { !it.expanded })
        model.toggle(headers.first().row.group)
        model.metadata(done)
        assertTrue(model.items()[1].expanded)
        assertFalse(model.items().last().expanded)
        assertEquals("p", model.items()[2].row.message.id())
    }

    @Test fun prependAndCumulativeToolUpdatesKeepStableKeysAndLiveBodies() {
        val model = TranscriptPresentation()
        val user = message("u", ChatMessage.Role.USER)
        val tool = message("t", ChatMessage.Role.TOOL_RESULT)
        model.submit(listOf(user, tool))
        val group = model.items().last().row.group
        model.toggle(group)
        val changed = ChatMessage.remote("t", ChatMessage.Role.TOOL_RESULT, "Cumulative output", null, "read")
            .withPresentation("turn", "work", "path", "")
        model.submit(listOf(message("older", ChatMessage.Role.USER, turn = "old"), user, changed))
        assertEquals(group, model.items().last().row.group)
        assertFalse(model.items().last().expanded)
        assertEquals("Cumulative output", model.items().last().tools.single().text())
    }

    @Test fun legacyTurnsAndSourceResetDoNotLeakDisclosureState() {
        val model = TranscriptPresentation()
        val user = ChatMessage.remote("u", ChatMessage.Role.USER, "Prompt", null, null)
        val tool = ChatMessage.remote("t", ChatMessage.Role.TOOL_RESULT, "Output", null, "read")
        model.sessionStatus(SessionStatus.IDLE)
        model.submit(listOf(user, tool))
        assertEquals("u", model.items().last().row.turn)
        assertFalse(model.items().last().expanded)
        model.toggle(model.items().last().row.group)
        model.reset()
        model.sessionStatus(SessionStatus.IDLE)
        model.submit(listOf(user, tool))
        assertFalse(model.items().last().expanded)
    }
}
