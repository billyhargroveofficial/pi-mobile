package ru.billyhargrove.pimobile.ui

import android.content.Intent
import android.content.pm.*
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Android boundary. Verify both before promoting a download and immediately before sharing it. */
internal class UpdateInstaller(private val activity: AppCompatActivity) {
    fun allowed() = activity.packageManager.canRequestPackageInstalls()
    fun openSettings() { activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))) }
    fun install(apk: File) {
        verify(apk)
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", apk)
        activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
    fun verify(apk: File) {
        val pm = activity.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val candidate = pm.getPackageArchiveInfo(apk.absolutePath, flags)
        val installed = pm.getPackageInfo(activity.packageName, flags)
        if (candidate == null || candidate.packageName != activity.packageName || PackageInfoCompat.getLongVersionCode(candidate) <= PackageInfoCompat.getLongVersionCode(installed))
            throw IOException("Wrong package or non-newer APK")
        val actual = signers(candidate)
        if (actual.isEmpty() || actual != signers(installed)) throw IOException("APK signing certificate differs from this installation")
    }
    private fun signers(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return signatures.orEmpty().map { signature -> MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) } }.toSet()
    }
}
