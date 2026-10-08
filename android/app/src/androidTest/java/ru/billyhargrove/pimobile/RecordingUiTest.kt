package ru.billyhargrove.pimobile

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import ru.billyhargrove.pimobile.core.PcmAudio
import ru.billyhargrove.pimobile.features.voice.RecordingSession
import ru.billyhargrove.pimobile.ui.DictationRecorder

/** Production facade/state/modal with controlled capture; no network transcription or Pi prompt. */
@RunWith(AndroidJUnit4::class)
class RecordingUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private class Wire : RecordingSession.Port {
        class Take(val meter: (PcmAudio.Meter) -> Unit, val done: (Result<File>) -> Unit) : RecordingSession.Control {
            var finishes = 0; var cancels = 0
            override fun finish() { finishes++ }
            override fun cancel() { cancels++ }
        }
        val takes = mutableListOf<Take>(); val timers = mutableListOf<() -> Unit>(); val discarded = mutableListOf<File>()
        var failure = false; var timerCancels = 0
        override fun start(meter: (PcmAudio.Meter) -> Unit, done: (Result<File>) -> Unit): RecordingSession.Control {
            if (failure) throw IllegalStateException("Synthetic microphone unavailable")
            return Take(meter, done).also(takes::add)
        }
        override fun deadline(delayMs: Long, action: () -> Unit): () -> Unit {
            assertEquals(600_000L, delayMs); timers.add(action); return { timerCancels++ }
        }
        override fun discard(file: File) { discarded.add(file); file.delete() }
    }
    private inner class Host(val scenario: ActivityScenario<ChatActivity>) {
        val wire = Wire(); val ready = mutableListOf<File>(); val errors = mutableListOf<String>(); val files = mutableListOf<File>()
        var recorder: DictationRecorder? = null
        fun start() { scenario.onActivity { activity ->
            recorder?.cancel()
            recorder = DictationRecorder(activity, { ready.add(it) }, { errors.add(it) }, wire)
            // Use the production Activity.onStop boundary as well as the public facade.
            ChatActivity::class.java.getDeclaredField("dictation").apply { isAccessible = true }.set(activity, recorder)
        }; idle() }
        fun onMain(action: () -> Unit) { instrumentation.runOnMainSync(action); idle() }
        fun pcm() = File.createTempFile("voice-ui-", ".pcm", context.cacheDir).also { it.writeBytes(ByteArray(3200)); files.add(it) }
        fun cancel() = onMain { recorder?.cancel() }
        fun close() { cancel(); files.forEach(File::delete) }
    }
    @Before fun isolate() {
        Configurator.getInstance().waitForIdleTimeout = 1000
        instrumentation.runOnMainSync {
            val app = PiApp.get(context); app.client().disconnect()

        }
        // Emulator-only fixture. Existing connection/credential preferences stay intact.
        instrumentation.uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.ACCESS_NETWORK_STATE)
        try {
            assertNull("Voice fixtures require an offline emulator", context.getSystemService(android.net.ConnectivityManager::class.java).activeNetwork)
        } finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
    }
    private fun idle() { instrumentation.waitForIdleSync(); device.waitForIdle(1000); instrumentation.uiAutomation.clearCache() }
    private fun shown(text: String) { assertTrue("Missing $text", device.wait(Until.hasObject(By.text(text)), 3000)) }
    private fun click(text: String) {
        shown(text); val node = device.findObject(By.text(text)); val bounds = node.visibleBounds
        assertTrue("Control outside display: $text $bounds", bounds.left >= 0 && bounds.right <= device.displayWidth && bounds.bottom < device.displayHeight)
        node.click(); idle()
    }
    private fun capture(name: String) {
        val frames = java.util.concurrent.CountDownLatch(1)
        instrumentation.runOnMainSync { val clock = android.view.Choreographer.getInstance(); clock.postFrameCallback { clock.postFrameCallback { frames.countDown() } } }
        assertTrue(frames.await(3, java.util.concurrent.TimeUnit.SECONDS))
        assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), name)))
    }
    private fun fixture(block: (Host) -> Unit) {
        ActivityScenario.launch<ChatActivity>(ChatActivity.intent(context, "voice-synthetic", "Voice preview", false)).use { scenario ->
            scenario.onActivity { ChatFixture.prepareLoading(it) }
            val host = Host(scenario)
            try { block(host) } finally { host.close() }
        }
    }
    @Test fun listeningRetainsBaselineMeterGeometryAndCancelAction() = fixture { host ->
        host.start(); val take = host.wire.takes.single()
        host.onMain { samples().forEach { take.meter(PcmAudio.Meter(2, it)) } }
        shown("00:02"); shown("Finish"); capture("recording-modal.png")
        val waveform = device.findObject(By.res(context.packageName, "voiceWaveform")); assertNotNull(waveform)
        assertTrue(waveform.visibleBounds.height() > 0); click("Cancel")
        assertEquals(1, take.cancels); assertTrue(host.ready.isEmpty()); assertTrue(host.errors.isEmpty())
    }
    @Test fun finishWaitsForOneCaptureResultAndOnlyReturnsFileToCaller() = fixture { host ->
        host.start(); val take = host.wire.takes.single(); click("Finish"); shown("Finishing…")
        val finish = device.wait(Until.findObject(By.res(context.packageName, "recordingFinish").enabled(false)), 3000)
        assertNotNull("Finishing control must be disabled", finish); finish!!.click(); idle()
        assertEquals(1, take.finishes); capture("recording-finishing.png")
        val file = host.pcm(); host.onMain { take.done(Result.success(file)); take.done(Result.success(file)) }
        assertEquals(listOf(file), host.ready); assertTrue(file.exists()); assertTrue(host.errors.isEmpty())
        assertFalse(device.hasObject(By.text("Listening"))); host.scenario.onActivity { assertTrue(ChatFixture.state(it).items.isEmpty()); assertEquals("", ChatFixture.state(it).composer.text) }
    }
    @Test fun backDuringFinishingCancelsAndDiscardsLateFile() = fixture { host ->
        host.start(); val take = host.wire.takes.single(); click("Finish"); device.pressBack(); idle()
        assertEquals(1, take.finishes); assertEquals(1, take.cancels)
        val file = host.pcm(); host.onMain { take.done(Result.success(file)); take.meter(PcmAudio.Meter(99, 1f)) }
        assertFalse(file.exists()); assertTrue(host.ready.isEmpty()); assertTrue(host.errors.isEmpty()); assertFalse(device.hasObject(By.text("Listening")))
    }
    @Test fun oldCaptureCannotUpdateNewDialogOrHandOffItsFile() = fixture { host ->
        host.start(); val old = host.wire.takes.single(); click("Cancel"); host.start(); val next = host.wire.takes.last()
        val file = host.pcm(); host.onMain { old.meter(PcmAudio.Meter(99, 1f)); old.done(Result.success(file)); host.wire.timers.first() }
        shown("00:00"); assertEquals(0, next.finishes); assertFalse(file.exists()); assertTrue(host.ready.isEmpty())
        host.onMain { next.meter(PcmAudio.Meter(3, .5f)) }; shown("00:03"); click("Cancel")
    }
    @Test fun activityStopCancelsAndReturningCannotResurrectRecording() = fixture { host ->
        host.start(); val take = host.wire.takes.single(); host.scenario.moveToState(Lifecycle.State.CREATED)
        val file = host.pcm(); host.onMain { take.done(Result.success(file)); take.meter(PcmAudio.Meter(9, 1f)) }
        assertEquals(1, take.cancels); assertFalse(file.exists()); assertTrue(host.ready.isEmpty())
        host.scenario.moveToState(Lifecycle.State.RESUMED); idle(); assertFalse(device.hasObject(By.text("Listening")))
    }
    @Test fun cancelledStartupErrorStaysQuietAndCaptureErrorTerminatesPanel() = fixture { host ->
        host.wire.failure = true
        host.scenario.onActivity { activity ->
            host.recorder = DictationRecorder(activity, { host.ready.add(it) }, { host.errors.add(it) }, host.wire)
            host.recorder!!.cancel()
        }; idle(); assertTrue(host.errors.isEmpty()); assertFalse(device.hasObject(By.text("Listening")))
        host.wire.failure = false; host.start(); host.onMain { host.wire.takes.last().done(Result.failure(Exception("Synthetic capture failure"))) }
        assertEquals(listOf("Synthetic capture failure"), host.errors); assertTrue(host.ready.isEmpty()); assertFalse(device.hasObject(By.text("Listening")))
    }
    companion object { private fun samples() = List(48) { (it % 8) / 10f } }
}
