package ru.billyhargrove.pimobile.net

import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class SpeechUploadTest {
    private fun audio(action: (File) -> Unit) {
        val dir = Files.createTempDirectory(File(System.getenv("PI_MOBILE_TEST_TMP") ?: System.getProperty("java.io.tmpdir")).toPath(), "speech-upload-").toFile()
        try { action(File(dir, "synthetic.pcm")) } finally { dir.deleteRecursively() }
    }
    @Test fun canonicalPcmUsesExistingBinaryEndpointSampleRateAndScopedBearer() = audio { file ->
        val bytes = ByteArray(3200) { (it % 128).toByte() }; file.writeBytes(bytes); var calls = 0
        val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).addInterceptor { chain ->
            calls++; val request = chain.request(); val body = Buffer(); request.body!!.writeTo(body)
            assertEquals("https://example.invalid/api/transcribe", request.url.toString()); assertEquals("POST", request.method)
            assertEquals("Bearer synthetic-test-token", request.header("Authorization")); assertEquals("16000", request.header("X-Audio-Sample-Rate"))
            assertEquals("application/octet-stream", request.body!!.contentType().toString()); assertArrayEquals(bytes, body.readByteArray())
            assertEquals(90_000, chain.writeTimeoutMillis()); assertEquals(590_000, chain.readTimeoutMillis())
            assertEquals(600_000_000_000L, chain.call().timeout().timeoutNanos())
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("synthetic").body("{\"text\":\"draft only\"}".toResponseBody()).build()
        }.build()
        try { assertEquals("draft only", HttpApi(client).transcribe("https://example.invalid", "synthetic-test-token", file)); assertEquals(1, calls) }
        finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
    @Test fun preparedRequestCancellationReachesOkHttpAndExecuteFails() = audio { file ->
        file.writeBytes(ByteArray(3200)); var cancellationSeen = false
        // Application interceptors can run for an already-cancelled call; no network is used.
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            cancellationSeen = chain.call().isCanceled()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("synthetic")
                .body("{\"text\":\"cancelled\"}".toResponseBody()).build()
        }.build()
        try {
            val request = HttpApi(client).transcription("https://example.invalid", "synthetic-token", file)
            request.cancel(); request.cancel()
            try { request.execute(); fail("Cancelled request executed") } catch (_: IOException) {}
            assertTrue(cancellationSeen); assertTrue("Blocking HTTP API does not own file cleanup", file.exists())
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
    @Test fun oddShortOversizedAndMissingRecordingAreRejectedBeforeHttp() = audio { file ->
        var calls = 0
        val client = OkHttpClient.Builder().addInterceptor { calls++; error("Invalid audio reached HTTP") }.build()
        val api = HttpApi(client)
        try {
            for (size in listOf(0L, 3198L, 3201L, 19_200_002L)) {
                java.io.RandomAccessFile(file, "rw").use { it.setLength(size) }
                try { api.transcribe("https://example.invalid", "synthetic-test-token", file); fail("Accepted $size") } catch (_: IOException) {}
            }
            file.delete(); try { api.transcribe("https://example.invalid", "synthetic-test-token", file); fail("Missing recording accepted") } catch (_: IOException) {}
            assertEquals(0, calls)
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
}
