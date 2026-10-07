package ru.billyhargrove.pimobile

import android.graphics.Rect
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import ru.billyhargrove.pimobile.ui.EffortPopup
import ru.billyhargrove.pimobile.ui.EffortSlider
import ru.billyhargrove.pimobile.ui.ModelSettingsSheet
import ru.billyhargrove.pimobile.core.Ack

/** Production modal/anchor/slider, isolated callbacks and no commands to a real Pi. */
@RunWith(AndroidJUnit4::class)
class ConfigurationPanelUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private fun node(id: String): UiObject2 = requireNotNull(device.wait(Until.findObject(By.res(context.packageName, id)), 5000)) { "Missing $id" }
    private fun idle() { instrumentation.waitForIdleSync(); device.waitForIdle(1000); instrumentation.uiAutomation.clearCache() }
    private fun launch() = ActivityScenario.launch<ChatActivity>(ChatActivity.intent(context, "configuration-synthetic", "Configure", false))
    private class CountedJson : JSONObject() {
        val arrays = AtomicInteger()
        override fun optJSONArray(name: String?): JSONArray? { arrays.incrementAndGet(); return super.optJSONArray(name) }
    }
    private fun model() = CountedJson().apply {
        put("provider", "test"); put("id", "a"); put("name", "Model A")
        put("thinkingLevels", JSONArray().put("low").put("high").put("low").put(JSONObject.NULL).put("unknown"))
        put("serviceTiers", JSONArray().put("standard").put("fast"))
    }
    @Before fun isolate() {
        Configurator.getInstance().waitForIdleTimeout = 1000
        instrumentation.runOnMainSync { PiApp.get(context).client().disconnect() }
    }
    @Test fun repeatedModelsAndPanelEditsUseOneImmutableCapabilityProjection() {
        val first = model(); val models = JSONArray().put(first).put(first).put(JSONObject().put("id", "invalid"))
        repeat(13) { index -> models.put(JSONObject().put("provider", "test").put("id", "b$index").put("name", "Model B$index").put("thinkingLevels", JSONArray().put("medium"))) }
        val config = CountedJson().apply { put("model", "test/a"); put("thinkingLevel", "low"); put("models", models) }
        var sheet: ModelSettingsSheet? = null; val sends = AtomicInteger()
        launch().use { scenario ->
            scenario.onActivity { a ->
                ChatFixture.prepareLoading(a)
                sheet = ModelSettingsSheet(a, config, true, ModelSettingsSheet.TierApply { _, _, effort, tier -> assertEquals("high", effort); assertEquals("fast", tier); sends.incrementAndGet() })
                sheet!!.show()
            }
            node("modelSearch"); node("tierFast").click(); idle()
            scenario.onActivity { requireNotNull(sheet!!.findViewById<EffortSlider>(R.id.effortSelector)).setProgress(1) }
            idle(); assertEquals(0, sends.get()); node("applyModelButton").click(); idle(); assertEquals(1, sends.get())
            scenario.onActivity { sheet!!.applied() }; idle(); assertTrue(node("applyModelButton").isEnabled)
            node("modelSearch").text = "Model B0"; idle(); assertTrue(device.hasObject(By.descStartsWith("Model B0")))
            assertEquals(1, config.arrays.get()); assertEquals(2, first.arrays.get())
            println("Configuration projection: registry=1, capabilities=2 after duplicate row, tier/effort edit, Apply/ACK and search")
            scenario.onActivity { sheet!!.dismiss() }
        }
    }
    @Test fun quickPanelSuccessfulAckThenFailureRestoresTheLastConfirmedValue() {
        val initial = model(); var popup: EffortPopup? = null; val values = mutableListOf<String>()
        launch().use { scenario ->
            scenario.onActivity { ChatFixture.prepareLoading(it) }; idle()
            val bounds = node("effortButton").visibleBounds
            scenario.onActivity { a -> popup = EffortPopup(a.findViewById(android.R.id.content), { Rect(bounds) }, initial, "low", true, {},
                { values.add(it) }, "standard", null) }
            val slider = node("quickEffortSlider").visibleBounds
            device.click(slider.right - 30, slider.centerY()); idle(); assertEquals(listOf("high"), values)
            scenario.onActivity { popup!!.completed(null) }; idle()
            device.click(slider.left + 30, slider.centerY()); idle(); assertEquals(listOf("high", "low"), values)
            scenario.onActivity { popup!!.completed("Synthetic rejection") }; idle(); node("effortError")
            scenario.onActivity { assertEquals("high", popup!!.contentView.findViewById<EffortSlider>(R.id.quickEffortSlider).value()) }
            assertEquals(2, initial.arrays.get()); assertTrue(popup!!.isShowing)
            node("closeEffortPanel").click(); assertTrue(device.wait(Until.gone(By.res(context.packageName, "quickEffortSlider")), 5000))
        }
    }
    @Test fun errorFromClosedModelPanelRemainsVisibleInChat() {
        val session = "configuration-synthetic"
        val config = JSONObject().put("model", "test/a").put("thinkingLevel", "low").put("models", JSONArray().put(model()))
        launch().use { scenario ->
            scenario.onActivity { a -> ChatFixture.install(a, session, false, emptyList()); a.onConfiguration(session, config) }
            node("effortButton").click(); node("popupModelButton").click(); node("applyModelButton").click(); idle()
            node("closeModelPanel").click(); idle(); node("quickEffortSlider")
            scenario.onActivity { it.onAck(Ack("config-1", session, false, "Closed panel rejection")) }; idle()
            node("chatNotice"); assertTrue(device.hasObject(By.text("Closed panel rejection")))
            assertFalse("A quick panel that sent no change must not acquire somebody else's failure", device.hasObject(By.res(context.packageName, "effortError")))
            node("closeEffortPanel").click()
        }
    }
}
