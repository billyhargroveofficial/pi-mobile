package ru.billyhargrove.pimobile.features.chat

import org.junit.Assert.*
import org.junit.Test
import ru.billyhargrove.pimobile.core.*

class ViewportSessionTest {
    private class Fixture {
        var cached: ViewportSession.Position? = null
        var reads = 0
        var duringLoad: (() -> Unit)? = null
        val saved = mutableListOf<ViewportSession.Position>()
        val restored = mutableListOf<ViewportSession.Position?>()
        val owner = ViewportSession({ reads++; duringLoad?.invoke(); cached }, saved::add, restored::add)
    }
    private fun item(key: String, text: String = key) = TranscriptPresentation.Item(
        WorkTimeline.Row(key, "turn", key, ChatMessage.remote(key, ChatMessage.Role.ASSISTANT, text, null, null), false, false, false, 0), emptyList(), true)
    @Test fun firstRowsRequestImmediateTailAndLaterTouchedTailUsesSmoothFollowing() {
        val f = Fixture(); f.owner.rendered(false, false); f.owner.start(); assertEquals(0, f.owner.tailRevision); assertFalse(f.owner.catchingUp)
        f.owner.rendered(true, true); assertEquals(1, f.owner.tailRevision); assertFalse(f.owner.smoothTail)
        f.owner.rendered(true, false); assertEquals(1, f.owner.tailRevision)
        f.owner.rendered(true, true); assertEquals(2, f.owner.tailRevision); assertTrue(f.owner.smoothTail)
    }
    @Test fun draggedReaderDoesNotFollowLiveTailAndSavesItsSignedOffset() {
        val f = Fixture(); f.owner.rendered(true, true); f.owner.readerDragged(); val revision = f.owner.tailRevision
        f.owner.rendered(true, true); assertEquals(revision, f.owner.tailRevision); assertFalse(f.owner.followTail)
        f.owner.save("", -80); assertTrue(f.saved.isEmpty()); f.owner.save("answer", -80)
        assertEquals(listOf(ViewportSession.Position("answer", -80, false)), f.saved)
        f.owner.readerSettled(true); f.owner.save("answer", 0); assertTrue(f.saved.last().follow)
    }
    @Test fun cachedAnchorIsLoadedOnceAndBlocksPrematureTailUntilApplied() {
        val f = Fixture(); f.cached = ViewportSession.Position("answer", -480, false)
        f.owner.restoreCached(); f.owner.restoreCached(); assertEquals(1, f.reads); assertEquals(listOf(f.cached), f.restored)
        assertFalse(f.owner.followTail); f.owner.rendered(true, true); assertEquals(0, f.owner.tailRevision)
        f.owner.readerSettled(false); f.owner.viewportRestored(); assertNull(f.restored.last()); assertEquals(0, f.owner.tailRevision)
        f.owner.start(); assertTrue(f.owner.catchingUp)
    }
    @Test fun savedFollowingAnchorOnlyRequestsSmoothTailAfterScreenRestoration() {
        val f = Fixture(); f.cached = ViewportSession.Position("last", -32, true); f.owner.restoreCached()
        assertFalse(f.owner.followTail); f.owner.rendered(true, true); assertEquals(0, f.owner.tailRevision)
        f.owner.readerSettled(true); f.owner.viewportRestored(); assertTrue(f.owner.followTail); assertTrue(f.owner.smoothTail); assertEquals(1, f.owner.tailRevision)
    }
    @Test fun manualReadingFencesADelayedCacheEvenWhenItContainsAFollowingAnchor() {
        val f = Fixture(); f.owner.rendered(true, true); f.cached = ViewportSession.Position("old", -20, true)
        f.owner.readerDragged(); f.owner.readerSettled(false); f.owner.restoreCached(); f.owner.restoreCached()
        assertEquals(0, f.reads); assertTrue(f.restored.all { it == null }); assertFalse(f.owner.followTail); assertEquals(1, f.owner.tailRevision)
    }
    @Test fun cacheLoadReservationAndReaderGenerationProtectReentrantPorts() {
        val f = Fixture(); f.cached = ViewportSession.Position("old", -30, false)
        f.duringLoad = { f.owner.restoreCached(); f.owner.readerDragged() }; f.owner.restoreCached()
        assertEquals(1, f.reads); assertTrue(f.restored.all { it == null }); assertFalse(f.owner.followTail)
        val closed = Fixture(); closed.cached = f.cached; closed.duringLoad = closed.owner::close; closed.owner.restoreCached()
        assertEquals(listOf<ViewportSession.Position?>(null), closed.restored)
    }
    @Test fun normalLiveArrivalOnlyMarksNewKeysNotStreamingOrDisclosureChanges() {
        val f = Fixture(); val old = item("a"); f.owner.rendered(true, true); f.owner.readerDragged()
        f.owner.published(listOf(old), listOf(item("a", "changed").copy(expanded = false), item("new")), false, true)
        assertEquals(setOf("new"), f.owner.arrivingKeys); assertEquals(1, f.owner.arrivalRevision); assertTrue(f.owner.newActivity)
        f.owner.arrivalsShown(1); f.owner.published(listOf(old), listOf(item("a", "stream")), false, true)
        assertTrue(f.owner.arrivingKeys.isEmpty()); assertEquals(1, f.owner.arrivalRevision)
    }
    @Test fun catchUpMarksChangedMessagesToolsAndExpansionInStableOrder() {
        val f = Fixture(); val a = item("a"); val b = item("b"); val c = item("c"); f.owner.rendered(true, true)
        val tool = ChatMessage.remote("tool", ChatMessage.Role.TOOL_RESULT, "new output", null, "bash")
        f.owner.published(listOf(a,b,c), listOf(item("a", "updated"), b.copy(tools = listOf(tool)), c.copy(expanded = false), item("d")), true, false)
        assertEquals(listOf("a","b","c","d"), f.owner.arrivingKeys.toList()); assertFalse(f.owner.newActivity)
    }
    @Test fun staleCompletionAndUnchangedFramesCannotClearNewerArrivalBatch() {
        val f = Fixture(); val a = item("a"); val b = item("b"); f.owner.rendered(true, true); f.owner.readerDragged()
        f.owner.published(emptyList(), listOf(a), false, true); val old = f.owner.arrivalRevision
        f.owner.published(listOf(a), listOf(a,b), false, true); f.owner.arrivalsShown(old)
        assertEquals(setOf("b"), f.owner.arrivingKeys)
        f.owner.published(listOf(a,b), listOf(a,b), true, true); assertEquals(setOf("b"), f.owner.arrivingKeys)
        f.owner.arrivalsShown(f.owner.arrivalRevision); assertTrue(f.owner.arrivingKeys.isEmpty()); assertTrue(f.owner.newActivity)
        f.owner.readerSettled(true); assertFalse(f.owner.newActivity)
    }
    @Test fun historyAndRemovedRowsDoNotCreateArrivalAndExplicitTailClearsDisclosure() {
        val f = Fixture(); val a = item("a"); f.owner.rendered(true, true); f.owner.readerDragged()
        f.owner.published(listOf(a), listOf(item("older"),a), false, false); assertEquals(0, f.owner.arrivalRevision); assertFalse(f.owner.newActivity)
        f.owner.published(listOf(item("older"),a), listOf(a), true, true); assertEquals(0, f.owner.arrivalRevision)
        f.owner.published(listOf(a), listOf(a,item("new")), false, true); assertTrue(f.owner.newActivity)
        val revision = f.owner.tailRevision; f.owner.tail(); assertTrue(f.owner.followTail); assertFalse(f.owner.newActivity); assertTrue(f.owner.smoothTail); assertEquals(revision+1,f.owner.tailRevision)
    }
    @Test fun resumeAndCachedPhaseAnimateCatchUpUntilTheFirstLiveUpdate() {
        val f = Fixture(); f.owner.start(); assertFalse(f.owner.catchingUp); f.owner.rendered(true, true)
        f.owner.start(); assertTrue(f.owner.catchingUp); f.owner.snapshotRendered(true); assertTrue(f.owner.catchingUp)
        f.owner.messagesRendered(); assertFalse(f.owner.catchingUp); f.owner.start(); assertTrue(f.owner.catchingUp)
        f.owner.snapshotRendered(false); assertFalse(f.owner.catchingUp)
    }
    @Test fun closeRetiresOnlyTransientViewportWorkAndAllLateOperationsBecomeInert() {
        val f = Fixture(); f.owner.rendered(true, true); f.owner.readerDragged(); f.owner.published(emptyList(),listOf(item("a")),false,true)
        f.owner.save("answer",-10); val saved=f.saved.toList(); val tail=f.owner.tailRevision; val arrival=f.owner.arrivalRevision
        f.owner.close(); val restores=f.restored.size; f.owner.close(); f.owner.restoreCached(); f.owner.start(); f.owner.tail(); f.owner.rendered(true,true)
        f.owner.readerSettled(true); f.owner.readerDragged(); f.owner.viewportRestored(); f.owner.arrivalsShown(arrival)
        f.owner.published(emptyList(),listOf(item("late")),true,true); f.owner.save("late",1); f.owner.snapshotRendered(true)
        assertEquals(0,f.reads); assertEquals(saved,f.saved); assertEquals(restores,f.restored.size); assertEquals(tail,f.owner.tailRevision)
        assertEquals(arrival,f.owner.arrivalRevision); assertTrue(f.owner.arrivingKeys.isEmpty()); assertFalse(f.owner.newActivity); assertFalse(f.owner.catchingUp)
    }
}
