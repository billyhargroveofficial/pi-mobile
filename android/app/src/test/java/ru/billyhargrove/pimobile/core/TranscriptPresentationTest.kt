package ru.billyhargrove.pimobile.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TranscriptPresentationTest {
    private fun message(id: String, role: ChatMessage.Role, phase: String = "work", turn: String = "turn") =
        ChatMessage.remote(id, role, id, null, if (role == ChatMessage.Role.TOOL_RESULT) "read" else null)
            .withPresentation(turn, phase, "{}", "")

    @Test fun repeatedEqualSourceStatusAndIrrelevantMetadataKeepTheProjectionInstances() {
        val model = TranscriptPresentation(); val source = listOf(message("u", ChatMessage.Role.USER), message("t", ChatMessage.Role.TOOL_RESULT))
        model.update(source, SessionStatus.RUNNING, JSONObject().put("activeTurnId", "turn"))
        val before = model.items()
        repeat(100) {
            model.submit(source.toList()); model.sessionStatus(SessionStatus.RUNNING)
            model.metadata(JSONObject().put("activeTurnId", "turn").put("status", "running").put("observedAt", it))
        }
        assertSame(before, model.items()); assertTrue(model.items().last().expanded)
    }
    @Test fun statusAndDisclosureReuseTopologyButPublishTheChangedExpansion() {
        val model = TranscriptPresentation(); model.update(listOf(message("u", ChatMessage.Role.USER), message("t", ChatMessage.Role.TOOL_RESULT)), SessionStatus.RUNNING)
        val live = model.items(); model.sessionStatus(SessionStatus.IDLE); val settled = model.items()
        assertNotSame(live, settled); assertSame(live.last().row, settled.last().row); assertSame(live.last().tools, settled.last().tools); assertFalse(settled.last().expanded)
        model.toggle(settled.last().row.group); assertSame(settled.last().row, model.items().last().row); assertTrue(model.items().last().expanded)
        val expanded = model.items(); model.sessionStatus(SessionStatus.IDLE); assertSame(expanded, model.items())
    }
    @Test fun explicitAtomicStatusWinsOverMetadataWithoutAFalseIdleSettlement() {
        val model = TranscriptPresentation(); val source = listOf(message("u", ChatMessage.Role.USER), message("t", ChatMessage.Role.TOOL_RESULT))
        model.update(source, SessionStatus.RUNNING, JSONObject().put("status", "idle").put("activeTurnId", "turn"))
        assertTrue(model.items().last().expanded)
        model.update(source, SessionStatus.IDLE, JSONObject().put("status", "running"))
        assertFalse(model.items().last().expanded)
    }
    @Test fun completedTurnSettlesOnceAndDuplicateCompletionKeepsManualDisclosure() {
        val model = TranscriptPresentation(); model.update(listOf(message("t", ChatMessage.Role.TOOL_RESULT)), SessionStatus.RUNNING)
        val row = model.items().single().row
        val done = JSONObject().put("turns", org.json.JSONArray().put(JSONObject().put("id", "turn").put("finishedAt", 1)))
        model.metadata(done); assertSame(row, model.items().single().row); assertFalse(model.items().single().expanded)
        model.toggle(row.group); val expanded = model.items(); repeat(50) { model.metadata(done) }
        assertSame(expanded, model.items()); assertTrue(model.items().single().expanded)
    }
    @Test fun externallyMutableSourceCannotSilentlyChangeTheCachedProjection() {
        val source = mutableListOf(message("u", ChatMessage.Role.USER)); val model = TranscriptPresentation()
        model.submit(source); val before = model.items(); source.add(message("t", ChatMessage.Role.TOOL_RESULT))
        model.sessionStatus(SessionStatus.RUNNING); assertSame(before, model.items())
        model.submit(source); assertEquals(2, model.items().size); assertEquals("t", model.items().last().tools.single().id())
    }
    @Test fun publishedItemsAndCachedToolListsCannotBeMutatedByTheCaller() {
        val model = TranscriptPresentation(); model.submit(listOf(message("t", ChatMessage.Role.TOOL_RESULT)))
        val before = model.items()
        try { (before as MutableList).clear(); fail("Published items were mutable") } catch (_: UnsupportedOperationException) {}
        try { (before.single().tools as MutableList).clear(); fail("Cached tools were mutable") } catch (_: UnsupportedOperationException) {}
        model.sessionStatus(SessionStatus.RUNNING); assertSame(before, model.items()); assertEquals("t", model.items().single().tools.single().id())
    }
    @Test fun resetAndChangedLegacyPromptInvalidateNormalizationAndTopology() {
        val user = ChatMessage.remote("u", ChatMessage.Role.USER, "prompt", null, null)
        val tool = ChatMessage.remote("t", ChatMessage.Role.TOOL_RESULT, "output", null, "read")
        val model = TranscriptPresentation(); model.update(listOf(user, tool), SessionStatus.IDLE)
        val first = model.items().last(); model.toggle(first.row.group); assertTrue(model.items().last().expanded)
        model.reset(); model.update(listOf(user, tool), SessionStatus.IDLE)
        assertNotSame(first.row, model.items().last().row); assertFalse(model.items().last().expanded)
        model.update(listOf(ChatMessage.remote("older", ChatMessage.Role.USER, "older", null, null), tool), SessionStatus.RUNNING)
        assertEquals("older", model.items().last().row.turn); assertEquals("tools:older:t", model.items().last().row.group)
    }

    @Test fun independentToolsSettleOnceAndProgressAlwaysRemainsVisible() {
        val model = TranscriptPresentation()
        model.metadata(JSONObject().put("activeTurnId", "turn"))
        model.submit(listOf(message("u", ChatMessage.Role.USER), message("t1", ChatMessage.Role.TOOL_RESULT),
            message("p", ChatMessage.Role.ASSISTANT), message("t2", ChatMessage.Role.TOOL_RESULT)))
        assertEquals(listOf("u", "work-header:tools:turn:t1", "p", "work-header:tools:turn:t2"), model.items().map { it.row.key })
        assertTrue(model.items().filter { it.row.header }.all { it.expanded })
        val done = JSONObject().put("status", "idle").put("activeTurnId", "")
        model.metadata(done)
        val headers = model.items().filter { it.row.header }
        assertTrue(headers.all { !it.expanded })
        model.toggle(headers.first().row.group)
        model.metadata(done)
        assertTrue(model.items()[1].expanded)
        assertFalse(model.items().last().expanded)
        assertEquals("p", model.items()[2].row.message!!.id())
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
