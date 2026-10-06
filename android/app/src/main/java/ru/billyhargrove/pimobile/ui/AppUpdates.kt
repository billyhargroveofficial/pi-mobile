package ru.billyhargrove.pimobile.ui

import android.content.*
import android.content.pm.*
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.*
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import okhttp3.*
import org.json.JSONArray
import java.io.*
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import ru.billyhargrove.pimobile.BuildConfig
import ru.billyhargrove.pimobile.core.ReleaseUpdate
import ru.billyhargrove.pimobile.net.AppExecutors

/** Independent public release client. Only explicit download can open the system installer. */
class AppUpdates(private val activity: AppCompatActivity) {
    private val prefs = activity.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS).build()
    private var busy = false
    @Volatile private var closed = false
    @Volatile private var cancelled = false
    @Volatile private var call: Call? = null
    private var pendingInstall: File? = null
    private var dialog: ComposeSheet? = null
    private var progress by mutableIntStateOf(0)
    fun check(manual: Boolean) {
        if (busy || closed) return
        val now = System.currentTimeMillis()
        if (!manual && now - prefs.getLong("lastCheck", 0) < 6 * 60 * 60 * 1000) return
        busy = true
        if (manual) toast("Checking for updates…")
        AppExecutors.io().execute {
            var update: ReleaseUpdate? = null; var error: String? = null
            try {
                val request = Request.Builder().url("https://api.github.com/repos/billyhargroveofficial/pi-mobile/releases?per_page=40")
                    .header("Accept", "application/vnd.github+json").header("User-Agent", "Pi-Mobile/${BuildConfig.VERSION_NAME}").build()
                val requestCall = http.newCall(request); call = requestCall
                requestCall.execute().use { response ->
                    if (!response.isSuccessful) throw IOException("GitHub returned HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Empty release response")
                    val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    body.byteStream().use { input -> while (true) {
                        val count = input.read(buffer); if (count == -1) break
                        if (output.size() + count > 1024 * 1024) throw IOException("Release response too large")
                        output.write(buffer, 0, count)
                    } }
                    update = ReleaseUpdate.newest(JSONArray(output.toString("UTF-8")), BuildConfig.VERSION_NAME)
                }
                prefs.edit().putLong("lastCheck", now).apply()
            } catch (failure: Exception) { error = failure.message ?: "Network error" }
            val result = update; val failure = error
            AppExecutors.main {
                busy = false
                if (closed) return@main
                if (failure != null) { if (manual) toast("Update check failed: $failure"); return@main }
                if (result == null) { if (manual) toast("You are up to date · ${BuildConfig.VERSION_NAME}"); return@main }
                if (!manual && result.version == prefs.getString("dismissed", "")) return@main
                ask("Pi Mobile ${result.version}", "A new ${if (result.preview) "preview " else ""}release is available (${kotlin.math.round(result.size / 1024.0 / 1024).toInt()} MB).\n\nDownload, verify and open the Android installer? Your connection settings will be kept.",
                    "Download update", { download(result) }, "Later", { prefs.edit().putString("dismissed", result.version).apply() })
            }
        }
    }
    private fun ask(title: String, message: String, confirm: String, accept: () -> Unit, cancel: String = "Later", onLater: () -> Unit = {}) {
        dialog?.dismiss()
        dialog = object : ComposeSheet(activity) { init { content {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(title, fontSize = 22.sp); Text(message, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { onLater(); this@AppUpdates.dialog?.dismiss() }) { Text(cancel) }
                    Button(onClick = { this@AppUpdates.dialog?.dismiss(); accept() }) { Text(confirm) }
                }
            }
        } } }.apply { setOnCancelListener { onLater() }; show() }
    }
    private fun download(update: ReleaseUpdate) {
        if (busy || closed) return
        busy = true; cancelled = false; progress = 0
        dialog = object : ComposeSheet(activity) { init { content {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("Updating Pi Mobile", fontSize = 22.sp)
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text("Downloading · $progress%")
                OutlinedButton(onClick = { cancelDownload(); dismiss() }) { Text("Cancel") }
            }
        } } }.apply { setOnCancelListener { cancelDownload() }; show() }
        AppExecutors.io().execute {
            var ready: File? = null; var failure: String? = null
            try {
                val dir = File(activity.cacheDir, "updates")
                if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create update directory")
                val partial = File(dir, "download.part"); val apk = File(dir, "update.apk")
                try {
                    var url = update.url; var response: Response? = null
                    for (redirect in 0 until 5) {
                        if (cancelled) throw IOException("Cancelled")
                        if (!ReleaseUpdate.allowedDownload(url)) throw IOException("Untrusted download host")
                        val request = http.newCall(Request.Builder().url(url).build()); call = request
                        val result = request.execute()
                        if (result.code in 300..399) {
                            val location = result.header("Location"); result.close()
                            url = location ?: throw IOException("Missing redirect URL")
                        } else { response = result; break }
                    }
                    (response ?: throw IOException("Too many redirects")).use { result ->
                        val body = result.body
                        if (!result.isSuccessful || body == null) throw IOException("Download failed: HTTP ${result.code}")
                        val digest = MessageDigest.getInstance("SHA-256")
                        body.byteStream().use { input -> FileOutputStream(partial).use { output ->
                            val buffer = ByteArray(65536); var total = 0L; var last = -1
                            while (true) {
                                val count = input.read(buffer); if (count == -1) break
                                if (cancelled) throw IOException("Cancelled")
                                total += count; if (total > update.size) throw IOException("Unexpected APK size")
                                digest.update(buffer, 0, count); output.write(buffer, 0, count)
                                val percent = (100 * total / update.size).toInt()
                                if (percent != last) { last = percent; AppExecutors.main { if (!closed) progress = percent } }
                            }
                            if (total != update.size) throw IOException("Incomplete APK download")
                        } }
                        if (hex(digest.digest()) != update.sha256) throw IOException("APK checksum mismatch")
                    }
                    if (cancelled) throw IOException("Cancelled")
                    verifyPackage(partial)
                    if (apk.exists() && !apk.delete()) throw IOException("Cannot replace previous download")
                    if (!partial.renameTo(apk)) throw IOException("Cannot save APK")
                    ready = apk
                } finally { partial.delete() }
            } catch (cause: Exception) { failure = cause.message ?: "Download failed" }
            val apk = ready; val error = failure
            AppExecutors.main {
                busy = false; if (closed) return@main
                dialog?.dismiss(); if (cancelled) return@main
                if (error != null) toast("Update failed: $error") else { pendingInstall = apk; install() }
            }
        }
    }
    private fun verifyPackage(apk: File) {
        val pm = activity.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val candidate = pm.getPackageArchiveInfo(apk.absolutePath, flags); val installed = pm.getPackageInfo(activity.packageName, flags)
        if (candidate == null || candidate.packageName != activity.packageName || PackageInfoCompat.getLongVersionCode(candidate) <= PackageInfoCompat.getLongVersionCode(installed))
            throw IOException("Wrong package or non-newer APK")
        val actual = signers(candidate)
        if (actual.isEmpty() || actual != signers(installed)) throw IOException("APK signing certificate differs from this installation")
    }
    private fun signers(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return signatures.orEmpty().map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }.toSet()
    }
    fun resume() { if (pendingInstall != null && !closed && activity.packageManager.canRequestPackageInstalls()) install() }
    private fun install() {
        val apk = pendingInstall ?: return
        try {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                ask("Allow app updates", "Enable installation from Pi Mobile on the next screen, then return here. Android will still ask you to confirm the update.", "Open settings",
                    { activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))) })
                return
            }
            verifyPackage(apk)
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", apk)
            activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            pendingInstall = null
        } catch (failure: Exception) { toast("Cannot install update: ${failure.message}") }
    }
    private fun cancelDownload() { cancelled = true; call?.cancel() }
    fun close() { closed = true; cancelDownload(); dialog?.dismiss() }
    private fun toast(text: String) { Toast.makeText(activity, text, Toast.LENGTH_LONG).show() }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
