package ru.billyhargrove.pimobile

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.billyhargrove.pimobile.core.ChatMessage
import ru.billyhargrove.pimobile.store.*
import java.io.File
import java.security.MessageDigest

private const val PREFS = "release-upgrade-fixture"
private const val HOST = "https://release-upgrade.example"
private const val LEGACY = "https://LEGACY-upgrade.example:443/"
private const val SECRET = "synthetic-legacy-upgrade-credential"
private const val SESSION = "release-upgrade-fixture-session"
private fun hashes(context: Context): Map<String, String> = buildMap {
    for (folder in listOf(File(context.applicationInfo.dataDir, "shared_prefs"), context.noBackupFilesDir)) {
        folder.walkTopDown().filter(File::isFile).forEach { file ->
            val relative = file.relativeTo(File(context.applicationInfo.dataDir)).path
            put(relative, MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) })
        }
    }
}

/** Runs against the downloaded previous APK; only APIs present in0.6.008 are invoked. */
@RunWith(AndroidJUnit4::class)
class ReleaseUpgradeSeedTest {
    @Test fun seedPublishedPreviousVersionWithoutTouchingRealHosts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("Explicit previous-version stage", InstrumentationRegistry.getArguments().getString("releaseUpgrade") == "seed")
        val context = instrumentation.targetContext
        assertEquals("0.6.008", context.packageManager.getPackageInfo(context.packageName, 0).versionName)
        val cipher = SecureTokenStore().encrypt(SECRET)!!
        assertTrue(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().putString("base_url", LEGACY).putString("token_gcm", cipher).commit())
        PendingMessages(context, HOST, SESSION).save(listOf(ChatMessage.local("upgrade-receipt", "Synthetic preserved receipt", null, ChatMessage.LocalState.UNCERTAIN)))
        // A commit fences previous apply writes before measuring on-disk bytes.
        assertTrue(context.getSharedPreferences("mobile-outbox", Context.MODE_PRIVATE).edit().commit())
        val before = hashes(context); assertTrue(before.isNotEmpty())
        File(context.cacheDir, "release-upgrade-proof.json").writeText(JSONObject(before).toString())
        println("Previous published APK seeded: ${before.size} private files recorded; no credentials printed")
    }
}

/** Runs after adb install -r of the signed release; no clear/uninstall, same package/key/Keystore. */
@RunWith(AndroidJUnit4::class)
class ReleaseUpgradeVerifyTest {
    @Test fun releaseUpgradeKeepsPrivateBytesLegacyCipherAndHostScopedReceipt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("Explicit upgraded-release stage", InstrumentationRegistry.getArguments().getString("releaseUpgrade") == "verify")
        val context = instrumentation.targetContext
        assertEquals("0.6.009", context.packageManager.getPackageInfo(context.packageName, 0).versionName)
        val proof = File(context.cacheDir, "release-upgrade-proof.json")
        val before = JSONObject(proof.readText()); val after = hashes(context)
        before.keys().forEach { name -> assertEquals("Private upgrade bytes changed: $name", before.getString(name), after[name]) }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val envelope = prefs.getString("token_gcm", null)
        val store = SettingsStore(prefs, SecureTokenStore())
        assertEquals(SECRET, store.token()); assertEquals(LEGACY, store.baseUrl()); assertEquals("legacy", store.activeProfileId())
        assertFalse(prefs.contains("connection_profiles_v1"))
        val legacy = store.saveComputer("legacy-upgrade.example", "Legacy desktop", "")
        assertEquals(envelope, prefs.getString("token_gcm", null)); assertEquals(LEGACY, store.baseUrl())
        val laptop = store.saveComputer("laptop-upgrade.example", "Laptop", "synthetic-laptop-secret")
        store.selectComputer(legacy.id); assertEquals(SECRET, store.token()); store.selectComputer(laptop.id); assertEquals("synthetic-laptop-secret", store.token())
        assertEquals("Synthetic preserved receipt", PendingMessages(context, HOST, SESSION).load().single().text())
        println("Published debug0.6.008 -> non-debuggable release0.6.009: ${before.length()} private file SHA256 values preserved; legacy cipher/host/outbox and independent profiles PASS")
        context.deleteSharedPreferences(PREFS); PendingMessages(context, HOST, SESSION).save(emptyList()); proof.delete()
    }
}
