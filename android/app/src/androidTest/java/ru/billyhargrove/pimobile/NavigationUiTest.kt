package ru.billyhargrove.pimobile

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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

/** Exercises the shared drawer against real screens, without sending a host command. */
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
    @Test fun chatDrawerKeepsUnsentTextAndCaret() {
        ActivityScenario.launch<ChatActivity>(ChatActivity.intent(context, "ui-test-no-agent", "Navigation draft", false)).use { scenario ->
            val draft = TextFieldValue("Неотправленный черновик\nстрока 2", TextRange(4))
            scenario.onActivity { activity ->
                ChatFixture.install(activity, "ui-test-no-agent", false, emptyList()).apply {
                    composer = draft
                    onCatalog(catalog())
                }
            }
            node("backButton").click()
            node("navigationDrawer")
            assertTrue(device.hasObject(By.text("Open in Orca")))
            device.takeScreenshot(File(context.getExternalFilesDir(null), "navigation-drawer-chat.png"))
            device.pressBack()
            assertTrue(device.wait(Until.gone(By.res(context.packageName, "navigationDrawer")), 5000))
            scenario.onActivity { assertEquals(draft, ChatFixture.state(it).composer) }
            node("composerInput")
        }
    }
    @Test fun catalogDrawerOpensSettingsAndReturnsToCatalog() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { CatalogFixture.install(it, catalog()) }
            node("navigationButton").click()
            node("navigationDrawer")
            assertTrue(device.hasObject(By.text("Navigation draft")))
            device.takeScreenshot(File(context.getExternalFilesDir(null), "navigation-drawer.png"))
            node("drawerSettingsButton").click()
            node("connectUrlInput")
            assertTrue(device.wait(Until.gone(By.res(context.packageName, "navigationDrawer")), 5000))
            device.pressBack()
            node("catalogList")
        }
    }
}
