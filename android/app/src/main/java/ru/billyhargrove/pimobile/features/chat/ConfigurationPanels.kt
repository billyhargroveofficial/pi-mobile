package ru.billyhargrove.pimobile.features.chat

import org.json.JSONObject

/** Opening policy and local result/window ownership. Hosts retain Android geometry and rendering. */
internal class ConfigurationPanels(private val available: () -> Boolean, private val state: () -> State,
    private val showQuick: (Quick) -> QuickPanel, private val showModels: (Models) -> Panel,
    private val notice: (String) -> Unit) {
    data class State(val configuration: JSONObject?, val connected: Boolean, val pending: Boolean,
        val readOnly: Boolean, val canConfigureModel: Boolean)
    data class Quick(val model: ModelCapabilities.Model, val effort: String, val tier: String, val editable: Boolean)
    data class Models(val catalog: ModelCapabilities.Catalog, val editable: Boolean)
    interface Panel {
        val showing: Boolean
        val pending: Boolean
        fun completed(error: String?)
        fun close()
    }
    interface QuickPanel : Panel { fun update(configuration: JSONObject) }
    private var quick: QuickPanel? = null
    private var models: Panel? = null
    private var closed = false
    private val active get() = !closed && available()

    fun openQuick() {
        if (!active) return
        val current = state()
        if (current.pending) return
        val config = current.configuration
        if (config == null || !current.connected) { notice("Connect to Pi first"); return }
        val selected = ModelCapabilities.find(config)
        if (selected == null) { openModels(); return }
        val previous = quick; quick = null; previous?.close()
        if (!active) return
        val next = showQuick(Quick(selected, config.optString("thinkingLevel", "off"),
            ModelCapabilities.tier(config.optString("serviceTier")), !current.readOnly))
        if (active) quick = next else next.close()
    }
    fun openModels() {
        if (!active) return
        val current = state()
        if (current.pending) return
        val config = current.configuration
        if (config?.optJSONArray("models") == null) {
            notice("Run /reload in Pi when idle to load models and effort levels."); return
        }
        val catalog = ModelCapabilities.project(config)
        val previous = models; models = null; previous?.close()
        if (!active) return
        val next = showModels(Models(catalog, current.canConfigureModel))
        if (active) models = next else next.close()
    }
    fun update(configuration: JSONObject) { if (active) quick?.update(configuration) }
    fun completed(error: String?) {
        if (!active) return
        val waitingQuick = quick?.takeIf { it.showing && it.pending }
        val waitingModels = models?.takeIf { it.showing && it.pending }
        waitingQuick?.completed(error); waitingModels?.completed(error)
        if (error != null && waitingQuick == null && waitingModels == null) notice(error)
    }
    fun close() {
        if (closed) return
        closed = true
        val oldModels = models; val oldQuick = quick
        models = null; quick = null
        oldModels?.close(); oldQuick?.close()
    }
}
