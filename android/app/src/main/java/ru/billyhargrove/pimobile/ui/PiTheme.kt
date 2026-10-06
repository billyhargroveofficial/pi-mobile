package ru.billyhargrove.pimobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import ru.billyhargrove.pimobile.R

/** Existing neutral resource palette owns both themes during the migration. */
@Composable
fun PiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = colorResource(R.color.accent), onPrimary = colorResource(R.color.on_accent),
        background = colorResource(R.color.bg), onBackground = colorResource(R.color.text_primary),
        surface = colorResource(R.color.surface), onSurface = colorResource(R.color.text_primary),
        surfaceVariant = colorResource(R.color.surface_alt), onSurfaceVariant = colorResource(R.color.text_secondary),
        outline = colorResource(R.color.outline), outlineVariant = colorResource(R.color.outline_soft),
        error = colorResource(R.color.danger), surfaceTint = Color.Transparent
    )) {
        // Existing screens choose their text sizes. Do not inherit bodyLarge line-height
        // and tracking into explicit 11–19sp baseline labels during incremental migration.
        CompositionLocalProvider(LocalTextStyle provides TextStyle.Default,
            LocalContentColor provides colorResource(R.color.text_primary), content = content)
    }
}
