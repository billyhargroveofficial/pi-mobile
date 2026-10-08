package ru.billyhargrove.pimobile

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import android.widget.TextView
import android.text.Spanned
import android.text.style.ClickableSpan
import androidx.lifecycle.Lifecycle
import org.json.JSONObject
import java.io.File
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.billyhargrove.pimobile.ui.MarkdownPreview
import ru.billyhargrove.pimobile.ui.MarkdownRenderer
import ru.billyhargrove.pimobile.core.Ack
import ru.billyhargrove.pimobile.features.chat.ChatSession

/** Native read-only document fixtures on an offline emulator; no live Pi command. */
@RunWith(AndroidJUnit4::class)
class DocumentUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val rich = "## Document preview\n\n**Bold** and `code`.\n\n| Name | Value |\n| --- | --- |\n| Item | 42 |\n\n$$\n\\frac{a}{b}=x^2\n$$\n\n[Next document](next.md)\n\n" +
        (1..40).joinToString("\n\n") { "Paragraph $it. Read-only document content with a stable reading position." }
    @Before fun isolate() {
        Configurator.getInstance().waitForIdleTimeout = 1000
        instrumentation.runOnMainSync { PiApp.get(context).client().disconnect() }
        instrumentation.uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.ACCESS_NETWORK_STATE)
        try { assertNull("Document fixtures require an offline emulator", context.getSystemService(android.net.ConnectivityManager::class.java).activeNetwork) }
        finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
    }
    private fun idle() { instrumentation.waitForIdleSync(); device.waitForIdle(1000); instrumentation.uiAutomation.clearCache() }
    private fun capture(name: String) {
        val frames = java.util.concurrent.CountDownLatch(1)
        instrumentation.runOnMainSync { val clock = android.view.Choreographer.getInstance(); clock.postFrameCallback { clock.postFrameCallback { frames.countDown() } } }
        assertTrue(frames.await(3, java.util.concurrent.TimeUnit.SECONDS))
        assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "documents-$name.png")))
    }
    private fun rendered(preview: MarkdownPreview) {
        var ready = false
        repeat(60) {
            if (!ready) {
                instrumentation.runOnMainSync {
                    val text = preview.findViewById<TextView>(R.id.documentText)?.text as? Spanned
                    ready = text != null && text.getSpans(0, text.length, io.noties.markwon.image.AsyncDrawableSpan::class.java).any { it.drawable.hasResult() }
                }
                if (!ready) android.os.SystemClock.sleep(100)
            }
        }
        assertTrue("Markdown/LaTeX did not render", ready); idle()
    }
    @Test fun richDocumentPreviewPreservesNativeRenderingAndGeometry() {
        ActivityScenario.launch<ChatActivity>(ChatActivity.intent(context, "documents-synthetic", "Document preview", true)).use { scenario ->
            lateinit var preview: MarkdownPreview
            scenario.onActivity { activity ->
                ChatFixture.prepareLoading(activity)
                preview = MarkdownPreview(activity, "docs/example.md", rich) {}; preview.show()
            }
            try { rendered(preview); capture("rich") }
            finally { scenario.onActivity { preview.dismiss() } }
        }
    }
    private inner class Host(val scenario: ActivityScenario<ChatActivity>) {
        lateinit var chat: ChatSession; lateinit var wire: ChatFixture.Transport
        init { scenario.onActivity { activity ->
            chat = ChatFixture.install(activity, "documents-synthetic", true, emptyList())
            wire = ChatSession::class.java.getDeclaredField("transport").apply { isAccessible = true }.get(chat) as ChatFixture.Transport
            ChatFixture.text(chat, "Retained document draft")
        }; idle() }
        fun onMain(action: () -> Unit) { instrumentation.runOnMainSync(action); idle() }
        fun open(path: String = "docs/example.md"): String {
            scenario.onActivity { it.onDocument(path) }; idle(); return "read-${wire.reads}"
        }
        fun reply(id: String, path: String = "docs/example.md", source: String = rich) {
            onMain { chat.onData(id, chat.sessionId, JSONObject().put("type", "document").put("path", path).put("text", source))
                chat.onAck(Ack(id, chat.sessionId, true, "")) }
        }
        fun preview(): MarkdownPreview? {
            var result: MarkdownPreview? = null
            scenario.onActivity { activity -> result = ChatActivity::class.java.getDeclaredField("documentPreview").apply { isAccessible = true }.get(activity) as MarkdownPreview? }
            return result
        }
        fun requestPath(): String? {
            val owner = ChatSession::class.java.getDeclaredField("documents").apply { isAccessible = true }.get(chat)
            val request = owner.javaClass.getDeclaredField("request").apply { isAccessible = true }.get(owner) ?: return null
            return request.javaClass.getDeclaredField("path").apply { isAccessible = true }.get(request) as String
        }
        fun linkNext() {
            val preview = requireNotNull(preview())
            onMain {
                val view = requireNotNull(preview.findViewById<TextView>(R.id.documentText)); val text = view.text as Spanned
                val link = text.getSpans(0, text.length, ClickableSpan::class.java).single { text.subSequence(text.getSpanStart(it), text.getSpanEnd(it)).toString() == "Next document" }
                link.onClick(view)
            }
        }
        fun noMutation() { assertEquals(0, wire.writes); assertEquals("Retained document draft", chat.composer.text); assertTrue(chat.items.isEmpty()); assertTrue(chat.queue.isEmpty()) }
    }
    private fun node(id: String) = requireNotNull(device.wait(Until.findObject(By.res(context.packageName, id)), 5000)) { "Missing $id" }
    private fun fixture(block: (Host) -> Unit) {
        ActivityScenario.launch<ChatActivity>(ChatActivity.intent(context, "documents-synthetic", "Document preview", true)).use { scenario -> block(Host(scenario)) }
    }
    private fun renderer(preview: MarkdownPreview) = MarkdownPreview::class.java.getDeclaredField("renderer").apply { isAccessible = true }.get(preview) as MarkdownRenderer
    @Test fun relativeLinkReadsScopedPathAndCloseRetiresItsPendingResult() = fixture { host ->
        val first = host.open(); host.reply(first); val preview = requireNotNull(host.preview()); rendered(preview)
        host.linkNext(); assertEquals("docs/next.md", host.requestPath()); assertEquals(2, host.wire.reads)
        node("documentClose").click(); idle(); assertNull(host.preview())
        host.reply("read-2", "docs/next.md"); assertNull(host.preview()); assertFalse(device.hasObject(By.res(context.packageName, "documentText")))
        assertEquals("", host.chat.notice); host.noMutation(); capture("closed")
    }
    @Test fun sameContentResponseRetainsScrolledViewAndSingleParse() = fixture { host ->
        host.reply(host.open()); val preview = requireNotNull(host.preview()); rendered(preview)
        val view = requireNotNull(preview.findViewById<TextView>(R.id.documentText)); val initial = IntArray(2); val before = IntArray(2)
        host.onMain { view.getLocationOnScreen(initial) }; val initialOffset = initial[1] - node("documentPath").visibleBounds.top
        var scroll = 0
        repeat(5) {
            if (scroll < 300) {
                node("documentScroll").scroll(Direction.DOWN, .6f); idle()
                host.onMain { view.getLocationOnScreen(before) }
                scroll = initialOffset - (before[1] - node("documentPath").visibleBounds.top)
            }
        }
        assertTrue("Document body must scroll independently of sheet movement: $scroll px", scroll >= 300)
        val engine = renderer(preview); assertEquals(1, engine.parseCount.get()); host.reply(host.open())
        assertSame(preview, host.preview()); assertSame(view, preview.findViewById(R.id.documentText)); val after = IntArray(2)
        host.onMain { view.getLocationOnScreen(after) }; assertEquals(before[1], after[1]); assertEquals(1, engine.parseCount.get())
        File(context.getExternalFilesDir(null), "documents-reading-proof.txt").writeText("body_scroll_px=$scroll; y_before=${before[1]}; y_after=${after[1]}; parses=${engine.parseCount.get()}\n")
        host.noMutation(); capture("scrolled")
    }
    @Test fun oldWindowDismissCannotCancelNewWindowNavigation() = fixture { host ->
        host.reply(host.open()); val old = requireNotNull(host.preview()); rendered(old)
        host.linkNext()
        // Do not drain queued onDismiss until the new window has requested another document.
        host.scenario.onActivity {
            host.chat.onData("read-2", host.chat.sessionId, JSONObject().put("type", "document").put("path", "docs/next.md").put("text", rich))
            it.onDocument("docs/third.md")
        }; idle()
        val next = requireNotNull(host.preview()); assertNotSame(old, next); assertEquals("docs/third.md", host.requestPath())
        host.reply("read-3", "docs/third.md"); assertTrue(requireNotNull(host.preview()).matches("docs/third.md", rich))
        assertEquals(3, host.wire.reads); assertEquals("", host.chat.notice); host.noMutation()
    }
    @Test fun rejectionAndMissingBodyKeepCurrentReaderAndExposeHonestFeedback() = fixture { host ->
        host.reply(host.open()); val preview = requireNotNull(host.preview()); rendered(preview)
        host.open("docs/missing.md"); host.onMain { host.chat.onAck(Ack("read-2", host.chat.sessionId, false, "Synthetic file missing")) }
        assertSame(preview, host.preview()); assertEquals("Synthetic file missing", host.chat.notice)
        host.open("docs/empty.md"); host.onMain { host.chat.onAck(Ack("read-3", host.chat.sessionId, true, "")) }
        assertSame(preview, host.preview()); assertEquals("Pi returned no document", host.chat.notice)
        node("documentClose").click(); idle(); node("chatNotice"); capture("error"); host.noMutation()
    }
    @Test fun stopRetainsReadAndDestroyRetiresLateResultWithoutReopening() = fixture { host ->
        val id = host.open(); host.scenario.moveToState(Lifecycle.State.CREATED); assertEquals("docs/example.md", host.requestPath())
        host.scenario.moveToState(Lifecycle.State.RESUMED); idle(); host.reply(id); assertNotNull(host.preview()); host.noMutation()
        host.open("docs/late.md"); host.scenario.close(); idle()
        host.onMain { host.chat.onData("read-2", host.chat.sessionId, JSONObject().put("type", "document").put("path", "docs/late.md").put("text", rich)) }
        assertNull(host.requestPath()); assertFalse(device.hasObject(By.res(context.packageName, "documentText"))); host.noMutation()
    }
    @Test fun unsupportedLinksNeverReadAndDecodedMarkdownFragmentUsesExpectedPath() = fixture { host ->
        for (path in listOf("notes.txt", "javascript:invalid", "content://invalid/doc.md", "mailto:invalid")) host.open(path)
        assertEquals(0, host.wire.reads); assertNull(host.requestPath())
        val id = host.open("docs/my%20notes.MARKDOWN#part"); assertEquals("docs/my notes.MARKDOWN", host.requestPath())
        host.reply(id, "docs/my notes.MARKDOWN"); assertNotNull(host.preview()); host.noMutation()
    }
    @Test fun backDismissRetiresReadAndClosesNativeRenderer() = fixture { host ->
        host.reply(host.open()); val preview = requireNotNull(host.preview()); rendered(preview)
        val engine = renderer(preview); val view = requireNotNull(preview.findViewById<TextView>(R.id.documentText)); val text = view.text.toString()
        host.linkNext(); device.pressBack(); idle(); assertNull(host.preview()); assertNull(host.requestPath())
        host.onMain { engine.render(view, "**must not render**") }; assertEquals(text, view.text.toString()); assertEquals(1, engine.parseCount.get())
        host.reply("read-2", "docs/next.md"); assertNull(host.preview()); host.noMutation()
    }
}
