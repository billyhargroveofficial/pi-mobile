package ru.billyhargrove.pimobile

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.billyhargrove.pimobile.core.*

/** Direct navigation replaces the removed drawer. Fixtures never control a real Pi. */
@RunWith(AndroidJUnit4::class)
class NavigationUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private fun node(id: String) = requireNotNull(device.wait(Until.findObject(By.res(context.packageName, id)), 5000)) { "Missing Compose node: $id" }
    private fun catalog() = Catalog(listOf(Workspace("w", "Mobile", "/mobile")),
        listOf(Session("ui-test-no-agent", "Navigation draft", "/mobile", "w", "t", true, SessionStatus.IDLE, "test/model")), emptyList())
    @Before fun isolate() {
        Configurator.getInstance().waitForIdleTimeout = 1000
        instrumentation.runOnMainSync { PiApp.get(context).client().disconnect() }
    }
    @Test fun chatBackReturnsWithoutOpeningDrawer() {
        ActivityScenario.launch<ChatActivity>(ChatActivity.intent(context, "ui-test-no-agent", "Navigation draft", false)).use { scenario ->
            scenario.onActivity { ChatFixture.install(it, "ui-test-no-agent", false, emptyList()) }
            assertEquals("Back to sessions", node("backButton").contentDescription)
            assertFalse(device.hasObject(By.desc("Open navigation")))
            assertFalse(device.hasObject(By.res(context.packageName, "navigationDrawer")))
            node("backButton").click()
            assertTrue(device.wait(Until.gone(By.res(context.packageName, "chatRoot")), 5000))
        }
    }
    @Test fun catalogSettingsAndHistoryStayDirectlyReachable() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { CatalogFixture.install(it, catalog()) }
            node("catalogList")
            assertFalse(device.hasObject(By.desc("Open navigation")))
            assertFalse(device.hasObject(By.res(context.packageName, "navigationButton")))
            node("historyButton")
            node("settingsButton").click()
            node("connectUrlInput")
            device.takeScreenshot(File(context.getExternalFilesDir(null), "hotfix-settings.png"))
            node("navigationButton").click()
            node("catalogList")
            assertFalse(device.hasObject(By.res(context.packageName, "navigationDrawer")))
            device.takeScreenshot(File(context.getExternalFilesDir(null), "hotfix-catalog.png"))
        }
    }
}
