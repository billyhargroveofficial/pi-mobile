package ru.billyhargrove.pimobile

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import ru.billyhargrove.pimobile.core.Catalog
import ru.billyhargrove.pimobile.ui.ArchiveSheet

/** Exercises production adapters/screens with synthetic data and no host commands. */
@RunWith(AndroidJUnit4::class)
class CatalogDataUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private fun node(id: String): UiObject2 = requireNotNull(device.wait(Until.findObject(By.res(context.packageName, id)), 5000)) { "Missing $id" }
    private fun idle() { instrumentation.waitForIdleSync(); device.waitForIdle(1000); instrumentation.uiAutomation.clearCache() }
    private class CountedJson(private val field: String) : JSONObject() {
        val reads = AtomicInteger()
        override fun optJSONArray(name: String?): JSONArray? { if (name == field) reads.incrementAndGet(); return super.optJSONArray(name) }
    }
    @Before fun isolate() {
        Configurator.getInstance().waitForIdleTimeout = 1000
        instrumentation.runOnMainSync { PiApp.get(context).client().disconnect() }
    }
    @Test fun usageDetailsRecomposeWithoutRescanningReportedJson() {
        val provider = CountedJson("windows").apply { put("provider", "codex"); put("status", "ok"); put("updatedAt", System.currentTimeMillis())
            put("windows", JSONArray().put(JSONObject().put("name", "weekly").put("usedPercent", 0))) }
        val frame = CountedJson("providers").apply { put("providers", JSONArray().put(provider)) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { CatalogFixture.install(it, Catalog.empty()); CatalogFixture.usage(it).render(frame) }
            node("usageCards"); idle(); assertEquals(1, frame.reads.get()); assertEquals(1, provider.reads.get())
            repeat(2) {
                node("usageCards").click(); node("usageDetails"); idle()
                assertTrue(device.hasObject(By.text("0% used")))
                device.pressBack(); assertTrue(device.wait(Until.gone(By.res(context.packageName, "usageDetails")), 5000)); idle()
            }
            assertEquals(1, frame.reads.get()); assertEquals(1, provider.reads.get())
            println("Catalog projection JSON reads: providers=${frame.reads.get()}, windows=${provider.reads.get()} after two native detail cycles")
        }
    }
    @Test fun recurringDateGroupsRemainUsableAfterPagination() {
        val today = Instant.now(); val yesterday = today.minusSeconds(86400)
        fun row(id: String, modified: String) = JSONObject().put("id", id).put("title", "History $id").put("workspaceName", "Synthetic")
            .put("modified", modified).put("messageCount", 2)
        val first = JSONObject().put("sessions", JSONArray().put(row("a", today.toString())).put(row("b", yesterday.toString())).put(row("c", today.toString())))
            .put("nextOffset", 3).put("hasMore", true)
        val second = JSONObject().put("sessions", JSONArray().put(row("d", "invalid")).put(row("e", today.toString())).put(row("f", "invalid"))).put("hasMore", false)
        val reads = AtomicInteger(); var sheet: ArchiveSheet? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                CatalogFixture.install(a, Catalog.empty())
                sheet = ArchiveSheet(a, ArchiveSheet.Loader { offset, query -> assertEquals("", query); reads.incrementAndGet(); if (offset == 0) first else second },
                    ArchiveSheet.Listener { _, _ -> fail("Native history check cannot open Pi") }).also { it.show() }
            }
            assertTrue(device.wait(Until.hasObject(By.text("History a")), 5000)); idle()
            assertTrue(device.hasObject(By.text("History c")))
            for (attempt in 0..5) {
                if (device.hasObject(By.text("Load more"))) break
                node("archiveList").scroll(Direction.DOWN, .6f); idle()
            }
            if (device.hasObject(By.text("Load more"))) device.findObject(By.text("Load more")).click()
            idle()
            repeat(5) { if (!device.hasObject(By.text("History f"))) { node("archiveList").scroll(Direction.DOWN, .6f); idle() } }
            assertTrue(device.hasObject(By.text("History f"))); assertEquals(2, reads.get())
            scenario.onActivity { sheet!!.dismiss() }
        }
    }
}
