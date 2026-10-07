package ru.billyhargrove.pimobile.ui

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import org.json.JSONArray

/** Session-local priority request; provider confirmation remains a separate status. */
class TierToggle(context: Context) : FrameLayout(context) {
    private var available by mutableStateOf(false)
    private var choice by mutableStateOf("standard")
    private var editable by mutableStateOf(true)
    init { addView(ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
        setContent { PiTheme { if (available) TierChoice(choice, editable) { choice = it } } }
    }, LayoutParams(-1, -2)) }
    fun configure(tiers: JSONArray?, preferred: String?) {
        available = supportsFast(tiers); choice = if (preferred == "fast") "fast" else "standard"
        visibility = if (available) View.VISIBLE else View.GONE
    }
    fun value(): String? = if (available) choice else null
    override fun setEnabled(enabled: Boolean) { super.setEnabled(enabled); editable = enabled }
    companion object { @JvmStatic fun supportsFast(tiers: JSONArray?) = (0 until (tiers?.length() ?: 0)).any { tiers?.optString(it) == "fast" } }
}

@Composable
fun TierChoice(value: String, enabled: Boolean, choose: (String) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (tier in listOf("standard", "fast")) {
                val name = if (tier == "fast") "Fast" else "Standard"
                TextButton(onClick = { choose(tier) }, enabled = enabled,
                    colors = ButtonDefaults.textButtonColors(containerColor = if (value == tier) MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .testTag("${context.packageName}:id/${if (tier == "fast") "tierFast" else "tierStandard"}")
                    .semantics { testTagsAsResourceId = true; selected = value == tier; contentDescription = name + if (value == tier) ", selected" else "" }) {
                    Text((if (value == tier) "✓ " else "") + if (tier == "fast") "ϟ Fast" else name)
                }
            }
        }
        Text(if (value == "fast") "Requests priority processing; subject to provider availability." else "Standard processing for this session.",
            Modifier.padding(top = 8.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
