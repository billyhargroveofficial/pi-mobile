package ru.billyhargrove.pimobile.core

import org.json.JSONObject

/** Active execution settings are distinct from the selected next request. */
class ExecutionInfo(configuration: JSONObject?, running: Boolean) {
    @JvmField val model: String
    @JvmField val effort: String
    @JvmField val tier: String
    @JvmField val confirmed: Boolean
    @JvmField val available: Boolean
    init {
        var source = configuration ?: JSONObject()
        val active = if (running) source.optJSONObject("execution") else null
        if (active != null && active.optString("model").isNotEmpty()) source = active
        model = source.optString("model", "").substringAfterLast('/')
        effort = source.optString("thinkingLevel", "off")
        tier = source.optString("serviceTier", "unknown")
        confirmed = source.optBoolean("tierConfirmed")
        val tiers = configuration?.optJSONArray("serviceTiers")
        available = if (active != null) tier != "unknown" else tiers != null && tiers.length() > 0
    }
    fun tierLabel(running: Boolean) = when {
        !available -> "Tier unavailable"
        tier == "fast" -> if (running && !confirmed) "Fast requested" else "Fast"
        else -> "Standard"
    }
}
