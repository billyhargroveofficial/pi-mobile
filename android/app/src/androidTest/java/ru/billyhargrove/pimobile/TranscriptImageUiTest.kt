package ru.billyhargrove.pimobile

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.ConnectivityManager
import android.view.Choreographer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.net.*
import ru.billyhargrove.pimobile.ui.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real public transcript, decoded pixels, same-URL loader replacement and local zoom, entirely intercepted/offline. */
@RunWith(AndroidJUnit4::class)
class TranscriptImageUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val baseline = InstrumentationRegistry.getArguments().getString("baseline") == "true"
    private val url = "/api/sessions/synthetic/media/image"
    @Before fun isolate() {
        Configurator.getInstance().waitForIdleTimeout = 1000
        instrumentation.runOnMainSync { PiApp.get(context).client().disconnect() }
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.ACCESS_NETWORK_STATE)
        try { assertNull("Image fixtures require an offline emulator", context.getSystemService(ConnectivityManager::class.java).activeNetwork) }
        finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
    }
    private fun frame() {
        val latch = CountDownLatch(1)
        instrumentation.runOnMainSync { Choreographer.getInstance().postFrameCallback { latch.countDown() } }
        assertTrue(latch.await(3, TimeUnit.SECONDS))
    }
    private fun idle() { instrumentation.waitForIdleSync(); device.waitForIdle(1000); instrumentation.uiAutomation.clearCache() }
    private fun doubleTap(bounds: Rect) {
        val start = android.os.SystemClock.uptimeMillis() - 130
        repeat(2) { tap ->
            val down = start + tap * 100
            for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                val event = android.view.MotionEvent.obtain(down, down + if (action == android.view.MotionEvent.ACTION_UP) 30 else 0,
                    action, bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0)
                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                assertTrue(instrumentation.uiAutomation.injectInputEvent(event, false)); event.recycle()
            }
        }
        idle()
    }
    private fun capture(name: String) { idle(); frame(); frame(); assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "transcript-image-$name.png"))) }
    private fun node() = requireNotNull(device.wait(Until.findObject(By.desc("Message image 1")), 4000))
    private fun colors(bounds: Rect, color: Int): Int {
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            var count = 0
            for (y in bounds.top.coerceAtLeast(0) until bounds.bottom.coerceAtMost(bitmap.height) step 4)
                for (x in bounds.left.coerceAtLeast(0) until bounds.right.coerceAtMost(bitmap.width) step 4)
                    if (bitmap.getPixel(x, y) == color) count++
            return count
        } finally { bitmap.recycle() }
    }
    private fun pixels(color: Int): Rect {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < end) {
            instrumentation.uiAutomation.clearCache()
            val bounds = try { device.findObject(By.desc("Message image 1"))?.visibleBounds } catch (_: StaleObjectException) { null }
            if (bounds != null && colors(bounds, color) > 100) return bounds
            frame()
        }
        throw AssertionError("No hardware-rendered image with color $color")
    }
    private fun items(value: String): List<TranscriptPresentation.Item> {
        val projection = TranscriptPresentation()
        projection.submit(listOf(ChatMessage.remote("image-message", ChatMessage.Role.USER, "Image fixture", listOf(ImageRef(value, "image/png")), "")))
        return projection.items()
    }
    private fun png(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(32, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle(); output.toByteArray() }
    }
    private inner class Source(private val color: Int, pending: Boolean = false, private val failure: Boolean = false) : AutoCloseable {
        val entered = CountDownLatch(1); val release = CountDownLatch(if (pending) 1 else 0); val finished = CountDownLatch(1)
        val requests = AtomicInteger()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet(); entered.countDown()
            try {
                assertTrue(release.await(15, TimeUnit.SECONDS))
                if (failure) throw IOException("Synthetic image failure")
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(png(color).toResponseBody("image/png".toMediaType())).build()
            } finally { finished.countDown() }
        }.build()
        val loader = MediaLoader(HttpApi(http)) { MediaLoader.Connection("https://fixture.invalid", "synthetic") }
        override fun close() { release.countDown(); loader.clearCache(); http.connectionPool.evictAll(); http.dispatcher.executorService.shutdown() }
    }
    private fun host(scenario: ActivityScenario<ChatActivity>, loader: State<MediaLoader>, content: State<List<TranscriptPresentation.Item>>, actions: TranscriptActions? = null) {
        scenario.onActivity { activity ->
            ChatFixture.install(activity, "transcript-image-synthetic", false, emptyList())
            activity.setContentView(ComposeView(activity).apply { setContent { PiTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()
                    .semantics { testTagsAsResourceId = true }) {
                    PiTranscript(content.value, rememberLazyListState(), 0, loader.value, {}, Modifier.fillMaxSize(), actions, followTailEnabled = false)
                }
            } } })
        }; idle()
    }
    private fun launch() = ActivityScenario.launch<ChatActivity>(ChatActivity.intent(context, "transcript-image-synthetic", "Image fixture", false))
    @Test fun replacingLoadedSourceClearsOldPixelsWhileSameUrlIsPending() {
        Source(Color.RED).use { old -> Source(Color.BLUE, pending = true).use { next -> launch().use { scenario ->
            val loader = mutableStateOf(old.loader); host(scenario, loader, mutableStateOf(items(url)))
            val bounds = pixels(Color.RED)
            scenario.onActivity { loader.value = next.loader }; assertTrue(next.entered.await(5, TimeUnit.SECONDS)); idle(); frame(); frame()
            val retained = colors(bounds, Color.RED)
            if (baseline) assertTrue("Baseline must reproduce retained old pixels", retained > 100)
            else { assertEquals("New binding must not display old source pixels", 0, retained); assertTrue(device.hasObject(By.text("Loading image…"))) }
            println("Same URL pending loader replacement: old red hardware samples=$retained; baseline=$baseline")
            capture("replacement-pending"); next.release.countDown(); pixels(Color.BLUE); capture("replacement-loaded")
            assertEquals(1, old.requests.get()); assertEquals(1, next.requests.get())
        } } }
    }
    @Test fun replacingFailedSourceClearsOldErrorWhileSameUrlIsPending() {
        Source(Color.RED, failure = true).use { old -> Source(Color.BLUE, pending = true).use { next -> launch().use { scenario ->
            val loader = mutableStateOf(old.loader); host(scenario, loader, mutableStateOf(items(url)))
            assertTrue(device.wait(Until.hasObject(By.textContains("Synthetic image failure")), 5000))
            scenario.onActivity { loader.value = next.loader }; assertTrue(next.entered.await(5, TimeUnit.SECONDS)); idle(); frame(); frame()
            if (baseline) assertTrue(device.hasObject(By.textContains("Synthetic image failure")))
            else { assertFalse(device.hasObject(By.textContains("Synthetic image failure"))); assertTrue(device.hasObject(By.text("Loading image…"))) }
            capture("error-replacement"); next.release.countDown(); pixels(Color.BLUE)
            assertEquals(1, old.requests.get()); assertEquals(1, next.requests.get())
        } } }
    }
    @Test fun lateOldDecodeCannotReplaceNewPixels() {
        Source(Color.RED, pending = true).use { old -> Source(Color.BLUE).use { next -> launch().use { scenario ->
            val loader = mutableStateOf(old.loader); host(scenario, loader, mutableStateOf(items(url)))
            assertTrue(old.entered.await(5, TimeUnit.SECONDS)); scenario.onActivity { loader.value = next.loader }
            val bounds = pixels(Color.BLUE); old.release.countDown(); assertTrue(old.finished.await(5, TimeUnit.SECONDS))
            val delivered = CountDownLatch(1)
            old.loader.load(url, object : MediaLoader.Callback {
                override fun onLoaded(resolvedUrl: String, bitmap: Bitmap) { assertEquals(Color.RED, bitmap.getPixel(0, 0)); delivered.countDown() }
                override fun onFailed(resolvedUrl: String, message: String) { fail(message) }
            })
            // This subscriber follows the old adapter in the same flight, or observes its completed cache.
            assertTrue("Old decode did not actually deliver its result", delivered.await(5, TimeUnit.SECONDS))
            println("Old decoded red result delivered; new blue binding remains visible; one request per loader")
            repeat(12) { frame(); assertEquals(0, colors(bounds, Color.RED)); assertTrue(colors(bounds, Color.BLUE) > 100) }
            assertEquals(1, old.requests.get()); assertEquals(1, next.requests.get()); capture("late-old-decode")
        } } }
    }
    @Test fun urlReplacementUsesNewBindingAndKeepsReadOnlyRemoteImageInspectable() {
        Source(Color.BLUE).use { source -> launch().use { scenario ->
            val content = mutableStateOf(items(url)); host(scenario, mutableStateOf(source.loader), content)
            pixels(Color.BLUE); scenario.onActivity { content.value = items(url+"-next") }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
            while (source.requests.get() < 2 && System.nanoTime() < deadline) frame()
            pixels(Color.BLUE)
            assertEquals(2, source.requests.get()); node().click()
            assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "zoomImage")), 4000)); capture("remote-fullscreen")
            device.findObject(By.res(context.packageName, "closeImageButton")).click()
            assertTrue(device.wait(Until.gone(By.res(context.packageName, "zoomImage")), 4000)); pixels(Color.BLUE)
        } }
    }
    @Test fun localPreviewDoesNotLoadRemotelyAndRetainsPortraitZoomAndMissingFeedback() {
        Source(Color.BLUE).use { source -> launch().use { scenario ->
            val local = mutableStateOf<Bitmap?>(null); val requests = mutableListOf<Pair<String,Int>>()
            val actions = TranscriptActions({ id, index -> requests.add(id to index); local.value }, {}, {}, {})
            val message = ChatMessage.local("local-request", "Image fixture", listOf(ImageRef("local:7", "image/png")), ChatMessage.LocalState.SENDING)
            val projection = TranscriptPresentation().apply { submit(listOf(message)) }
            host(scenario, mutableStateOf(source.loader), mutableStateOf(projection.items()), actions)
            assertTrue(device.hasObject(By.text("Select attachment again to restore preview"))); assertEquals(0, source.requests.get())
            val bitmap = Bitmap.createBitmap(32, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
            try {
                scenario.onActivity { local.value = bitmap }; val bounds = pixels(Color.GREEN)
                assertEquals(.5f, bounds.width().toFloat()/bounds.height(), .05f); capture("local-portrait")
                assertTrue(requests.all { it == "local-request" to 7 }); node().click()
                val zoom = requireNotNull(device.wait(Until.findObject(By.res(context.packageName, "zoomImage")), 4000))
                fun scale(): Float { var value = 0f; scenario.onActivity { activity ->
                    val viewer = activity.supportFragmentManager.findFragmentByTag("image-viewer") as androidx.fragment.app.DialogFragment
                    val image = viewer.requireDialog().findViewById<android.widget.ImageView>(R.id.zoomImage)
                    val matrix = FloatArray(9); image.imageMatrix.getValues(matrix); value = matrix[android.graphics.Matrix.MSCALE_X]
                }; return value }
                idle(); val fit = scale(); val center = zoom.visibleBounds
                doubleTap(center)
                assertTrue(scale() > fit * 1.5f); capture("local-fullscreen")
                device.findObject(By.res(context.packageName, "closeImageButton")).click(); idle()
                assertFalse(bitmap.isRecycled); assertEquals(0, source.requests.get()); pixels(Color.GREEN)
            } finally { scenario.close(); bitmap.recycle() }
        } }
    }
}
