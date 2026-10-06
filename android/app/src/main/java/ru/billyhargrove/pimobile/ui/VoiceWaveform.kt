package ru.billyhargrove.pimobile.ui

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import ru.billyhargrove.pimobile.R

/** Rolling real microphone RMS. Silence remains flat. */
class VoiceWaveform(context: Context) : FrameLayout(context) {
    private var levels by mutableStateOf(List(48) { 0f })
    init {
        contentDescription = "Microphone waveform"; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent { PiTheme { Waveform(levels) } }
        }, LayoutParams(-1, -1))
    }
    fun sample(rms: Float) { levels = levels.drop(1) + rms.coerceIn(0f, 1f) }
}

@Composable
fun Waveform(levels: List<Float>, modifier: Modifier = Modifier) {
    val ink = colorResource(R.color.text_primary)
    Canvas(modifier.fillMaxSize()) {
        val step = size.width / levels.size.coerceAtLeast(1); val width = minOf(3.dp.toPx(), step * .5f)
        levels.forEachIndexed { i, value ->
            val height = maxOf(2.dp.toPx(), value * size.height * .85f)
            drawRoundRect(ink.copy(alpha = (70 + 185f * i / (levels.size - 1).coerceAtLeast(1)) / 255),
                Offset((i + .5f) * step - width / 2, (size.height - height) / 2), Size(width, height), CornerRadius(width / 2))
        }
    }
}
