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
import ru.billyhargrove.pimobile.store.PendingMessages
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual Compose queue and private codec/store, synthetic command ports on an offline device. */
@RunWith(AndroidJUnit4::class)
class OutboxStateUiTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val device=UiDevice.getInstance(instrumentation)
    private val session="outbox-state-synthetic"
    private val pending get()=PendingMessages(context,"https://fixture.invalid",session)
    @Before fun isolate() {
        Configurator.getInstance().waitForIdleTimeout=1000
        instrumentation.runOnMainSync { PiApp.get(context).client().disconnect() }
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.ACCESS_NETWORK_STATE)
        try { assertNull("Outbox fixtures require an offline emulator",context.getSystemService(ConnectivityManager::class.java).activeNetwork) }
        finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
        pending.save(emptyList())
    }
    @After fun clear() { pending.save(emptyList()) }
    private fun idle() { instrumentation.waitForIdleSync();device.waitForIdle(1000);instrumentation.uiAutomation.clearCache() }
    private fun node(id: String)=requireNotNull(device.wait(Until.findObject(By.res(context.packageName,id)),4000))
    private fun row(id: String,text: String,role: ChatMessage.Role=ChatMessage.Role.ASSISTANT)=ChatMessage.remote(id,role,text,null,null).withPresentation("turn","final","","")
    private fun answer(suffix: String="")=row("answer",(1..90).joinToString("\n\n") { "Reading line $it stays in place while receipts are saved." }+suffix)
    private fun source(suffix: String="",echo: Boolean=false)=listOf(row("old","Question",ChatMessage.Role.USER),answer(suffix))+if(echo) listOf(row("echo","Repeat request",ChatMessage.Role.USER)) else emptyList()
    private fun launch()=ActivityScenario.launch<ChatActivity>(ChatActivity.intent(context,session,"Outbox state",false))
    private fun capture(name: String) {
        val done=CountDownLatch(1)
        instrumentation.runOnMainSync { Choreographer.getInstance().postFrameCallback { Choreographer.getInstance().postFrameCallback { done.countDown() } } }
        assertTrue(done.await(3,TimeUnit.SECONDS));assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null),"outbox-state-$name.png")))
    }
    private inner class Host(val scenario: ActivityScenario<ChatActivity>,initial: List<ChatMessage> = emptyList()) {
        lateinit var chat: ChatSession;lateinit var wire: ChatFixture.Transport
        var saves=0
        init { scenario.onActivity {
            chat=ChatFixture.install(it,session,false,initial) { receipts -> saves++;pending.save(receipts) }
            wire=ChatSession::class.java.getDeclaredField("transport").apply { isAccessible=true }.get(chat) as ChatFixture.Transport
        };show() }
        fun show(suffix: String="",echo: Boolean=false) {
            scenario.onActivity { it.onTimelineMeta(JSONObject().put("sessionId",session).put("type","snapshot").put("epoch",1).put("history",JSONObject().put("hasMore",false)))
                it.onSnapshot(Snapshot(session,SessionStatus.RUNNING,true,source(suffix,echo),false)) };idle()
        }
        fun send(text: String) {
            scenario.onActivity { ChatFixture.text(chat,text);assertTrue(chat.send(CommandBuilder.Behavior.FOLLOW_UP)) };idle()
        }
        fun anchor(): Pair<String,Int> {
            var result="" to 0
            scenario.onActivity { val list=ChatFixture.list(it);val row=list.layoutInfo.visibleItemsInfo.first { info -> info.index==list.firstVisibleItemIndex };result=row.key.toString() to row.offset }
            return result
        }
        fun read() {
            val done=CountDownLatch(1)
            scenario.onActivity { a -> chat.readerDragged();CoroutineScope(Dispatchers.Main).launch { ChatFixture.list(a).scrollToItem(chat.items.indexOfFirst { it.row.key=="answer" },480);chat.readerSettled(false);done.countDown() } }
            assertTrue(done.await(4,TimeUnit.SECONDS));idle();assertEquals("answer" to -480,anchor())
        }
    }
    /** Previous APK invokes the real codec/store port on all 40 unchanged streaming/snapshot frames. */
    @Test fun unchangedStreamingAndSnapshotsDoNotSerializeTheReceiptAgain() {
        launch().use { scenario ->
            val h=Host(scenario);h.send("Follow-up request");scenario.onActivity { ChatFixture.text(h.chat,"Next draft") };idle();h.read()
            val before=h.saves;val anchor=h.anchor();val queue=h.chat.queue
            scenario.onActivity { a -> repeat(30) { a.onMessages(MessagesUpdate(session,listOf(answer("\n\nStream $it")),emptyList(),SessionStatus.RUNNING,true,false,false,false,false)) } }
            repeat(10) { h.show("\n\nStream 29") };idle();node("messageQueue");capture("stream")
            println("Unchanged receipt persistence calls: $before -> ${h.saves}, stream/snapshot frames=40; anchor $anchor -> ${h.anchor()}")
            assertEquals("Unchanged receipt frames must not invoke codec/store again",before,h.saves);assertSame(queue,h.chat.queue);assertEquals(anchor,h.anchor())
            assertEquals(1,h.wire.writes);assertEquals("Next draft",h.chat.composer.text);assertEquals("Follow-up request",pending.load().single().text());assertTrue(pending.load().single().queued())
        }
    }
    @Test fun ackKeepsBothQueuedCopiesAndOneCanonicalEchoConsumesOnlyOne() {
        launch().use { scenario ->
            val h=Host(scenario);repeat(2) { h.send("Repeat request") }
            scenario.onActivity { h.chat.onAck(Ack("request-1",session,true,""));h.chat.onAck(Ack("request-2",session,true,""));ChatFixture.text(h.chat,"Next draft") };idle()
            assertEquals(2,h.chat.queue.size);assertTrue(h.chat.queue.all { it.localState()==ChatMessage.LocalState.ACCEPTED });node("queueHeader").click();idle()
            assertEquals(2,device.findObjects(By.res(context.packageName,"queueRow")).size);capture("two-queued")
            h.show(echo=true);val saves=h.saves;repeat(10) { h.show(echo=true) }
            assertEquals(saves,h.saves);assertEquals("request-2",h.chat.queue.single().requestId());assertEquals("request-2",pending.load().single().requestId())
            assertTrue("echo" in pending.load().single().queueBaseline());assertEquals(2,h.wire.writes);assertEquals("Next draft",h.chat.composer.text);node("messageQueue");capture("remaining")
            println("One canonical echo retains request-2; repeated snapshot saves=$saves -> ${h.saves}, writes=2")
        }
    }
    @Test fun restoredUnknownAndAcceptedQueueStatesSurviveStopStartWithoutReplay() {
        val receipt=ChatMessage.local("old-request","Restored follow-up",null,ChatMessage.LocalState.SENDING).withQueue(listOf("old","answer"))
        val accepted=ChatMessage.local("accepted-request","Accepted follow-up",null,ChatMessage.LocalState.ACCEPTED).withQueue(listOf("old","answer"))
        launch().use { scenario ->
            val h=Host(scenario,listOf(receipt,accepted));scenario.onActivity { h.wire.liveRequests=emptySet();ChatFixture.text(h.chat,"Retained draft") };idle()
            node("queueHeader").click();idle();assertNotNull(device.wait(Until.findObject(By.text("Unknown")),4000))
            assertNotNull(device.wait(Until.findObject(By.text("Last known")),4000));val saves=h.saves
            repeat(2) { scenario.moveToState(Lifecycle.State.CREATED);scenario.moveToState(Lifecycle.State.RESUMED);h.show() }
            assertEquals(saves,h.saves);assertEquals(0,h.wire.writes);assertEquals(listOf(ChatMessage.LocalState.UNCERTAIN,ChatMessage.LocalState.ACCEPTED),h.chat.queue.map { it.localState() });assertTrue(h.chat.queueRestored("old-request"))
            assertEquals("Retained draft",h.chat.composer.text);assertEquals(listOf(ChatMessage.LocalState.UNCERTAIN,ChatMessage.LocalState.ACCEPTED),pending.load().map { it.localState() });capture("last-known")
            println("Restored queue stays unknown/last-known; stop/start saves=$saves -> ${h.saves}, writes=0")
        }
    }
}
