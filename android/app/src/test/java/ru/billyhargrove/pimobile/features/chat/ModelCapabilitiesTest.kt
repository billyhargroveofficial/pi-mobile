package ru.billyhargrove.pimobile.features.chat

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ModelCapabilitiesTest {
    private fun model(id: String, provider: String = "test") = JSONObject().put("id", id).put("provider", provider).put("name", "Model $id")
        .put("thinkingLevels", JSONArray().put("high").put("low")).put("serviceTiers", JSONArray().put("standard").put("fast"))
    @Test fun invalidIdentitiesAndDuplicateModelsCannotCreateListKeysOrCommands() {
        val config = JSONObject().put("model", "test/a").put("models", JSONArray().put(1).put(JSONObject()).put(model("")).put(model("a", ""))
            .put(model("a").put("provider", JSONObject())).put(model("a")).put(model("a").put("name", "Duplicate")).put(model("a", "other")))
        val catalog = ModelCapabilities.project(config)
        assertEquals(listOf("test/a", "other/a"), catalog.models.map { it.key }); assertEquals("Model a", catalog.selected!!.name)
        assertTrue(catalog.providers); assertEquals(2, catalog.byKey.size)
    }
    @Test fun capabilitiesOnlyAcceptExactReportedValuesPreservingTheirOrder() {
        val levels = ModelCapabilities.levels(JSONArray().put("high").put("low").put("high").put("unknown").put(2).put(JSONObject.NULL).put(JSONObject()).put(""))
        assertEquals(listOf("high", "low"), levels)
        assertTrue(ModelCapabilities.supportsFast(JSONArray().put("fast")))
        assertFalse(ModelCapabilities.supportsFast(JSONArray().put("FAST").put(JSONObject().put("name", "fast"))))
        assertEquals("standard", ModelCapabilities.tier("unknown")); assertEquals("fast", ModelCapabilities.tier("fast"))
    }
    @Test fun snapshotCopiesModelsCapabilitiesAndNames() {
        val model = model("a"); val config = JSONObject().put("model", "test/a").put("thinkingLevel", "low").put("models", JSONArray().put(model))
        val catalog = ModelCapabilities.project(config)
        model.put("name", "Changed"); model.getJSONArray("thinkingLevels").put("max"); config.put("model", "test/b")
        assertEquals("Model a", catalog.selected!!.name); assertEquals(listOf("high", "low"), catalog.selected.levels); assertEquals("test/a", catalog.current)
        assertEquals("test/a Model a", catalog.selected.searchText)
    }
    @Test fun capabilityFallbackDoesNotInventAnEditableLevelForUnreportedModel() {
        assertEquals("medium", ModelCapabilities.fit(listOf("medium", "xhigh"), "high"))
        assertEquals("xhigh", ModelCapabilities.fit(listOf("medium", "xhigh"), "xhigh"))
        assertEquals("off", ModelCapabilities.fit(emptyList(), "high"))
        val unknown = ModelCapabilities.unavailable("provider/new-model")
        assertEquals("new-model", unknown.name); assertTrue(unknown.levels.isEmpty()); assertFalse(unknown.fast)
        assertEquals("high", ModelCapabilities.reported(unknown.levels, "high"))
    }
    @Test fun registryIsBoundedAndExplicitTruncationIsPreserved() {
        val config = JSONObject().put("models", JSONArray((0..1000).map { model("$it") }))
        val catalog = ModelCapabilities.project(config); assertEquals(1000, catalog.models.size); assertTrue(catalog.truncated)
        assertTrue(ModelCapabilities.project(JSONObject().put("modelsTruncated", true)).truncated)
        assertFalse(ModelCapabilities.project(JSONObject()).truncated)
    }
    @Test fun duplicateModelsDoNotReparseTheirCapabilityArrays() {
        val counted = object : JSONObject() {
            var reads = 0
            override fun optJSONArray(key: String?): JSONArray? { reads++; return super.optJSONArray(key) }
        }
        counted.put("provider", "test").put("id", "a").put("thinkingLevels", JSONArray().put("low"))
        val catalog = ModelCapabilities.project(JSONObject().put("models", JSONArray().put(counted).put(counted)))
        assertEquals(1, catalog.models.size); assertEquals(2, counted.reads) // levels and tiers once
    }
    @Test fun quickSelectionMatchesCatalogValidationFirstDuplicateAndRegistryBound() {
        val config = JSONObject().put("model", "test/a").put("models", JSONArray().put(1)
            .put(model("a").put("provider", JSONObject())).put(model("a")).put(model("a").put("name", "Duplicate")))
        assertEquals(ModelCapabilities.project(config).selected, ModelCapabilities.find(config))
        assertEquals("Model a", ModelCapabilities.find(config)!!.name)
        assertNull(ModelCapabilities.find(JSONObject().put("model", "test/a")))
        config.put("models", JSONArray((0..1000).map { model("$it") })).put("model", "test/1000")
        assertNull(ModelCapabilities.find(config)); assertNotNull(ModelCapabilities.find(config, "test/999"))
    }
    @Test fun quickSelectionReadsCapabilitiesOnlyForTheSelectedModel() {
        val values = (0 until 1000).map { index -> object : JSONObject() {
            var reads = 0
            override fun optJSONArray(key: String?): JSONArray? { reads++; return super.optJSONArray(key) }
        }.apply { put("provider", "test"); put("id", "$index"); put("thinkingLevels", JSONArray().put("low")) } }
        val config = JSONObject().put("model", "test/999").put("models", JSONArray(values))
        assertEquals("test/999", ModelCapabilities.find(config)!!.key)
        assertEquals(2, values.sumOf { it.reads }); assertEquals(0, values.first().reads)
    }
}
