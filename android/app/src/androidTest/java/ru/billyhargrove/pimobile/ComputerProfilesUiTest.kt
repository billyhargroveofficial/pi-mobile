package ru.billyhargrove.pimobile

import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.features.catalog.*
import ru.billyhargrove.pimobile.ui.PiTheme
import java.io.File

/** Real screen, profile port and local confirmation; no network/host commands. */
@RunWith(AndroidJUnit4::class)
class ComputerProfilesUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private fun node(id: String) = requireNotNull(device.wait(Until.findObject(By.res(context.packageName, id)), 5000)) { "Missing node: $id" }
    private fun description(text: String) = requireNotNull(device.wait(Until.findObject(By.desc(text)), 5000)) { "Missing action: $text" }
    private class Port : CatalogSession.Transport {
        var entries = listOf(ConnectionProfile("a", "Desktop", "https://desktop.example", true), ConnectionProfile("b", "Laptop", "https://laptop.example:8443/gw", true))
        var active: String? = "a"; var commands = 0; var connects = 0
        lateinit var owner: CatalogSession
        override fun computers() = entries
        override fun activeComputerId() = active
        override fun hasToken() = active != null
        override fun hasTokenFor(url: String) = try { entries.any { it.baseUrl == EndpointPolicy.normalize(url) && it.hasToken } } catch (_: Exception) { false }
        override fun catalog() = Catalog(null, listOf(Session("same-session", if (active == "a") "Desktop task" else "Laptop task", "/fixture", "w", "t", true, SessionStatus.IDLE, "test/model")), null)
        override fun saveComputer(url: String, name: String, token: String): ConnectionProfile {
            val prior = entries.find { it.baseUrl == EndpointPolicy.normalize(url) }
            check(token.isNotEmpty() || prior != null)
            val saved = ConnectionProfile(prior?.id ?: "new", name, EndpointPolicy.normalize(url), true)
            entries = entries.filterNot { it.id == saved.id } + saved; active = saved.id; return saved
        }
        override fun selectComputer(id: String): ConnectionProfile { active = id; return entries.first { it.id == id } }
        override fun forgetComputer(id: String) { entries = entries.filterNot { it.id == id }; if (active == id) active = null }
        override fun connect(url: String, typedToken: String) { connects++; owner.onConnectionState(ConnectionState.CONNECTED, url); owner.onCatalog(catalog()) }
        override fun disconnect() { owner.onConnectionState(ConnectionState.DISCONNECTED, "Disconnected") }
        override fun health(url: String, result: (Result<String>) -> Unit) { result(Result.success("Synthetic")) }
        override fun refresh(result: (Result<Catalog>) -> Unit) { result(Result.success(catalog())) }
        override fun command(session: String, kind: String, args: JSONObject): String { commands++; return "unexpected" }
        override fun timeout(delay: Long, action: () -> Unit) {}
        override fun cancelTimeout() {}
    }
    private fun install(activity: MainActivity, port: Port) {
        val app = PiApp.get(activity); app.client().clearListener(activity); app.client().disconnect()
        port.owner = CatalogSession(port, "https://desktop.example", true) {}
        MainActivity::class.java.getDeclaredField("catalogState").apply { isAccessible = true }.set(activity, port.owner)
        val usage = CatalogFixture.usage(activity); usage.stop()
        port.owner.start(); port.owner.onConnectionState(ConnectionState.CONNECTED, "https://desktop.example")
        activity.setContent { PiTheme { CatalogScreen(port.owner, usage, {}, {}) } }
    }
    private fun reach(id: String): UiObject2 {
        repeat(12) {
            device.waitForIdle()
            try {
                val button = device.findObject(By.res(context.packageName, id))
                if (button != null && button.visibleBounds.height() >= 24) return button
                node("connectPanel").scroll(Direction.DOWN, 0.5f)
            } catch (_: StaleObjectException) { /* Compose can replace accessibility nodes after IME animation. Re-query. */ }
        }
        error("Unreachable setting: $id")
    }
    @Before fun isolate() {
        Configurator.getInstance().waitForIdleTimeout = 1000
        instrumentation.runOnMainSync { PiApp.get(context).client().disconnect() }
    }
    @Test fun headerSwitchUsesNamedComputerAndReplacesCatalogWithoutCommands() {
        val port = Port()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { install(it, port) }
            description("Switch computer").click(); node("computerPicker")
            assertTrue(device.hasObject(By.text("Desktop"))); assertTrue(device.hasObject(By.text("Laptop")))
            device.takeScreenshot(File(context.getExternalFilesDir(null), "multi-endpoint-picker.png"))
            description("Use computer: Laptop").click()
            assertTrue(device.wait(Until.hasObject(By.text("Laptop task")), 5000)); assertFalse(device.hasObject(By.text("Desktop task")))
            scenario.onActivity { assertEquals("b", port.active); assertEquals(1, port.connects); assertEquals(0, port.commands) }
            description("Switch computer").click(); description("Use computer: Laptop").click()
            scenario.onActivity { assertEquals(1, port.connects) }
            device.takeScreenshot(File(context.getExternalFilesDir(null), "multi-endpoint-switched.png"))
        }
    }
    @Test fun addAndEditHaveSeparateBlankTokenFieldsAndSaveThroughTheRealForm() {
        val port = Port()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { install(it, port) }
            description("Switch computer").click(); node("addComputerButton").click()
            assertEquals("", node("connectUrlInput").text.orEmpty())
            node("computerNameInput").text = "Workstation"
            node("connectUrlInput").text = "https://work.example"
            reach("connectTokenInput").text = "synthetic-new-token"
            scenario.onActivity { activity ->
                (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
            }
            reach("connectButton").click()
            node("catalogList")
            scenario.onActivity { assertEquals("new", port.active); assertEquals("Workstation", port.entries.last().name); assertEquals(0, port.commands) }
            description("Switch computer").click(); description("Edit computer: Desktop").click()
            scenario.onActivity { assertEquals("new", port.active); assertEquals("", port.owner.typedToken); assertEquals("Desktop", port.owner.computerName) }
            reach("connectButton").click(); node("catalogList")
            scenario.onActivity { assertEquals("a", port.active); assertEquals(2, port.connects) }
        }
    }
    @Test fun forgetRequiresConfirmationAndAffectsOnlySavedDeviceConnection() {
        val port = Port()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { install(it, port) }
            description("Switch computer").click(); description("Edit computer: Desktop").click()
            reach("forgetComputerButton").click()
            requireNotNull(device.wait(Until.findObject(By.text("Cancel")), 5000)).click()
            scenario.onActivity { assertEquals(2, port.entries.size); assertEquals("a", port.active) }
            reach("forgetComputerButton").click()
            requireNotNull(device.wait(Until.findObject(By.text("Forget")), 5000)).click()
            scenario.onActivity { assertEquals(1, port.entries.size); assertNull(port.active); assertEquals(0, port.connects); assertEquals(0, port.commands); assertTrue(port.owner.rows.isEmpty()) }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            scenario.onActivity { assertTrue("Forgotten computer stays absent after returning", port.owner.rows.isEmpty()); assertNull(port.active) }
            node("connectPanel"); device.waitForIdle() // Wait for the actual resumed frame, not a blank transition screenshot.
            device.takeScreenshot(File(context.getExternalFilesDir(null), "multi-endpoint-forgotten.png"))
        }
    }
    @Test fun settingsAndPickerRemainReachableAtTheCurrentFontAndTheme() {
        val port = Port()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { install(it, port) }
            description("Switch computer").click(); description("Edit computer: Laptop").click()
            node("savedComputersButton"); node("computerNameInput"); reach("connectButton"); reach("forgetComputerButton")
            device.takeScreenshot(File(context.getExternalFilesDir(null), "multi-endpoint-settings-adaptive.png"))
            node("navigationButton").click(); description("Switch computer").click()
            description("Use computer: Laptop"); description("Edit computer: Laptop"); node("addComputerButton")
            device.takeScreenshot(File(context.getExternalFilesDir(null), "multi-endpoint-picker-adaptive.png"))
        }
    }
}
