package ru.billyhargrove.pimobile.features.chat

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

import ru.billyhargrove.pimobile.ui.EffortSlider
import ru.billyhargrove.pimobile.ui.PiFieldColors
import ru.billyhargrove.pimobile.ui.TierChoice

@Composable internal fun ModelSettingsScreen(owner: ModelSettingsSession, height: Dp, prefix: String, dismiss: () -> Unit) {
    val models = owner.catalog.models; val providers = owner.catalog.providers; val selected = owner.selected
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { val index = models.indexOf(selected); if (index >= 0) list.scrollToItem(index) }
    Column(Modifier.fillMaxWidth().heightIn(max = height)
        .navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Configure", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = dismiss, modifier = Modifier.panelTag(prefix, "closeModelPanel").size(48.dp)) {
                Icon(painterResource(ru.billyhargrove.pimobile.R.drawable.ic_close), "Close model panel")
            }
        }
        Text(owner.notice, fontSize = 12.sp, color = if (owner.error.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        if (models.size > 12) TextField(owner.query, owner::search, colors = PiFieldColors(), singleLine = true, placeholder = { Text("Search models") },
            modifier = Modifier.panelTag(prefix, "modelSearch").fillMaxWidth())
        LazyColumn(state = list, modifier = Modifier.panelTag(prefix, "modelSelector").fillMaxWidth().weight(1f, fill = false).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))) {
            items(owner.visible, key = { it.key }) { model ->
                val chosen = selected?.key == model.key
                Row(Modifier.fillMaxWidth().background(if (chosen) MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(enabled = owner.editable) { owner.select(model.key) }
                    .semantics { this.selected = chosen; contentDescription = model.name + ", " + model.provider + if (chosen) ", selected" else "" }
                    .padding(16.dp).heightIn(min = if (providers) 44.dp else 28.dp)) {
                    Column(Modifier.weight(1f)) { Text(model.name, fontSize = 16.sp, fontWeight = if (chosen) FontWeight.Medium else FontWeight.Normal)
                        if (providers) Text(model.provider, Modifier.padding(top = 4.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (chosen) Text("✓", fontSize = 22.sp)
                }
            }
        }
        Text(if (selected == null) "Select a model" else ModelCapabilities.label(owner.effort) + " effort", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        val model = selected
        AndroidView(factory = { EffortSlider(it).apply { id = ru.billyhargrove.pimobile.R.id.effortSelector } },
            update = { slider ->
                val signature = model?.sliderKey ?: ("" to emptyList<String>())
                if (slider.tag != signature) {
                    slider.tag = signature
                    slider.configureLevels(model?.levels.orEmpty(), owner.effort) { value, _ -> owner.chooseEffort(value) }
                }
                val index = model?.levels?.indexOf(owner.effort) ?: -1
                if (index >= 0 && slider.getProgress() != index) slider.setProgress(index)
                slider.isEnabled = owner.editable && model != null
            }, modifier = Modifier.fillMaxWidth().height(64.dp))
        if (model?.fast == true) TierChoice(owner.tier, owner.editable, owner::chooseTier)
        Button(onClick = owner::submit, enabled = owner.canApply, modifier = Modifier.panelTag(prefix, "applyModelButton").fillMaxWidth().heightIn(min = 56.dp)) { Text("Apply") }
    }
}
private fun Modifier.panelTag(prefix: String, name: String) = testTag(prefix + name).semantics { testTagsAsResourceId = true }
