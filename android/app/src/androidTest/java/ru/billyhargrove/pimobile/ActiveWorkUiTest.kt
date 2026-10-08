package ru.billyhargrove.pimobile

import android.Manifest
import android.net.ConnectivityManager
import android.view.Choreographer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.billyhargrove.pimobile.ui.ActiveOrchestration
import ru.billyhargrove.pimobile.ui.PiTheme
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Public facade and actual chat surfaces; frozen facts, real clicks/ticker, no network or Pi commands. */
@RunWith(AndroidJUnit4::class)
class ActiveWorkUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val session = "active-work-synthetic"
    @Before fun isolate() {
        Configurator.getInstance().waitForIdleTimeout = 1000
        instrumentation.runOnMainSync { PiApp.get(context).client().disconnect() }
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.ACCESS_NETWORK_STATE)
        try { assertNull("Fixtures require an offline emulator", context.getSystemService(ConnectivityManager::class.java).activeNetwork) }
        finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
    }
    private fun idle() { instrumentation.waitForIdleSync(); device.waitForIdle(1000); instrumentation.uiAutomation.clearCache() }
    private fun node(name: String) = requireNotNull(device.wait(Until.findObject(By.res(context.packageName, name)), 4000)) { "Missing $name" }
    private fun tap(name: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
        var previous: android.graphics.Rect? = null; var stable = 0
        while (System.nanoTime() < deadline) {
            instrumentation.uiAutomation.clearCache()
            val bounds = try { device.findObject(By.res(context.packageName, name))?.visibleBounds } catch (_: StaleObjectException) { null }
            if (bounds != null && !bounds.isEmpty) {
                stable = if (bounds == previous) stable + 1 else 1; previous = bounds
                // A click during horizontal momentum stops scrolling instead of opening a row.
                // Observe settled frames and hit the title, outside its nested metrics scroller.
                if (stable >= 16) { assertTrue(device.click(bounds.centerX(), bounds.top + bounds.height() / 4)); idle(); return }
            }
            val frame = CountDownLatch(1)
            instrumentation.runOnMainSync { Choreographer.getInstance().postFrameCallback { frame.countDown() } }
            assertTrue(frame.await(2, TimeUnit.SECONDS))
        }
        fail("No stable rendered target for $name")
    }
    private fun capture(name: String) {
        idle(); val done = CountDownLatch(1)
        instrumentation.runOnMainSync { Choreographer.getInstance().postFrameCallback { Choreographer.getInstance().postFrameCallback { done.countDown() } } }
        assertTrue(done.await(3, TimeUnit.SECONDS)); assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "active-work-$name.png")))
    }
    private fun launch() = ActivityScenario.launch<ChatActivity>(ChatActivity.intent(context, session, "Active work", false))
    private fun fixture() = JSONObject().put("liveAvailable", true)
        .put("workflows", JSONArray().put(JSONObject().put("id", "w").put("title", "Workflow fixture").put("status", "running")
            .put("startedAt", 1000).put("finishedAt", 21000).put("phases", JSONArray()
                .put(JSONObject().put("title", "Explore").put("status", "completed").put("agentCount", 2).put("completed", 2))
                .put(JSONObject().put("title", "Build").put("status", "running")))))
        .put("agents", JSONArray().put(JSONObject().put("id", "assigned").put("workflowId", "w").put("status", "running").put("toolCalls", 7).put("outputTokens", 321))
            .put(JSONObject().put("id", "solo").put("name", "Independent review").put("status", "running").put("startedAt", 1000).put("finishedAt", 11000)))
    private fun host(scenario: ActivityScenario<ChatActivity>, data: JSONObject, compact: Boolean, targets: MutableList<List<String>>) {
        scenario.onActivity { activity ->
            ChatFixture.install(activity, session, false, emptyList())
            activity.setContentView(ComposeView(activity).apply { setContent { PiTheme {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()
                    .semantics { testTagsAsResourceId = true }) {
                    ActiveOrchestration(data, { kind, id, title -> targets.add(listOf(kind, id, title)) }, compact)
                }
            } } })
        }; idle()
    }
    @Test fun expandedFacadePreservesPhasesMetricsAndExactReadOnlyTargets() {
        launch().use { scenario ->
            val targets = mutableListOf<List<String>>(); host(scenario, fixture(), false, targets)
            node("activeWorkflow"); assertTrue(device.hasObject(By.text("✓ Explore 2/2")))
            assertTrue(device.hasObject(By.text("● Build"))); assertTrue(device.hasObject(By.text("1 active · 7 tools · 321 tokens · 20s")))
            assertTrue(device.hasObject(By.text("Independent review"))); capture("expanded")
            tap("activeWorkflow"); tap("activeAgents")
            assertEquals(listOf(listOf("workflow", "w", "Workflow fixture"), listOf("agent", "solo", "Independent review")), targets)
        }
    }
    @Test fun compactFacadeKeepsTheRunningPhaseAndScrollableAgentTarget() {
        launch().use { scenario ->
            val targets = mutableListOf<List<String>>(); host(scenario, fixture(), true, targets)
            assertTrue(device.hasObject(By.text("Workflow · Build"))); capture("compact")
            tap("activeWorkflow")
            val dock = node("activeOrchestration").visibleBounds
            // Stay inside the content; starting at the screen edge invokes Android's Back gesture.
            val margin = (dock.width() * .2f).toInt()
            device.swipe(dock.right - margin, dock.centerY(), dock.left + margin, dock.centerY(), 20); idle()
            tap("activeAgents"); capture("compact-agents")
            assertEquals(listOf(listOf("workflow", "w", "Workflow fixture"), listOf("agent", "solo", "Independent review")), targets)
        }
    }
    /** Baseline mode records the old rereads; final mode requires frozen phase/clock facts across real ticks. */
    @Test fun actualElapsedTickerDoesNotRereadPhaseArraysOrClockJson() {
        val phases = AtomicInteger(); val clocks = AtomicInteger(); val metrics = AtomicInteger()
        val flow = object : JSONObject() {
            override fun optJSONArray(name: String?): JSONArray? { if (name == "phases") phases.incrementAndGet(); return super.optJSONArray(name) }
            override fun optLong(name: String?): Long { if (name == "startedAt" || name == "finishedAt") clocks.incrementAndGet(); return super.optLong(name) }
        }.put("id", "w").put("title", "Ticker fixture").put("status", "running").put("startedAt", System.currentTimeMillis() - 6000)
            .put("phases", JSONArray().put(JSONObject().put("title", "Build").put("status", "running")))
        val agent = object : JSONObject() {
            override fun optLong(name: String?): Long { if (name == "toolCalls" || name == "outputTokens") metrics.incrementAndGet(); return super.optLong(name) }
        }.put("id", "a").put("workflowId", "w").put("status", "running").put("toolCalls", 7).put("outputTokens", 321)
        val data = JSONObject().put("liveAvailable", true).put("workflows", JSONArray().put(flow)).put("agents", JSONArray().put(agent))
        launch().use { scenario ->
            host(scenario, data, false, mutableListOf()); node("activeWorkflow")
            val before = listOf(phases.get(), clocks.get(), metrics.get())
            assertTrue(before.all { it > 0 })
            val initial = requireNotNull(device.findObject(By.textContains("7 tools · 321 tokens"))).text
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            var current = initial
            while (current == initial && System.nanoTime() < deadline) {
                Thread.sleep(100); instrumentation.uiAutomation.clearCache()
                current = device.findObject(By.textContains("7 tools · 321 tokens"))?.text ?: initial
            }
            assertNotEquals("The actual elapsed timer did not advance", initial, current)
            val after = listOf(phases.get(), clocks.get(), metrics.get())
            if (InstrumentationRegistry.getArguments().getString("baseline") == "true") {
                assertTrue(after[0] > before[0]); assertTrue(after[1] > before[1]); assertEquals(before[2], after[2])
            } else { assertEquals(listOf(1, 2, 2), before); assertEquals(before, after) }
            println("Active work ticker JSON reads: phase/clock/counters $before -> $after; label $initial -> $current")
        }
    }
    @Test fun actualChatSharesLiveFactsAndRetainsHistoryAndDraftWhenTheyBecomeSaved() {
        launch().use { scenario ->
            scenario.onActivity { activity ->
                val chat = ChatFixture.install(activity, session, false, emptyList()); ChatFixture.text(chat, "Retained active work draft")
                ChatFixture.orchestration(activity, fixture())
            }; idle(); node("activeWorkflow"); node("activeAgents"); node("orchestrationHistory"); capture("chat-live")
            scenario.onActivity { ChatFixture.orchestration(it, fixture().put("liveAvailable", false)) }
            idle(); assertTrue(device.wait(Until.gone(By.res(context.packageName, "activeOrchestration")), 3000)); node("orchestrationHistory")
            scenario.onActivity {
                val chat = ChatFixture.state(it); assertEquals("Retained active work draft", chat.composer.text)
                val wire = ChatSessionTransport(it); assertEquals(0, wire.writes); assertTrue(chat.queue.isEmpty())
            }; capture("chat-saved")
            scenario.onActivity { ChatFixture.orchestration(it, JSONObject()) }; idle()
            assertTrue(device.wait(Until.gone(By.res(context.packageName, "orchestrationHistory")), 3000))
        }
    }
    @Test fun actualChatProjectsOneSnapshotForHeaderAndDockAcrossUnrelatedUpdates() {
        val reads = AtomicInteger(); val source = fixture()
        val data = object : JSONObject() {
            override fun optJSONArray(name: String?): JSONArray? {
                if (name == "agents" || name == "workflows") reads.incrementAndGet()
                return super.optJSONArray(name)
            }
        }.put("liveAvailable", true).put("agents", source.getJSONArray("agents")).put("workflows", source.getJSONArray("workflows"))
        launch().use { scenario ->
            scenario.onActivity { activity ->
                val chat = ChatFixture.install(activity, session, false, emptyList()); ChatFixture.text(chat, "Retained shared draft")
                ChatFixture.orchestration(activity, data)
            }; idle(); node("activeWorkflow"); node("activeAgents"); node("orchestrationHistory")
            assertEquals(2, reads.get())
            scenario.onActivity { activity ->
                val chat = ChatFixture.state(activity)
                repeat(40) { chat.onTimelineMeta(JSONObject().put("sessionId", session).put("activeTurnId", "")) }
                chat.showNotice("Independent shared snapshot update"); ChatFixture.text(chat, "Next shared draft")
            }; idle(); node("activeWorkflow"); node("chatNotice")
            assertEquals(2, reads.get())
            scenario.onActivity { assertEquals(0, ChatSessionTransport(it).writes); assertEquals("Next shared draft", ChatFixture.state(it).composer.text) }
            println("Shared chat active-work arrays: 2 -> 2 after40 metadata frames, header notice and draft edit")
        }
    }
    private fun ChatSessionTransport(activity: ChatActivity): ChatFixture.Transport {
        val chat = ChatFixture.state(activity)
        return chat.javaClass.getDeclaredField("transport").apply { isAccessible = true }.get(chat) as ChatFixture.Transport
    }
}
