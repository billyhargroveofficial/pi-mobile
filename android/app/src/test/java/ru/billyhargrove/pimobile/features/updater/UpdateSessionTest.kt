package ru.billyhargrove.pimobile.features.updater

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import ru.billyhargrove.pimobile.core.ReleaseUpdate

class UpdateSessionTest {
    private open class Wire : UpdateSession.Port {
        val checks = mutableListOf<(Result<ReleaseUpdate?>) -> Unit>()
        data class Download(val progress: (Int) -> Unit, val done: (Result<File>) -> Unit)
        val downloads = mutableListOf<Download>()
        var checked = 0L; var declined = ""; var cancels = 0; var allowed = false
        var installs = 0; var settings = 0; var failure: Exception? = null
        override fun lastCheck() = checked
        override fun dismissed() = declined
        override fun rememberCheck(time: Long) { checked = time }
        override fun rememberDismissed(version: String) { declined = version }
        override fun check(done: (Result<ReleaseUpdate?>) -> Unit): () -> Unit { checks.add(done); return { cancels++ } }
        override fun download(update: ReleaseUpdate, progress: (Int) -> Unit, done: (Result<File>) -> Unit): () -> Unit {
            downloads.add(Download(progress, done)); return { cancels++ }
        }
        override fun allowedToInstall() = allowed
        override fun openSettings() { settings++; failure?.let { throw it } }
        override fun install(apk: File) { installs++; failure?.let { throw it } }
    }
    private class Fixture(val wire: Wire = Wire()) {
        val panels = mutableListOf<UpdateSession.Panel?>()
        val owner = UpdateSession("0.6.007", wire, { 100_000_000L }, panels::add)
        fun offer() { owner.check(true); wire.checks.last()(Result.success(update())) }
        fun downloading() { offer(); owner.accept() }
        fun permission() { downloading(); wire.downloads.last().done(Result.success(File("synthetic.apk"))) }
    }
    @Test fun automaticCheckThrottlesButManualCheckAndSingleFlightWork() {
        val f = Fixture(); f.wire.checked = 99_999_999L; f.owner.check(false); assertTrue(f.wire.checks.isEmpty())
        f.owner.check(true); f.owner.check(true); assertEquals(1, f.wire.checks.size)
        f.wire.checks[0](Result.success(null)); assertEquals(100_000_000L, f.wire.checked)
        assertEquals("You are up to date · 0.6.007", f.owner.status)
    }
    @Test fun automaticDeclinedOfferStaysQuietAndManualOfferStillAppears() {
        val f = Fixture(); f.wire.declined = "v0.6.008"; f.owner.check(false); f.wire.checks[0](Result.success(update()))
        assertNull(f.owner.panel); assertTrue(f.wire.downloads.isEmpty()); f.offer(); assertTrue(f.owner.panel is UpdateSession.Panel.Offer)
    }
    @Test fun laterRemembersOfferAndNoCheckCanReplaceAnOpenPanel() {
        val f = Fixture(); f.offer(); f.owner.check(true); assertEquals(1, f.wire.checks.size)
        f.owner.dismiss(); assertEquals("v0.6.008", f.wire.declined); assertNull(f.owner.panel); assertTrue(f.wire.downloads.isEmpty())
    }
    @Test fun duplicateCheckResultCannotChangeNewRequestOrRememberAnotherTime() {
        val f = Fixture(); f.owner.check(true); val old = f.wire.checks[0]; old(Result.success(null))
        f.owner.check(true); old(Result.success(update())); assertNull(f.owner.panel); assertEquals("Checking for updates…", f.owner.status)
        f.wire.checks[1](Result.failure(Exception("offline"))); assertEquals("Update check failed: offline", f.owner.status)
    }
    @Test fun failedCheckDoesNotThrottleRetryAndAutomaticErrorIsQuiet() {
        val f = Fixture(); f.owner.check(false); f.wire.checks[0](Result.failure(Exception("offline")))
        assertEquals(0L, f.wire.checked); assertEquals("", f.owner.status)
        f.owner.check(true); assertEquals(2, f.wire.checks.size)
    }
    @Test fun explicitDownloadIsSingleFlightAndProgressIsBoundedMonotonic() {
        val f = Fixture(); f.downloading(); f.owner.accept(); f.owner.check(true); assertEquals(1, f.wire.downloads.size)
        val download = f.wire.downloads[0]; download.progress(45); download.progress(12); download.progress(-20)
        assertEquals(45, f.owner.progress); download.progress(120); assertEquals(100, f.owner.progress)
        assertEquals(UpdateSession.Panel.Progress, f.owner.panel)
    }
    @Test fun cancelledDownloadCannotChangeOrInstallIntoItsSuccessor() {
        val f = Fixture(); f.downloading(); val old = f.wire.downloads[0]; f.owner.dismiss(); assertEquals(1, f.wire.cancels)
        f.downloading(); old.progress(99); old.done(Result.success(File("old.apk"))); old.done(Result.failure(Exception("old")))
        assertEquals(0, f.owner.progress); assertEquals(UpdateSession.Panel.Progress, f.owner.panel); assertEquals(0, f.wire.installs)
        f.wire.downloads[1].progress(30); assertEquals(30, f.owner.progress)
    }
    @Test fun downloadFailureClosesPanelAndAllowsExplicitRetry() {
        val f = Fixture(); f.downloading(); f.wire.downloads[0].done(Result.failure(Exception("checksum")))
        assertNull(f.owner.panel); assertEquals("Update failed: checksum", f.owner.status)
        f.downloading(); assertEquals(2, f.wire.downloads.size)
    }
    @Test fun permissionLaterAndOrdinaryResumeCannotLaunchInstaller() {
        val f = Fixture(); f.permission(); assertEquals(UpdateSession.Panel.Permission, f.owner.panel)
        f.owner.resume(); assertEquals(0, f.wire.settings); f.owner.dismiss(); f.wire.allowed = true; f.owner.resume()
        assertEquals(0, f.wire.installs); f.owner.check(true); assertEquals(2, f.wire.checks.size)
    }
    @Test fun explicitSettingsReturnInstallsOnceAndDuplicateDownloadIsIgnored() {
        val f = Fixture(); f.permission(); f.owner.accept(); assertEquals(1, f.wire.settings); assertNull(f.owner.panel)
        f.owner.check(true); assertEquals(1, f.wire.checks.size)
        f.wire.allowed = true; f.owner.resume(); f.owner.resume(); f.wire.downloads[0].done(Result.success(File("again.apk")))
        assertEquals(1, f.wire.installs)
    }
    @Test fun deniedSettingsReturnOffersRetryOrLaterWithoutGettingStuck() {
        val f = Fixture(); f.permission(); f.owner.accept(); f.owner.resume()
        assertEquals(UpdateSession.Panel.Permission, f.owner.panel); f.owner.accept(); assertEquals(2, f.wire.settings)
        f.owner.resume(); f.owner.dismiss(); f.owner.check(true); assertEquals(2, f.wire.checks.size); assertEquals(0, f.wire.installs)
    }
    @Test fun permittedDownloadInstallsOnceAndFailureNeverRetriesOnResume() {
        val f = Fixture(); f.wire.allowed = true; f.wire.failure = Exception("blocked"); f.permission(); f.owner.resume()
        assertNull(f.owner.panel); assertEquals(1, f.wire.installs); assertEquals("Cannot install update: blocked", f.owner.status)
    }
    @Test fun settingsFailureConsumesPendingIntentAndAllowsNewCheck() {
        val f = Fixture(); f.permission(); f.wire.failure = Exception("missing activity"); f.owner.accept()
        f.wire.allowed = true; f.owner.resume(); assertEquals(0, f.wire.installs); f.owner.check(true); assertEquals(2, f.wire.checks.size)
    }
    @Test fun closeRetiresCheckBeforeLateSuccessOrPrefsWrite() {
        val f = Fixture(); f.owner.check(true); f.owner.close(); f.owner.close(); f.wire.checks[0](Result.success(update()))
        f.owner.check(true); f.owner.accept(); assertEquals(1, f.wire.cancels); assertEquals(0L, f.wire.checked); assertNull(f.owner.panel)
    }
    @Test fun closeRetiresDownloadAndPendingPermissionActions() {
        val f = Fixture(); f.downloading(); f.owner.close(); f.wire.downloads[0].progress(99)
        f.wire.downloads[0].done(Result.success(File("old.apk"))); assertEquals(0, f.owner.progress); assertEquals(0, f.wire.installs)
        val p = Fixture(); p.permission(); p.owner.accept(); p.owner.close(); p.wire.allowed = true; p.owner.resume(); assertEquals(0, p.wire.installs)
    }
    @Test fun synchronousCompletionDoesNotLeaveRequestBusy() {
        val wire = object : Wire() {
            override fun check(done: (Result<ReleaseUpdate?>) -> Unit): () -> Unit { checks.add(done); done(Result.success(null)); return {} }
        }
        val f = Fixture(wire); f.owner.check(true); f.owner.check(true); assertEquals(2, wire.checks.size)
    }
    @Test fun synchronousPortFailureCanBeRetried() {
        val wire = object : Wire() {
            override fun check(done: (Result<ReleaseUpdate?>) -> Unit): () -> Unit { if (checks.isEmpty()) { checks.add(done); throw Exception("sync") }; return super.check(done) }
            override fun download(update: ReleaseUpdate, progress: (Int) -> Unit, done: (Result<File>) -> Unit): () -> Unit { throw Exception("download sync") }
        }
        val f = Fixture(wire); f.owner.check(true); assertEquals("Update check failed: sync", f.owner.status)
        f.offer(); f.owner.accept(); assertEquals("Update failed: download sync", f.owner.status); assertNull(f.owner.panel)
    }
    companion object {
        private fun update() = ReleaseUpdate.newest(JSONArray().put(JSONObject().put("tag_name", "v0.6.008").put("assets", JSONArray().put(
            JSONObject().put("name", "Pi-Mobile-0.6.008.apk").put("browser_download_url", "https://github.com/billyhargroveofficial/pi-mobile/releases/download/v0.6.008/Pi-Mobile-0.6.008.apk")
                .put("digest", "sha256:" + "a".repeat(64)).put("size", 16 * 1024 * 1024)))), "0.6.007")!!
    }
}
