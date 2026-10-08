package ru.billyhargrove.pimobile

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import java.io.File
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.billyhargrove.pimobile.features.chat.ChatSession
import ru.billyhargrove.pimobile.features.chat.TranscriptionSession
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.net.HttpApi

/** Actual Compose/chat/lifecycle with controlled speech ports; no live HTTP/model prompt. */
@RunWith(AndroidJUnit4::class)
class TranscriptionUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private class Port : TranscriptionSession.Port {
        class Job(val file: File, val done: (Result<String>) -> Unit) : TranscriptionSession.Control {
            var cancels = 0
            override fun cancel() { cancels++; file.delete() }
            fun reply(result: Result<String>) { file.delete(); done(result) }
        }
        val jobs = mutableListOf<Job>()
        override fun start(file: File, done: (Result<String>) -> Unit): TranscriptionSession.Control = Job(file, done).also(jobs::add)
        override fun discard(file: File) { file.delete() }
    }
    private inner class Host(val scenario: ActivityScenario<ChatActivity>, readOnly: Boolean) {
        lateinit var chat: ChatSession; lateinit var owner: TranscriptionSession; lateinit var transport: ChatFixture.Transport
        val port = Port(); val files = mutableListOf<File>()
        init { scenario.onActivity { activity ->
            chat = ChatFixture.install(activity, "speech-ui-synthetic", readOnly, emptyList())
            transport = ChatSession::class.java.getDeclaredField("transport").apply { isAccessible = true }.get(chat) as ChatFixture.Transport
            val field = ChatActivity::class.java.getDeclaredField("transcription").apply { isAccessible = true }
            (field.get(activity) as TranscriptionSession).close()
            owner = TranscriptionSession(readOnly, port, chat::transcriptionChanged, chat::dismissNotice, chat::insertDictation,
                chat::showNotice, { !activity.isFinishing && !activity.isDestroyed })
            field.set(activity, owner)
        }; idle() }
        fun file() = File.createTempFile("speech-ui-", ".pcm", context.cacheDir).also { it.writeBytes(ByteArray(3200)); files.add(it) }
        fun start(): File {
            val file = file(); onMain { assertTrue(owner.start(file)) }; return file
        }
        fun onMain(action: () -> Unit) { instrumentation.runOnMainSync(action); idle() }
        fun noPrompt() { assertEquals(0, transport.writes); assertTrue(chat.items.isEmpty()) }
        fun close() { onMain { owner.close() }; files.forEach(File::delete) }
    }
    @Before fun isolate() {
        Configurator.getInstance().waitForIdleTimeout = 1000
        instrumentation.runOnMainSync { PiApp.get(context).client().disconnect() }
        instrumentation.uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.ACCESS_NETWORK_STATE)
        try { assertNull("Speech fixtures require an offline emulator", context.getSystemService(android.net.ConnectivityManager::class.java).activeNetwork) }
        finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
    }
    private fun idle() { instrumentation.waitForIdleSync(); device.waitForIdle(1000); instrumentation.uiAutomation.clearCache() }
    private fun node(id: String) = requireNotNull(device.wait(Until.findObject(By.res(context.packageName, id)), 5000)) { "Missing $id" }
    private fun capture(name: String) {
        val frames = java.util.concurrent.CountDownLatch(1)
        instrumentation.runOnMainSync { val clock = android.view.Choreographer.getInstance(); clock.postFrameCallback { clock.postFrameCallback { frames.countDown() } } }
        assertTrue(frames.await(3, java.util.concurrent.TimeUnit.SECONDS))
        assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "speech-$name.png")))
    }
    private fun fixture(readOnly: Boolean = false, block: (Host) -> Unit) {
        ActivityScenario.launch<ChatActivity>(ChatActivity.intent(context, "speech-ui-synthetic", "Speech preview", readOnly)).use { scenario ->
            val host = Host(scenario, readOnly)
            try { block(host) } finally { host.close() }
        }
    }
    @Test fun spinnerRetainsCurrentDraftAndSpeechInsertsAtCurrentCursorWithoutSending() = fixture { host ->
        host.onMain { ChatFixture.text(host.chat, "Original draft"); host.chat.showNotice("Old notice") }
        val file = host.start(); node("transcriptionSpinner")
        assertEquals("Transcribing…", node("sendButton").contentDescription); assertFalse(node("sendButton").isEnabled)
        assertFalse(device.hasObject(By.res(context.packageName, "chatNotice"))); capture("spinner")
        host.onMain { host.chat.composer = TextFieldValue("Keep suffix", TextRange(4)); host.port.jobs.single().reply(Result.success("speech")) }
        assertTrue(device.wait(Until.gone(By.res(context.packageName, "transcriptionSpinner")), 5000))
        assertEquals("Keep speech suffix", host.chat.composer.text); assertEquals(TextRange(11), host.chat.composer.selection)
        assertFalse(file.exists()); host.noPrompt(); capture("draft")
    }
    @Test fun actualStopStartRetainsSinglePendingJobAndShowsItsResultOnReturn() = fixture { host ->
        host.onMain { ChatFixture.text(host.chat, "Retained draft") }; val file = host.start(); val job = host.port.jobs.single()
        host.scenario.moveToState(Lifecycle.State.CREATED)
        assertEquals(0, job.cancels); assertTrue(host.chat.transcribing); assertTrue(file.exists())
        host.scenario.moveToState(Lifecycle.State.RESUMED); idle(); node("transcriptionSpinner")
        assertEquals(1, host.port.jobs.size); host.onMain { job.reply(Result.success("speech")) }
        assertEquals("Retained draft speech", host.chat.composer.text); assertFalse(host.chat.transcribing); host.noPrompt()
    }
    @Test fun backgroundCompletionUpdatesOnlyTheRetainedDraftAndDoesNotRestartOnReturn() = fixture { host ->
        host.onMain { ChatFixture.text(host.chat, "Background draft") }; host.start(); val job = host.port.jobs.single()
        host.scenario.moveToState(Lifecycle.State.CREATED); host.onMain { job.reply(Result.success("speech")) }
        assertEquals("Background draft speech", host.chat.composer.text); assertFalse(host.chat.transcribing); assertEquals(0, job.cancels)
        host.scenario.moveToState(Lifecycle.State.RESUMED); idle(); assertFalse(device.hasObject(By.res(context.packageName, "transcriptionSpinner")))
        assertEquals(1, host.port.jobs.size); host.noPrompt()
    }
    @Test fun destroyCancelsOwnedJobAndOldReplyCannotTouchNewActivitySpinnerOrDraft() = fixture { old ->
        old.onMain { ChatFixture.text(old.chat, "Old draft") }; val file = old.start(); val job = old.port.jobs.single()
        old.scenario.close(); idle(); assertEquals(1, job.cancels); assertFalse(file.exists()); assertFalse(old.chat.transcribing)
        fixture { next ->
            next.onMain { ChatFixture.text(next.chat, "New draft") }; next.start(); node("transcriptionSpinner")
            next.onMain { job.reply(Result.success("late speech")); job.reply(Result.failure(Exception("late error"))) }
            assertEquals("Old draft", old.chat.composer.text); assertEquals("New draft", next.chat.composer.text)
            assertTrue(next.chat.transcribing); assertEquals("", next.chat.notice); node("transcriptionSpinner")
            next.onMain { next.port.jobs.single().reply(Result.success("fresh speech")) }
            assertEquals("New draft fresh speech", next.chat.composer.text); old.noPrompt(); next.noPrompt()
        }
    }
    @Test fun errorAndEmptySpeechPreserveDraftAndShowDismissibleInlineFeedback() = fixture { host ->
        host.onMain { ChatFixture.text(host.chat, "Keep this draft") }; host.start()
        host.onMain { host.port.jobs.single().reply(Result.failure(Exception("Synthetic speech failure"))) }
        assertEquals("Keep this draft", host.chat.composer.text); assertFalse(host.chat.transcribing)
        node("chatNotice"); assertTrue(device.hasObject(By.text("Synthetic speech failure"))); capture("error")
        node("dismissNotice").click(); idle(); host.start()
        assertEquals("", host.chat.notice); host.onMain { host.port.jobs.last().reply(Result.success(" \n")) }
        assertEquals("No speech detected", host.chat.notice); assertEquals("Keep this draft", host.chat.composer.text); host.noPrompt()
    }
    @Test fun readOnlyHandoffRejectsFileWithoutHttpOrDraftMutation() = fixture(true) { host ->
        val file = host.file(); host.onMain { assertFalse(host.owner.start(file)) }
        assertFalse(file.exists()); assertTrue(host.port.jobs.isEmpty()); assertFalse(host.chat.transcribing)
        assertEquals("", host.chat.composer.text); assertFalse(device.hasObject(By.res(context.packageName, "transcriptionSpinner"))); host.noPrompt()
    }
    @Test fun actualRecordingHandoffRunsProductionSpeechPortsAndOnlyInsertsSyntheticHttpText() {
        val app = PiApp.get(context)
        val apiField = PiApp::class.java.getDeclaredField("api").apply { isAccessible = true }
        val original = apiField.get(app)
        val permitted = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val calls = java.util.concurrent.atomic.AtomicInteger(); val bytes = java.util.concurrent.atomic.AtomicLong()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = if (request.url.encodedPath.endsWith("/api/transcribe")) {
                calls.incrementAndGet(); assertEquals("16000", request.header("X-Audio-Sample-Rate"))
                assertNotNull(request.header("Authorization"))
                val data = Buffer(); request.body!!.writeTo(data); bytes.set(data.size)
                assertTrue(PcmAudio.validSize(data.size)); "{\"text\":\"Synthetic speech\"}"
            } else "{}"
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("synthetic").body(body.toResponseBody()).build()
        }.build()
        fun pcmFiles() = File(context.cacheDir, "dictation").listFiles().orEmpty().map { it.name }.toSet()
        fun permission(action: String) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "pm $action ${context.packageName} android.permission.RECORD_AUDIO")).use { it.readBytes() }
        }
        val existing = pcmFiles()
        try {
            if (!permitted) permission("grant")
            instrumentation.runOnMainSync { apiField.set(app, HttpApi(client)) }
            ActivityScenario.launch<ChatActivity>(ChatActivity.intent(context, "speech-handoff-synthetic", "Speech handoff", false)).use { scenario ->
                lateinit var chat: ChatSession
                scenario.onActivity { activity ->
                    app.client().clearListener(activity); app.client().disconnect()
                    val discovery = ChatActivity::class.java.getDeclaredField("orchestration").apply { isAccessible = true }.get(activity) as ru.billyhargrove.pimobile.ui.OrchestrationEntry
                    discovery.stop(); discovery.render(org.json.JSONObject())
                    chat = ChatFixture.state(activity)
                    activity.onConnectionState(ConnectionState.CONNECTED, "Synthetic")
                    chat.onSnapshot(Snapshot(chat.sessionId, SessionStatus.IDLE, true, emptyList(), false))
                    ChatFixture.text(chat, "Keep draft")
                    ChatActivity::class.java.getDeclaredMethod("startDictation").apply { isAccessible = true }.invoke(activity)
                }; idle()
                assertTrue(device.wait(Until.hasObject(By.text("Listening")), 5000)); node("voiceWaveform")
                android.os.SystemClock.sleep(2500)
                node("recordingFinish").click(); idle()
                var finished = false
                repeat(50) {
                    if (!finished) { scenario.onActivity { finished = chat.composer.text == "Keep draft Synthetic speech" }; if (!finished) android.os.SystemClock.sleep(100) }
                }
                assertTrue("Production handoff did not insert the synthetic transcript", finished)
                assertEquals(1, calls.get()); assertTrue("Capture must contain actual PCM reads", bytes.get() >= 32000)
                assertEquals(existing, pcmFiles()); assertFalse(chat.transcribing); assertTrue(chat.items.isEmpty()); assertTrue(chat.queue.isEmpty())
                capture("handoff")
            }
        } finally {
            instrumentation.runOnMainSync { apiField.set(app, original) }
            client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll()
            if (!permitted) permission("revoke")
        }
    }
}
