package ru.billyhargrove.pimobile.net

import okhttp3.*
import okio.buffer
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.nio.file.Files
import java.security.MessageDigest
import ru.billyhargrove.pimobile.core.ReleaseUpdate

/** Interceptors return synthetic bodies; no network or Android package/system actions. */
class ReleaseClientTest {
    private class Fixture : AutoCloseable {
        val dir = Files.createTempDirectory(File(System.getenv("PI_MOBILE_TEST_TMP") ?: System.getProperty("java.io.tmpdir")).toPath(), "release-client-").toFile()
        val workers = ArrayDeque<() -> Unit>(); val mains = ArrayDeque<() -> Unit>()
        val requests = mutableListOf<Request>(); val verified = mutableListOf<File>(); val progress = mutableListOf<Int>()
        var result: Result<File>? = null; var checked: Result<ReleaseUpdate?>? = null
        var verifyFailure: Exception? = null
        var respond: (Request) -> Response = { response(it, bytes = payload) }
        val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).addInterceptor { chain ->
            requests.add(chain.request()); respond(chain.request())
        }.build()
        val client = ReleaseClient(dir, "0.6.007", { verified.add(it); verifyFailure?.let { failure -> throw failure } }, http, workers::addLast, mains::addLast)
        val ready get() = File(dir, "updates/update.apk")
        fun download(update: ReleaseUpdate = update()): () -> Unit = client.download(update, progress::add) { result = it }
        fun worker() = workers.removeFirst()()
        fun main() { while (mains.isNotEmpty()) mains.removeFirst()() }
        fun partials() = File(dir, "updates").listFiles().orEmpty().filter { it.extension == "part" }
        fun preservePrevious() { ready.parentFile!!.mkdirs(); ready.writeText("previous verified") }
        fun failed(message: String) {
            assertTrue(result!!.isFailure); assertEquals(message, result!!.exceptionOrNull()!!.message)
            assertEquals("previous verified", ready.readText()); assertTrue(partials().isEmpty())
        }
        override fun close() { http.dispatcher.executorService.shutdown(); http.connectionPool.evictAll(); dir.deleteRecursively() }
    }
    @Test fun checkUsesFixedPublicEndpointHeadersAndParsesBoundedMetadata() = Fixture().use { f ->
        f.respond = { response(it, bytes = releases().toString().toByteArray()) }
        f.client.check { f.checked = it }; f.worker(); assertNull(f.checked); f.main()
        assertEquals("v0.6.008", f.checked!!.getOrThrow()!!.version)
        val request = f.requests.single(); assertEquals("api.github.com", request.url.host)
        assertEquals("40", request.url.queryParameter("per_page")); assertNull(request.header("Authorization"))
        assertEquals("Pi-Mobile/0.6.007", request.header("User-Agent")); assertEquals("application/vnd.github+json", request.header("Accept"))
    }
    @Test fun oversizedReleaseBodyFailsBeforeParsing() = Fixture().use { f ->
        f.respond = { response(it, bytes = ByteArray(1024 * 1024 + 1)) }; f.client.check { f.checked = it }; f.worker(); f.main()
        assertEquals("Release response too large", f.checked!!.exceptionOrNull()!!.message)
    }
    @Test fun checkHttpFailureIsReportedWithoutFollowingRedirect() = Fixture().use { f ->
        f.respond = { response(it, 302, "https://example.invalid") }; f.client.check { f.checked = it }; f.worker(); f.main()
        assertEquals("GitHub returned HTTP 302", f.checked!!.exceptionOrNull()!!.message); assertEquals(1, f.requests.size)
    }
    @Test fun verifiedDownloadPromotesAtomicallyAndCleansOwnedPartial() = Fixture().use { f ->
        f.preservePrevious(); f.download(); f.worker(); f.main()
        assertEquals(f.ready, f.result!!.getOrThrow()); assertArrayEquals(payload, f.ready.readBytes())
        assertEquals(1, f.verified.size); assertTrue(f.verified[0].name.startsWith("download-")); assertTrue(f.partials().isEmpty())
        assertEquals(100, f.progress.last()); assertEquals(f.progress.distinct().sorted(), f.progress)
    }
    @Test fun oversizedApkCannotReplacePreviousVerifiedFile() = Fixture().use { f ->
        f.preservePrevious(); f.respond = { response(it, bytes = payload + byteArrayOf(1)) }; f.download(); f.worker(); f.main()
        f.failed("Unexpected APK size"); assertTrue(f.verified.isEmpty())
    }
    @Test fun shortApkCannotReplacePreviousVerifiedFile() = Fixture().use { f ->
        f.preservePrevious(); f.respond = { response(it, bytes = payload.copyOf(payload.size - 1)) }; f.download(); f.worker(); f.main()
        f.failed("Incomplete APK download"); assertTrue(f.verified.isEmpty())
    }
    @Test fun wrongDigestCannotReachPackageVerifierOrReplacePreviousFile() = Fixture().use { f ->
        f.preservePrevious(); f.download(update(digest = "a".repeat(64))); f.worker(); f.main()
        f.failed("APK checksum mismatch"); assertTrue(f.verified.isEmpty())
    }
    @Test fun packageVerifierFailureKeepsPreviousArtifact() = Fixture().use { f ->
        f.preservePrevious(); f.verifyFailure = IOException("certificate differs"); f.download(); f.worker(); f.main()
        f.failed("certificate differs"); assertEquals(1, f.verified.size)
    }
    @Test fun trustedRedirectFollowsOnlyAfterClosingPreviousResponse() = Fixture().use { f ->
        f.respond = { if (it.url.host == "github.com") response(it, 302, "https://release-assets.githubusercontent.com/synthetic.apk") else response(it, bytes = payload) }
        f.download(); f.worker(); f.main(); assertTrue(f.result!!.isSuccess)
        assertEquals(listOf("github.com", "release-assets.githubusercontent.com"), f.requests.map { it.url.host })
        assertTrue(f.requests.all { it.header("Authorization") == null })
    }
    @Test fun untrustedRedirectIsRejectedBeforeSendingRequest() = Fixture().use { f ->
        f.preservePrevious(); f.respond = { response(it, 302, "https://evil.invalid/apk") }; f.download(); f.worker(); f.main()
        f.failed("Untrusted download host"); assertEquals(1, f.requests.size)
    }
    @Test fun missingLocationAndRedirectLoopAreBounded() = Fixture().use { f ->
        f.respond = { response(it, 302) }; f.download(); f.worker(); f.main()
        assertEquals("Missing redirect URL", f.result!!.exceptionOrNull()!!.message)
        f.requests.clear(); f.respond = { response(it, 302, "https://objects.githubusercontent.com/loop") }
        f.download(); f.worker(); f.main(); assertEquals("Too many redirects", f.result!!.exceptionOrNull()!!.message); assertEquals(5, f.requests.size)
    }
    @Test fun cancelBeforeWorkerPreventsNetworkAndCallbacks() = Fixture().use { f ->
        f.download()(); f.worker(); f.main(); assertTrue(f.requests.isEmpty()); assertNull(f.result); assertTrue(f.progress.isEmpty())
        val cancel = f.client.check { f.checked = it }; cancel(); f.worker(); f.main(); assertNull(f.checked); assertTrue(f.requests.isEmpty())
    }
    @Test fun cancelAfterWorkerSuppressesAlreadyQueuedResultAndProgress() = Fixture().use { f ->
        val cancel = f.download(); f.worker(); cancel(); f.main(); assertNull(f.result); assertTrue(f.progress.isEmpty()); assertTrue(f.partials().isEmpty())
    }
    @Test fun cancellationDuringStreamCleansOnlyItsOwnFileAndAllowsNextDownload() = Fixture().use { f ->
        f.preservePrevious(); var cancel: (() -> Unit)? = null
        f.respond = { request ->
            val source = object : okio.ForwardingSource(okio.Buffer().write(payload)) {
                override fun read(sink: okio.Buffer, byteCount: Long): Long { val count = super.read(sink, byteCount); cancel!!(); return count }
            }
            val body = object : ResponseBody() {
                override fun contentType(): MediaType? = null
                override fun contentLength() = payload.size.toLong()
                override fun source() = source.buffer()
            }
            response(request).newBuilder().body(body).build()
        }
        cancel = f.download(); f.worker(); f.main(); assertNull(f.result); assertTrue(f.partials().isEmpty())
        assertEquals("previous verified", f.ready.readText()); assertTrue(f.verified.isEmpty())
        f.respond = { response(it, bytes = payload) }; f.download(); f.worker(); f.main(); assertTrue(f.result!!.isSuccess)
    }
    @Test fun orphanCleanupIsBoundedToKnownNamesAndPreservesUnrelatedCache() = Fixture().use { f ->
        f.preservePrevious()
        val legacy = File(f.ready.parentFile, "download.part").apply { writeText("abandoned legacy") }
        val orphan = File(f.ready.parentFile, "download-abandoned.part").apply { writeText("abandoned new") }
        val unrelated = File(f.ready.parentFile, "unrelated.part").apply { writeText("keep") }
        f.download(); f.worker(); f.main()
        assertFalse(legacy.exists()); assertFalse(orphan.exists()); assertEquals("keep", unrelated.readText())
        assertArrayEquals(payload, f.ready.readBytes()); assertEquals(listOf(unrelated), f.partials())
    }
    @Test fun newJobCleanupPreservesActivePartialAndOldCancellationCannotDeleteSuccessor() = Fixture().use { f ->
        var cancelOld: (() -> Unit)? = null; var active: File? = null
        f.respond = { request ->
            val source = object : okio.ForwardingSource(okio.Buffer().write(payload)) {
                var first = true
                override fun read(sink: okio.Buffer, byteCount: Long): Long {
                    if (first) {
                        first = false; active = f.partials().single()
                        f.respond = { response(it, bytes = payload) }
                        // Separate client models a replacement host while the old read is live.
                        val successor = ReleaseClient(f.dir, "0.6.007", { part ->
                            assertTrue(active!!.exists()); assertNotEquals(active, part); f.verified.add(part)
                        }, f.http, f.workers::addLast, f.mains::addLast)
                        successor.download(update(), f.progress::add) { f.result = it }; f.worker()
                        assertTrue(active!!.exists()); cancelOld!!()
                    }
                    return super.read(sink, byteCount)
                }
            }
            response(request).newBuilder().body(object : ResponseBody() {
                override fun contentType(): MediaType? = null
                override fun contentLength() = payload.size.toLong()
                override fun source() = source.buffer()
            }).build()
        }
        cancelOld = f.download(); f.worker(); f.main()
        assertTrue(f.result!!.isSuccess); assertArrayEquals(payload, f.ready.readBytes())
        assertEquals(1, f.verified.size); assertFalse(active!!.exists()); assertTrue(f.partials().isEmpty())
    }
    companion object {
        private val payload = ByteArray(180_000) { (it % 251).toByte() }
        private fun releases(digest: String = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it.toInt() and 255) }) = JSONArray().put(
            JSONObject().put("tag_name", "v0.6.008").put("assets", JSONArray().put(JSONObject().put("name", "Pi-Mobile-0.6.008.apk")
                .put("browser_download_url", "https://github.com/billyhargroveofficial/pi-mobile/releases/download/v0.6.008/Pi-Mobile-0.6.008.apk")
                .put("digest", "sha256:$digest").put("size", payload.size))))
        private fun update(digest: String? = null) = ReleaseUpdate.newest(if (digest == null) releases() else releases(digest), "0.6.007")!!
        private fun response(request: Request, code: Int = 200, location: String? = null, bytes: ByteArray = byteArrayOf()) = Response.Builder()
            .request(request).protocol(Protocol.HTTP_1_1).code(code).message("synthetic").body(bytes.toResponseBody())
            .apply { if (location != null) header("Location", location) }.build()
    }
}
