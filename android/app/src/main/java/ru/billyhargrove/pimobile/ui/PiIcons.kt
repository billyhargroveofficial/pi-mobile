package ru.billyhargrove.pimobile.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import ru.billyhargrove.pimobile.R

/** Circular control with an explicit description on the actual clickable semantics node. */
@Composable
fun CircleIcon(icon: Int, description: String, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }; val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .92f else 1f, if (ExpressiveMotion.enabled()) spring(.7f, 500f) else snap(), label = "iconPress")
    IconButton(onClick, enabled = enabled, interactionSource = interaction, modifier = Modifier.size(48.dp).scale(scale)
        .background(androidx.compose.ui.graphics.Color.Transparent, CircleShape).testTag(tag).clearAndSetSemantics {
            testTagsAsResourceId = true; contentDescription = description; role = Role.Button
            if (enabled) onClick { onClick(); true } else disabled()
        }) { Icon(painterResource(icon), null, modifier = Modifier.size(22.dp), tint = colorResource(R.color.text_primary)) }
}
