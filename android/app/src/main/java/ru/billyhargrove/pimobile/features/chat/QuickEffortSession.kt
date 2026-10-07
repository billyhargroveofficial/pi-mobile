package ru.billyhargrove.pimobile.features.chat

import androidx.compose.runtime.*
import org.json.JSONObject

/** Preview/commit, pending and confirmed rollback state for one anchored panel. */
internal class QuickEffortSession(initial: JSONObject, current: String, private val editable: Boolean,
    currentTier: String?, private val apply: (String) -> Unit, private val changeTier: ((String) -> Unit)?) {
    var model by mutableStateOf(ModelCapabilities.model(initial)); private set
    var selected by mutableStateOf(ModelCapabilities.reported(model.levels, current)); private set
    var tier by mutableStateOf(ModelCapabilities.tier(currentTier)); private set
    var pending by mutableStateOf(false); private set
    var error by mutableStateOf(""); private set
    private var confirmedLevel = selected; private var confirmedTier = tier
    private var closed = false; private var configurationRevision = 0L
    private var effortRevision = 0L; private var tierRevision = 0L
    private data class Change(val effort: String?, val tier: String?, val revision: Long)
    private var requested: Change? = null
    val canEdit get() = editable && !pending && !closed
    val canChangeTier get() = canEdit && model.fast && changeTier != null
    val canOpenModels get() = !pending && !closed
    val showTier get() = model.fast && changeTier != null
    fun preview(value: String, committed: Boolean) {
        if (!canEdit || value !in model.levels) return
        selected = value
        if (committed && value != confirmedLevel) send(Change(value, null, configurationRevision)) { apply(value) }
    }
    fun toggleTier() {
        if (!canChangeTier) return
        tier = if (tier == "fast") "standard" else "fast"
        send(Change(null, tier, configurationRevision)) { changeTier?.invoke(tier) }
    }
    private fun send(change: Change, action: () -> Unit) {
        requested = change; pending = true; error = ""
        try { action() } catch (cause: Exception) { completed(cause.message ?: "Could not apply settings") }
    }
    fun updateConfiguration(config: JSONObject) {
        if (closed) return
        val catalog = ModelCapabilities.project(config)
        val previous = model; configurationRevision++
        if (config.has("model")) model = catalog.selected ?: if (!config.has("models") && catalog.current == model.key) model else ModelCapabilities.unavailable(catalog.current)
        else catalog.byKey[model.key]?.let { model = it }
        if (config.has("thinkingLevel") || previous.sliderKey != model.sliderKey) {
            confirmedLevel = ModelCapabilities.reported(model.levels, config.optString("thinkingLevel", confirmedLevel))
            selected = confirmedLevel; effortRevision = configurationRevision
        }
        if (config.has("serviceTier") || previous.key != model.key || previous.fast != model.fast) {
            confirmedTier = ModelCapabilities.tier(config.optString("serviceTier", confirmedTier))
            tier = confirmedTier; tierRevision = configurationRevision
        }
    }
    /** ChatSession scopes ACKs; only a panel that actually sent a change consumes them. */
    fun completed(failure: String?) {
        if (closed || !pending) return
        val sent = requested
        pending = false; requested = null; error = failure.orEmpty()
        if (failure != null) { selected = confirmedLevel; tier = confirmedTier }
        else if (sent != null) {
            sent.effort?.takeIf { sent.revision >= effortRevision }?.let { confirmedLevel = it }
            sent.tier?.takeIf { sent.revision >= tierRevision }?.let { confirmedTier = it }
        }
    }
    fun close() { closed = true; pending = false; requested = null }
}
