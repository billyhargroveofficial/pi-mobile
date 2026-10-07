package ru.billyhargrove.pimobile.features.chat

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ModelSettingsSessionTest {
    private fun config() = JSONObject("""{"model":"test/a","thinkingLevel":"high","serviceTier":"fast","models":[
        {"provider":"test","id":"a","name":"Model A","thinkingLevels":["off","high"],"serviceTiers":["standard","fast"]},
        {"provider":"test","id":"b","name":"Model B","thinkingLevels":["medium","xhigh"]}]}""")
    @Test fun selectingModelImmediatelyNormalizesEffortButDoesNotSendUntilApply() {
        val sent = mutableListOf<ModelSettingsSession.Selection>(); val owner = ModelSettingsSession(config(), true, sent::add)
        owner.select("test/b"); assertEquals("medium", owner.effort); owner.chooseEffort("xhigh"); assertTrue(sent.isEmpty())
        owner.submit(); assertEquals(ModelSettingsSession.Selection("test", "b", "xhigh", null), sent.single()); assertTrue(owner.pending)
    }
    @Test fun pendingApplyLocksAllChangesAndDuplicateSubmit() {
        val sent = mutableListOf<ModelSettingsSession.Selection>(); val owner = ModelSettingsSession(config(), true, sent::add)
        owner.submit(); owner.submit(); owner.select("test/b"); owner.chooseEffort("off"); owner.chooseTier("standard")
        assertEquals(1, sent.size); assertEquals("test/a", owner.selected!!.key); assertEquals("high", owner.effort); assertEquals("fast", owner.tier)
        owner.applied(); assertTrue(owner.canApply); owner.chooseEffort("off"); owner.submit(); assertEquals(2, sent.size)
    }
    @Test fun failureRetainsDraftForExplicitRetryAndUnownedCompletionIsIgnored() {
        val sent = mutableListOf<ModelSettingsSession.Selection>(); val owner = ModelSettingsSession(config(), true, sent::add)
        owner.failed("unrelated"); assertEquals("", owner.error); owner.chooseTier("standard"); owner.submit(); owner.failed("rejected")
        assertFalse(owner.pending); assertEquals("rejected", owner.notice); assertEquals("standard", owner.tier)
        owner.applied(); assertEquals("rejected", owner.error); owner.submit(); assertEquals(2, sent.size); assertEquals("", owner.error)
    }
    @Test fun readOnlyOrBusySnapshotCannotSelectOrSend() {
        val owner = ModelSettingsSession(config(), false) { fail("Busy model must not send") }
        owner.select("test/b"); owner.chooseEffort("off"); owner.submit()
        assertEquals("test/a", owner.selected!!.key); assertEquals("high", owner.effort); assertFalse(owner.canApply)
        assertEquals("Wait for the current task to finish", owner.notice)
    }
    @Test fun missingCurrentModelRequiresExplicitSelectionAndInvalidValuesAreRejected() {
        val value = config().put("model", "unknown/model"); val sent = mutableListOf<ModelSettingsSession.Selection>()
        val owner = ModelSettingsSession(value, true, sent::add); assertNull(owner.selected); owner.submit(); assertTrue(sent.isEmpty())
        owner.select("not/a-model"); assertNull(owner.selected); owner.select("test/a"); owner.chooseEffort("max"); owner.chooseTier("unknown")
        assertEquals("off", owner.effort); assertEquals("fast", owner.tier)
    }
    @Test fun searchUsesImmutableProjectionAndDoesNotForgetHiddenSelection() {
        val value = config(); val owner = ModelSettingsSession(value, true) { }
        value.getJSONArray("models").getJSONObject(0).put("name", "Changed"); owner.search("MODEL B")
        assertEquals(listOf("test/b"), owner.visible.map { it.key }); assertEquals("test/a", owner.selected!!.key)
        owner.search("test/a"); assertEquals("Model A", owner.visible.single().name)
        owner.search("missing"); assertTrue(owner.visible.isEmpty()); owner.search(""); assertEquals(2, owner.visible.size)
    }
    @Test fun dismissalRetiresDraftActionsAndLateResult() {
        val sent = mutableListOf<ModelSettingsSession.Selection>(); val owner = ModelSettingsSession(config(), true, sent::add)
        owner.submit(); owner.close(); owner.failed("late"); owner.applied(); owner.select("test/b"); owner.submit()
        assertEquals(1, sent.size); assertFalse(owner.pending); assertEquals("", owner.error); assertFalse(owner.canApply)
    }
    @Test fun synchronousFailureAndSynchronousCompletionHaveSamePendingContract() {
        val failed = ModelSettingsSession(config(), true) { throw IllegalStateException("synthetic") }; failed.submit()
        assertFalse(failed.pending); assertEquals("synthetic", failed.error)
        lateinit var owner: ModelSettingsSession
        owner = ModelSettingsSession(config(), true) { owner.applied() }; owner.submit(); assertFalse(owner.pending); assertTrue(owner.canApply)
    }
    @Test fun unavailableAndTruncatedCatalogHaveHonestNotices() {
        val empty = ModelSettingsSession(JSONObject(), true) { fail("No capabilities") }
        empty.submit(); assertTrue(empty.notice.startsWith("Models unavailable")); assertFalse(empty.canApply)
        assertTrue(ModelSettingsSession(config().put("modelsTruncated", true), true) { }.notice.contains("First 1,000 models"))
    }
}
