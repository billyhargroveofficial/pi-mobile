package ru.billyhargrove.pimobile

import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.features.chat.ChatSession
import ru.billyhargrove.pimobile.media.Attachment
import ru.billyhargrove.pimobile.store.PendingMessages

@RunWith(AndroidJUnit4::class)
class ChatOutboxUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val session = "chat-outbox-fixture"
    @Before fun isolate() { androidx.test.uiautomator.Configurator.getInstance().setWaitForIdleTimeout(1000); instrumentation.runOnMainSync { PiApp.get(context).client().disconnect() }; clearReceipts() }
    @After fun clearReceipts() { PendingMessages(context, PiApp.get(context).settings().baseUrl(), session).save(emptyList()) }
    private fun launch(readOnly: Boolean = false) = ActivityScenario.launch<ChatActivity>(ChatActivity.intent(context, session, "Outbox preview", readOnly))
    private fun idle() { instrumentation.waitForIdleSync(); device.waitForIdle(1000) }
    private fun file(name: String) = Attachment(ImagePayload.file(byteArrayOf(1, 2), name), null, name)
    private fun receipt(chat: ChatSession, request: String = "request-1") = chat.items.map { it.row.message!! }.first { it.requestId() == request }
    private fun tap(text: String) { assertTrue(device.wait(Until.hasObject(By.text(text)), 5000)); device.findObject(By.text(text)).click(); idle() }

    @Test fun failedWriteKeepsComposerAndAttachment() {
        launch().use { scenario ->
            lateinit var chat: ChatSession
            scenario.onActivity { activity ->
                chat = ChatFixture.install(activity, session, false, emptyList())
                val transportField = ChatSession::class.java.getDeclaredField("transport").apply { isAccessible = true }
                (transportField.get(chat) as ChatFixture.Transport).failWrite = true
                ChatFixture.text(chat, "Current draft"); chat.prepared(listOf(file("current.md")))
            }; idle()
            device.findObject(By.res(context.packageName, "sendButton")).click(); idle()
            scenario.onActivity {
                assertEquals("Current draft", chat.composer.text); assertEquals("current.md", chat.attachments.single().payload().fileName())
                assertTrue(chat.items.isEmpty())
            }
        }
    }
    @Test fun restoreTapPreservesOccupiedComposerThenRestoresOriginalBytes() {
        val thumb = Bitmap.createBitmap(8, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff88aabb.toInt()) }
        try { launch().use { scenario ->
            lateinit var chat: ChatSession
            scenario.onActivity { activity ->
                chat = ChatFixture.install(activity, session, false, emptyList())
                ChatFixture.text(chat, "Original prompt")
                chat.prepared(listOf(file("original.md"), Attachment(ImagePayload(byteArrayOf(3, 4), "image/png"), thumb, "picture.png")))
                chat.send(CommandBuilder.Behavior.STEER); chat.onCommandUncertain("request-1", session, "Timeout")
                chat.prepared(listOf(file("current.md")))
                assertSame(thumb, chat.thumbnail("request-1", 1)); assertNull(chat.thumbnail("request-1", 0))
            }; idle(); tap("Restore")
            scenario.onActivity {
                assertEquals("", chat.composer.text); assertEquals("current.md", chat.attachments.single().payload().fileName())
                ChatFixture.text(chat, "Current text"); chat.restore(receipt(chat)); assertEquals("Current text", chat.composer.text)
                ChatFixture.text(chat, ""); chat.removeAttachment(0)
            }; idle(); tap("Restore")
            scenario.onActivity {
                assertEquals("Original prompt", chat.composer.text); assertEquals(2, chat.attachments.size)
                assertEquals("original.md", chat.attachments[0].payload().fileName()); assertSame(thumb, chat.attachments[1].thumbnail())
                assertTrue(chat.items.isEmpty())
            }
            device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "chat-restored-draft.png"))
        } } finally { thumb.recycle() }
    }
    @Test fun rejectionDoesNotMixOldTextWithNewAttachmentsAndForeignAckIsIgnored() {
        launch().use { scenario -> scenario.onActivity { activity ->
            val chat = ChatFixture.install(activity, session, false, emptyList())
            ChatFixture.text(chat, "Original prompt"); assertTrue(chat.send(CommandBuilder.Behavior.FOLLOW_UP))
            chat.onAck(Ack("request-1", "other-session", false, "Rejected")); assertEquals(ChatMessage.LocalState.SENDING, receipt(chat).localState())
            chat.prepared(listOf(file("new.md"))); chat.onAck(Ack("request-1", session, false, "Rejected"))
            assertEquals("", chat.composer.text); assertEquals("new.md", chat.attachments.single().payload().fileName())
            chat.onCommandUncertain("request-1", session, "Late timeout"); assertEquals(ChatMessage.LocalState.FAILED, receipt(chat).localState())
            chat.removeAttachment(0); chat.restore(receipt(chat)); assertEquals("Original prompt", chat.composer.text)
        } }
    }
    @Test fun rejectionRestoresOnlyEmptyComposerAndAcceptanceStaysTerminal() {
        launch().use { scenario -> scenario.onActivity { activity ->
            val chat = ChatFixture.install(activity, session, false, emptyList())
            ChatFixture.text(chat, "Rejected prompt"); chat.send(CommandBuilder.Behavior.FOLLOW_UP)
            chat.onAck(Ack("request-1", session, false, "Rejected")); assertEquals("Rejected prompt", chat.composer.text)
            ChatFixture.text(chat, "Next prompt"); chat.send(CommandBuilder.Behavior.FOLLOW_UP)
            chat.onAck(Ack("request-2", session, true, "")); chat.onCommandUncertain("request-2", session, "Late timeout")
            assertEquals(ChatMessage.LocalState.ACCEPTED, receipt(chat, "request-2").localState())
        } }
    }
    @Test fun readOnlyCallbacksCannotSendOrConsumeReceipt() {
        val receipt = ChatMessage.local("cached", "Old prompt", null, ChatMessage.LocalState.UNCERTAIN)
        launch(true).use { scenario -> scenario.onActivity { activity ->
            val chat = ChatFixture.install(activity, session, true, listOf(receipt))
            chat.retry(receipt); chat.restore(receipt); assertFalse(chat.send(CommandBuilder.Behavior.FOLLOW_UP))
            val rendered = chat.items.single().row.message!!
            assertEquals(receipt.requestId(), rendered.requestId()); assertEquals(receipt.text(), rendered.text())
            assertEquals(ChatMessage.LocalState.UNCERTAIN, rendered.localState())
        }; idle(); assertFalse(device.hasObject(By.res(context.packageName, "composerInput"))) }
    }
    @Test fun activityRecreationPreservesUnsentTextAndCaret() {
        launch().use { scenario ->
            scenario.onActivity { activity ->
                val chat = ChatFixture.install(activity, session, false, emptyList())
                chat.composer = androidx.compose.ui.text.input.TextFieldValue("Unsent draft", androidx.compose.ui.text.TextRange(4))
            }
            scenario.recreate(); idle()
            scenario.onActivity { activity ->
                val chat = ChatFixture.state(activity)
                assertEquals("Unsent draft", chat.composer.text); assertEquals(4, chat.composer.selection.start)
                assertEquals(4, chat.composer.selection.end)
            }
            assertTrue(device.hasObject(By.text("Unsent draft")))
        }
    }
}
