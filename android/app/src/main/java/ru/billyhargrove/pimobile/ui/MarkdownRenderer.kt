package ru.billyhargrove.pimobile.ui

import android.content.Context
import android.os.*
import android.text.*
import android.text.method.LinkMovementMethod
import android.util.LruCache
import android.widget.TextView
import io.noties.markwon.*
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import java.util.concurrent.Executors
import ru.billyhargrove.pimobile.core.MathMarkdown
import ru.billyhargrove.pimobile.R

/** Native Markdown/LaTeX leaf; no WebView, HTML execution or remote image loading. */
class MarkdownRenderer(context: Context, links: Links) {
    fun interface Links { fun open(path: String) }
    @Volatile private var closed = false
    private val cache = LruCache<String, Spanned>(48)
    private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "pi-mobile-markdown").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    private val blocked = Regex("(?s).*\\\\(includegraphics|input|include|write|href|url)\\b.*")
    private val markwon = Markwon.builder(context).usePlugin(MarkwonInlineParserPlugin.create())
        .usePlugin(JLatexMathPlugin.create(16 * context.resources.displayMetrics.scaledDensity) { builder ->
            builder.inlinesEnabled(true); builder.executorService(executor); builder.theme().textColor(context.getColor(R.color.text_primary))
        }).usePlugin(TablePlugin.create(context)).usePlugin(StrikethroughPlugin.create()).usePlugin(TaskListPlugin.create(context))
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureTheme(builder: io.noties.markwon.core.MarkwonTheme.Builder) {
                // Inline backticks are typography, not per-line gray selection rectangles.
                // Fenced code remains a distinct document block.
                // Markwon 4.6 treats integer zero as "use default", so keep a
                // nonzero RGB with alpha zero to actually disable the background.
                builder.codeBackgroundColor(0x00ffffff)
                    .codeBlockBackgroundColor(context.getColor(R.color.surface_alt))
            }
            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) { builder.linkResolver { _, link -> links.open(link) } }
        }).build()
    fun render(view: TextView, source: String?) {
        if (closed) return
        view.movementMethod = LinkMovementMethod.getInstance()
        val key = source.orEmpty(); if (key == view.getTag(R.id.markdownSource)) return
        view.setTag(R.id.markdownSource, key)
        cache.get(key)?.let { markwon.setParsedMarkdown(view, it); return }
        view.text = key
        executor.execute {
            if (closed) return@execute
            val parsed = try {
                val normalized = MathMarkdown.normalize(key)
                if (blocked.matches(normalized)) SpannableString(key) else markwon.toMarkdown(normalized)
            } catch (_: Throwable) { return@execute }
            cache.put(key, parsed)
            main.post { if (!closed && key == view.getTag(R.id.markdownSource)) markwon.setParsedMarkdown(view, parsed) }
        }
    }
    fun close() { closed = true; executor.shutdownNow(); main.removeCallbacksAndMessages(null); cache.evictAll() }
}
