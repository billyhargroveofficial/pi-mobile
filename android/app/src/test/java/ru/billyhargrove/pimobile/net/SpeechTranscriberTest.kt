package ru.billyhargrove.pimobile.net

import java.io.*
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class SpeechTranscriberTest {
    private class Call : HttpApi.Transcription {
        var executes = 0; var cancels = 0; var onExecute: () -> String = { "recognized speech" }
        override fun execute(): String { executes++; return onExecute() }
        override fun cancel() { cancels++ }
    }
    private class Fixture : AutoCloseable {
        val dir = Files.createTempDirectory(File(System.getenv("PI_MOBILE_TEST_TMP") ?: System.getProperty("java.io.tmpdir")).toPath(), "speech-worker-").toFile()
        val file = File(dir, "recording.pcm").apply { writeBytes(ByteArray(3200)) }
        val workers = ArrayDeque<() -> Unit>(); val mains = ArrayDeque<() -> Unit>(); val results = mutableListOf<Result<String>>()
        val call = Call(); var onOpen: () -> Unit = {}; var opens = 0
        var workFailure = false
        val runner = SpeechTranscriber({ url, token, owned ->
            opens++; assertEquals("https://example.invalid", url); assertEquals("synthetic-token", token); assertTrue(owned.exists())
            onOpen(); call
        }, { if (workFailure) throw IOException("executor closed"); workers.addLast(it) }, mains::addLast)
        fun start() = runner.start("https://example.invalid", "synthetic-token", file, results::add)
        fun worker() = workers.removeFirst()()
        fun main() { while (mains.isNotEmpty()) mains.removeFirst()() }
        override fun close() { dir.deleteRecursively() }
    }
    @Test fun successCleansOwnedPcmBeforeOneMainDelivery() = Fixture().use { f ->
        f.call.onExecute = { assertTrue(f.file.exists()); "speech" }; val control = f.start(); f.worker()
        assertFalse(f.file.exists()); assertTrue(f.results.isEmpty()); assertEquals(1, f.mains.size)
        val delivery = f.mains.first(); f.main(); delivery(); control.cancel()
        assertEquals("speech", f.results.single().getOrThrow()); assertEquals(1, f.call.executes); assertEquals(0, f.call.cancels)
    }
    @Test fun queuedCancelDeletesFileImmediatelyAndNeverOpensHttp() = Fixture().use { f ->
        val control = f.start(); control.cancel(); control.cancel(); assertFalse(f.file.exists()); f.worker(); f.main()
        assertEquals(0, f.opens); assertEquals(0, f.call.executes); assertTrue(f.results.isEmpty())
    }
    @Test fun cancelDuringFactoryCancelsCreatedCallBeforeExecute() = Fixture().use { f ->
        val control = f.start(); f.onOpen = { control.cancel(); assertTrue(f.file.exists()) }; f.worker(); f.main()
        assertEquals(1, f.call.cancels); assertEquals(0, f.call.executes); assertFalse(f.file.exists()); assertTrue(f.results.isEmpty())
    }
    @Test fun activeCancelPreservesFileUntilReaderExitsAndSuppressesLateSuccess() = Fixture().use { f ->
        val control = f.start(); f.call.onExecute = { control.cancel(); control.cancel(); assertTrue(f.file.exists()); "late" }
        f.worker(); f.main(); assertEquals(1, f.call.cancels); assertFalse(f.file.exists()); assertTrue(f.results.isEmpty())
    }
    @Test fun cancelAfterWorkerSuppressesQueuedResultWithoutCancellingFinishedHttp() = Fixture().use { f ->
        val control = f.start(); f.worker(); assertFalse(f.file.exists()); control.cancel(); f.main()
        assertEquals(0, f.call.cancels); assertTrue(f.results.isEmpty())
    }
    @Test fun factoryFailureDeletesFileAndReportsOriginalError() = Fixture().use { f ->
        f.onOpen = { throw IOException("invalid recording") }; f.start(); f.worker(); f.main()
        assertEquals("invalid recording", f.results.single().exceptionOrNull()!!.message); assertFalse(f.file.exists()); assertEquals(0, f.call.executes)
    }
    @Test fun httpFailureDeletesFileAndReportsOriginalError() = Fixture().use { f ->
        f.call.onExecute = { throw IOException("server unavailable") }; f.start(); f.worker(); f.main()
        assertEquals("server unavailable", f.results.single().exceptionOrNull()!!.message); assertFalse(f.file.exists())
    }
    @Test fun schedulingFailureDeletesFileAndHasNoHttpWork() = Fixture().use { f ->
        f.workFailure = true; f.start(); assertFalse(f.file.exists()); f.main()
        assertEquals("executor closed", f.results.single().exceptionOrNull()!!.message); assertEquals(0, f.opens)
    }
    @Test fun cancellationCanSuppressPendingSchedulingError() = Fixture().use { f ->
        f.workFailure = true; val control = f.start(); control.cancel(); f.main(); assertTrue(f.results.isEmpty()); assertFalse(f.file.exists())
    }
    @Test fun fatalReaderErrorStillRemovesPrivateFile() = Fixture().use { f ->
        f.call.onExecute = { throw AssertionError("synthetic fatal reader") }; f.start()
        var error: AssertionError? = null
        try { f.worker() } catch (cause: AssertionError) { error = cause }
        assertEquals("synthetic fatal reader", error?.message)
        assertFalse(f.file.exists()); assertTrue(f.results.isEmpty())
    }
    @Test fun cancellingOneQueuedJobPreservesOtherFileAndCredentialSnapshot() = Fixture().use { f ->
        val other = File(f.dir, "other.pcm").apply { writeBytes(ByteArray(6400)) }; val seen = mutableListOf<Pair<String, String>>()
        val calls = mutableListOf<Call>(); val results = mutableListOf<Result<String>>()
        val runner = SpeechTranscriber({ url, token, file -> seen.add(url to token); assertTrue(file.exists()); Call().also(calls::add) },
            f.workers::addLast, f.mains::addLast)
        val first = runner.start("https://old.invalid", "old-synthetic", f.file, results::add)
        runner.start("https://new.invalid", "new-synthetic", other, results::add)
        first.cancel(); assertFalse(f.file.exists()); assertTrue(other.exists()); f.worker(); f.worker(); f.main()
        assertEquals(listOf("https://new.invalid" to "new-synthetic"), seen); assertEquals(1, results.size)
        assertFalse(other.exists()); assertEquals(0, calls.single().cancels)
    }
    @Test fun activeCancellationReachesRealOkHttpCallAndCleansPcmAfterResponseCloses() = Fixture().use { f ->
        val entered = CountDownLatch(1); val release = CountDownLatch(1); var call: okhttp3.Call? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            call = chain.call(); entered.countDown(); check(release.await(5, TimeUnit.SECONDS))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("synthetic")
                .body("{\"text\":\"late speech\"}".toResponseBody()).build()
        }.build()
        val runner = SpeechTranscriber(HttpApi(client)::transcription, f.workers::addLast, f.mains::addLast)
        val control = runner.start("https://example.invalid", "synthetic-token", f.file, f.results::add)
        val worker = Thread { f.worker() }; worker.start()
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS)); control.cancel(); assertTrue(call!!.isCanceled())
            assertTrue("Don't unlink the PCM while its HTTP reader is active", f.file.exists())
        } finally {
            release.countDown(); worker.join(5000); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll()
        }
        assertFalse(worker.isAlive); f.main(); assertTrue(f.results.isEmpty()); assertFalse(f.file.exists())
    }
}
