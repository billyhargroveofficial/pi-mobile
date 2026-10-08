package ru.billyhargrove.pimobile.features.chat

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QuickEffortSessionTest {
    private fun model(id: String = "a", levels: List<String> = listOf("low", "medium", "high")) = JSONObject().put("provider", "test").put("id", id)
        .put("name", "Model $id").put("thinkingLevels", JSONArray(levels)).put("serviceTiers", JSONArray().put("standard").put("fast"))
    private fun config(effort: String, tier: String = "standard", model: JSONObject = model()) = JSONObject().put("model", "test/" + model.optString("id"))
        .put("thinkingLevel", effort).put("serviceTier", tier).put("models", JSONArray().put(model))
    private class Fixture(initial: JSONObject, editable: Boolean = true) {
        val levels = mutableListOf<String>(); val tiers = mutableListOf<String>()
        val owner = QuickEffortSession(initial, "low", editable, "standard", { levels.add(it) }, { tiers.add(it) })
    }
    @Test fun previewNeverSendsAndOnlyChangedCommitCreatesOnePendingRequest() {
        val f = Fixture(model()); f.owner.preview("medium", false); assertTrue(f.levels.isEmpty()); assertEquals("medium", f.owner.selected)
        f.owner.preview("low", true); assertTrue(f.levels.isEmpty()); f.owner.preview("high", true); f.owner.preview("medium", true)
        assertEquals(listOf("high"), f.levels); assertTrue(f.owner.pending); assertEquals("high", f.owner.selected); assertFalse(f.owner.canOpenModels)
    }
    @Test fun successfulAckWithoutConfigurationPromotesOnlyTheConfirmedEffort() {
        val f = Fixture(model()); f.owner.preview("high", true); f.owner.completed(null); f.owner.preview("high", true)
        assertEquals(listOf("high"), f.levels); assertFalse(f.owner.pending)
        f.owner.preview("medium", true); f.owner.completed("rejected"); assertEquals("high", f.owner.selected); assertEquals("rejected", f.owner.error)
    }
    @Test fun authoritativeConfigurationAfterRequestWinsOverSuccessfulAck() {
        val f = Fixture(model()); f.owner.preview("high", true); f.owner.updateConfiguration(config("medium")); f.owner.completed(null)
        assertEquals("medium", f.owner.selected); f.owner.preview("high", true); f.owner.completed("rejected"); assertEquals("medium", f.owner.selected)
    }
    @Test fun partialTierFrameCannotConfirmPendingEffortAndDoesNotBlockItsSuccessfulAck() {
        val f = Fixture(model()); f.owner.preview("high", true)
        f.owner.updateConfiguration(JSONObject().put("serviceTier", "fast")); f.owner.completed("rejected")
        assertEquals("low", f.owner.selected); assertEquals("fast", f.owner.tier)
        f.owner.preview("high", true); f.owner.updateConfiguration(JSONObject().put("serviceTier", "standard")); f.owner.completed(null)
        f.owner.preview("medium", true); f.owner.completed("rejected"); assertEquals("high", f.owner.selected)
    }
    @Test fun errorRollsBackBothFieldsAndUnownedResultCannotOverwriteIt() {
        val f = Fixture(model()); f.owner.toggleTier(); assertEquals(listOf("fast"), f.tiers); assertEquals("fast", f.owner.tier)
        f.owner.completed("rejected"); assertEquals("standard", f.owner.tier); assertEquals("low", f.owner.selected)
        f.owner.completed(null); assertEquals("rejected", f.owner.error)
        f.owner.preview("high", true); assertEquals("", f.owner.error)
    }
    @Test fun tierAckDoesNotPromoteAnUncommittedEffortPreview() {
        val f = Fixture(model()); f.owner.preview("medium", false); f.owner.toggleTier(); f.owner.completed(null)
        f.owner.preview("medium", true); assertEquals(listOf("medium"), f.levels)
        f.owner.completed("rejected"); assertEquals("low", f.owner.selected); assertEquals("fast", f.owner.tier)
    }
    @Test fun modelReplacementReprojectsCapabilitiesAndUnsupportedEffortBeforeEditing() {
        val f = Fixture(model()); f.owner.updateConfiguration(config("high", model = model("b", listOf("medium", "xhigh"))))
        assertEquals("test/b", f.owner.model.key); assertEquals("medium", f.owner.selected)
        f.owner.preview("high", true); assertTrue(f.levels.isEmpty()); f.owner.preview("xhigh", true); assertEquals(listOf("xhigh"), f.levels)
    }
    @Test fun explicitlyMissingModelDropsCapabilitiesWhileKeepingReportedEffort() {
        val f = Fixture(model()); f.owner.updateConfiguration(JSONObject().put("model", "test/new").put("thinkingLevel", "high").put("models", JSONArray()))
        assertEquals("new", f.owner.model.name); assertTrue(f.owner.model.levels.isEmpty()); assertEquals("high", f.owner.selected)
        assertFalse(f.owner.showTier); f.owner.preview("high", true); f.owner.toggleTier(); assertTrue(f.levels.isEmpty()); assertTrue(f.tiers.isEmpty())
    }
    @Test fun partialConfigurationWithSameModelDoesNotLoseReportedCapabilities() {
        val f = Fixture(model()); f.owner.updateConfiguration(JSONObject().put("model", "test/a").put("thinkingLevel", "medium"))
        assertEquals(listOf("low", "medium", "high"), f.owner.model.levels); assertEquals("medium", f.owner.selected)
        f.owner.updateConfiguration(JSONObject().put("models", JSONArray().put(model("a", listOf("low", "max")))))
        assertEquals(listOf("low", "max"), f.owner.model.levels)
    }
    @Test fun immutableInitialCapabilitiesDoNotChangeWithJsonMutation() {
        val source = model(); val f = Fixture(source); source.getJSONArray("thinkingLevels").put("max"); source.put("name", "changed")
        f.owner.preview("max", true); assertTrue(f.levels.isEmpty()); assertEquals("Model a", f.owner.model.name)
    }
    @Test fun pendingAndReadOnlyPanelsCannotSendOrPreview() {
        val readOnly = Fixture(model(), false); readOnly.owner.preview("high", false); readOnly.owner.toggleTier()
        assertEquals("low", readOnly.owner.selected); assertTrue(readOnly.levels.isEmpty()); assertTrue(readOnly.tiers.isEmpty())
        val f = Fixture(model()); f.owner.toggleTier(); f.owner.toggleTier(); f.owner.preview("high", true)
        assertEquals(listOf("fast"), f.tiers); assertTrue(f.levels.isEmpty()); assertEquals("low", f.owner.selected)
    }
    @Test fun missingTierCapabilityOrCallbackNeverSendsATierChange() {
        val initial = model().put("serviceTiers", JSONArray().put("standard")); val f = Fixture(initial); f.owner.toggleTier(); assertTrue(f.tiers.isEmpty())
        val owner = QuickEffortSession(model(), "low", true, "standard", {}, null); owner.toggleTier(); assertFalse(owner.showTier); assertFalse(owner.pending)
    }
    @Test fun dismissalRejectsLateConfigurationResultsAndActions() {
        val f = Fixture(model()); f.owner.preview("high", true); f.owner.close(); f.owner.updateConfiguration(config("medium")); f.owner.completed("late")
        f.owner.toggleTier(); f.owner.preview("medium", true); assertEquals(listOf("high"), f.levels); assertEquals("high", f.owner.selected)
        assertFalse(f.owner.pending); assertEquals("", f.owner.error); assertFalse(f.owner.canOpenModels)
    }
    @Test fun synchronousCallbackFailureRollsBackAndCanBeRetriedExplicitly() {
        val owner = QuickEffortSession(model(), "low", true, "standard", { throw IllegalStateException("synthetic") }, null)
        owner.preview("high", true); assertFalse(owner.pending); assertEquals("low", owner.selected); assertEquals("synthetic", owner.error)
    }
    @Test fun equalExplicitReportsStillFenceSuccessForEachConfirmedField() {
        val f = Fixture(model()); f.owner.preview("high", true)
        f.owner.updateConfiguration(JSONObject().put("thinkingLevel", "low")); f.owner.completed(null)
        f.owner.preview("high", true); assertEquals(listOf("high", "high"), f.levels)
        f.owner.completed("rejected"); assertEquals("low", f.owner.selected)
        f.owner.toggleTier(); f.owner.updateConfiguration(JSONObject().put("serviceTier", "standard")); f.owner.completed(null)
        f.owner.toggleTier(); assertEquals(listOf("fast", "fast"), f.tiers)
        f.owner.completed("rejected"); assertEquals("standard", f.owner.tier)
    }
    @Test fun metadataOnlyReportsNeverReadARegistryOrDropCurrentCapabilities() {
        val config = object : JSONObject() {
            override fun optJSONArray(key: String?): JSONArray? = throw AssertionError("No registry in a partial report")
        }.put("thinkingLevel", "medium")
        val f = Fixture(model()); f.owner.updateConfiguration(config)
        assertEquals("medium", f.owner.selected); assertEquals(listOf("low", "medium", "high"), f.owner.model.levels)
    }
}
