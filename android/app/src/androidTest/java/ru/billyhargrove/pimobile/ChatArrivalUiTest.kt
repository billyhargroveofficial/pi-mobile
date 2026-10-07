package ru.billyhargrove.pimobile

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.PixelCopy
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.billyhargrove.pimobile.core.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual Compose viewport and hardware-rendered frames; synthetic transport only. */
@RunWith(AndroidJUnit4::class)
class ChatArrivalUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val session = "ui-arrival-no-agent"
    @Before fun isolate() {
        Configurator.getInstance().setWaitForIdleTimeout(1000)
        instrumentation.runOnMainSync { PiApp.get(context).client().disconnect() }
    }
    private fun launch() = ActivityScenario.launch<ChatActivity>(ChatActivity.intent(context, session, "Arrival fixture", false))
    private fun message(id: String, text: String, role: ChatMessage.Role = ChatMessage.Role.ASSISTANT) =
        ChatMessage.remote(id, role, text, null, if (role == ChatMessage.Role.TOOL_RESULT) "bash" else null)
            .withPresentation("turn", "final", "{}", "")
    private fun idle() { instrumentation.waitForIdleSync(); device.waitForIdle(1000); Thread.sleep(600) }
    private fun anchor(scenario: ActivityScenario<ChatActivity>): Pair<String, Int> {
        var value = "" to 0
        scenario.onActivity { a ->
            val list = ChatFixture.list(a)
            // visibleItemsInfo also includes preceding rows inside content padding.
            // Measure the actual anchor row and its pixels, never combine a padded
            // predecessor's key with another row's firstVisibleItemScrollOffset.
            val row = list.layoutInfo.visibleItemsInfo.first { it.index == list.firstVisibleItemIndex }
            value = row.key.toString() to row.offset
        }
        return value
    }
    private fun scroll(scenario: ActivityScenario<ChatActivity>, key: String, offset: Int) {
        val done = CountDownLatch(1)
        scenario.onActivity { a ->
            val chat = ChatFixture.state(a); chat.readerDragged()
            CoroutineScope(Dispatchers.Main).launch {
                ChatFixture.list(a).scrollToItem(chat.items.indexOfFirst { it.row.key == key }, offset)
                chat.readerSettled(false); done.countDown()
            }
        }
        assertTrue(done.await(5, TimeUnit.SECONDS)); idle(); assertEquals("Fixture must anchor the requested row", key, anchor(scenario).first)
    }
    private fun longAnswer() = message("answer", (1..90).joinToString("\n\n") { "Reading line $it stays in place while events arrive." })
    private fun source() = listOf(message("u", "Question", ChatMessage.Role.USER), longAnswer()) +
        (1..12).map { message("tool$it", "Tool $it", ChatMessage.Role.TOOL_RESULT) }

    @Test fun lateToolsNewAnswerStreamingAndHistoryKeepTheReadersPixelAnchor() {
        launch().use { scenario ->
            scenario.onActivity { a -> ChatFixture.install(a, session, false, emptyList()); ChatFixture.show(a, source()) }
            idle(); scroll(scenario, "answer", 480); val before = anchor(scenario)
            scenario.onActivity { a -> a.onMessages(MessagesUpdate(session,
                listOf(message("tool13", "New tool", ChatMessage.Role.TOOL_RESULT), message("next", "New answer")), emptyList(),
                SessionStatus.RUNNING, true, false, false, false, false)) }
            idle(); assertEquals("A final-to-progress transition must keep its identity and offset", before, anchor(scenario))
            scenario.onActivity { a -> repeat(15) { index -> a.onMessages(MessagesUpdate(session,
                listOf(message("next", "Streaming update $index")), emptyList(), SessionStatus.RUNNING, true, false, false, false, false)) } }
            idle(); assertEquals(before, anchor(scenario))
            scenario.onActivity { a ->
                a.onTimelineMeta(JSONObject().put("sessionId", session).put("type", "snapshot").put("epoch", 1)
                    .put("history", JSONObject().put("before", "older-cursor").put("hasMore", true)))
                val chat = ChatFixture.state(a); chat.loadOlder()
                chat.onData("read-1", session, JSONObject().put("type", "history").put("epoch", 1)
                    .put("messages", org.json.JSONArray().put(JSONObject().put("id", "older").put("role", "user").put("text", "Older history")))
                    .put("history", JSONObject().put("hasMore", false)))
                chat.onAck(Ack("read-1", session, true, ""))
            }
            idle(); assertEquals("History prepend must preserve the pixel anchor", before, anchor(scenario))
            assertNotNull(device.findObject(By.res(context.packageName, "jumpToLatest")))
            assertNotNull("New events must be disclosed without moving the reader", device.findObject(By.res(context.packageName, "newActivity")))
            device.takeScreenshot(File(context.getExternalFilesDir(null), "chat-reader-stable.png"))
        }
    }
    @Test fun actualStopStartPreservesReaderAndCachedViewportWithoutJumpingToNewEvents() {
        launch().use { scenario ->
            scenario.onActivity { a -> ChatFixture.install(a, session, false, emptyList()); ChatFixture.show(a, source()) }
            idle(); scroll(scenario, "answer", 620); val before = anchor(scenario)
            scenario.moveToState(Lifecycle.State.CREATED); scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { a ->
                a.onTimelineMeta(JSONObject().put("sessionId", session).put("type", "snapshot").put("cached", true).put("epoch", 1))
                a.onSnapshot(Snapshot(session, SessionStatus.RUNNING, true, source() + message("new", "Arrived while away"), false))
            }
            idle(); assertEquals(before, anchor(scenario))
            scenario.onActivity { a -> assertFalse(ChatFixture.state(a).followTail); assertNull(ChatFixture.state(a).restoreViewport) }
        }
    }
    @Test fun returningAtTailMovesThroughIntermediateFramesAndEndsAtMeasuredBottom() {
        launch().use { scenario ->
            val old = (1..8).map { message("m$it", "Message $it\n\nSome content") }
            scenario.onActivity { a -> ChatFixture.install(a, session, false, emptyList()); ChatFixture.show(a, old) }
            idle()
            val positions = mutableListOf<Pair<Int, Int>>(); val done = CountDownLatch(1)
            scenario.moveToState(Lifecycle.State.CREATED); scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { a ->
                val list = ChatFixture.list(a)
                val start = System.nanoTime()
                val callback = object : Choreographer.FrameCallback {
                    override fun doFrame(time: Long) {
                        positions.add(list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)
                        if (System.nanoTime() - start < TimeUnit.SECONDS.toNanos(2)) Choreographer.getInstance().postFrameCallback(this)
                        else done.countDown()
                    }
                }
                Choreographer.getInstance().postFrameCallback(callback)
                a.onTimelineMeta(JSONObject().put("sessionId", session).put("type", "snapshot").put("epoch", 1))
                a.onSnapshot(Snapshot(session, SessionStatus.IDLE, true, old + (9..15).map { message("m$it", "New message $it\n\nMore content") }, false))
            }
            assertTrue(done.await(6, TimeUnit.SECONDS)); idle()
            System.out.println("Catch-up viewport distinct frame positions: ${positions.distinct().size}")
            assertTrue("Catch-up must visibly pass intermediate positions: $positions", positions.distinct().size > 4)
            scenario.onActivity { a -> assertFalse("Must finish at actual measured tail", ChatFixture.list(a).canScrollForward); assertTrue(ChatFixture.state(a).followTail) }
            device.takeScreenshot(File(context.getExternalFilesDir(null), "chat-catchup-tail.png"))
        }
    }
    @Test fun changedVisibleContentOnReturnActuallyFadesAcrossHardwareFrames() {
        launch().use { scenario ->
            val old = message("visible", "**Before returning**\n\n" + "Visible content. ".repeat(12))
            scenario.onActivity { a -> ChatFixture.install(a, session, false, emptyList()); ChatFixture.show(a, listOf(old)) }
            idle(); scenario.onActivity { a -> ChatFixture.state(a).readerDragged() }
            val area = requireNotNull(device.findObject(By.res(context.packageName, "assistantMessage"))).visibleBounds
            // Ignore the top island and its fade; sample the text document itself.
            area.top = maxOf(area.top, (200 * context.resources.displayMetrics.density).toInt())
            val frames = mutableListOf<Bitmap>(); val done = CountDownLatch(1)
            scenario.moveToState(Lifecycle.State.CREATED); scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { a ->
                var count = 0
                val decor = a.window.decorView
                fun capture() {
                    val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
                    PixelCopy.request(a.window, bitmap, { result ->
                        if (result == PixelCopy.SUCCESS) frames.add(bitmap) else bitmap.recycle()
                        count++
                        if (count < 16) Choreographer.getInstance().postFrameCallback { capture() } else done.countDown()
                    }, Handler(Looper.getMainLooper()))
                }
                Choreographer.getInstance().postFrameCallback { capture() }
                a.onTimelineMeta(JSONObject().put("sessionId", session).put("type", "snapshot").put("epoch", 1))
                a.onSnapshot(Snapshot(session, SessionStatus.IDLE, true,
                    listOf(message("visible", "**New events arrived**\n\n" + "Updated content. ".repeat(12))), false))
            }
            assertTrue(done.await(6, TimeUnit.SECONDS)); assertTrue(frames.size >= 8)
            fun pixels(bitmap: Bitmap): IntArray = IntArray(area.width() * area.height()).also {
                bitmap.getPixels(it, 0, area.width(), area.left, area.top, area.width(), area.height())
            }
            val hashes = frames.map { pixels(it).contentHashCode() }.distinct()
            System.out.println("Catch-up document distinct hardware frames: ${hashes.size}")
            assertTrue("Visible updated content must have intermediate rendered opacity", hashes.size >= 4)
            frames.forEachIndexed { index, bitmap ->
                if (index in setOf(0, 4, 8, 15)) File(context.getExternalFilesDir(null), "chat-catchup-frame-$index.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        }
    }
    @Test fun disabledSystemAnimationsSettleCatchUpDirectlyAndStillDiscloseNewActivity() {
        val previous = device.executeShellCommand("settings get global animator_duration_scale").trim()
        device.executeShellCommand("settings put global animator_duration_scale 0")
        try {
            launch().use { scenario ->
                scenario.onActivity { a -> ChatFixture.install(a, session, false, emptyList()); ChatFixture.show(a, source()) }
                idle(); scroll(scenario, "answer", 400); val before = anchor(scenario)
                scenario.moveToState(Lifecycle.State.CREATED); scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.onActivity { a ->
                    assertFalse(ru.billyhargrove.pimobile.ui.ExpressiveMotion.enabled())
                    a.onTimelineMeta(JSONObject().put("sessionId", session).put("type", "snapshot").put("epoch", 1))
                    a.onSnapshot(Snapshot(session, SessionStatus.IDLE, true, source() + message("new", "New without motion"), false))
                }
                idle(); assertEquals(before, anchor(scenario))
                assertNotNull(device.findObject(By.res(context.packageName, "newActivity")))
                device.findObject(By.res(context.packageName, "jumpToLatest")).click(); idle()
                scenario.onActivity { a -> assertFalse(ChatFixture.list(a).canScrollForward); assertFalse(ChatFixture.state(a).newActivity) }
            }
        } finally {
            if (previous == "null") device.executeShellCommand("settings delete global animator_duration_scale")
            else device.executeShellCommand("settings put global animator_duration_scale $previous")
        }
    }

}
