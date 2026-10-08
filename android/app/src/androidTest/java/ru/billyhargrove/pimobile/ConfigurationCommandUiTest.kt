package ru.billyhargrove.pimobile

import android.Manifest
import android.net.ConnectivityManager
import android.view.Choreographer
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.features.chat.ChatSession
import ru.billyhargrove.pimobile.ui.EffortPopup
import ru.billyhargrove.pimobile.ui.ModelSettingsSheet
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import ru.billyhargrove.pimobile.ui.EffortSlider

/** Actual Activity/panels/ACK routing; no network and no live configuration writes. */
@RunWith(AndroidJUnit4::class)
class ConfigurationCommandUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val session = "configuration-command-synthetic"
    @Before fun isolate() {
        Configurator.getInstance().waitForIdleTimeout = 1000
        instrumentation.runOnMainSync { PiApp.get(context).client().disconnect() }
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.ACCESS_NETWORK_STATE)
        try { assertNull("Configuration fixtures require an offline emulator", context.getSystemService(ConnectivityManager::class.java).activeNetwork) }
        finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
    }
    private fun idle() { instrumentation.waitForIdleSync(); device.waitForIdle(1000); instrumentation.uiAutomation.clearCache() }
    private fun node(id: String) = requireNotNull(device.wait(Until.findObject(By.res(context.packageName, id)), 4000)) { "Missing $id" }
    private fun text(value: String) = requireNotNull(device.wait(Until.findObject(By.text(value)), 4000)) { "Missing $value" }
    private fun capture(name: String) {
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync { Choreographer.getInstance().postFrameCallback { Choreographer.getInstance().postFrameCallback { done.countDown() } } }
        assertTrue(done.await(3, TimeUnit.SECONDS)); assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "configuration-$name.png")))
    }
    private fun launch(readOnly: Boolean = false) = ActivityScenario.launch<ChatActivity>(ChatActivity.intent(context, session, "Configuration fixture", readOnly))
    private fun config() = JSONObject().put("model", "test/a").put("thinkingLevel", "low").put("serviceTier", "standard").put("models", JSONArray()
        .put(JSONObject().put("provider", "test").put("id", "a").put("name", "Model A")
            .put("thinkingLevels", JSONArray().put("low").put("high")).put("serviceTiers", JSONArray().put("standard").put("fast"))))
    private inner class Host(val scenario: ActivityScenario<ChatActivity>, readOnly: Boolean = false) {
        lateinit var activity: ChatActivity; lateinit var chat: ChatSession; lateinit var wire: ChatFixture.Transport
        val reported = config()
        init { scenario.onActivity {
            activity = it; chat = ChatFixture.install(it, session, readOnly, emptyList())
            wire = ChatSession::class.java.getDeclaredField("transport").apply { isAccessible = true }.get(chat) as ChatFixture.Transport
            it.onConfiguration(session, reported); ChatFixture.text(chat, "Retained configuration draft")
        }; idle() }
        fun onMain(action: () -> Unit) { instrumentation.runOnMainSync(action); idle() }
        fun ack(id: String, ok: Boolean = true, error: String = "", target: String = session) = onMain { activity.onAck(Ack(id, target, ok, error)) }
        fun openQuick() { node("effortButton").click(); node("quickTierButton") }
        fun openModels() { openQuick(); node("popupModelButton").click(); node("applyModelButton") }
        fun invariant() { assertEquals(0, wire.prompts); assertEquals(0, wire.aborts); assertTrue(chat.queue.isEmpty()); assertEquals("Retained configuration draft", chat.composer.text) }
        fun field(name: String) = ChatActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(activity)
    }
    /** Also runs on the previous APK to capture unchanged native panel geometry. */
    @Test fun quickTierUsesScopedAckKeepsPanelOpenAndRollsBackToConfirmedValue() {
        launch().use { scenario ->
            val h = Host(scenario); h.openQuick(); capture("quick")
            node("quickTierButton").click(); idle(); assertTrue(h.chat.configurationPending)
            assertEquals(listOf(null, null, null, "fast"), h.wire.configurations.single()); assertFalse(node("quickTierButton").isEnabled)
            h.ack("config-1", target = "foreign"); assertTrue(h.chat.configurationPending)
            h.ack("config-1"); assertTrue(node("quickTierButton").isEnabled); assertTrue((h.field("effortPopup") as EffortPopup).isShowing)
            assertSame(h.reported, h.chat.configuration); assertEquals("standard", h.chat.configuration!!.getString("serviceTier"))
            node("quickTierButton").click(); idle(); assertEquals(2, h.wire.configurations.size)
            h.onMain { h.activity.onConfiguration(session, JSONObject().put("thinkingLevel", "high")) }
            h.ack("config-1", false, "old"); assertTrue(h.chat.configurationPending)
            h.ack("config-2", false, "Synthetic tier rejection"); node("effortError"); text("Synthetic tier rejection")
            text("ϟ Fast"); h.invariant(); capture("rejected")
            node("closeEffortPanel").click()
        }
    }
    @Test fun modelApplyHasOneWriteAndReportsDoNotConsumeItsTicket() {
        launch().use { scenario ->
            val h = Host(scenario); h.openModels(); capture("model")
            node("applyModelButton").click(); idle(); assertTrue(h.chat.configurationPending); assertFalse(node("applyModelButton").isEnabled)
            assertEquals(listOf("test", "a", "low", "standard"), h.wire.configurations.single())
            h.onMain { assertFalse(h.chat.configure("test", "b", "high", null, true)); h.activity.onConfiguration(session, JSONObject().put("thinkingLevel", "high")) }
            assertTrue(h.chat.configurationPending); h.ack("foreign"); assertTrue(h.chat.configurationPending)
            h.ack("config-1"); assertTrue(node("applyModelButton").isEnabled); assertTrue((h.field("modelSheet") as ModelSettingsSheet).isShowing)
            assertEquals("high", h.chat.configuration!!.getString("thinkingLevel")); h.invariant(); node("closeModelPanel").click(); node("closeEffortPanel").click()
        }
    }
    @Test fun stopStartReconcilesMissingConfigurationWithoutReplayAndShowsErrorInItsPanel() {
        launch().use { scenario ->
            val h = Host(scenario); h.openQuick(); node("quickTierButton").click(); idle()
            scenario.moveToState(Lifecycle.State.CREATED); h.wire.liveRequests = setOf("config-1"); scenario.moveToState(Lifecycle.State.RESUMED); idle()
            assertTrue(h.chat.configurationPending); assertEquals(1, h.wire.configurations.size)
            scenario.moveToState(Lifecycle.State.CREATED); h.wire.liveRequests = emptySet(); scenario.moveToState(Lifecycle.State.RESUMED); idle()
            assertFalse(h.chat.configurationPending); node("effortError"); text("Result unknown. Check the model in the terminal before retrying.")
            h.ack("config-1"); assertEquals(1, h.wire.configurations.size); h.invariant(); capture("unknown"); node("closeEffortPanel").click()
        }
    }
    @Test fun failedSetupAndNullIdReleaseTheActualPanelForExplicitRetry() {
        launch().use { scenario ->
            val h = Host(scenario); h.openQuick(); h.wire.configurationFailure = IllegalStateException("Synthetic write")
            node("quickTierButton").click(); text("Changes could not be sent: Synthetic write"); assertFalse(h.chat.configurationPending); assertTrue(node("quickTierButton").isEnabled)
            h.wire.configurationFailure = null; h.wire.configurationId = null
            node("quickTierButton").click(); text("Pi is disconnected. Changes not sent."); assertFalse(h.chat.configurationPending)
            h.wire.configurationId = "auto"; node("quickTierButton").click(); idle(); assertTrue(h.chat.configurationPending)
            h.ack("config-3"); assertTrue(node("quickTierButton").isEnabled); assertEquals(3, h.wire.configurations.size); h.invariant(); node("closeEffortPanel").click()
        }
    }
    @Test fun destroyClosesBothPanelsAndRetiresConfigurationBeforeLateEffects() {
        val scenario = launch(); val h = Host(scenario); h.openModels(); node("applyModelButton").click(); idle()
        val model = h.field("modelSheet") as ModelSettingsSheet; val quick = h.field("effortPopup") as EffortPopup
        assertTrue(h.chat.configurationPending); scenario.moveToState(Lifecycle.State.DESTROYED); idle()
        assertFalse(h.chat.configurationPending); assertFalse(h.chat.canConfigureModel); assertNull(h.field("modelSheet")); assertNull(h.field("effortPopup"))
        assertFalse(model.isShowing); assertFalse(quick.isShowing)
        h.onMain {
            h.activity.onAck(Ack("config-1", session, false, "Late destroy rejection")); h.activity.onCommandUncertain("config-1", session, "late")
            assertFalse(h.chat.configure(null, null, "high", null, false))
            ChatActivity::class.java.getDeclaredMethod("effect", ChatSession.Effect::class.java).apply { isAccessible = true }
                .invoke(h.activity, ChatSession.Effect.ConfigurationResult("Late direct effect"))
        }
        assertEquals("", h.chat.notice); assertFalse(device.hasObject(By.text("Late direct effect"))); assertEquals(1, h.wire.configurations.size); h.invariant(); scenario.close()
    }
    @Test fun readOnlyScreenDoesNotExposeConfigurationActionsOrWrite() {
        launch(true).use { scenario ->
            val h = Host(scenario, true); node("readOnlyBanner")
            assertFalse(device.hasObject(By.res(context.packageName, "effortButton")))
            assertFalse(device.hasObject(By.res(context.packageName, "composerInput")))
            assertNull(h.field("effortPopup")); assertNull(h.field("modelSheet"))
            h.onMain { assertFalse(h.chat.configure("test", "a", "high", "fast", true)); assertFalse(h.chat.configure(null, null, "high", null, false)) }
            assertTrue(h.wire.configurations.isEmpty()); h.invariant()
        }
    }
    /** Runs against the baseline APK as well; counts real JSON reads inside the open quick panel. */
    @Test fun quickConfigurationReportsOnlyReadSelectedCapabilitiesAndSkipPartialRegistryReads() {
        launch().use { scenario ->
            val h = Host(scenario); h.openQuick()
            val reads = AtomicInteger()
            val models = JSONArray()
            repeat(1000) { index -> models.put(object : JSONObject() {
                override fun optJSONArray(key: String?): JSONArray? { reads.incrementAndGet(); return super.optJSONArray(key) }
            }.apply {
                put("provider", "test"); put("id", if (index == 999) "a" else "other-$index")
                put("name", if (index == 999) "Model A" else "Other $index")
                put("thinkingLevels", JSONArray().put("low").put("high")); put("serviceTiers", JSONArray().put("standard").put("fast"))
            }) }
            h.onMain { h.activity.onConfiguration(session, config().put("models", models)) }
            val baseline = InstrumentationRegistry.getArguments().getString("baseline") == "true"
            assertEquals(if (baseline) 2000 else 2, reads.get())
            val partialReads = AtomicInteger()
            h.onMain { repeat(40) { h.activity.onConfiguration(session, object : JSONObject() {
                override fun optJSONArray(key: String?): JSONArray? {
                    if (key == "models") partialReads.incrementAndGet()
                    return super.optJSONArray(key)
                }
            }.put("thinkingLevel", "low")) } }
            assertEquals(if (baseline) 40 else 0, partialReads.get())
            println("Quick panel capability reads: ${reads.get()} for 1000 models; partial registry reads: ${partialReads.get()} for 40 reports")
            text("Model A ▾"); text("Low"); h.invariant(); capture("selected"); node("closeEffortPanel").click()
        }
    }
    @Test fun equalExplicitEffortReportStillWinsOverSuccessAndLaterRejection() {
        launch().use { scenario ->
            val h = Host(scenario); h.openQuick()
            fun chooseHigh() {
                val bounds = node("quickEffortSlider").visibleBounds
                device.click(bounds.right - 30, bounds.centerY()); idle()
            }
            chooseHigh(); assertTrue(h.chat.configurationPending)
            h.onMain { h.activity.onConfiguration(session, JSONObject().put("thinkingLevel", "low")) }
            h.ack("config-1"); text("Low")
            chooseHigh(); assertEquals(2, h.wire.configurations.size)
            h.ack("config-2", false, "Explicit report rollback"); text("Low"); text("Explicit report rollback")
            h.onMain { assertEquals("low", (h.field("effortPopup") as EffortPopup).contentView.findViewById<EffortSlider>(R.id.quickEffortSlider).value()) }
            h.invariant(); capture("equal-report"); node("closeEffortPanel").click()
        }
    }
}
