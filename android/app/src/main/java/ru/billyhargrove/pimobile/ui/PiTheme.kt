package ru.billyhargrove.pimobile.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.billyhargrove.pimobile.R

/** One neutral native design system; modal surfaces must not inherit Material's purple defaults. */
@Composable
fun PiTheme(content: @Composable () -> Unit) {
    val base = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    val ink = colorResource(R.color.text_primary)
    val muted = colorResource(R.color.text_secondary)
    val surface = colorResource(R.color.surface)
    MaterialTheme(colorScheme = base.copy(
        primary = colorResource(R.color.accent), onPrimary = colorResource(R.color.on_accent),
        primaryContainer = colorResource(R.color.accent_soft), onPrimaryContainer = ink,
        secondary = muted, onSecondary = colorResource(R.color.bg),
        secondaryContainer = colorResource(R.color.secondary_soft), onSecondaryContainer = ink,
        tertiary = muted, onTertiary = colorResource(R.color.bg),
        tertiaryContainer = colorResource(R.color.secondary_soft), onTertiaryContainer = ink,
        background = colorResource(R.color.bg), onBackground = ink,
        surface = surface, onSurface = ink,
        surfaceVariant = colorResource(R.color.surface_alt), onSurfaceVariant = muted,
        surfaceDim = surface, surfaceBright = surface,
        surfaceContainerLowest = colorResource(R.color.bg), surfaceContainerLow = surface,
        surfaceContainer = surface, surfaceContainerHigh = surface, surfaceContainerHighest = colorResource(R.color.surface_alt),
        inverseSurface = ink, inverseOnSurface = colorResource(R.color.bg), inversePrimary = colorResource(R.color.bg),
        outline = colorResource(R.color.outline), outlineVariant = colorResource(R.color.outline_soft),
        error = colorResource(R.color.danger), onError = colorResource(R.color.bg),
        errorContainer = colorResource(R.color.danger_soft), onErrorContainer = colorResource(R.color.danger),
        surfaceTint = Color.Transparent
    ), typography = Typography(
        headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 36.sp),
        headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 30.sp),
        titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
        titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
        titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = "tnum"),
        labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = .3.sp),
        labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp)
    ), shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp)
    )) {
        // Dense metadata specifies its own size; do not inherit bodyLarge line height into 11sp labels.
        CompositionLocalProvider(LocalTextStyle provides TextStyle.Default,
            LocalContentColor provides ink, content = content)
    }
}

/** Filled fields share the borderless surface model, including error/focus states. */
@Composable
fun PiFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    errorContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent
)
