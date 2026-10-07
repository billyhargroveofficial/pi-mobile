package ru.billyhargrove.pimobile.features.chat

import org.json.JSONArray
import org.json.JSONObject

/** Session-reported capabilities copied once, without UI or transport dependencies. */
internal object ModelCapabilities {
    private val allowedLevels = setOf("off", "minimal", "low", "medium", "high", "xhigh", "max")
    data class Model(val provider: String, val id: String, val name: String, val levels: List<String>, val fast: Boolean) {
        val key = "$provider/$id"
        val searchText = "$key $name"
        val sliderKey = key to levels
    }
    data class Catalog(val models: List<Model>, val current: String, val effort: String, val tier: String, val truncated: Boolean) {
        val selected = models.firstOrNull { it.key == current }
        val providers = models.map { it.provider }.distinct().size > 1
        val byKey = models.associateBy { it.key }
    }
    fun project(config: JSONObject): Catalog {
        val array = config.optJSONArray("models")
        val seen = mutableSetOf<String>()
        val models = (0 until minOf(1000, array?.length() ?: 0)).mapNotNull { index ->
            val value = array?.optJSONObject(index) ?: return@mapNotNull null
            val provider = value.opt("provider") as? String; val id = value.opt("id") as? String
            if (provider.isNullOrEmpty() || id.isNullOrEmpty() || !seen.add("$provider/$id")) return@mapNotNull null
            model(value)
        }
        return Catalog(models, config.optString("model"), config.optString("thinkingLevel", "off"), tier(config.optString("serviceTier")),
            config.optBoolean("modelsTruncated") || (array?.length() ?: 0) > 1000)
    }
    /** A standalone quick panel may have capabilities even without registry identity. */
    fun model(value: JSONObject) = Model(value.optString("provider"), value.optString("id"),
        (value.opt("name") as? String)?.takeIf { it.isNotEmpty() } ?: value.optString("id"), levels(value.optJSONArray("thinkingLevels")), supportsFast(value.optJSONArray("serviceTiers")))
    fun unavailable(key: String) = Model(key.substringBefore('/'), key.substringAfter('/', key), key.substringAfter('/', key), emptyList(), false)
    fun levels(values: JSONArray?): List<String> = buildSet {
        for (index in 0 until (values?.length() ?: 0)) {
            (values?.opt(index) as? String)?.takeIf { it in allowedLevels }?.let(::add)
            if (size == allowedLevels.size) break
        }
    }.toList()
    fun supportsFast(values: JSONArray?) = (0 until (values?.length() ?: 0)).any { values?.opt(it) == "fast" }
    fun fit(levels: List<String>, preferred: String) = preferred.takeIf { it in levels } ?: levels.firstOrNull() ?: "off"
    fun reported(levels: List<String>, preferred: String) = if (levels.isEmpty() && preferred in allowedLevels) preferred else fit(levels, preferred)
    fun tier(value: String?) = if (value == "fast") "fast" else "standard"
    fun label(value: String) = when (value) {
        "off" -> "Off"; "minimal" -> "Minimal"; "low" -> "Low"; "medium" -> "Medium"
        "high" -> "High"; "xhigh" -> "Extra High"; "max" -> "Max"; else -> value
    }
}
