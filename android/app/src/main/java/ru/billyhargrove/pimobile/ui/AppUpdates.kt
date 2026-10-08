package ru.billyhargrove.pimobile.ui

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import ru.billyhargrove.pimobile.BuildConfig
import ru.billyhargrove.pimobile.core.ReleaseUpdate
import ru.billyhargrove.pimobile.features.updater.UpdateSession
import ru.billyhargrove.pimobile.features.updater.UpdateScreen
import ru.billyhargrove.pimobile.net.ReleaseClient
import java.io.File

/** Catalog-facing facade; wires public HTTP, preferences, Android and modal lifetime. */
class AppUpdates private constructor(private val activity: AppCompatActivity, ports: (UpdateInstaller) -> UpdateSession.Port) {
    constructor(activity: AppCompatActivity) : this(activity, { installer ->
        val prefs = activity.getSharedPreferences("updates", Context.MODE_PRIVATE)
        val client = ReleaseClient(activity.cacheDir, BuildConfig.VERSION_NAME, installer::verify)
        object : UpdateSession.Port {
            override fun lastCheck() = prefs.getLong("lastCheck", 0)
            override fun dismissed() = prefs.getString("dismissed", "").orEmpty()
            override fun rememberCheck(time: Long) { prefs.edit().putLong("lastCheck", time).apply() }
            override fun rememberDismissed(version: String) { prefs.edit().putString("dismissed", version).apply() }
            override fun check(done: (Result<ReleaseUpdate?>) -> Unit) = client.check(done)
            override fun download(update: ReleaseUpdate, progress: (Int) -> Unit, done: (Result<File>) -> Unit) = client.download(update, progress, done)
            override fun allowedToInstall() = installer.allowed()
            override fun openSettings() = installer.openSettings()
            override fun install(apk: File) = installer.install(apk)
        }
    })
    internal constructor(activity: AppCompatActivity, port: UpdateSession.Port) : this(activity, { port })
    private var dialog: ComposeSheet? = null
    private val owner = UpdateSession(BuildConfig.VERSION_NAME, ports(UpdateInstaller(activity)), show = ::present)
    val status get() = owner.status
    fun check(manual: Boolean) = owner.check(manual)
    fun resume() = owner.resume()
    fun close() = owner.close()
    private fun present(panel: UpdateSession.Panel?) {
        dialog?.dismiss(); dialog = null
        if (panel == null) return
        dialog = object : ComposeSheet(activity) { init { content {
            UpdateScreen(panel, owner.progress, owner::accept, owner::dismiss)
        } } }.apply { setOnCancelListener { owner.dismiss() }; show() }
    }
}
