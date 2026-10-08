package ru.billyhargrove.pimobile.features.chat

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import ru.billyhargrove.pimobile.core.*

class TranscriptSessionTest {
    private fun row(id: String, role: ChatMessage.Role = ChatMessage.Role.ASSISTANT, text: String = id) = ChatMessage.remote(id, role, text, null, null)
    private class Fixture {
        val order = mutableListOf<String>()
        val publications = mutableListOf<Pair<Boolean, Boolean>>()
        val renders = mutableListOf<Pair<Boolean, Boolean>>()
        var canonical: List<ChatMessage> = emptyList()
        var local: List<ChatMessage> = emptyList()
        var failCheckpoint = false
        val state = TranscriptSession({ canonical = it; order.add("overlay"); it + local },
            { order.add("checkpoint"); if (failCheckpoint) error("Save failed") },
            { _, _, updates, added -> order.add("publish"); publications.add(updates to added) },
            { items, touched -> order.add("rendered"); renders.add(items to touched) })
    }
    @Test fun initialRenderAndSnapshotPublishAfterTheCheckpointAndReuseCanonicalSnapshot() {
        val f = Fixture(); f.state.render(); assertEquals(listOf("overlay", "checkpoint", "publish", "rendered"), f.order)
        f.order.clear(); f.state.snapshot(listOf(row("u", ChatMessage.Role.USER)), SessionStatus.IDLE, null, true, false)
        val canonical = f.canonical; f.state.render(); assertSame(canonical, f.canonical)
        assertEquals(true to false, f.publications[1]); assertEquals(true to true, f.renders[1]); assertTrue(f.state.hasUserContext)
    }
    @Test fun deltaAndRemovalKeepCanonicalOrderAndSeparateTailFromMiddleChange() {
        val f = Fixture(); f.state.snapshot(listOf(row("u", ChatMessage.Role.USER), row("a")), SessionStatus.RUNNING, null, false, true)
        f.state.messages(listOf(row("u", ChatMessage.Role.USER, "edited")), emptyList(), SessionStatus.RUNNING, false)
        assertEquals(true to false, f.renders.last()); assertEquals(listOf("u", "a"), f.canonical.map { it.id() })
        f.state.messages(listOf(row("a", text = "new")), listOf("u"), SessionStatus.IDLE, true)
        assertEquals(true to true, f.renders.last()); assertFalse(f.state.hasUserContext); assertEquals("new", f.state.items.single().row.message!!.text())
        assertEquals(true to true, f.publications.last())
    }
    @Test fun unchangedDeltaKeepsVisibleItemsButStillCompletesFramePublication() {
        val f = Fixture(); val rows = listOf(row("u", ChatMessage.Role.USER), row("a"))
        f.state.snapshot(rows, SessionStatus.IDLE, null, false, true); val before = f.state.items
        repeat(40) { f.state.messages(emptyList(), emptyList(), SessionStatus.IDLE, true) }
        assertSame(before, f.state.items); assertEquals(true to false, f.renders.last()); assertEquals(41, f.renders.size)
    }
    @Test fun overlayCannotMutateTheOwnedCanonicalSnapshot() {
        val f = Fixture(); f.state.snapshot(listOf(row("u", ChatMessage.Role.USER)), SessionStatus.IDLE, null, false, true)
        try { (f.canonical as MutableList).clear(); fail("Canonical overlay input was mutable") } catch (_: UnsupportedOperationException) {}
        f.state.render(); assertTrue(f.state.hasUserContext); assertEquals("u", f.canonical.single().id())
    }
    @Test fun prependUsesCanonicalUserContextWithoutTailOrArrivalSignals() {
        val f = Fixture(); f.state.snapshot(listOf(row("a")), SessionStatus.RUNNING, null, false, false)
        f.local = listOf(ChatMessage.local("local", "optimistic", null, ChatMessage.LocalState.SENDING)); f.state.render()
        assertFalse(f.state.hasUserContext)
        f.state.prepend(listOf(row("u", ChatMessage.Role.USER)), JSONObject())
        assertTrue(f.state.hasUserContext); assertEquals(listOf("u", "a"), f.canonical.map { it.id() })
        assertEquals(false to false, f.publications.last()); assertEquals(true to false, f.renders.last())
    }
    @Test fun receiptOnlyRenderAddsAndRemovesOverlayWithoutChangingCanonicalContext() {
        val f = Fixture(); f.state.snapshot(listOf(row("a")), SessionStatus.RUNNING, null, false, true)
        val canonical = f.canonical; f.local = listOf(ChatMessage.local("local", "draft", null, ChatMessage.LocalState.SENDING)); f.state.render()
        assertSame(canonical, f.canonical); assertEquals(listOf("a", "local"), f.state.items.map { if (it.row.message!!.isLocal) it.row.message!!.requestId() else it.row.message!!.id() })
        assertFalse(f.state.hasUserContext); f.local = emptyList(); f.state.render(); assertEquals(1, f.state.items.size)
    }
    @Test fun epochReplacementResetsManualDisclosureEvenForTheSameSource() {
        val f = Fixture(); val rows = listOf(row("u", ChatMessage.Role.USER), ChatMessage.remote("t", ChatMessage.Role.TOOL_RESULT, "output", null, "read"))
        f.state.snapshot(rows, SessionStatus.IDLE, null, false, true); val group = f.state.items.last().row.group
        f.state.toggle(group); val expanded = f.state.items; f.state.snapshot(rows, SessionStatus.IDLE, null, true, true)
        assertSame(expanded, f.state.items); assertTrue(f.state.items.last().expanded)
        f.state.snapshot(rows, SessionStatus.IDLE, JSONObject().put("activeTurnId", ""), true, true)
        assertFalse(f.state.items.last().expanded); assertNotSame(expanded.last().row, f.state.items.last().row)
    }
    @Test fun statusAndMetadataDoNotRunOutboxCheckpointOrTailPublication() {
        val f = Fixture(); f.state.snapshot(listOf(row("u", ChatMessage.Role.USER)), SessionStatus.RUNNING, null, false, true)
        f.order.clear(); val renders = f.renders.size; f.state.status(SessionStatus.IDLE); assertTrue(f.order.isEmpty())
        f.state.metadata(JSONObject().put("observedAt", 10), true)
        assertEquals(listOf("publish"), f.order); assertEquals(renders, f.renders.size); assertEquals(true to false, f.publications.last())
    }
    @Test fun failedCheckpointCannotPublishAndALaterRenderCanRetryTheSameCanonicalData() {
        val f = Fixture(); f.failCheckpoint = true
        try { f.state.snapshot(listOf(row("u", ChatMessage.Role.USER)), SessionStatus.IDLE, null, false, true); fail("checkpoint failure swallowed") }
        catch (_: IllegalStateException) {}
        assertTrue(f.state.items.isEmpty()); assertTrue(f.publications.isEmpty()); assertTrue(f.state.hasUserContext)
        val canonical = f.canonical; f.failCheckpoint = false; f.state.render()
        assertSame(canonical, f.canonical); assertEquals("u", f.state.items.single().row.message!!.id())
    }
}
