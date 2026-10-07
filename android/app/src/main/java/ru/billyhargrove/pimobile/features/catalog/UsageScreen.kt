package ru.billyhargrove.pimobile.features.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.*
import kotlin.math.roundToInt
import ru.billyhargrove.pimobile.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun UsageScreen(owner: UsageSession, prefix: String, modifier: Modifier = Modifier) {
    if (!owner.visible) return
    val providers = owner.providers
    // Three compact INFORMATION lines, one large accessible action. We do not
    // turn 28dp labels into three undersized, overlapping touch targets.
    Column(modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(prefix + "usageCards").semantics {
        testTagsAsResourceId = true; role = Role.Button
        contentDescription = "Usage limits. Show all provider details"
        customActions = providers.map { provider -> CustomAccessibilityAction("Show ${provider.name} usage") { owner.select(provider.key); true } }
    }.clickable { owner.select("all") }.padding(vertical = 4.dp)) {
        for (provider in providers) {
            val window = provider.primary; val used = window?.used
            val stale = provider.stale; val reported = provider.quotas
            val ink = MaterialTheme.colorScheme.onSurfaceVariant
            val summary = buildAnnotatedString {
                if (used == null) append("Unavailable · check in Orca")
                else {
                    reported.take(3).forEachIndexed { index, quota ->
                        if (index > 0) append(" · ")
                        val percent = quota.used
                        withStyle(SpanStyle(color = if (stale) ink else usageColor(percent))) { append("${percent.roundToInt()}%") }
                        append(" ${quota.shortName}")
                    }
                    if (reported.size > 3) append(" · +${reported.size - 3} limits")
                    append(" · ")
                    append(if (stale) "cached" else provider.resetSummary)
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 28.dp).testTag(prefix + "usage" + provider.name)
                .semantics { testTagsAsResourceId = true; contentDescription = "${provider.name}, $summary" },
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ProviderIcon(provider.key)
                Box(Modifier.width(40.dp)) {
                    if (used != null) Meter(used)
                    else Spacer(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.outlineVariant))
                }
                Text(summary, Modifier.weight(1f).padding(vertical = 3.dp), style = MaterialTheme.typography.bodySmall, color = ink)
            }
        }
    }
    owner.selected?.let { choice ->
        ModalBottomSheet(onDismissRequest = { owner.select(null) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(20.dp).testTag(prefix + "usageDetails").semantics { testTagsAsResourceId = true }, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("Usage", style = MaterialTheme.typography.titleLarge)
                Text("Reported by your connected Orca", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for (provider in providers.filter { choice == "all" || it.key == choice }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProviderIcon(provider.key)
                            Text(provider.name + " usage", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
                        }
                        val quotas = provider.quotas
                        if (quotas.isEmpty()) Text("Orca has not provided usage limits for this account. Open Orca to check the account connection.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        quotas.forEach { quota ->
                            val used = quota.used
                            Row { Text(quota.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text("${used.roundToInt()}% used", style = MaterialTheme.typography.bodyMedium, color = usageColor(used)) }
                            Meter(used)
                            Text(quota.reset,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (provider.updated.isNotEmpty()) Text(provider.updated,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
@Composable private fun ProviderIcon(provider: String) {
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        val codex = provider == "codex"
        Icon(painterResource(when (provider) { "codex" -> R.drawable.ic_provider_codex; "cursor" -> R.drawable.ic_provider_cursor; else -> R.drawable.ic_provider_grok }),
            null, Modifier.size(if (codex) 28.dp else 14.dp), tint = if (codex) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun Meter(used: Double) {
    val fraction = (used / 100).toFloat().coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp))
        .background(MaterialTheme.colorScheme.outlineVariant).clearAndSetSemantics {}) {
        if (fraction > 0f) Spacer(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(usageColor(used), RoundedCornerShape(1.dp)))
    }
}
@Composable private fun usageColor(used: Double) = when { used >= 95 -> MaterialTheme.colorScheme.error; used >= 75 -> androidx.compose.ui.res.colorResource(R.color.warning); else -> MaterialTheme.colorScheme.onSurface }
