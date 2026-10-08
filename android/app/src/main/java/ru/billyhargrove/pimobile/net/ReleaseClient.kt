package ru.billyhargrove.pimobile.net

import okhttp3.*
import org.json.JSONArray
import java.io.*
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import ru.billyhargrove.pimobile.core.ReleaseUpdate

/** Independent public HTTP client: no session credentials or gateway dependency. */
internal class ReleaseClient(private val cacheDir: File, private val version: String, private val verify: (File) -> Unit,
    private val http: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS).build(),
    private val work: (() -> Unit) -> Unit = { AppExecutors.io().execute(it) },
    private val deliver: (() -> Unit) -> Unit = { AppExecutors.main(it) }) {
    init { require(!http.followRedirects && !http.followSslRedirects) { "Release redirects must be checked explicitly" } }
    private class Job {
        @Volatile var cancelled = false; private var call: Call? = null
        @Synchronized fun cancel() { cancelled = true; call?.cancel() }
        @Synchronized fun own(value: Call) { ensureActive(); call = value }
        fun ensureActive() { if (cancelled) throw IOException("Cancelled") }
        @Synchronized fun commit(action: () -> Unit) { ensureActive(); action() }
    }
    fun check(done: (Result<ReleaseUpdate?>) -> Unit): () -> Unit = launch(done) { job ->
        val request = Request.Builder().url("https://api.github.com/repos/billyhargroveofficial/pi-mobile/releases?per_page=40")
            .header("Accept", "application/vnd.github+json").header("User-Agent", "Pi-Mobile/$version").build()
        response(job, request).use { result ->
            if (!result.isSuccessful) throw IOException("GitHub returned HTTP ${result.code}")
            val body = result.body ?: throw IOException("Empty release response")
            val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            body.byteStream().use { input -> while (true) {
                job.ensureActive(); val count = input.read(buffer); if (count == -1) break
                if (output.size() + count > 1024 * 1024) throw IOException("Release response too large")
                output.write(buffer, 0, count)
            } }
            ReleaseUpdate.newest(JSONArray(output.toString("UTF-8")), version)
        }
    }
    fun download(update: ReleaseUpdate, progress: (Int) -> Unit, done: (Result<File>) -> Unit): () -> Unit = launch(done) { job ->
        val dir = File(cacheDir, "updates")
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) throw IOException("Cannot create update directory")
        // Each job owns its partial; a cancelled host cannot delete a successor's stream.
        val partial = createPartial(dir)
        try {
            downloadResponse(job, update.url).use { result ->
                val body = result.body
                if (!result.isSuccessful || body == null) throw IOException("Download failed: HTTP ${result.code}")
                val digest = MessageDigest.getInstance("SHA-256")
                body.byteStream().use { input -> FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(65536); var total = 0L; var last = -1
                    while (true) {
                        job.ensureActive(); val count = input.read(buffer); if (count == -1) break
                        job.ensureActive(); total += count
                        if (total > update.size) throw IOException("Unexpected APK size")
                        digest.update(buffer, 0, count); output.write(buffer, 0, count)
                        val percent = (100 * total / update.size).toInt()
                        if (percent != last) { last = percent; deliver { if (!job.cancelled) progress(percent) } }
                    }
                    if (total != update.size) throw IOException("Incomplete APK download")
                } }
                if (hex(digest.digest()) != update.sha256) throw IOException("APK checksum mismatch")
            }
            job.ensureActive(); verify(partial)
            val apk = File(dir, "update.apk")
            job.commit { synchronized(promotionLock) {
                // Same-directory rename atomically replaces the previous verified APK.
                if (!partial.renameTo(apk)) throw IOException("Cannot save APK")
            } }
            apk
        } finally { synchronized(promotionLock) { partial.delete(); activePartials.remove(partial) } }
    }
    private fun createPartial(dir: File): File = synchronized(promotionLock) {
        // Process death clears the registry. Only updater-owned orphan names are pruned;
        // concurrent jobs/hosts keep their registered files until their own finally block.
        for (file in dir.listFiles().orEmpty()) {
            val ownedName = file.name == "download.part" || file.name.startsWith("download-") && file.extension == "part"
            if (ownedName && file !in activePartials && !file.delete()) throw IOException("Cannot clean previous download")
        }
        File.createTempFile("download-", ".part", dir).also { activePartials.add(it) }
    }
    private fun downloadResponse(job: Job, initial: String): Response {
        var url = initial
        repeat(5) {
            job.ensureActive()
            if (!ReleaseUpdate.allowedDownload(url)) throw IOException("Untrusted download host")
            val result = response(job, Request.Builder().url(url).build())
            if (result.code !in 300..399) return result
            val location = result.header("Location"); result.close()
            url = location ?: throw IOException("Missing redirect URL")
        }
        throw IOException("Too many redirects")
    }
    private fun response(job: Job, request: Request): Response {
        val call = http.newCall(request); job.own(call)
        return call.execute()
    }
    private fun <T> launch(done: (Result<T>) -> Unit, action: (Job) -> T): () -> Unit {
        val job = Job()
        work {
            val result = runCatching { job.ensureActive(); action(job) }
            deliver { if (!job.cancelled) done(result) }
        }
        return job::cancel
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    private companion object { val promotionLock = Any(); val activePartials = mutableSetOf<File>() }
}
