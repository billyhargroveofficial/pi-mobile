package ru.billyhargrove.pimobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/** Reveals an action; the feature must still ask before changing authoritative data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeAction(enabled: Boolean, label: String, onAction: () -> Unit, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        if (value == SwipeToDismissBoxValue.EndToStart) onAction()
        false
    })
    SwipeToDismissBox(state, enableDismissFromStartToEnd = false, modifier = Modifier.clip(RoundedCornerShape(18.dp)).semantics {
        customActions = listOf(CustomAccessibilityAction(label) { onAction(); true })
    }, backgroundContent = {
        // Do not paint a destructive surface behind a plain list while settled:
        // rounded clipping can otherwise leak a faint red edge through its canvas.
        if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart)
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp), contentAlignment = Alignment.CenterEnd) {
                Text(label, color = MaterialTheme.colorScheme.onErrorContainer)
            }
    }) { content() }
}
