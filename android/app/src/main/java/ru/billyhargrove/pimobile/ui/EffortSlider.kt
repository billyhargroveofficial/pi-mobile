package ru.billyhargrove.pimobile.ui

import android.content.Context
import android.view.HapticFeedbackConstants
import android.widget.FrameLayout
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import ru.billyhargrove.pimobile.R
import kotlin.math.*

/** Compose brain slider shared by both platform sheets during their bounded migration. */
class EffortSlider(context: Context) : FrameLayout(context) {
    fun interface Change { fun changed(value: String, committed: Boolean) }
    companion object {
        @JvmStatic fun label(value: String) = when (value) {
            "off" -> "Off"; "minimal" -> "Minimal"; "low" -> "Low"; "medium" -> "Medium"
            "high" -> "High"; "xhigh" -> "Extra High"; "max" -> "Max"; else -> value
        }
    }
    private var levels by mutableStateOf<List<String>>(emptyList())
    private var selected by mutableIntStateOf(0)
    private var editable by mutableStateOf(true)
    private var version by mutableIntStateOf(0)
    private var change: Change? = null
    init {
        minimumHeight = (60 * resources.displayMetrics.density).roundToInt()
        addView(ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent { PiTheme { Track() } }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
    fun configure(values: JSONArray?, preferred: String, change: Change?) {
        this.change = null
        levels = (0 until (values?.length() ?: 0)).mapNotNull { values?.optString(it)?.takeIf(String::isNotEmpty) }.distinct()
        selected = levels.indexOf(preferred).coerceAtLeast(0)
        editable = levels.size > 1; super.setEnabled(editable)
        version++; this.change = change
    }
    fun value() = levels.getOrNull(selected) ?: "off"
    fun getMax() = maxOf(1, levels.size - 1)
    fun getProgress() = selected
    fun setProgress(progress: Int) {
        val next = progress.coerceIn(0, maxOf(0, levels.lastIndex))
        if (selected != next) { selected = next; change?.changed(value(), false) }
    }
    override fun setEnabled(enabled: Boolean) { super.setEnabled(enabled); editable = enabled && levels.size > 1 }
    private fun select(progress: Int, haptic: Boolean = true) {
        val previous = selected; setProgress(progress)
        if (haptic && previous != selected) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    private fun commit() { change?.changed(value(), true) }

    @Composable
    private fun Track() {
        val motion = ExpressiveMotion.enabled()
        val initial = remember(version) { selected.toFloat() / maxOf(1, levels.lastIndex) }
        val position = remember(version) { Animatable(initial) }
        LaunchedEffect(selected, version) {
            val target = selected.toFloat() / maxOf(1, levels.lastIndex)
            if (motion) position.animateTo(target, spring(dampingRatio = .76f, stiffness = 240f)) else position.snapTo(target)
        }
        val galaxy = value() in setOf("max", "xhigh") && selected == levels.lastIndex
        var pressed by remember { mutableStateOf(false) }
        val focus = remember { FocusRequester() }
        val scale by animateFloatAsState(if (pressed) 1.16f else 1f,
            if (motion) spring(.62f, 360f) else snap(), label = "brainPress")
        val phase = if (galaxy && motion) {
            val clock = rememberInfiniteTransition(label = "galaxy")
            val animated by clock.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "particles")
            animated
        } else .5f
        val ink = colorResource(R.color.text_secondary)
        val track = colorResource(R.color.surface_alt)
        Canvas(Modifier.fillMaxSize().testTag("${context.packageName}:id/effortTrack")
            .semantics {
                testTagsAsResourceId = true
                contentDescription = "Effort level"
                stateDescription = label(value())
                progressBarRangeInfo = ProgressBarRangeInfo(this@EffortSlider.selected.toFloat(), 0f..(maxOf(1, levels.lastIndex).toFloat()), maxOf(0, levels.size - 2))
                if (!editable) disabled()
                setProgress { requested ->
                    if (!editable) false else { select(requested.roundToInt()); commit(); true }
                }
            }.focusRequester(focus).onKeyEvent { event ->
                if (!editable || event.key !in listOf(Key.DirectionLeft, Key.DirectionRight)) false
                else {
                    if (event.type == KeyEventType.KeyDown) select(selected + if (event.key == Key.DirectionRight) 1 else -1)
                    if (event.type == KeyEventType.KeyUp) commit()
                    true
                }
            }.focusable(editable).pointerInput(editable, levels, version) {
                if (!editable) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    focus.requestFocus()
                    val original = selected
                    val inset = 18.dp.toPx()
                    fun index(x: Float): Int {
                        var fraction = ((x - inset) / maxOf(1f, size.width - 2 * inset)).coerceIn(0f, 1f)
                        if (this@EffortSlider.layoutDirection == LAYOUT_DIRECTION_RTL) fraction = 1f - fraction
                        return (fraction * maxOf(0, levels.lastIndex)).roundToInt()
                    }
                    parent?.requestDisallowInterceptTouchEvent(true)
                    pressed = true; down.consume(); select(index(down.position.x))
                    var released = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (pointer.isConsumed) break
                            select(index(pointer.position.x)); pointer.consume()
                            if (!pointer.pressed) { released = true; break }
                        }
                    } finally {
                        pressed = false; parent?.requestDisallowInterceptTouchEvent(false)
                        if (!released) select(original, haptic = false)
                    }
                    if (released) commit()
                }
            }) {
            val cy = size.height / 2
            val inset = 18.dp.toPx()
            val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
            val fraction = position.value.coerceIn(0f, 1f)
            val x = inset + (size.width - 2 * inset) * if (rtl) 1f - fraction else fraction
            val half = 11.dp.toPx()
            val left = if (rtl) x else 0f
            val right = if (rtl) size.width else x
            val pink = Color(0xfff38ac5)
            val violet = Color(0xffac8dff)
            drawRoundRect(track, Offset(0f, cy - half), Size(size.width, 2 * half), CornerRadius(8.dp.toPx()))
            val fill = if (galaxy) listOf(Color(0xff100921), Color(0xff171028), Color(0xff382248))
                else listOf(pink.copy(alpha = .04f), pink.copy(alpha = .38f))
            drawRoundRect(Brush.horizontalGradient(if (rtl) fill.reversed() else fill),
                Offset(left, cy - half), Size(maxOf(1f, right - left), 2 * half), CornerRadius(8.dp.toPx()), alpha = if (editable) 1f else .45f)
            for (i in levels.indices) {
                val dot = inset + (size.width - 2 * inset) * i / maxOf(1, levels.lastIndex)
                val actual = if (rtl) size.width - dot else dot
                if (abs(actual - x) > 16.dp.toPx()) drawCircle(ink.copy(alpha = .35f), 2.dp.toPx(), Offset(actual, cy))
            }
            if (galaxy) {
                val fixedPhase = if (motion) phase else .5f
                val pitch = 4.dp.toPx()
                val pixel = 2.8.dp.toPx()
                val columns = (size.width / pitch).toInt()
                val palette = listOf(Color(0xffb9adfa), Color(0xfff29ccf), Color(0xffb7efff), Color(0xff7d72c8), Color(0xffeee1ff))
                for (column in 0..columns) for (row in 0 until 5) {
                    val seed = noise(column * 131 + row * 37)
                    val px = column * pitch + 1.dp.toPx()
                    if (if (rtl) px < x else px > x) continue
                    val nearness = (1f - abs(px - x) / size.width).coerceIn(0f, 1f)
                    if (seed > .14f + .76f * nearness * nearness) continue
                    val py = cy - 2 * pitch + row * pitch - pixel / 2
                    val shimmer = .55f + .45f * sin((seed + fixedPhase + column * .027f) * 2 * PI).toFloat()
                    val alpha = (.15f + .8f * nearness * shimmer).coerceIn(0f, 1f)
                    drawRoundRect(palette[(seed * palette.size).toInt().coerceAtMost(palette.lastIndex)].copy(alpha = alpha),
                        Offset(px, py), Size(pixel, pixel), CornerRadius(.7.dp.toPx()))
                }
                for (i in 0 until 12) {
                    val angle = i * PI / 6 + .12
                    for (dot in 0 until 5) {
                        val travel = (fixedPhase + noise(i * 73)) % 1f
                        val radius = (18 + dot * 4 + travel * 4).dp.toPx()
                        val center = Offset(x + cos(angle).toFloat() * radius, cy + sin(angle).toFloat() * radius)
                        val alpha = (.4f * (1f - dot / 5f) * (.5f + .5f * noise(i * 31 + dot))).coerceIn(0f, 1f)
                        drawRect(palette[i % palette.size].copy(alpha = alpha), center, Size(.8.dp.toPx(), .8.dp.toPx()))
                    }
                }
            }
            val brainRadius = 14.dp.toPx() * scale
            withTransform({ translate(x - brainRadius, cy - brainRadius); scale(2 * brainRadius / 24f, 2 * brainRadius / 24f, Offset.Zero) }) {
                brain(pink, galaxy, if (editable) 1f else .45f)
            }
        }
    }
}

private fun noise(value: Int): Float {
    var bits = value + 0x9e3779b9.toInt()
    bits = (bits xor (bits ushr 16)) * 0x85ebca6b.toInt()
    bits = (bits xor (bits ushr 13)) * 0xc2b2ae35.toInt()
    return ((bits xor (bits ushr 16)) ushr 8) / 16777216f
}

private fun DrawScope.brain(color: Color, galaxy: Boolean, alpha: Float) {
    val shape = Path().apply {
        moveTo(11f, 4f); cubicTo(11f, .5f, 5.5f, .5f, 5.5f, 5f)
        cubicTo(2f, 4f, 1f, 8f, 3f, 10f); cubicTo(-.5f, 13f, 2f, 17f, 5f, 17f)
        cubicTo(3.5f, 21.5f, 10.5f, 24f, 11f, 19.5f); lineTo(11f, 4f); close()
        moveTo(13f, 4f); cubicTo(13f, .5f, 18.5f, .5f, 18.5f, 5f)
        cubicTo(22f, 4f, 23f, 8f, 21f, 10f); cubicTo(24.5f, 13f, 22f, 17f, 19f, 17f)
        cubicTo(20.5f, 21.5f, 13.5f, 24f, 13f, 19.5f); lineTo(13f, 4f); close()
    }
    val outline = if (galaxy) Color(0xff33207b) else Color(0xff68133c)
    if (galaxy) for (width in listOf(8f, 5f)) drawPath(shape, Color(0xff8560ff), alpha * .05f, style = Stroke(width))
    val fill = if (galaxy) Brush.linearGradient(listOf(Color(0xff9ee7ff), Color(0xffad88ff), Color(0xffdb8bea)), Offset(2f, 2f), Offset(22f, 22f))
        else Brush.linearGradient(listOf(color, Color(0xffed7cba)), Offset.Zero, Offset(24f, 24f))
    drawPath(shape, fill, alpha)
    drawPath(shape, outline, alpha, style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val folds = Path().apply {
        moveTo(11f, 10f); cubicTo(11f, 12f, 9f, 13f, 7.5f, 13f)
        moveTo(13f, 10f); cubicTo(13f, 12f, 15f, 13f, 16.5f, 13f)
    }
    drawPath(folds, outline, alpha, style = Stroke(2.3f, cap = StrokeCap.Round))
}
