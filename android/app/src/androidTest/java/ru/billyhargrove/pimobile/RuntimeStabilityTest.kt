package ru.billyhargrove.pimobile

import android.graphics.Bitmap
import android.graphics.Color
import android.text.Spanned
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.net.*
import ru.billyhargrove.pimobile.ui.MarkdownRenderer
import ru.billyhargrove.pimobile.ui.ActiveOrchestration
import ru.billyhargrove.pimobile.ui.PiTheme
import androidx.test.uiautomator.*
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Real native rendering/cache/WS dispatch with synthetic data; no live bridge or paid commands. */
@RunWith(AndroidJUnit4::class)
class RuntimeStabilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun rendered(view: TextView, expected: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        var ready = false
        while (!ready && System.nanoTime() < deadline) {
            main { ready = view.text is Spanned && view.text.toString() == expected }
            if (!ready) Thread.sleep(10)
        }
        assertTrue("Parsed native text did not arrive: $expected", ready)
    }

    @Test fun identicalMarkdownLeavesShareOneParseAndStreamingTextStaysFresh() {
        lateinit var renderer: MarkdownRenderer
        lateinit var first: TextView
        lateinit var second: TextView
        main {
            renderer = MarkdownRenderer(context) {}
            first = TextView(context); second = TextView(context)
            renderer.render(first, "**Same**"); renderer.render(second, "**Same**")
        }
        try {
            rendered(first, "Same"); rendered(second, "Same")
            assertEquals(1, renderer.parseCount.get())
            main { repeat(20) { renderer.render(first, "**Same**"); renderer.render(second, "**Same**") } }
            assertEquals(1, renderer.parseCount.get())
            main { renderer.render(first, "**Old stream**"); renderer.render(first, "**Fresh stream**") }
            rendered(first, "Fresh stream"); rendered(second, "Same")
            main { assertEquals("Fresh stream", first.text.toString()) }
        } finally { main { renderer.close() } }
    }
    @Test fun replacingRendererRebindsTheSameSourceForNewThemeOrFontContext() {
        lateinit var first: MarkdownRenderer
        lateinit var next: MarkdownRenderer
        lateinit var view: TextView
        main { first = MarkdownRenderer(context) {}; view = TextView(context); first.render(view, "**Same**") }
        rendered(view, "Same")
        main { first.close(); next = MarkdownRenderer(context) {}; next.render(view, "**Same**") }
        try { rendered(view, "Same"); assertEquals(1, next.parseCount.get()) }
        finally { main { next.close() } }
    }
    @Test fun closedMarkdownRendererCannotOverwriteANewerBinding() {
        lateinit var old: MarkdownRenderer
        lateinit var next: MarkdownRenderer
        lateinit var view: TextView
        main {
            old = MarkdownRenderer(context) {}; view = TextView(context)
            old.render(view, "**Old**"); old.close()
            next = MarkdownRenderer(context) {}; next.render(view, "**New**")
        }
        try { rendered(view, "New"); main { assertEquals("New", view.text.toString()) } }
        finally { main { next.close() } }
    }
    @Test fun activeDockClockTicksWithoutRereadingAgentCounters() {
        val reads = AtomicInteger()
        val agent = object : JSONObject() {
            override fun optLong(name: String?): Long {
                if (name == "toolCalls" || name == "outputTokens") reads.incrementAndGet()
                return super.optLong(name)
            }
        }.put("id", "a").put("workflowId", "w").put("status", "running").put("toolCalls", 7).put("outputTokens", 321)
        val data = JSONObject().put("liveAvailable", true).put("agents", JSONArray().put(agent))
            .put("workflows", JSONArray().put(JSONObject().put("id", "w").put("title", "Counter fixture")
                .put("status", "running").put("startedAt", System.currentTimeMillis() - 6000)))
        main { PiApp.get(context).client().disconnect() }
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContentView(ComposeView(activity).apply {
                setContent { PiTheme { Column(Modifier.statusBarsPadding()) { ActiveOrchestration(data, { _, _, _ -> }) } } }
            }) }
            val selector = By.textContains("7 tools · 321 tokens")
            device.wait(Until.hasObject(selector), 5000)
            device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "runtime-active-dock.png"))
            val first = requireNotNull(device.wait(Until.findObject(selector), 5000)).text
            val count = reads.get()
            assertTrue(device.wait(Until.gone(By.text(first)), 6000))
            System.out.println("Active dock metric reads before/after clock tick: $count/${reads.get()}")
            assertEquals("A real clock update must not aggregate static counters again", count, reads.get())
            assertEquals(2, count)
        }
    }
    private fun png(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        return try { bitmap.eraseColor(color); ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() }
        finally { bitmap.recycle() }
    }
    @Test fun imageBytesAndInflightRequestsStaySeparatedAcrossCredentialChanges() {
        val enteredOld = CountDownLatch(1); val releaseOld = CountDownLatch(1)
        val requests = AtomicInteger()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            val old = chain.request().header("Authorization") == "Bearer synthetic-old"
            if (old) { enteredOld.countDown(); assertTrue(releaseOld.await(5, TimeUnit.SECONDS)) }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(png(if (old) Color.RED else Color.BLUE).toResponseBody("image/png".toMediaType())).build()
        }.build()
        val connection = AtomicReference(MediaLoader.Connection("https://fixture.invalid", "synthetic-old"))
        val loader = MediaLoader(HttpApi(http), connection::get)
        fun load(): Pair<CountDownLatch, AtomicInteger> {
            val done = CountDownLatch(1); val color = AtomicInteger()
            loader.load("/api/sessions/s/media/image", object : MediaLoader.Callback {
                override fun onLoaded(resolvedUrl: String, bitmap: Bitmap) { color.set(bitmap.getPixel(0, 0)); done.countDown() }
                override fun onFailed(resolvedUrl: String, message: String) { color.set(0); done.countDown() }
            })
            return done to color
        }
        val old = load()
        try {
            assertTrue(enteredOld.await(5, TimeUnit.SECONDS))
            connection.set(MediaLoader.Connection("https://fixture.invalid", "synthetic-new"))
            val next = load(); assertTrue(next.first.await(5, TimeUnit.SECONDS)); assertEquals(Color.BLUE, next.second.get())
            releaseOld.countDown(); assertTrue(old.first.await(5, TimeUnit.SECONDS)); assertEquals(Color.RED, old.second.get())
            val cached = load(); assertTrue(cached.first.await(5, TimeUnit.SECONDS)); assertEquals(Color.BLUE, cached.second.get())
            assertEquals(2, requests.get())
        } finally { releaseOld.countDown(); loader.clearCache(); http.dispatcher.executorService.shutdown() }
    }
    @Test fun websocketDataIsDeliveredOnlyForAnOwnedPendingTicketButLateAckStillSettlesReceipt() {
        val http = OkHttpClient(); val client = PiClient(http, HttpApi(http))
        val data = mutableListOf<String>(); val acks = mutableListOf<Ack>()
        val listener = object : PiClient.Listener {
            override fun onConnectionState(state: ConnectionState, detail: String) {}
            override fun onCatalog(catalog: Catalog) {}
            override fun onSnapshot(snapshot: Snapshot) {}
            override fun onAck(ack: Ack) { acks.add(ack) }
            override fun onData(requestId: String, sessionId: String, value: JSONObject) { data.add(value.optString("text")) }
        }
        val register = PiClient::class.java.getDeclaredMethod("registerPending", String::class.java, String::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        val handle = PiClient::class.java.getDeclaredMethod("handleFrame", String::class.java).apply { isAccessible = true }
        fun frame(id: String, session: String, text: String) = JSONObject().put("type", "ack").put("ok", true)
            .put("requestId", id).put("sessionId", session).put("data", JSONObject().put("type", "document").put("text", text)).toString()
        main {
            client.setListener(listener); register.invoke(client, "r", "s", false)
            handle.invoke(client, frame("unknown", "s", "unsolicited")); handle.invoke(client, frame("r", "foreign", "foreign"))
            assertTrue(data.isEmpty()); assertEquals(listOf("r"), client.pendingRequestIds("s"))
            handle.invoke(client, frame("r", "s", "owned")); handle.invoke(client, frame("r", "s", "duplicate"))
            assertEquals(listOf("owned"), data); assertTrue(client.pendingRequestIds().isEmpty())
            assertEquals(4, acks.size); client.shutdown()
        }
        http.dispatcher.executorService.shutdown()
    }
    @Test fun actualSnapshotAndResumedDispatchRetainTheProvenHistoryPrefixAndMetadata() {
        val http = OkHttpClient(); val client = PiClient(http, HttpApi(http))
        val snapshots = mutableListOf<List<String>>(); val cursors = mutableListOf<String>()
        val listener = object : PiClient.Listener {
            override fun onConnectionState(state: ConnectionState, detail: String) {}
            override fun onCatalog(catalog: Catalog) {}
            override fun onSnapshot(snapshot: Snapshot) { snapshots.add(snapshot.messages().map { it.id() }) }
            override fun onAck(ack: Ack) {}
            override fun onTimelineMeta(frame: JSONObject) { cursors.add(frame.optJSONObject("history")?.optString("before").orEmpty()) }
        }
        val handle = PiClient::class.java.getDeclaredMethod("handleFrame", String::class.java).apply { isAccessible = true }
        fun rows(vararg ids: String) = JSONArray().apply { ids.forEach { id -> put(JSONObject().put("id", id).put("role", "user").put("text", id)) } }
        fun frame(epoch: Int, before: String, vararg ids: String) = JSONObject().put("type", "snapshot").put("sessionId", "s")
            .put("epoch", epoch).put("status", "idle").put("connected", true).put("messages", rows(*ids))
            .put("history", JSONObject().put("before", before).put("hasMore", true))
        main {
            client.setListener(listener)
            handle.invoke(client, frame(1, "older", "a", "b", "c", "removed").toString())
            handle.invoke(client, frame(1, "tail", "c", "d").toString())
            handle.invoke(client, JSONObject().put("type", "messages").put("sessionId", "s").put("epoch", 1)
                .put("resumed", true).put("messages", rows("e")).put("order", JSONArray().put("c").put("d").put("e")).toString())
            assertEquals(listOf("a", "b", "c", "d"), snapshots[1])
            assertEquals(listOf("a", "b", "c", "d", "e"), snapshots[2])
            assertEquals(listOf("older", "older", "older"), cursors)
            handle.invoke(client, frame(2, "reset", "fresh").toString())
            assertEquals(listOf("fresh"), snapshots.last()); assertEquals("reset", cursors.last())
            client.shutdown()
        }
        http.dispatcher.executorService.shutdown()
    }

}
