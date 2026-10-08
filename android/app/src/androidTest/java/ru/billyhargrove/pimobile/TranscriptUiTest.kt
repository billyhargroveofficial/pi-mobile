package ru.billyhargrove.pimobile

import android.Manifest
import android.net.ConnectivityManager
import android.graphics.Rect
import android.view.Choreographer
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.features.chat.ChatSession
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TranscriptUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val session = "transcript-fixture"
    @Before fun isolate() {
        Configurator.getInstance().waitForIdleTimeout = 1000
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.ACCESS_NETWORK_STATE)
        try { assertNull(context.getSystemService(ConnectivityManager::class.java).activeNetwork) }
        finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
        instrumentation.runOnMainSync { PiApp.get(context).client().disconnect() }
    }
    private fun idle() { instrumentation.waitForIdleSync(); device.waitForIdle(1000); instrumentation.uiAutomation.clearCache() }
    private fun row(id: String, role: ChatMessage.Role, text: String = id, phase: String = "work") =
        ChatMessage.remote(id, role, text, null, if (role == ChatMessage.Role.TOOL_RESULT) "read" else null).withPresentation("turn", phase, "{}", "")
    private fun meta(epoch: Long = 1) = JSONObject().put("sessionId", session).put("type", "snapshot").put("epoch", epoch)
        .put("activeTurnId", "turn").put("history", JSONObject().put("hasMore", false))
    private fun launch() = ActivityScenario.launch<ChatActivity>(ChatActivity.intent(context, session, "Transcript preview", false))
    private fun wire(chat: ChatSession) = ChatSession::class.java.getDeclaredField("transport").apply { isAccessible = true }.get(chat) as ChatFixture.Transport
    private fun capture(name: String) {
        if (name == "expanded") awaitRenderedToolLog()
        assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "transcript-$name.png")))
    }
    private fun awaitRenderedToolLog() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6)
        var previous: Pair<Rect, Int>? = null; var matches = 0
        while (System.nanoTime() < deadline) {
            val frame = CountDownLatch(1)
            instrumentation.runOnMainSync { Choreographer.getInstance().postFrameCallback { frame.countDown() } }
            assertTrue(frame.await(2, TimeUnit.SECONDS)); instrumentation.uiAutomation.clearCache()
            val bounds = device.findObject(By.res(context.packageName, "workLogList"))?.visibleBounds ?: continue
            if (bounds.isEmpty) continue
            val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: continue
            var hash = 1; val colors = hashSetOf<Int>()
            try {
                for (y in bounds.top.coerceAtLeast(0) until bounds.bottom.coerceAtMost(bitmap.height) step 2)
                    for (x in bounds.left.coerceAtLeast(0) until bounds.right.coerceAtMost(bitmap.width) step 2) {
                        val pixel = bitmap.getPixel(x, y); colors.add(pixel); hash = 31 * hash + pixel
                    }
            } finally { bitmap.recycle() }
            // Accessibility exposes an entering AnimatedVisibility before its pixels appear.
            if (colors.size < 5) { matches = 0; previous = null; continue }
            val current = Rect(bounds) to hash
            matches = if (current == previous) matches + 1 else 0; previous = current
            if (matches >= 2) { println("Transcript rendered tool log settled: $bounds, colors=${colors.size}"); return }
        }
        fail("Expanded tool log never reached stable, visible hardware pixels")
    }
    private fun anchor(scenario: ActivityScenario<ChatActivity>): Pair<String, Int> {
        var result = "" to 0
        scenario.onActivity { a -> val list = ChatFixture.list(a); val row = list.layoutInfo.visibleItemsInfo.first { it.index == list.firstVisibleItemIndex }; result = row.key.toString() to row.offset }
        return result
    }
    @Test fun unchangedMetadataAndDeltaReuseProjectionAndKeepActualReaderAndDraft() {
        launch().use { scenario ->
            lateinit var chat: ChatSession; var saves = 0; var replacements = 0
            scenario.onActivity { a ->
                chat = ChatFixture.install(a, session, false, emptyList()) { saves++ }
                chat.onTimelineMeta(meta()); chat.onSnapshot(Snapshot(session, SessionStatus.RUNNING, true,
                    listOf(row("u", ChatMessage.Role.USER), row("t", ChatMessage.Role.TOOL_RESULT),
                        row("answer", ChatMessage.Role.ASSISTANT, (1..70).joinToString("\n\n") { "Reading line $it retains its position through repeated metadata." }, "answer")), false))
                chat.composer = androidx.compose.ui.text.input.TextFieldValue("Stable draft", androidx.compose.ui.text.TextRange(4))
            }; idle()
            val scrolled = CountDownLatch(1)
            scenario.onActivity { a -> chat.readerDragged(); CoroutineScope(Dispatchers.Main).launch {
                ChatFixture.list(a).scrollToItem(chat.items.indexOfFirst { it.row.key == "answer" }, 480); chat.readerSettled(false); scrolled.countDown()
            } }
            assertTrue(scrolled.await(5, TimeUnit.SECONDS)); idle()
            val before = anchor(scenario); assertEquals("answer", before.first)
            scenario.onActivity {
                var prior = chat.items.first { it.row.key == "answer" }.row
                val tail = chat.tailRevision; val arrival = chat.arrivalRevision; val persisted = saves
                repeat(40) { index ->
                    chat.onTimelineMeta(JSONObject().put("sessionId", session).put("activeTurnId", "turn").put("status", "running").put("observedAt", index))
                    var next = chat.items.first { it.row.key == "answer" }.row; if (next !== prior) replacements++; prior = next
                    chat.onMessages(MessagesUpdate(session, emptyList(), emptyList(), SessionStatus.RUNNING, true, true, true, false, false))
                    next = chat.items.first { it.row.key == "answer" }.row; if (next !== prior) replacements++; prior = next
                }
                val baseline = InstrumentationRegistry.getArguments().getString("baseline") == "true"
                assertEquals(if (baseline) 80 else 0, replacements)
                assertEquals(persisted, saves); assertEquals(tail, chat.tailRevision); assertEquals(arrival, chat.arrivalRevision)
                assertEquals("Stable draft", chat.composer.text); assertEquals(4, chat.composer.selection.start); assertEquals(0, wire(chat).writes)
            }; idle(); assertEquals(before, anchor(scenario)); assertTrue(device.hasObject(By.text("Stable draft")))
            capture("stable"); println("Transcript 40 metadata +40 empty deltas: row replacements=$replacements; reader=$before -> ${anchor(scenario)}; saves=$saves; writes=0")
        }
    }
    @Test fun idleSettlementManualExpansionAndNewEpochKeepTheirRenderedDisclosure() {
        launch().use { scenario ->
            lateinit var chat: ChatSession; val rows = listOf(row("u", ChatMessage.Role.USER), row("t", ChatMessage.Role.TOOL_RESULT, "Tool body remains inspectable"))
            scenario.onActivity { a ->
                chat = ChatFixture.install(a, session, false, emptyList()); chat.onTimelineMeta(meta())
                chat.onSnapshot(Snapshot(session, SessionStatus.IDLE, true, rows, false)); assertFalse(chat.items.last().expanded)
            }; idle()
            device.wait(Until.findObject(By.res(context.packageName, "workHeader")), 5000).click(); idle()
            scenario.onActivity {
                assertTrue(chat.items.last().expanded)
                repeat(40) { chat.onTimelineMeta(JSONObject().put("sessionId", session).put("status", "idle").put("activeTurnId", "turn")); chat.onSnapshot(Snapshot(session, SessionStatus.IDLE, true, rows, false)) }
                assertTrue(chat.items.last().expanded); assertEquals("Tool body remains inspectable", chat.items.last().tools.single().text()); assertEquals(0, wire(chat).writes)
            }; idle(); assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "workLogList")), 5000)); capture("expanded")
            scenario.onActivity { chat.onTimelineMeta(meta(2)); chat.onSnapshot(Snapshot(session, SessionStatus.IDLE, true, rows, false)); assertFalse(chat.items.last().expanded) }
            idle(); assertTrue(device.wait(Until.gone(By.res(context.packageName, "workLogList")), 5000)); capture("epoch")
        }
    }
    @Test fun actualStreamingChangesCanonicalBodyAndKeepsTheNextDraftAndPresenceFlags() {
        launch().use { scenario ->
            lateinit var chat: ChatSession
            scenario.onActivity { a ->
                chat = ChatFixture.install(a, session, false, emptyList()); chat.onTimelineMeta(meta())
                chat.onSnapshot(Snapshot(session, SessionStatus.RUNNING, true, listOf(row("u", ChatMessage.Role.USER), row("answer", ChatMessage.Role.ASSISTANT, "Before stream", "answer")), true))
                ChatFixture.text(chat, "Next draft")
                val previous = chat.items.last().row
                chat.onMessages(MessagesUpdate(session, listOf(row("answer", ChatMessage.Role.ASSISTANT, "Final streamed body", "answer")), emptyList(), SessionStatus.OFFLINE, false, false, false, false, false))
                assertNotSame(previous, chat.items.last().row); assertEquals(SessionStatus.RUNNING, chat.status); assertTrue(chat.truncated)
                chat.onMessages(MessagesUpdate(session, emptyList(), emptyList(), SessionStatus.IDLE, true, true, true, false, true))
                assertEquals(SessionStatus.IDLE, chat.status); assertFalse(chat.truncated); assertEquals("Next draft", chat.composer.text); assertEquals(0, wire(chat).writes)
            }; idle(); assertTrue(device.wait(Until.hasObject(By.text("Final streamed body")), 5000)); assertTrue(device.hasObject(By.text("Next draft"))); capture("stream")
        }
    }
}
