package ru.billyhargrove.pimobile.ui

import android.content.Context
import android.widget.TextView
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import ru.billyhargrove.pimobile.R
import ru.billyhargrove.pimobile.features.chat.DocumentScreen

/** Native modal/Markdown host. Feature presentation owns geometry and scroll. */
class MarkdownPreview(context: Context, private val path: String, private val source: String, links: MarkdownRenderer.Links) : ComposeSheet(context) {
    private val renderer = MarkdownRenderer(context, links)
    private var afterClosed: () -> Unit = {}
    fun onClosed(action: () -> Unit) { afterClosed = action }
    fun matches(path: String, source: String) = this.path == path && this.source == source
    init {
        val maxHeight = (context.resources.displayMetrics.heightPixels / context.resources.displayMetrics.density * .85f).dp
        content {
            DocumentScreen(path, source, maxHeight, context.packageName, ::dismiss) { text, modifier ->
                AndroidView(factory = { TextView(it).apply { id = R.id.documentText; textSize = 16f; setTextColor(context.getColor(R.color.text_primary)); setTextIsSelectable(true) } },
                    update = { renderer.render(it, text) }, modifier = modifier)
            }
        }
        setOnDismissListener { renderer.close(); afterClosed() }
    }
}
