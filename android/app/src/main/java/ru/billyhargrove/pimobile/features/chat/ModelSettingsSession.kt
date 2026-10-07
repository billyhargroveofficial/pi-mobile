package ru.billyhargrove.pimobile.features.chat

import androidx.compose.runtime.*
import org.json.JSONObject

/** Owns the draft and explicit Apply; confirmed wire commands remain in ChatSession. */
internal class ModelSettingsSession(config: JSONObject, private val idle: Boolean, private val apply: (Selection) -> Unit) {
    data class Selection(val provider: String, val model: String, val effort: String, val tier: String?)
    val catalog = ModelCapabilities.project(config)
    var selected by mutableStateOf(catalog.selected); private set
    var effort by mutableStateOf(ModelCapabilities.fit(selected?.levels.orEmpty(), catalog.effort)); private set
    var tier by mutableStateOf(catalog.tier); private set
    var query by mutableStateOf(""); private set
    var visible by mutableStateOf(catalog.models); private set
    var pending by mutableStateOf(false); private set
    var error by mutableStateOf(""); private set
    private var closed = false
    val editable get() = idle && !pending && !closed
    val canApply get() = editable && selected != null
    val notice get() = when {
        error.isNotEmpty() -> error; pending -> "Waiting for Pi to confirm…"
        catalog.models.isEmpty() -> "Models unavailable. Run /reload in Pi when idle."
        !idle -> "Wait for the current task to finish"
        else -> "This session only" + if (catalog.truncated) " · First 1,000 models" else ""
    }
    fun search(text: String) {
        if (closed || text == query) return
        query = text; visible = catalog.models.filter { it.searchText.contains(text, ignoreCase = true) }
    }
    fun select(key: String) {
        if (!editable) return
        val model = catalog.byKey[key] ?: return
        selected = model; effort = ModelCapabilities.fit(model.levels, effort); error = ""
    }
    fun chooseEffort(value: String) { if (editable && value in selected?.levels.orEmpty()) effort = value }
    fun chooseTier(value: String) { if (editable && selected?.fast == true && value in setOf("standard", "fast")) tier = value }
    fun submit() {
        val model = selected ?: return
        if (!canApply) return
        pending = true; error = ""
        try { apply(Selection(model.provider, model.id, effort, tier.takeIf { model.fast })) }
        catch (cause: Exception) { failed(cause.message ?: "Could not apply settings") }
    }
    fun applied() { if (!closed && pending) { pending = false; error = "" } }
    fun failed(message: String) { if (!closed && pending) { pending = false; error = message } }
    fun close() { closed = true; pending = false }
}
