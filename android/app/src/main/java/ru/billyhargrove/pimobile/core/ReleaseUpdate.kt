package ru.billyhargrove.pimobile.core

import org.json.JSONArray
import java.net.URI

/** Public release metadata, restricted origins, bounded size and mandatory digest. */
class ReleaseUpdate private constructor(@JvmField val version: String, @JvmField val url: String,
    @JvmField val sha256: String, @JvmField val size: Long, @JvmField val preview: Boolean) {
    companion object {
        private val versionPattern = Regex("v?[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}")
        @JvmStatic fun compare(a: String, b: String): Int {
            val x = a.removePrefix("v").split('.'); val y = b.removePrefix("v").split('.')
            for (i in 0..2) { val result = x[i].toInt().compareTo(y[i].toInt()); if (result != 0) return result }
            return 0
        }
        @JvmStatic fun newest(releases: JSONArray, installed: String): ReleaseUpdate? {
            var best: ReleaseUpdate? = null
            for (i in 0 until releases.length()) {
                val release = releases.optJSONObject(i) ?: continue
                if (release.optBoolean("draft")) continue
                val tag = release.optString("tag_name")
                if (!versionPattern.matches(tag) || compare(tag, installed) <= 0) continue
                val assets = release.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val asset = assets.optJSONObject(j) ?: continue
                    if (asset.optString("name") != "Pi-Mobile-${tag.removePrefix("v")}.apk") continue
                    val url = asset.optString("browser_download_url"); val digest = asset.optString("digest"); val size = asset.optLong("size")
                    if (!url.startsWith("https://github.com/billyhargroveofficial/pi-mobile/releases/download/$tag/") ||
                        !Regex("sha256:[0-9a-f]{64}").matches(digest) || size <= 0 || size > 32 * 1024 * 1024) continue
                    if (best == null || compare(tag, best.version) > 0) best = ReleaseUpdate(tag, url, digest.substring(7), size, release.optBoolean("prerelease"))
                }
            }
            return best
        }
        @JvmStatic fun allowedDownload(url: String?): Boolean = try {
            val uri = URI.create(url)
            uri.scheme == "https" && uri.userInfo == null && uri.port in setOf(-1, 443) &&
                uri.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
        } catch (_: Exception) { false }
    }
}
