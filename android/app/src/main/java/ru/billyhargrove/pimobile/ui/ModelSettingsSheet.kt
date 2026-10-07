package ru.billyhargrove.pimobile.ui

import android.content.Context
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import ru.billyhargrove.pimobile.features.chat.ModelSettingsScreen
import ru.billyhargrove.pimobile.features.chat.ModelSettingsSession

/** Platform modal adapter for the session-local model draft owner. */
class ModelSettingsSheet(context: Context, config: JSONObject, idle: Boolean, callback: TierApply) : ComposeSheet(context) {
    fun interface Apply { fun apply(provider: String, model: String, effort: String) }
    fun interface TierApply { fun apply(provider: String, model: String, effort: String, tier: String?) }
    constructor(context: Context, config: JSONObject, idle: Boolean, callback: Apply) : this(context, config, idle,
        TierApply { provider, model, effort, _ -> callback.apply(provider, model, effort) })
    private val owner = ModelSettingsSession(config, idle) { callback.apply(it.provider, it.model, it.effort, it.tier) }
    internal val awaitingResult get() = owner.pending
    init {
        content { ModelSettingsScreen(owner, (context.resources.displayMetrics.heightPixels / context.resources.displayMetrics.density * .85f).dp,
            "${context.packageName}:id/", ::dismiss) }
        setOnDismissListener { owner.close() }
    }
    fun applied() = owner.applied()
    fun failed(message: String) = owner.failed(message)
}
