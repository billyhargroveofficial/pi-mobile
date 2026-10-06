package ru.billyhargrove.pimobile.ui

import android.content.Context
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import ru.billyhargrove.pimobile.R

class MarkdownPreview(context: Context, path: String, source: String, links: MarkdownRenderer.Links) : ComposeSheet(context) {
    private val renderer = MarkdownRenderer(context, links)
    init {
        content {
            Column(Modifier.fillMaxWidth().heightIn(max = (context.resources.displayMetrics.heightPixels / context.resources.displayMetrics.density * .85f).dp)
                .navigationBarsPadding().padding(20.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(path, Modifier.weight(1f), maxLines = 2, fontSize = 16.sp)
                    TextButton(onClick = ::dismiss) { Text("Close") }
                }
                Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState())) {
                    AndroidView(factory = { TextView(it).apply { id = R.id.documentText; textSize = 16f; setTextColor(context.getColor(R.color.text_primary)); setTextIsSelectable(true) } },
                        update = { renderer.render(it, source) }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
                }
            }
        }
        setOnDismissListener { renderer.close() }
    }
}
