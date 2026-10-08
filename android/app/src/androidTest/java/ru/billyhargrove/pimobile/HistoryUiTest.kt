package ru.billyhargrove.pimobile

import android.Manifest
import android.net.ConnectivityManager
import android.view.Choreographer
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.features.chat.ChatSession
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual Activity/Compose pagination and lifecycle; all read replies are synthetic and offline. */
@RunWith(AndroidJUnit4::class)
class HistoryUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val session = "history-synthetic"
    @Before fun isolate() {
        Configurator.getInstance().waitForIdleTimeout = 1000
        instrumentation.runOnMainSync { PiApp.get(context).client().disconnect() }
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.ACCESS_NETWORK_STATE)
        try { assertNull("History fixtures require an offline emulator", context.getSystemService(ConnectivityManager::class.java).activeNetwork) }
        finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
    }
    private fun idle() { instrumentation.waitForIdleSync(); device.waitForIdle(1000); instrumentation.uiAutomation.clearCache() }
    private fun capture(name: String) {
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync { Choreographer.getInstance().postFrameCallback { Choreographer.getInstance().postFrameCallback { done.countDown() } } }
        assertTrue(done.await(3, TimeUnit.SECONDS)); assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "history-$name.png")))
    }
    private fun row(id: String, role: ChatMessage.Role = ChatMessage.Role.ASSISTANT, text: String = id) = ChatMessage.remote(id, role, text, null, null)
    private fun meta(epoch: Long = 1) = JSONObject().put("sessionId", session).put("type", "snapshot").put("epoch", epoch)
        .put("history", JSONObject().put("before", "a").put("hasMore", true))
    private fun page(next: String = "b", id: String = "older", role: String = "assistant", text: String = "Earlier message") = JSONObject()
        .put("type", "history").put("epoch", 1).put("messages", JSONArray().put(JSONObject().put("id", id).put("role", role).put("text", text)))
        .put("history", JSONObject().put("before", next).put("hasMore", true))
    private fun launch() = ActivityScenario.launch<ChatActivity>(ChatActivity.intent(context, session, "History fixture", true))
    private inner class Host(val scenario: ActivityScenario<ChatActivity>, rows: List<ChatMessage>) {
        lateinit var chat: ChatSession; lateinit var wire: ChatFixture.Transport
        init { scenario.onActivity { a ->
            chat = ChatFixture.install(a, session, true, emptyList())
            wire = ChatSession::class.java.getDeclaredField("transport").apply { isAccessible = true }.get(chat) as ChatFixture.Transport
            chat.composer = androidx.compose.ui.text.input.TextFieldValue("Retained history draft")
            a.onTimelineMeta(meta()); a.onSnapshot(Snapshot(session, SessionStatus.IDLE, true, rows, false))
        }; idle() }
        fun onMain(action: () -> Unit) { instrumentation.runOnMainSync(action); idle() }
        fun anchor(): Pair<String, Int> {
            var result = "" to 0
            scenario.onActivity { a -> val list = ChatFixture.list(a); val row = list.layoutInfo.visibleItemsInfo.first { it.index == list.firstVisibleItemIndex }; result = row.key.toString() to row.offset }
            return result
        }
        fun scroll(key: String, offset: Int) {
            val done = CountDownLatch(1)
            scenario.onActivity { a -> chat.readerDragged(); CoroutineScope(Dispatchers.Main).launch {
                ChatFixture.list(a).scrollToItem(chat.items.indexOfFirst { it.row.key == key }, offset); chat.readerSettled(false); done.countDown()
            } }
            assertTrue(done.await(5, TimeUnit.SECONDS)); idle(); assertEquals(key, anchor().first)
        }
        fun noMutation() { assertEquals(0, wire.writes); assertEquals("Retained history draft", chat.composer.text); assertTrue(chat.queue.isEmpty()) }
    }
    @Test fun duplicatePageAndAckRetainTheActualReaderAnchorAndOnlyOneAcceptedBody() {
        launch().use { scenario ->
            val host = Host(scenario, listOf(row("user", ChatMessage.Role.USER), row("answer", text = (1..70).joinToString("\n\n") { "Reading line $it retains its position while history arrives." })))
            host.scroll("answer", 480); val anchor = host.anchor(); val tail = host.chat.tailRevision; val arrival = host.chat.arrivalRevision
            host.onMain { host.chat.loadOlder() }; assertTrue(host.chat.historyLoading)
            assertNotNull("History spinner must appear after the Compose frame", device.wait(Until.findObject(By.res(context.packageName, "historySpinner")), 3000))
            host.onMain { host.chat.onData("read-1", session, page(role = "user")) }; assertEquals(anchor, host.anchor())
            val accepted = host.chat.items
            host.onMain { host.chat.onData("read-1", session, page("wrong", text = "Duplicate must never appear")); host.chat.onAck(Ack("read-1", session, true, "")) }
            assertSame(accepted, host.chat.items); assertEquals(anchor, host.anchor()); assertFalse(host.chat.historyLoading)
            assertTrue("History spinner must disappear after the Compose frame", device.wait(Until.gone(By.res(context.packageName, "historySpinner")), 3000)); assertEquals(tail, host.chat.tailRevision); assertEquals(arrival, host.chat.arrivalRevision)
            assertEquals("Accepted prepend must preserve the rendered anchor", anchor, host.anchor())
            scenario.onActivity { assertEquals(host.chat.items.size, ChatFixture.list(it).layoutInfo.totalItemsCount) }
            assertFalse(host.chat.followTail); host.noMutation(); println("History reader anchor: $anchor -> ${host.anchor()}"); capture("reader")
        }
    }
    @Test fun repeatedCursorExposesInlineFeedbackAndStreamingCannotRestartPagination() {
        launch().use { scenario ->
            val host = Host(scenario, listOf(row("tail", text = "Pi is working")))
            assertEquals(1, host.wire.reads)
            host.onMain { host.chat.onData("read-1", session, page("a")); host.chat.onAck(Ack("read-1", session, true, ""))
                repeat(50) { host.chat.onMessages(MessagesUpdate(session, listOf(row("tail", text = "Streaming $it")), emptyList(), SessionStatus.IDLE, false, false, false, false, false)); host.chat.loadOlder() } }
            assertEquals(1, host.wire.reads); assertFalse(host.chat.historyLoading); assertEquals("History cursor did not advance", host.chat.notice)
            assertNotNull("History feedback must render", device.wait(Until.findObject(By.res(context.packageName, "chatNotice")), 3000)); host.noMutation(); capture("stalled")
        }
    }
    @Test fun automaticPagesWaitForAckAndStopAtTheCanonicalUserPrompt() {
        launch().use { scenario ->
            val host = Host(scenario, listOf(row("tail", text = "Long assistant turn"))); assertEquals(1, host.wire.reads)
            host.onMain { host.chat.onData("read-1", session, page()); host.chat.loadOlder() }; assertEquals(1, host.wire.reads)
            host.onMain { host.chat.onAck(Ack("read-1", session, true, "")) }; assertEquals(2, host.wire.reads)
            host.onMain { host.chat.onData("read-2", session, page("c", "prompt", "user", "Original user question")); host.chat.onAck(Ack("read-2", session, true, "")) }
            assertEquals(2, host.wire.reads); assertFalse(host.chat.historyLoading); assertEquals(listOf("prompt", "older", "tail"), host.chat.items.map { it.row.message!!.id() })
            assertTrue("Completed context pagination must dismiss its spinner", device.wait(Until.gone(By.res(context.packageName, "historySpinner")), 3000))
            assertNotNull("Loaded user context must actually render", device.wait(Until.findObject(By.text("Original user question")), 3000))
            host.noMutation(); capture("context")
        }
    }
    @Test fun actualStopStartReconcilesMissingTicketWithoutReplayAndKeepsExplicitRetry() {
        launch().use { scenario ->
            val host = Host(scenario, listOf(row("user", ChatMessage.Role.USER), row("answer", text = "Reading\n\n".repeat(70))))
            host.scroll("answer", 500); val anchor = host.anchor(); host.onMain { host.chat.loadOlder(); host.wire.liveRequests = setOf("read-1") }
            scenario.moveToState(Lifecycle.State.CREATED); scenario.moveToState(Lifecycle.State.RESUMED); idle()
            assertTrue(host.chat.historyLoading); assertEquals(1, host.wire.reads); assertEquals(anchor, host.anchor())
            scenario.moveToState(Lifecycle.State.CREATED); host.wire.liveRequests = emptySet(); scenario.moveToState(Lifecycle.State.RESUMED); idle()
            assertFalse(host.chat.historyLoading); assertEquals(1, host.wire.reads)
            host.onMain { host.chat.onData("read-1", session, page(text = "Lost reply")); host.chat.onAck(Ack("read-1", session, true, "")); host.chat.loadOlder() }
            assertEquals(2, host.wire.reads); assertTrue(host.chat.historyLoading)
            host.onMain { host.chat.onData("read-2", session, page(role = "user")); host.chat.onAck(Ack("read-2", session, true, "")) }
            assertEquals(anchor, host.anchor()); assertFalse(host.chat.items.any { it.row.message?.text() == "Lost reply" }); host.noMutation()
        }
    }
    @Test fun activityDestroyRetiresWaitingHistoryAndItsLatePage() {
        val scenario = launch(); lateinit var host: Host
        try { host = Host(scenario, listOf(row("user", ChatMessage.Role.USER))); host.onMain { host.chat.loadOlder() }; assertTrue(host.chat.historyLoading) }
        finally { scenario.close() }
        host.onMain { host.chat.onData("read-1", session, page()); host.chat.onAck(Ack("read-1", session, false, "late")); host.chat.loadOlder() }
        assertFalse(host.chat.historyLoading); assertEquals(1, host.wire.reads); assertEquals(listOf("user"), host.chat.items.map { it.row.message!!.id() }); assertEquals("", host.chat.notice); host.noMutation()
    }
}
