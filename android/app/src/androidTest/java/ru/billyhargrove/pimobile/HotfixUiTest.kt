package ru.billyhargrove.pimobile

import android.os.SystemClock
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import java.io.File
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.features.chat.ChatSession

/** Real IME/Compose/window regression journeys using only credential-free synthetic transport. */
@RunWith(AndroidJUnit4::class)
class HotfixUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val session = "hotfix-ui-no-agent"
    @Before fun isolate() {
        Configurator.getInstance().waitForIdleTimeout = 1000
        instrumentation.runOnMainSync { PiApp.get(context).client().disconnect() }
    }
    private fun launch() = ActivityScenario.launch<ChatActivity>(ChatActivity.intent(context, session, "Hotfix preview", false))
    private fun node(id: String) = requireNotNull(device.wait(Until.findObject(By.res(context.packageName, id)), 5000)) { "Missing $id" }
    private fun idle() { instrumentation.waitForIdleSync(); device.waitForIdle(1000) }
    private fun screenshot(name: String) { device.takeScreenshot(File(context.getExternalFilesDir(null), "hotfix-$name.png")) }
    private fun config(effort: String = "low", tier: String = "standard", model: String = "a") = JSONObject("""{
        "model":"test/$model","thinkingLevel":"$effort","serviceTier":"$tier","models":[
        {"provider":"test","id":"a","name":"Model A","thinkingLevels":["low","medium","high"],"serviceTiers":["standard","fast"]},
        {"provider":"test","id":"b","name":"Model B","thinkingLevels":["medium","xhigh"],"serviceTiers":["standard","fast"]}]}""")
    private fun ready(scenario: ActivityScenario<ChatActivity>): ChatSession {
        lateinit var owner: ChatSession
        scenario.onActivity { owner = ChatFixture.install(it, session, false, emptyList()); it.onConfiguration(session, config()) }
        idle(); return owner
    }
    private fun ime(scenario: ActivityScenario<ChatActivity>): Boolean {
        var visible = false
        scenario.onActivity { visible = ViewCompat.getRootWindowInsets(it.findViewById(android.R.id.content))?.isVisible(WindowInsetsCompat.Type.ime()) == true }
        return visible
    }
    private fun awaitIme(scenario: ActivityScenario<ChatActivity>, visible: Boolean) {
        repeat(60) { if (ime(scenario) == visible) return; SystemClock.sleep(100) }
        fail("IME did not become visible=$visible")
    }
    private fun typeDraft(scenario: ActivityScenario<ChatActivity>): String {
        val text = "Draft message that must survive keyboard dismissal"
        node("composerInput").click(); awaitIme(scenario, true)
        node("composerInput").text = text
        idle(); scenario.onActivity { assertEquals(text, ChatFixture.state(it).composer.text) }
        return text
    }
    private fun assertImeStaysHidden(scenario: ActivityScenario<ChatActivity>, text: String) {
        awaitIme(scenario, false)
        // Exercise layout and unrelated stream recompositions after the dismiss, not just one frame.
        scenario.onActivity { it.onConfiguration(session, config("medium")) }
        repeat(15) { SystemClock.sleep(100); assertFalse("IME reopened after dismissal", ime(scenario)) }
        scenario.onActivity { assertEquals(text, ChatFixture.state(it).composer.text) }
        node("composerInput")
    }
    @Test fun keyboardBackDismissalDoesNotReopenOrLoseDraft() {
        launch().use { scenario ->
            ready(scenario); val text = typeDraft(scenario)
            device.pressBack(); assertImeStaysHidden(scenario, text)
            screenshot("keyboard-hidden")
            node("composerInput").click(); awaitIme(scenario, true); idle(); SystemClock.sleep(600)
            assertTrue(ime(scenario)); screenshot("keyboard-reopened-by-user")
            device.pressBack(); assertImeStaysHidden(scenario, text)
        }
    }
    @Test fun keyboardNavigationDownArrowDoesNotReopenOrLeaveChat() {
        launch().use { scenario ->
            ready(scenario); val text = typeDraft(scenario)
            idle(); SystemClock.sleep(600)
            screenshot("keyboard-down-arrow-before")
            device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "hotfix-keyboard-navigation.xml"))
            // API35 delegates the down-arrow to the IME window, not SystemUI.
            val hide = device.wait(Until.findObject(By.res("android:id/input_method_nav_back")), 5000)
            assertNotNull("The actual IME down-arrow must be present", hide)
            hide.click()
            assertImeStaysHidden(scenario, text)
            screenshot("keyboard-down-arrow")
        }
    }
    @Test fun keyboardOwnHideActionDoesNotReopenAfterComposerCollapse() {
        launch().use { scenario ->
            ready(scenario); val text = typeDraft(scenario)
            scenario.onActivity { WindowCompat.getInsetsController(it.window, it.findViewById(android.R.id.content)).hide(WindowInsetsCompat.Type.ime()) }
            assertImeStaysHidden(scenario, text)
        }
    }
    @Test fun acceptedReadCaptionIsOutsideBubbleAndAbsentBeforeAck() {
        launch().use { scenario ->
            val chat = ready(scenario)
            scenario.onActivity { ChatFixture.text(chat, "Compact accepted message"); chat.send(CommandBuilder.Behavior.FOLLOW_UP) }
            idle(); assertFalse(device.hasObject(By.res(context.packageName, "messageRead")))
            scenario.onActivity { it.onAck(Ack("request-1", session, true, "")) }; idle()
            val caption = node("messageRead"); val bubble = node("messageBubble")
            assertEquals("read", caption.text)
            assertTrue("Receipt must be BELOW, not inside the bubble", caption.visibleBounds.top >= bubble.visibleBounds.bottom)
            assertFalse(device.hasObject(By.text("✓✓")))
            assertEquals("Accepted by Pi; not task completion", caption.contentDescription)
            screenshot("read-caption")
        }
    }
    @Test fun effortSelectionsAndAcknowledgmentsKeepPanelOpenUntilClose() {
        launch().use { scenario ->
            ready(scenario); node("effortButton").click()
            var slider = node("quickEffortSlider").visibleBounds
            device.click(slider.right - 30, slider.centerY()); idle()
            assertFalse(device.hasObject(By.res(context.packageName, "effortPending")))
            assertFalse(device.hasObject(By.textContains("Waiting for Pi")))
            assertFalse("The panel still guards an unconfirmed change", node("popupModelButton").isEnabled)
            scenario.onActivity { it.onConfiguration(session, config("high")); it.onAck(Ack("config-1", session, true, "")) }; idle()
            assertFalse(device.hasObject(By.res(context.packageName, "chatNotice")))
            slider = node("quickEffortSlider").visibleBounds
            screenshot("effort-persistent")
            device.click(slider.left + 30, slider.centerY()); idle()
            scenario.onActivity { it.onAck(Ack("config-2", session, false, "Rejected synthetic change")) }; idle()
            assertTrue("A rejected change is shown at the TOP of its panel", node("effortError").visibleBounds.bottom <= node("popupModelButton").visibleBounds.top)
            node("quickEffortSlider")
            node("closeEffortPanel").click()
            assertTrue(device.wait(Until.gone(By.res(context.packageName, "quickEffortSlider")), 5000))
        }
    }
    @Test fun tierChoiceAndModelApplyNeverAutoDismissPanels() {
        launch().use { scenario ->
            ready(scenario); node("effortButton").click(); node("quickTierButton").click(); idle()
            scenario.onActivity { it.onConfiguration(session, config(tier = "fast")); it.onAck(Ack("config-1", session, true, "")) }; idle()
            node("quickTierButton")
            assertTrue(device.hasObject(By.text("ϟ Fast")))
            node("popupModelButton").click(); node("modelSelector")
            device.findObject(By.descStartsWith("Model B")).click()
            node("applyModelButton").click(); idle()
            scenario.onActivity { it.onConfiguration(session, config("medium", "fast", "b")); it.onAck(Ack("config-2", session, true, "")) }; idle()
            assertTrue("ACK must re-enable Apply without closing", node("applyModelButton").isEnabled)
            assertFalse(device.hasObject(By.text("Session settings updated")))
            screenshot("model-persistent")
            node("closeModelPanel").click()
            node("quickEffortSlider")
            assertTrue(node("popupModelButton").text?.contains("Model B") == true || device.hasObject(By.textStartsWith("Model B")))
            screenshot("model-return-to-effort")
            node("closeEffortPanel").click()
        }
    }
    @Test fun transcribingShowsSpinnerInsteadOfNotificationAndRetainsDraft() {
        launch().use { scenario ->
            val chat = ready(scenario)
            scenario.onActivity { chat.transcriptionChanged(true) }; idle()
            assertEquals("Transcribing…", node("sendButton").contentDescription)
            assertFalse(node("sendButton").isEnabled)
            node("transcriptionSpinner")
            assertFalse(device.hasObject(By.res(context.packageName, "chatNotice")))
            assertFalse(device.hasObject(By.text("Transcribing on your computer…")))
            screenshot("transcription-spinner")
            scenario.onActivity { chat.transcriptionChanged(false); chat.insertDictation("Recognized speech") }; idle()
            assertTrue(device.wait(Until.gone(By.res(context.packageName, "transcriptionSpinner")), 5000))
            scenario.onActivity { assertEquals("Recognized speech", chat.composer.text); assertTrue(chat.items.isEmpty()) }
        }
    }
    @Test fun errorsAreDismissibleInlineBelowHeaderNotBottomNotifications() {
        launch().use { scenario ->
            val chat = ready(scenario)
            scenario.onActivity { chat.showNotice("Synthetic connection error") }; idle()
            val notice = node("chatNotice").visibleBounds
            assertTrue(notice.top >= node("chatTopBar").visibleBounds.bottom)
            assertTrue(notice.bottom < node("composerContainer").visibleBounds.top)
            screenshot("inline-error")
            node("dismissNotice").click(); assertTrue(device.wait(Until.gone(By.res(context.packageName, "chatNotice")), 5000))
        }
    }
}
