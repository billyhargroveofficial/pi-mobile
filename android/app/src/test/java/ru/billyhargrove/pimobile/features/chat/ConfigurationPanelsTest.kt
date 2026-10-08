package ru.billyhargrove.pimobile.features.chat

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ConfigurationPanelsTest {
    private fun config() = JSONObject().put("model", "test/a").put("thinkingLevel", "low").put("serviceTier", "fast")
        .put("models", JSONArray().put(JSONObject().put("provider", "test").put("id", "a").put("name", "Model A")
            .put("thinkingLevels", JSONArray().put("low").put("high")).put("serviceTiers", JSONArray().put("fast"))))
    private class Window : ConfigurationPanels.QuickPanel {
        override var showing = true
        override var pending = false
        val results = mutableListOf<String?>(); val updates = mutableListOf<JSONObject>()
        var closes = 0
        override fun completed(error: String?) { results.add(error); pending = false }
        override fun update(configuration: JSONObject) { updates.add(configuration) }
        override fun close() { closes++; showing = false }
    }
    private inner class Fixture {
        var active = true
        var state = ConfigurationPanels.State(config(), true, false, false, true)
        val quick = mutableListOf<Pair<ConfigurationPanels.Quick, Window>>()
        val models = mutableListOf<Pair<ConfigurationPanels.Models, Window>>()
        val notices = mutableListOf<String>()
        var created: (() -> Unit)? = null
        val owner = ConfigurationPanels({ active }, { state }, { request ->
            Window().also { quick.add(request to it); created?.invoke() }
        }, { request -> Window().also { models.add(request to it); created?.invoke() } }, notices::add)
    }
    @Test fun quickUsesTheReportedModelAndCopiesItsCapabilitiesBeforeTheHost() {
        val f = Fixture(); f.owner.openQuick()
        val request = f.quick.single().first
        f.state.configuration!!.getJSONArray("models").getJSONObject(0).put("name", "mutated")
        assertEquals("Model A", request.model.name); assertEquals(listOf("low", "high"), request.model.levels)
        assertEquals("low", request.effort); assertEquals("fast", request.tier); assertTrue(request.editable)
        assertTrue(f.models.isEmpty()); assertTrue(f.notices.isEmpty())
    }
    @Test fun disconnectedOrMissingConfigurationOnlyShowsTheConnectionNotice() {
        val f = Fixture(); f.state = f.state.copy(connected = false); f.owner.openQuick()
        f.state = f.state.copy(configuration = null, connected = true); f.owner.openQuick()
        assertEquals(listOf("Connect to Pi first", "Connect to Pi first"), f.notices)
        assertTrue(f.quick.isEmpty()); assertTrue(f.models.isEmpty())
    }
    @Test fun unknownSelectionFallsBackToModelsWithoutInventingQuickCapabilities() {
        val f = Fixture(); f.state.configuration!!.put("model", "test/missing"); f.owner.openQuick()
        assertTrue(f.quick.isEmpty()); assertNull(f.models.single().first.catalog.selected)
        assertEquals(1, f.models.single().first.catalog.models.size)
    }
    @Test fun missingRegistryRequiresReloadWhileAnEmptyRegistryCanBeInspected() {
        val f = Fixture(); f.state.configuration!!.remove("models"); f.owner.openModels()
        assertEquals(listOf("Run /reload in Pi when idle to load models and effort levels."), f.notices)
        f.state.configuration!!.put("models", JSONArray()); f.owner.openQuick()
        assertTrue(f.models.single().first.catalog.models.isEmpty()); assertTrue(f.quick.isEmpty())
    }
    @Test fun pendingAndInactiveScreensCannotReplaceEitherWindow() {
        val f = Fixture(); f.owner.openQuick(); f.owner.openModels()
        f.state = f.state.copy(pending = true); f.owner.openQuick(); f.owner.openModels()
        f.state = f.state.copy(pending = false); f.active = false; f.owner.openQuick(); f.owner.openModels()
        assertEquals(1, f.quick.size); assertEquals(1, f.models.size)
        assertEquals(0, f.quick.single().second.closes); assertEquals(0, f.models.single().second.closes)
    }
    @Test fun readOnlyAndBusyInspectionNeverBecomesAnEditableModelDraft() {
        val f = Fixture(); f.state = f.state.copy(readOnly = true, canConfigureModel = false)
        f.owner.openQuick(); f.owner.openModels()
        assertFalse(f.quick.single().first.editable); assertFalse(f.models.single().first.editable)
        f.state = f.state.copy(readOnly = false); f.owner.openQuick(); f.owner.openModels()
        assertTrue(f.quick.last().first.editable); assertFalse(f.models.last().first.editable)
    }
    @Test fun replacementClosesOnlyTheSameKindAndResultsBelongToTheCurrentWindow() {
        val f = Fixture(); f.owner.openQuick(); f.owner.openModels()
        val old = f.quick.single().second; old.pending = true
        f.owner.openQuick(); val current = f.quick.last().second; current.pending = true
        f.owner.completed(null)
        assertEquals(1, old.closes); assertTrue(old.results.isEmpty()); assertEquals(listOf<String?>(null), current.results)
        assertEquals(0, f.models.single().second.closes)
    }
    @Test fun resultReachesOnlyVisibleWaitingPanelsAndDoesNotDismissThem() {
        val f = Fixture(); f.owner.openQuick(); f.owner.openModels()
        val q = f.quick.single().second; val m = f.models.single().second
        q.pending = true; f.owner.completed("quick rejected")
        assertEquals(listOf("quick rejected"), q.results); assertTrue(m.results.isEmpty())
        m.pending = true; f.owner.completed(null)
        assertEquals(listOf<String?>(null), m.results); assertTrue(q.showing); assertTrue(m.showing)
        assertTrue(f.notices.isEmpty())
    }
    @Test fun dismissalRoutesAnOutstandingErrorToPersistentChatFeedback() {
        val f = Fixture(); f.owner.openQuick(); val q = f.quick.single().second
        q.pending = true; q.showing = false; f.owner.completed("dismissed rejection"); f.owner.completed(null)
        assertTrue(q.results.isEmpty()); assertEquals(listOf("dismissed rejection"), f.notices)
    }
    @Test fun reportsReachTheQuickPanelWithoutSettlingPendingOrChangingTheModelSnapshot() {
        val f = Fixture(); f.owner.openQuick(); f.owner.openModels()
        val q = f.quick.single().second; q.pending = true
        val partial = JSONObject().put("thinkingLevel", "high"); f.owner.update(partial)
        assertSame(partial, q.updates.single()); assertTrue(q.pending); assertTrue(q.results.isEmpty())
        assertEquals("low", f.models.single().first.catalog.effort)
        f.active = false; f.owner.update(JSONObject()); f.owner.completed("late")
        assertEquals(1, q.updates.size); assertTrue(f.notices.isEmpty())
    }
    @Test fun closeRetiresBothWindowsOnceAndRejectsLateResultsReportsAndOpenings() {
        val f = Fixture(); f.owner.openQuick(); f.owner.openModels(); f.owner.close(); f.owner.close()
        f.owner.openQuick(); f.owner.openModels(); f.owner.update(JSONObject()); f.owner.completed("late")
        assertEquals(1, f.quick.size); assertEquals(1, f.models.size)
        assertEquals(1, f.quick.single().second.closes); assertEquals(1, f.models.single().second.closes)
        assertTrue(f.quick.single().second.updates.isEmpty()); assertTrue(f.notices.isEmpty())
    }
    @Test fun closeDuringHostCreationDoesNotLeaveAnUnownedWindow() {
        for (quick in listOf(true, false)) {
            val f = Fixture(); f.created = f.owner::close
            if (quick) f.owner.openQuick() else f.owner.openModels()
            val window = (if (quick) f.quick.single().second else f.models.single().second)
            assertFalse(window.showing); assertEquals(1, window.closes)
        }
    }
}
