package ru.billyhargrove.pimobile.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

/** Models and capability ranges come only from the selected live Pi. */
class ModelSettingsSheet(context: Context, private val config: JSONObject, private val idle: Boolean, private val callback: TierApply) : ComposeSheet(context) {
    fun interface Apply { fun apply(provider: String, model: String, effort: String) }
    fun interface TierApply { fun apply(provider: String, model: String, effort: String, tier: String?) }
    constructor(context: Context, config: JSONObject, idle: Boolean, callback: Apply) : this(context, config, idle,
        TierApply { provider, model, effort, _ -> callback.apply(provider, model, effort) })
    private val models = config.optJSONArray("models").let { array -> (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) } }
    private var selected by mutableStateOf(models.firstOrNull { key(it) == config.optString("model") })
    private var preferred by mutableStateOf(config.optString("thinkingLevel", "off"))
    private var tier by mutableStateOf(config.optString("serviceTier", "standard"))
    private var pending by mutableStateOf(false)
    private var error by mutableStateOf("")
    init { content {
        var query by remember { mutableStateOf("") }
        val visible = models.filter { (key(it) + " " + it.optString("name")).contains(query, true) }
        val providers = models.map { it.optString("provider") }.distinct().size > 1
        val list = rememberLazyListState()
        LaunchedEffect(Unit) { val index = models.indexOf(selected); if (index >= 0) list.scrollToItem(index) }
        Column(Modifier.fillMaxWidth().heightIn(max = (context.resources.displayMetrics.heightPixels / context.resources.displayMetrics.density * .85f).dp)
            .navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Configure", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { dismiss() }, modifier = tag("closeModelPanel").size(48.dp)) {
                    Icon(painterResource(ru.billyhargrove.pimobile.R.drawable.ic_close), "Close model panel")
                }
            }
            Text(when { error.isNotEmpty() -> error; pending -> "Waiting for Pi to confirm…"; models.isEmpty() -> "Models unavailable. Run /reload in Pi when idle."
                !idle -> "Wait for the current task to finish"; else -> "This session only" + if (config.optBoolean("modelsTruncated")) " · First 1,000 models" else "" },
                fontSize = 12.sp, color = if (error.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (models.size > 12) TextField(query, { query = it }, colors = PiFieldColors(), singleLine = true, placeholder = { Text("Search models") },
                modifier = tag("modelSearch").fillMaxWidth())
            LazyColumn(state = list, modifier = tag("modelSelector").fillMaxWidth().weight(1f, fill = false).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))) {
                items(visible, key = ::key) { model ->
                    val chosen = selected?.let(::key) == key(model)
                    Row(Modifier.fillMaxWidth().background(if (chosen) MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(enabled = idle && !pending) { selected = model; error = "" }
                        .semantics { this.selected = chosen; contentDescription = model.optString("name", model.optString("id")) + ", " + model.optString("provider") + if (chosen) ", selected" else "" }
                        .padding(16.dp).heightIn(min = if (providers) 44.dp else 28.dp)) {
                        Column(Modifier.weight(1f)) { Text(model.optString("name", model.optString("id")), fontSize = 16.sp, fontWeight = if (chosen) FontWeight.Medium else FontWeight.Normal)
                            if (providers) Text(model.optString("provider"), Modifier.padding(top = 4.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (chosen) Text("✓", fontSize = 22.sp)
                    }
                }
            }
            Text(if (selected == null) "Select a model" else EffortSlider.label(preferred) + " effort", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            val model = selected
            AndroidView(factory = { EffortSlider(it).apply { id = ru.billyhargrove.pimobile.R.id.effortSelector } },
                update = { slider ->
                    // Reconfigure only when the capability owner changes, not each finger step.
                    val signature = key(model) + "|" + model?.optJSONArray("thinkingLevels")
                    if (slider.tag != signature) {
                        slider.tag = signature
                        slider.configure(model?.optJSONArray("thinkingLevels"), preferred) { value, _ -> preferred = value }
                        preferred = slider.value()
                    }
                    slider.isEnabled = idle && !pending && model != null
                }, modifier = Modifier.fillMaxWidth().height(64.dp))
            val fast = TierToggle.supportsFast(model?.optJSONArray("serviceTiers"))
            if (fast) TierChoice(tier, idle && !pending) { tier = it }
            Button(onClick = {
                val current = selected ?: return@Button
                if (!idle || pending) return@Button
                pending = true; error = ""
                callback.apply(current.optString("provider"), current.optString("id"), preferred, if (fast) tier else null)
            }, enabled = idle && !pending && selected != null, modifier = tag("applyModelButton").fillMaxWidth().heightIn(min = 56.dp)) { Text("Apply") }
        }
    } }
    fun applied() { pending = false; error = "" }
    fun failed(message: String) { pending = false; error = message }
    private fun tag(name: String) = Modifier.testTag("${context.packageName}:id/$name").semantics { testTagsAsResourceId = true }
    private fun key(model: JSONObject?) = model?.let { it.optString("provider") + "/" + it.optString("id") }.orEmpty()
}
