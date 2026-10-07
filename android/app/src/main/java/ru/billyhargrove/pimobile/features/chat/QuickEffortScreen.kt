package ru.billyhargrove.pimobile.features.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import ru.billyhargrove.pimobile.R
import ru.billyhargrove.pimobile.ui.EffortSlider

@Composable internal fun QuickEffortScreen(owner: QuickEffortSession, prefix: String, openModels: () -> Unit, dismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)
        .padding(16.dp)) {
        if (owner.error.isNotEmpty()) Text(owner.error, Modifier.panelTag(prefix, "effortError").padding(bottom = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = openModels, enabled = owner.canOpenModels,
                modifier = Modifier.panelTag(prefix, "popupModelButton").weight(1f).heightIn(min = 48.dp)) {
                Text(owner.model.name + " ▾", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            if (owner.showTier) {
                val fast = owner.tier == "fast"
                TextButton(onClick = owner::toggleTier, enabled = owner.canChangeTier,
                    modifier = Modifier.panelTag(prefix, "quickTierButton").heightIn(min = 48.dp).semantics { contentDescription = "Processing tier: ${if (fast) "Fast" else "Standard"}. Switch to ${if (fast) "Standard" else "Fast"}" }) {
                    Text(if (fast) "ϟ Fast" else "Standard", fontSize = 11.sp)
                }
            }
            IconButton(onClick = dismiss, modifier = Modifier.panelTag(prefix, "closeEffortPanel").size(48.dp)) {
                Icon(painterResource(R.drawable.ic_close), "Close effort panel")
            }
        }
        Row(Modifier.panelTag(prefix, "effortTitle").padding(top = 12.dp, bottom = 16.dp)) {
            Text("Effort  ", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(ModelCapabilities.label(owner.selected), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }
        Row(Modifier.fillMaxWidth()) {
            Text("Faster", Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Smarter", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AndroidView(factory = { EffortSlider(it).apply { id = R.id.quickEffortSlider } }, update = { slider ->
            val levels = owner.model.levels
            val signature = owner.model.sliderKey
            if (slider.tag != signature) {
                slider.tag = signature
                slider.configureLevels(levels, owner.selected, owner::preview)
            }
            val index = levels.indexOf(owner.selected)
            if (index >= 0 && slider.getProgress() != index) slider.setProgress(index)
            slider.isEnabled = owner.canEdit && levels.size > 1
        }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(64.dp))
    }
}
private fun Modifier.panelTag(prefix: String, name: String) = testTag(prefix + name).semantics { testTagsAsResourceId = true }
