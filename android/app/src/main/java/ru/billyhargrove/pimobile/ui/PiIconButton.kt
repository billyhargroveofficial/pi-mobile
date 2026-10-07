package ru.billyhargrove.pimobile.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import ru.billyhargrove.pimobile.R

/** Shared 48dp icon action, accessibility role, optional loading and neutral surfaces. */
@Composable
fun PiIconButton(icon: Int, description: String, tag: String, enabled: Boolean = true,
    tint: Color = colorResource(R.color.text_primary), filled: Boolean = false, soft: Boolean = false,
    loading: Boolean = false, loadingTag: String = "", onClick: () -> Unit) {
    Box(if (loading && loadingTag.isNotEmpty()) Modifier.testTag(loadingTag) else Modifier) {
    IconButton(onClick, enabled = enabled, modifier = Modifier.size(48.dp).testTag(tag)
        .clip(CircleShape).background(if (soft) colorResource(R.color.surface_alt) else Color.Transparent)
        .clearAndSetSemantics {
            testTagsAsResourceId = true; contentDescription = description; role = Role.Button
            if (enabled) onClick { onClick(); true } else disabled()
        }) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (filled) colorResource(R.color.accent) else Color.Transparent),
            contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = tint, strokeWidth = 2.dp)
            else Icon(painterResource(icon), null, tint = if (enabled) tint else tint.copy(alpha = .38f), modifier = Modifier.size(22.dp))
        }
    }
    }
}
