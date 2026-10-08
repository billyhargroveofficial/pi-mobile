package ru.billyhargrove.pimobile

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.billyhargrove.pimobile.core.ReleaseUpdate
import ru.billyhargrove.pimobile.features.updater.UpdateSession
import ru.billyhargrove.pimobile.ui.AppUpdates
import ru.billyhargrove.pimobile.ui.UpdateInstaller
import java.io.IOException
import java.io.File

/** Production modal host + controlled ports: no HTTP, download or Android installer launch. */
@RunWith(AndroidJUnit4::class)
class UpdaterUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private class Wire : UpdateSession.Port {
        val checks = mutableListOf<(Result<ReleaseUpdate?>) -> Unit>()
        data class Download(val progress: (Int) -> Unit, val done: (Result<File>) -> Unit)
        val downloads = mutableListOf<Download>()
        var declined = ""; var cancels = 0; var installs = 0; var settings = 0; var allowed = false; var failInstall = false
        override fun lastCheck() = 0L
        override fun dismissed() = declined
        override fun rememberCheck(time: Long) {}
        override fun rememberDismissed(version: String) { declined = version }
        override fun check(done: (Result<ReleaseUpdate?>) -> Unit): () -> Unit { checks.add(done); return { cancels++ } }
        override fun download(update: ReleaseUpdate, progress: (Int) -> Unit, done: (Result<File>) -> Unit): () -> Unit {
            downloads.add(Download(progress, done)); return { cancels++ }
        }
        override fun allowedToInstall() = allowed
        override fun openSettings() { settings++ }
        override fun install(apk: File) { installs++; if (failInstall) throw Exception("synthetic refusal") }
    }
    private fun idle() { instrumentation.waitForIdleSync(); device.waitForIdle(1000); instrumentation.uiAutomation.clearCache() }
    private fun click(text: String) {
        val node = device.wait(Until.findObject(By.text(text)), 3000) ?: error("Missing control: $text")
        val bounds = node.visibleBounds
        assertTrue("Control must fit display: $text $bounds", bounds.left >= 0 && bounds.right <= device.displayWidth && bounds.top > 0 && bounds.bottom < device.displayHeight)
        node.click(); idle()
    }
    private fun capture(name: String) {
        // Accessibility can publish new semantics before the next hardware frame.
        val frames = java.util.concurrent.CountDownLatch(1)
        instrumentation.runOnMainSync {
            val choreographer = android.view.Choreographer.getInstance()
            choreographer.postFrameCallback { choreographer.postFrameCallback { frames.countDown() } }
        }
        assertTrue("Hardware frames did not advance", frames.await(3, java.util.concurrent.TimeUnit.SECONDS))
        assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), name)))
    }
    @Before fun isolate() {
        Configurator.getInstance().waitForIdleTimeout = 1000
        context.getSharedPreferences("updates", 0).edit().putLong("lastCheck", Long.MAX_VALUE).commit()
        instrumentation.runOnMainSync { PiApp.get(context).client().disconnect() }
    }
    private fun fixture(block: (ActivityScenario<MainActivity>, AppUpdates, Wire) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val wire = Wire(); lateinit var updates: AppUpdates
            scenario.onActivity { updates = AppUpdates(it, wire) }
            try { block(scenario, updates, wire) } finally { scenario.onActivity { updates.close() } }
        }
    }
    private fun offer(scenario: ActivityScenario<MainActivity>, updates: AppUpdates, wire: Wire) {
        scenario.onActivity { updates.check(true); wire.checks.last()(Result.success(update())) }; idle()
        assertTrue(device.wait(Until.hasObject(By.text("Download update")), 3000))
    }
    private fun complete(scenario: ActivityScenario<MainActivity>, wire: Wire) {
        scenario.onActivity { wire.downloads.last().done(Result.success(File("synthetic.apk"))) }; idle()
    }
    @Test fun offerProgressAndPermissionKeepGeometryAndExplicitControls() = fixture { scenario, updates, wire ->
        offer(scenario, updates, wire); capture("update-offer.png")
        assertTrue(wire.downloads.isEmpty()); click("Later"); assertEquals("v$nextVersion", wire.declined)
        offer(scenario, updates, wire); click("Download update")
        scenario.onActivity { wire.downloads.last().progress(42) }; idle()
        assertTrue(device.wait(Until.hasObject(By.text("Downloading · 42%")), 3000)); capture("update-progress.png")
        complete(scenario, wire); assertTrue(device.wait(Until.hasObject(By.text("Open settings")), 3000)); capture("update-permission.png")
        click("Later"); scenario.onActivity { wire.allowed = true; updates.resume() }; assertEquals(0, wire.installs)
    }
    @Test fun cancelledStreamCannotDismissNewOfferOrAdvanceNewProgress() = fixture { scenario, updates, wire ->
        offer(scenario, updates, wire); click("Download update"); val old = wire.downloads.last(); click("Cancel")
        assertEquals(1, wire.cancels); offer(scenario, updates, wire)
        scenario.onActivity { old.progress(99); old.done(Result.success(File("old.apk"))) }; idle()
        assertTrue(device.wait(Until.hasObject(By.text("Download update")), 3000)); assertEquals(0, wire.installs)
        click("Download update"); scenario.onActivity { old.progress(100); old.done(Result.failure(Exception("old error"))) }; idle()
        assertTrue(device.wait(Until.hasObject(By.text("Downloading · 0%")), 3000)); click("Cancel")
    }
    @Test fun settingsReturnNeedsExplicitActionAndCannotInstallTwice() = fixture { scenario, updates, wire ->
        offer(scenario, updates, wire); click("Download update"); complete(scenario, wire)
        scenario.onActivity { updates.resume() }; idle(); assertTrue(device.wait(Until.hasObject(By.text("Open settings")), 3000))
        click("Open settings"); assertEquals(1, wire.settings)
        scenario.onActivity { updates.resume() }; idle(); assertTrue(device.wait(Until.hasObject(By.text("Open settings")), 3000))
        click("Open settings"); scenario.onActivity { wire.allowed = true; updates.resume(); updates.resume() }; idle()
        assertEquals(1, wire.installs); assertEquals(2, wire.settings); assertFalse(device.hasObject(By.text("Open settings")))
    }
    @Test fun backCancelsPermissionAndInstallerFailureDoesNotRetryOnResume() = fixture { scenario, updates, wire ->
        offer(scenario, updates, wire); click("Download update"); complete(scenario, wire)
        device.pressBack(); idle(); scenario.onActivity { wire.allowed = true; updates.resume() }; assertEquals(0, wire.installs)
        offer(scenario, updates, wire); click("Download update"); scenario.onActivity { wire.failInstall = true }; complete(scenario, wire)
        scenario.onActivity { updates.resume(); assertEquals("Cannot install update: synthetic refusal", updates.status) }
        assertEquals(1, wire.installs); assertFalse(device.hasObject(By.text("Cancel")))
    }
    @Test fun closeRetiresQueuedCheckAndDownloadWhileNewHostRemainsUsable() = fixture { scenario, updates, wire ->
        scenario.onActivity { updates.check(true); updates.close(); wire.checks.last()(Result.success(update())) }; idle()
        assertFalse(device.hasObject(By.text("Download update"))); assertEquals(1, wire.cancels)
        lateinit var next: AppUpdates
        scenario.onActivity { next = AppUpdates(it, wire) }
        try {
            offer(scenario, next, wire); click("Download update")
            scenario.onActivity { next.close(); wire.downloads.last().progress(99); wire.downloads.last().done(Result.success(File("late.apk"))); next.resume() }
            idle(); assertEquals(0, wire.installs); assertFalse(device.hasObject(By.text("Cancel"))); assertFalse(device.hasObject(By.text("Open settings")))
        } finally { scenario.onActivity { next.close() } }
    }
    @Test fun installerRejectsNonNewerAndInvalidArchiveBeforeAnySystemAction() = fixture { scenario, _, _ ->
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val candidate = File(dir, "updater-verifier-test.part")
        try {
            File(context.applicationInfo.sourceDir).copyTo(candidate, overwrite = true)
            scenario.onActivity { activity ->
                val installer = UpdateInstaller(activity)
                try { installer.verify(candidate); fail("Same version must be rejected") }
                catch (expected: IOException) { assertEquals("Wrong package or non-newer APK", expected.message) }
                candidate.writeText("invalid archive")
                try { installer.verify(candidate); fail("Invalid archive must be rejected") }
                catch (expected: IOException) { assertEquals("Wrong package or non-newer APK", expected.message) }
            }
        } finally { candidate.delete() }
    }
    companion object {
        private val nextVersion = BuildConfig.VERSION_NAME.substringBeforeLast('.') + "." +
            (BuildConfig.VERSION_NAME.substringAfterLast('.').toInt() + 1).toString().padStart(3, '0')
        private fun update() = ReleaseUpdate.newest(JSONArray().put(JSONObject().put("tag_name", "v$nextVersion").put("assets", JSONArray().put(
            JSONObject().put("name", "Pi-Mobile-$nextVersion.apk").put("browser_download_url", "https://github.com/billyhargroveofficial/pi-mobile/releases/download/v$nextVersion/Pi-Mobile-$nextVersion.apk")
                .put("digest", "sha256:" + "a".repeat(64)).put("size", 16 * 1024 * 1024)))), BuildConfig.VERSION_NAME)!!
    }
}
