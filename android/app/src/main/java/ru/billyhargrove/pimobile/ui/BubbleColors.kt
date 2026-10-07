package ru.billyhargrove.pimobile.ui

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import androidx.core.graphics.ColorUtils
import java.util.Locale
import ru.billyhargrove.pimobile.R

/** Only the user bubble is customizable; foreground follows measured contrast. */
object BubbleColors {
    @JvmStatic fun color(context: Context) = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getInt("userBubble", context.getColor(R.color.bubble_user))
    @JvmStatic fun foreground(color: Int) = if (ColorUtils.calculateLuminance(color) > .179) AndroidColor.BLACK else AndroidColor.WHITE
    @JvmStatic fun background(context: Context, color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = 24 * context.resources.displayMetrics.density }
    @JvmStatic fun show(context: Context) {
        object : ComposeSheet(context) { init { content {
            var hex by remember { mutableStateOf(String.format(Locale.ROOT, "#%06X", color(context) and 0xffffff)) }
            var selected by remember { mutableIntStateOf(color(context)) }
            val valid = hex.matches(Regex("#[0-9a-fA-F]{6}"))
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your bubble color", fontSize = 20.sp)
                Text("Your messages will look like this.", Modifier.fillMaxWidth().background(Color(selected), RoundedCornerShape(24.dp)).padding(24.dp),
                    color = Color(foreground(selected)), fontSize = 16.sp)
                TextField(hex, { hex = it; if (it.matches(Regex("#[0-9a-fA-F]{6}"))) selected = AndroidColor.parseColor(it) }, colors = PiFieldColors(),
                    label = { Text("Hex color · #RRGGBB") }, singleLine = true, isError = !valid, modifier = Modifier.fillMaxWidth())
                val palette = listOf("Blue" to 0xff2f5de5, "Gray" to 0xff303030, "Green" to 0xff247653, "Purple" to 0xff7450bd, "Orange" to 0xffd89a4b, "Rose" to 0xffb75079)
                palette.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (name, value) -> Button(onClick = { hex = String.format(Locale.ROOT, "#%06X", value and 0xffffff); selected = value.toInt() },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(value), contentColor = Color(foreground(value.toInt())))) { Text(name) } }
                } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = ::dismiss) { Text("Cancel") }
                    TextButton(onClick = { context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().remove("userBubble").apply(); dismiss() }) { Text("Reset") }
                    Button(onClick = { context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putInt("userBubble", selected).apply(); dismiss() }, enabled = valid) { Text("Save") }
                }
            }
        } } }.show()
    }
}
