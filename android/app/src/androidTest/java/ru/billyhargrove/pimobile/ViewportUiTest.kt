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
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.features.chat.ChatSession
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual pixel anchors and delayed cache/destroy; synthetic data on an offline emulator. */
@RunWith(AndroidJUnit4::class)
class ViewportUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val session = "viewport-synthetic"
    @Before fun isolate() {
        Configurator.getInstance().waitForIdleTimeout = 1000
        instrumentation.runOnMainSync { PiApp.get(context).client().disconnect() }
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.ACCESS_NETWORK_STATE)
        try { assertNull("Viewport fixtures require an offline emulator", context.getSystemService(ConnectivityManager::class.java).activeNetwork) }
        finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
    }
    private fun idle() { instrumentation.waitForIdleSync(); device.waitForIdle(1000); instrumentation.uiAutomation.clearCache() }
    private fun node(id: String) = requireNotNull(device.wait(Until.findObject(By.res(context.packageName,id)),4000))
    private fun row(id: String,text: String,role: ChatMessage.Role = ChatMessage.Role.ASSISTANT) = ChatMessage.remote(id,role,text,null,null).withPresentation("turn","final","","")
    private fun source() = listOf(row("u","Question",ChatMessage.Role.USER),row("answer",(1..90).joinToString("\n\n") { "Reading line $it remains under the reader's control." }),row("tail","Last known answer"))
    private fun meta(cached: Boolean) = JSONObject().put("sessionId",session).put("type","snapshot").put("epoch",1).put("cached",cached)
        .put("history",JSONObject().put("hasMore",false))
    private fun launch() = ActivityScenario.launch<ChatActivity>(ChatActivity.intent(context,session,"Viewport fixture",false))
    private fun capture(name: String) {
        val done=CountDownLatch(1)
        instrumentation.runOnMainSync { Choreographer.getInstance().postFrameCallback { Choreographer.getInstance().postFrameCallback { done.countDown() } } }
        assertTrue(done.await(3,TimeUnit.SECONDS));assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null),"viewport-$name.png")))
    }
    private inner class Host(val scenario: ActivityScenario<ChatActivity>) {
        lateinit var activity: ChatActivity;lateinit var chat: ChatSession;lateinit var wire: ChatFixture.Transport
        init { scenario.onActivity {
            activity=it;chat=ChatFixture.install(it,session,false,emptyList());ChatFixture.text(chat,"Retained viewport draft")
            wire=ChatSession::class.java.getDeclaredField("transport").apply { isAccessible=true }.get(chat) as ChatFixture.Transport
        };idle() }
        fun show(cached: Boolean,extra: Boolean = false) {
            scenario.onActivity { it.onTimelineMeta(meta(cached));it.onSnapshot(Snapshot(session,SessionStatus.RUNNING,true,source()+if(extra) listOf(row("new","Arrived while away")) else emptyList(),false)) }
            idle();node("assistantMessage")
        }
        fun anchor(): Pair<String,Int> {
            var result="" to 0
            scenario.onActivity { val list=ChatFixture.list(it);val row=list.layoutInfo.visibleItemsInfo.first { info -> info.index==list.firstVisibleItemIndex };result=row.key.toString() to row.offset }
            return result
        }
        fun waitAnchor(expected: Pair<String,Int>) {
            val done=CountDownLatch(1);var success=false
            scenario.onActivity { a ->
                val started=System.nanoTime();var matches=0
                val callback=object : Choreographer.FrameCallback {
                    override fun doFrame(time: Long) {
                        val list=ChatFixture.list(a);val row=list.layoutInfo.visibleItemsInfo.firstOrNull { it.index==list.firstVisibleItemIndex }
                        matches=if (row?.key.toString()==expected.first && row?.offset==expected.second && chat.restoreViewport==null) matches+1 else 0
                        if(matches>=2) { success=true;done.countDown() }
                        else if(System.nanoTime()-started>TimeUnit.SECONDS.toNanos(4)) done.countDown()
                        else Choreographer.getInstance().postFrameCallback(this)
                    }
                };Choreographer.getInstance().postFrameCallback(callback)
            };assertTrue(done.await(6,TimeUnit.SECONDS));assertTrue("Expected actual stable anchor $expected, got ${anchor()}",success)
        }
        fun scroll(offset: Int) {
            val done=CountDownLatch(1)
            scenario.onActivity { a -> chat.readerDragged();CoroutineScope(Dispatchers.Main).launch {
                ChatFixture.list(a).scrollToItem(chat.items.indexOfFirst { it.row.key=="answer" },offset);chat.readerSettled(false);done.countDown()
            } };assertTrue(done.await(4,TimeUnit.SECONDS));waitAnchor("answer" to -offset);idle()
        }
        fun observedAnchors(): List<Pair<String,Int>> {
            val done=CountDownLatch(1);val positions=mutableListOf<Pair<String,Int>>()
            scenario.onActivity { a ->
                val callback=object : Choreographer.FrameCallback {
                    override fun doFrame(time: Long) {
                        val list=ChatFixture.list(a);val row=list.layoutInfo.visibleItemsInfo.first { it.index==list.firstVisibleItemIndex }
                        positions.add(row.key.toString() to row.offset)
                        if(positions.size==30) done.countDown() else Choreographer.getInstance().postFrameCallback(this)
                    }
                };Choreographer.getInstance().postFrameCallback(callback)
            };assertTrue(done.await(4,TimeUnit.SECONDS));return positions.toList()
        }
        fun invariant() { assertEquals(0,wire.writes);assertEquals("Retained viewport draft",chat.composer.text);assertTrue(chat.queue.isEmpty()) }
    }
    /** Fails on the previous APK: a delayed cached snapshot rearmed the old following anchor. */
    @Test fun delayedCachedSnapshotCannotReplaceTheReadersNewPixelAnchor() {
        launch().use { scenario ->
            val h=Host(scenario);h.show(false);h.wire.savedViewport=JSONObject().put("anchor","u").put("offset",0).put("follow",true)
            h.scroll(620);val before=h.anchor();val tail=h.chat.tailRevision;scenario.onActivity { h.chat.start() };h.show(true,true)
            val positions=h.observedAnchors()
            println("Delayed-cache reader frames: $before -> ${positions.distinct()}, viewport reads=${h.wire.viewportReads}")
            assertTrue("Delayed cached restoration must not move the reader: $before -> ${positions.distinct()}",positions.all { it==before });assertEquals(tail,h.chat.tailRevision)
            assertEquals(0,h.wire.viewportReads);assertFalse(h.chat.followTail);assertNull(h.chat.restoreViewport);node("newActivity")
            h.show(true,true);assertEquals(before,h.anchor());h.invariant();capture("late-cache")
            println("Delayed-cache reader anchor: $before -> ${h.anchor()}, viewport reads=${h.wire.viewportReads}")
        }
    }
    @Test fun initialCachedAnchorRestoresOnceAndTheNextCacheCannotUndoManualReading() {
        launch().use { scenario ->
            val h=Host(scenario);h.wire.savedViewport=JSONObject().put("anchor","answer").put("offset",-480).put("follow",false)
            h.show(true);h.waitAnchor("answer" to -480);assertEquals(1,h.wire.viewportReads);assertFalse(h.chat.followTail);capture("restored")
            h.scroll(620);h.wire.savedViewport=JSONObject().put("anchor","u").put("follow",true);h.show(true,true)
            h.waitAnchor("answer" to -620);assertEquals(1,h.wire.viewportReads);h.invariant()
            println("Initial cached reader restored at answer/-480; subsequent manual anchor answer/-620 retained, reads=1")
        }
    }
    @Test fun destroyRetiresViewportCallbacksWithoutSavingOrDisclosingLateFrames() {
        val scenario=launch();val h=Host(scenario);h.show(false);h.scroll(480)
        scenario.moveToState(Lifecycle.State.DESTROYED);idle();val saved=h.wire.savedViewport?.toString();val tail=h.chat.tailRevision;val arrival=h.chat.arrivalRevision
        instrumentation.runOnMainSync {
            h.chat.start();h.activity.onTimelineMeta(meta(true));h.activity.onSnapshot(Snapshot(session,SessionStatus.RUNNING,true,source()+row("late","Late after destroy"),false))
            h.chat.readerSettled(true);h.chat.viewportRestored();h.chat.saveViewport("late",1);h.chat.arrivalsShown(arrival)
        }
        assertEquals(saved,h.wire.savedViewport?.toString());assertEquals(0,h.wire.viewportReads);assertEquals(tail,h.chat.tailRevision)
        assertEquals(arrival,h.chat.arrivalRevision);assertNull(h.chat.restoreViewport);assertTrue(h.chat.arrivingKeys.isEmpty());assertFalse(h.chat.newActivity);h.invariant();scenario.close()
    }
}
