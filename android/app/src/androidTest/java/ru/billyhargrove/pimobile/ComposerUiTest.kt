package ru.billyhargrove.pimobile

import android.net.ConnectivityManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.billyhargrove.pimobile.core.CommandBuilder
import ru.billyhargrove.pimobile.core.ImagePayload
import ru.billyhargrove.pimobile.features.chat.ChatSession
import ru.billyhargrove.pimobile.media.Attachment

@RunWith(AndroidJUnit4::class)
class ComposerUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val session = "composer-fixture"
    @Before fun isolate() {
        Configurator.getInstance().setWaitForIdleTimeout(1000)
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.ACCESS_NETWORK_STATE")
        try { assertNull(context.getSystemService(ConnectivityManager::class.java).activeNetwork) }
        finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
        instrumentation.runOnMainSync { PiApp.get(context).client().disconnect() }
    }
    private fun launch() = ActivityScenario.launch<ChatActivity>(ChatActivity.intent(context, session, "Draft preview", false))
    private fun idle() { instrumentation.waitForIdleSync(); device.waitForIdle(1000); instrumentation.uiAutomation.clearCache() }
    private fun file(name: String) = Attachment(ImagePayload.file(byteArrayOf(1, 2), name), null, name)
    private fun wire(chat: ChatSession) = ChatSession::class.java.getDeclaredField("transport").apply { isAccessible = true }.get(chat) as ChatFixture.Transport
    private fun png(name: String) = assertTrue(device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "$name.png")))

    @Test fun successfulSendKeepsReentrantNextTextCaretAndAttachmentVisible() {
        launch().use { scenario ->
            lateinit var chat: ChatSession; lateinit var transport: ChatFixture.Transport
            scenario.onActivity { activity ->
                chat = ChatFixture.install(activity, session, false, emptyList()); transport = wire(chat)
                ChatFixture.text(chat, "Submitted draft")
                transport.prompting = { chat.composer = TextFieldValue("Next draft", TextRange(4)); chat.prepared(listOf(file("next.md"))) }
            }; idle()
            device.findObject(By.res(context.packageName, "sendButton")).click(); idle(); png("composer-next-draft")
            scenario.onActivity {
                assertEquals("Next draft", chat.composer.text); assertEquals(TextRange(4), chat.composer.selection)
                assertEquals("next.md", chat.attachments.single().payload().fileName()); assertEquals(1, transport.writes)
                assertEquals("Submitted draft", transport.sentPrompts.single().first); assertTrue(transport.sentPrompts.single().second.isEmpty())
                assertEquals("Submitted draft", chat.items.single().row.message!!.text())
            }
            assertTrue(device.hasObject(By.text("Next draft"))); assertTrue(device.hasObject(By.textContains("next.md")))
            assertTrue(device.hasObject(By.text("Submitted draft"))); png("composer-next-draft")
            println("Composer reentrant send: submitted=1, next text/caret4/attachment retained, writes=1")
        }
    }
    @Test fun preparingAndTranscribingSurviveStopStartAndDictationInsertsAtTheCaret() {
        launch().use { scenario ->
            lateinit var chat: ChatSession; lateinit var transport: ChatFixture.Transport
            scenario.onActivity { activity ->
                chat = ChatFixture.install(activity, session, false, emptyList()); transport = wire(chat)
                chat.composer = TextFieldValue("Alpha Omega", TextRange(5)); chat.prepared(listOf(file("notes.md")))
                assertTrue(chat.beginPreparing()); chat.transcriptionChanged(true)
            }; idle()
            assertTrue(device.hasObject(By.text("Preparing attachments…")))
            assertTrue(device.hasObject(By.res(context.packageName, "transcriptionSpinner")))
            assertFalse(device.findObject(By.res(context.packageName, "sendButton")).isEnabled)
            png("composer-busy")
            scenario.moveToState(Lifecycle.State.CREATED); scenario.moveToState(Lifecycle.State.RESUMED); idle()
            scenario.onActivity {
                assertTrue(chat.preparing); assertTrue(chat.transcribing); assertEquals(TextRange(5), chat.composer.selection)
                assertFalse(chat.send(CommandBuilder.Behavior.FOLLOW_UP)); assertEquals(0, transport.writes)
                chat.prepared(emptyList()); chat.insertDictation("words"); chat.transcriptionChanged(false); chat.dismissNotice()
                assertEquals("Alpha words Omega", chat.composer.text); assertEquals(TextRange(11), chat.composer.selection)
                assertEquals("notes.md", chat.attachments.single().payload().fileName()); assertEquals(0, transport.writes)
            }; idle()
            png("composer-dictation")
            assertFalse(device.hasObject(By.res(context.packageName, "transcriptionSpinner")))
            assertTrue(device.wait(Until.hasObject(By.text("Alpha words Omega")), 5000)); assertTrue(device.hasObject(By.textContains("notes.md")))
            png("composer-dictation"); println("Composer stop/start: flags and caret5 retained; dictation caret11; no automatic send")
        }
    }
    @Test fun activityRecreationPreservesBothEndsOfReversedSelection() {
        launch().use { scenario ->
            scenario.onActivity { activity ->
                val chat = ChatFixture.install(activity, session, false, emptyList())
                chat.composer = TextFieldValue("Selected draft", TextRange(9, 2), TextRange(2, 9))
                assertEquals(TextRange(2, 9), chat.composer.composition)
            }
            scenario.recreate(); idle()
            scenario.onActivity { activity ->
                assertEquals("Selected draft", ChatFixture.state(activity).composer.text)
                assertEquals(TextRange(9, 2), ChatFixture.state(activity).composer.selection)
            }
            assertTrue(device.hasObject(By.text("Selected draft"))); png("composer-recreated")
        }
    }
    @Test fun emptyPreparationAndInvalidRemovalKeepPublishedListAndTheVisibleDraft() {
        launch().use { scenario ->
            scenario.onActivity { activity ->
                val chat = ChatFixture.install(activity, session, false, emptyList())
                ChatFixture.text(chat, "Stable draft"); chat.prepared(listOf(file("stable.md")))
                val before = chat.attachments
                repeat(100) { chat.prepared(emptyList()); chat.removeAttachment(-1); chat.removeAttachment(1) }
                assertSame(before, chat.attachments); assertEquals("Stable draft", chat.composer.text)
                assertEquals(0, wire(chat).writes)
            }; idle()
            assertTrue(device.hasObject(By.text("Stable draft"))); assertTrue(device.hasObject(By.textContains("stable.md")))
            png("composer-stable"); println("Composer 100 empty completions + 200 invalid removals: published list reference unchanged; writes=0")
        }
    }
    @Test fun actualActivityDestroyRetiresItsComposerAndLateCallbacksCannotEraseTheSavedValue() {
        lateinit var chat: ChatSession; lateinit var transport: ChatFixture.Transport
        launch().use { scenario ->
            scenario.onActivity { activity ->
                chat = ChatFixture.install(activity, session, false, emptyList()); transport = wire(chat)
                chat.composer = TextFieldValue("Retained draft", TextRange(7, 2)); chat.prepared(listOf(file("retained.md")))
                chat.beginPreparing(); chat.transcriptionChanged(true)
            }
        }
        instrumentation.runOnMainSync {
            assertFalse(chat.preparing); assertFalse(chat.transcribing); assertFalse(chat.canSend)
            chat.composer = TextFieldValue("late"); chat.insertDictation("late"); chat.prepared(listOf(file("late.md")))
            chat.removeAttachment(0); chat.transcriptionChanged(true); chat.selectSkill("late")
            assertFalse(chat.beginPreparing()); assertFalse(chat.send(CommandBuilder.Behavior.STEER))
            assertEquals("Retained draft", chat.composer.text); assertEquals(TextRange(7, 2), chat.composer.selection)
            assertEquals("retained.md", chat.attachments.single().payload().fileName()); assertEquals(0, transport.writes)
        }
        println("Composer actual onDestroy: late text/files/dictation retired; retained selection7..2; writes=0")
    }
}
