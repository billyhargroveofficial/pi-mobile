package ru.billyhargrove.pimobile

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import java.io.File
import java.io.IOException
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.features.chat.AttachmentSession
import ru.billyhargrove.pimobile.features.chat.ChatSession
import ru.billyhargrove.pimobile.media.Attachment
import ru.billyhargrove.pimobile.media.ImagePreparer
import ru.billyhargrove.pimobile.net.AttachmentPreparer

/** Native draft/composer/lifecycle and actual picked-URI import, on an offline emulator. */
@RunWith(AndroidJUnit4::class)
class AttachmentUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val provider = Uri.parse("content://ru.billyhargrove.pimobile.test.attachments")
    private class Port : AttachmentSession.Port<Uri, Attachment> {
        class Job(val keys: List<Uri>, val budget: Long, val done: (AttachmentSession.Batch<Attachment>) -> Boolean) : AttachmentSession.Control {
            var cancels = 0
            override fun cancel() { cancels++ }
            fun reply(values: List<Attachment>, error: String? = null): Boolean {
                val accepted = done(AttachmentSession.Batch(values, error))
                if (!accepted) values.forEach { it.thumbnail()?.takeUnless(Bitmap::isRecycled)?.recycle() }
                return accepted
            }
        }
        val jobs = mutableListOf<Job>()
        override fun start(keys: List<Uri>, budget: Long, done: (AttachmentSession.Batch<Attachment>) -> Boolean) = Job(keys, budget, done).also(jobs::add)
    }
    private inner class Host(val scenario: ActivityScenario<ChatActivity>, readOnly: Boolean) {
        lateinit var chat: ChatSession; lateinit var owner: AttachmentSession<Uri, Attachment>; lateinit var transport: ChatFixture.Transport
        val port = Port(); val bitmaps = mutableListOf<Bitmap>()
        init { scenario.onActivity { activity ->
            chat = ChatFixture.install(activity, "attachment-ui-synthetic", readOnly, emptyList())
            transport = ChatSession::class.java.getDeclaredField("transport").apply { isAccessible = true }.get(chat) as ChatFixture.Transport
            val field = ChatActivity::class.java.getDeclaredField("attachments").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST") val original = field.get(activity) as AttachmentSession<Uri, Attachment>; original.close()
            owner = AttachmentSession(readOnly, port, chat::beginPreparing, chat::prepared,
                { chat.showNotice(context.getString(R.string.error_attach_limit)) },
                { chat.showNotice(context.getString(R.string.error_attach_failed, it)) }, { !activity.isFinishing && !activity.isDestroyed })
            field.set(activity, owner)
        }; idle() }
        fun pick(vararg names: String) { scenario.onActivity { picked(it, names.map { name -> Uri.withAppendedPath(provider, name) }) }; idle() }
        fun onMain(action: () -> Unit) { instrumentation.runOnMainSync(action); idle() }
        fun image(): Attachment {
            val bitmap = Bitmap.createBitmap(8, 16, Bitmap.Config.ARGB_8888).also(bitmaps::add)
            return Attachment(ImagePayload(byteArrayOf(1, 2), "image/png"), bitmap, "image.png")
        }
        fun noPrompt() { assertEquals(0, transport.writes); assertTrue(chat.items.isEmpty()); assertTrue(chat.queue.isEmpty()) }
        fun close() { onMain { owner.close() }; bitmaps.forEach { if (!it.isRecycled) it.recycle() } }
    }
    @Before fun isolate() {
        Configurator.getInstance().waitForIdleTimeout = 1000
        instrumentation.runOnMainSync { PiApp.get(context).client().disconnect() }
        instrumentation.uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.ACCESS_NETWORK_STATE)
        try { assertNull("Attachment tests require an offline emulator", context.getSystemService(android.net.ConnectivityManager::class.java).activeNetwork) }
        finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
    }
    @After fun cleanProvider() { context.contentResolver.call(provider, "cleanup", null, null) }
    private fun idle() { instrumentation.waitForIdleSync(); device.waitForIdle(1000); instrumentation.uiAutomation.clearCache() }
    private fun node(id: String) = requireNotNull(device.wait(Until.findObject(By.res(context.packageName, id)), 5000)) { "Missing $id" }
    private fun capture(name: String) {
        val frames = java.util.concurrent.CountDownLatch(1)
        instrumentation.runOnMainSync { val clock = android.view.Choreographer.getInstance(); clock.postFrameCallback { clock.postFrameCallback { frames.countDown() } } }
        assertTrue(frames.await(3, java.util.concurrent.TimeUnit.SECONDS))
        assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "attachments-$name.png")))
    }
    private fun picked(activity: ChatActivity, keys: List<Uri>) {
        ChatActivity::class.java.getDeclaredMethod("picked", List::class.java).apply { isAccessible = true }.invoke(activity, keys)
    }
    private fun fixture(readOnly: Boolean = false, block: (Host) -> Unit) {
        ActivityScenario.launch<ChatActivity>(ChatActivity.intent(context, "attachment-ui-synthetic", "Attachment preview", readOnly)).use { scenario ->
            val host = Host(scenario, readOnly)
            try { block(host) } finally { host.close() }
        }
    }
    private fun file(name: String = "notes.txt", bytes: ByteArray = byteArrayOf(0, 1, 127, -1, 5)) = Attachment(ImagePayload.file(bytes, name), null, name)
    @Test fun preparationDisablesActionsRetainsEditedDraftAndShowsSuccessfulPrefixWithError() = fixture { host ->
        host.onMain { ChatFixture.text(host.chat, "Original draft"); host.chat.prepared(listOf(file("old.txt"))) }
        host.pick("one", "two"); assertTrue(host.chat.preparing)
        assertFalse(node("attachImageButton").isEnabled); assertFalse(node("sendButton").isEnabled); assertFalse(node("removeAttachment0").isEnabled)
        assertTrue(device.hasObject(By.text("Preparing attachments…"))); capture("preparing")
        host.onMain { ChatFixture.text(host.chat, "Edited during import"); assertTrue(host.port.jobs.single().reply(listOf(file()), "Unreadable second file")) }
        assertFalse(host.chat.preparing); assertEquals("Edited during import", host.chat.composer.text)
        assertEquals(listOf("old.txt", "notes.txt"), host.chat.attachments.map { it.displayName() })
        assertTrue(host.chat.notice.contains("Unreadable second file")); node("chatNotice"); assertTrue(node("sendButton").isEnabled)
        node("removeAttachment0").click(); idle(); assertEquals("notes.txt", host.chat.attachments.single().displayName())
        host.noPrompt(); capture("partial-error")
    }
    @Test fun excessSelectionUsesOnlyFreeSlotAndKeepsExistingAttachments() = fixture { host ->
        host.onMain { ChatFixture.text(host.chat, "Keep draft"); host.chat.prepared(listOf(file("a"), file("b"))) }
        host.pick("one", "two", "three"); assertEquals(1, host.port.jobs.single().keys.size)
        assertEquals(ImageGuard.MAX_TOTAL_BYTES - 10, host.port.jobs.single().budget); assertEquals(context.getString(R.string.error_attach_limit), host.chat.notice)
        host.onMain { assertTrue(host.port.jobs.single().reply(listOf(file("c")))) }; host.pick("overflow")
        assertEquals(1, host.port.jobs.size); assertEquals(3, host.chat.attachments.size); assertEquals("Keep draft", host.chat.composer.text); host.noPrompt()
    }
    @Test fun stopAndReturnKeepOneImportAndBackgroundCompletionKeepsCurrentDraft() = fixture { host ->
        host.onMain { ChatFixture.text(host.chat, "Retained draft") }; host.pick("one"); val job = host.port.jobs.single()
        host.scenario.moveToState(Lifecycle.State.CREATED); assertEquals(0, job.cancels); assertTrue(host.chat.preparing)
        host.onMain { assertTrue(job.reply(listOf(file()))) }; assertFalse(host.chat.preparing)
        host.scenario.moveToState(Lifecycle.State.RESUMED); idle(); assertEquals(1, host.port.jobs.size)
        assertEquals("Retained draft", host.chat.composer.text); assertEquals(1, host.chat.attachments.size); node("attachmentStrip"); host.noPrompt()
    }
    @Test fun destroyRejectsOldThumbnailAndCannotClearNewPreparationOrDraft() = fixture { old ->
        old.onMain { ChatFixture.text(old.chat, "Old draft") }; old.pick("one"); val job = old.port.jobs.single(); val late = old.image()
        old.scenario.close(); idle(); assertEquals(1, job.cancels); assertFalse(old.chat.preparing)
        fixture { next ->
            next.onMain { ChatFixture.text(next.chat, "New draft") }; next.pick("new")
            next.onMain { assertFalse(job.reply(listOf(late), "late error")) }
            assertTrue(late.thumbnail()!!.isRecycled); assertTrue(next.chat.preparing); assertEquals("New draft", next.chat.composer.text); assertEquals("", next.chat.notice)
            next.onMain { next.port.jobs.single().reply(listOf(file("new.txt"))) }; assertEquals("new.txt", next.chat.attachments.single().displayName()); old.noPrompt(); next.noPrompt()
        }
    }
    @Test fun closingAfterTransferKeepsAcceptedThumbnailAlive() = fixture { host ->
        host.pick("one"); val image = host.image(); host.onMain { assertTrue(host.port.jobs.single().reply(listOf(image))); host.owner.close() }
        assertSame(image, host.chat.attachments.single()); assertFalse(image.thumbnail()!!.isRecycled); host.noPrompt()
    }
    @Test fun readOnlyPickerCannotPrepareOrChangeDraft() = fixture(true) { host ->
        host.onMain { ChatFixture.text(host.chat, "Read only") }; host.pick("one")
        assertTrue(host.port.jobs.isEmpty()); assertFalse(host.chat.preparing); assertTrue(host.chat.attachments.isEmpty()); assertEquals("Read only", host.chat.composer.text); host.noPrompt()
    }
    private fun production(block: (ActivityScenario<ChatActivity>, ChatSession) -> Unit) {
        ActivityScenario.launch<ChatActivity>(ChatActivity.intent(context, "attachment-production-synthetic", "Attachment handoff", false)).use { scenario ->
            lateinit var chat: ChatSession
            scenario.onActivity { activity ->
                val app = PiApp.get(activity); app.client().clearListener(activity); app.client().disconnect()
                val discovery = ChatActivity::class.java.getDeclaredField("orchestration").apply { isAccessible = true }.get(activity) as ru.billyhargrove.pimobile.ui.OrchestrationEntry
                discovery.stop(); discovery.render(JSONObject()); chat = ChatFixture.state(activity)
                activity.onConnectionState(ConnectionState.CONNECTED, "Synthetic")
                chat.onSnapshot(Snapshot(chat.sessionId, SessionStatus.IDLE, true, emptyList(), false)); ChatFixture.text(chat, "Keep draft")
            }
            block(scenario, chat)
            assertEquals("Keep draft", chat.composer.text); assertTrue(chat.items.isEmpty()); assertTrue(chat.queue.isEmpty())
        }
    }
    private fun completed(scenario: ActivityScenario<ChatActivity>, chat: ChatSession) {
        var finished = false
        repeat(60) { if (!finished) { scenario.onActivity { finished = !chat.preparing }; if (!finished) android.os.SystemClock.sleep(100) } }
        assertTrue("Production URI import did not settle", finished); idle()
    }
    @Test fun actualUriPickerReadsFileAndImageOnceAndHandsOffImmutableBytesAndPortraitThumbnail() {
        context.contentResolver.call(provider, "reset", null, null)
        production { scenario, chat ->
            scenario.onActivity { picked(it, listOf(Uri.withAppendedPath(provider, "notes"), Uri.withAppendedPath(provider, "portrait"))) }
            completed(scenario, chat); assertEquals(2, chat.attachments.size); assertEquals("", chat.notice)
            val document = chat.attachments[0]; assertTrue(document.payload().isFile); assertEquals(".._notes_.txt", document.payload().fileName())
            assertArrayEquals(byteArrayOf(0, 1, 127, -1, 5), document.payload().bytes()); assertNull(document.thumbnail())
            val image = chat.attachments[1]; assertEquals("image/png", image.payload().mimeType()); assertFalse(image.thumbnail()!!.isRecycled)
            assertEquals(0.5f, image.thumbnail()!!.width.toFloat() / image.thumbnail()!!.height, .01f)
            val stats = requireNotNull(context.contentResolver.call(provider, "stats", null, null)); assertEquals(2, stats.getInt("queries")); assertEquals(2, stats.getInt("opens"))
            capture("handoff"); node("removeAttachment0").click(); idle(); assertSame(image, chat.attachments.single())
        }
    }
    @Test fun fourByteRemainingImageBudgetRejectsOvershootAndPreservesSuccessfulFilePrefix() {
        production { scenario, chat ->
            scenario.onActivity { chat.prepared(listOf(file("existing.bin", ByteArray((ImageGuard.MAX_TOTAL_BYTES - 9).toInt()))))
                picked(it, listOf(Uri.withAppendedPath(provider, "notes"), Uri.withAppendedPath(provider, "portrait"))) }
            completed(scenario, chat); assertEquals(2, chat.attachments.size)
            assertEquals(ImageGuard.MAX_TOTAL_BYTES - 4, ImageGuard.totalBytes(chat.attachments.map { it.payload() }))
            assertTrue(chat.notice.contains("Не удалось ужать")); node("chatNotice"); capture("budget-error")
        }
    }
    @Test fun publicImportContractsRespectExactTinyBudgetsAndRejectEmptyBudgetBeforeUriOpen() {
        val resolver = context.contentResolver; val image = Uri.withAppendedPath(provider, "portrait"); val document = Uri.withAppendedPath(provider, "notes")
        val raw = ImagePreparer.prepare(resolver, image, 1024); assertTrue(raw.size() <= 1024)
        assertThrows(IOException::class.java) { ImagePreparer.prepare(resolver, image, 4) }
        val payload = AttachmentPreparer.prepare(resolver, document, 5); assertEquals(5, payload.size())
        assertThrows(IOException::class.java) { AttachmentPreparer.prepare(resolver, document, 4) }
        resolver.call(provider, "reset", null, null)
        assertThrows(IOException::class.java) { ImagePreparer.prepare(resolver, image, 0) }
        assertThrows(IOException::class.java) { AttachmentPreparer.prepare(resolver, document, 0) }
        assertEquals(0, requireNotNull(resolver.call(provider, "stats", null, null)).getInt("opens"))
    }
}
