package ru.billyhargrove.pimobile.media

import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.nio.file.Files
import ru.billyhargrove.pimobile.core.PcmAudio

/** Synthetic PCM sources/dispatchers; never instantiate Android AudioRecord in JVM tests. */
class PcmRecorderTest {
    private class Source : PcmRecorder.Source {
        var bytes = ByteArray(6400); var offset = 0; var starts = 0; var stops = 0; var releases = 0
        var startFailure = false; var readFailure = false; var invalidCount: Int? = null; var zeroOnce = false
        var onRead: (() -> Unit)? = null; var end: () -> Unit = {}
        override fun start() { starts++; if (startFailure) throw IOException("start unavailable") }
        override fun read(buffer: ByteArray, count: Int): Int {
            onRead?.invoke(); if (readFailure) throw IOException("capture unavailable")
            invalidCount?.let { return it }; if (zeroOnce) { zeroOnce = false; return 0 }
            if (offset >= bytes.size) { end(); return -1 }
            val n = minOf(count, bytes.size - offset); bytes.copyInto(buffer, 0, offset, offset + n); offset += n; return n
        }
        override fun stop() { stops++ }
        override fun release() { releases++ }
    }
    private class Fixture : AutoCloseable {
        val dir = Files.createTempDirectory(File(System.getenv("PI_MOBILE_TEST_TMP") ?: System.getProperty("java.io.tmpdir")).toPath(), "pcm-").toFile()
        val source = Source(); val workers = ArrayDeque<() -> Unit>(); val mains = ArrayDeque<() -> Unit>()
        val meters = mutableListOf<PcmAudio.Meter>(); val results = mutableListOf<Result<File>>()
        var openFailure = false; var workFailure = false; lateinit var control: PcmRecorder.Control
        val recorder = PcmRecorder(dir, { if (openFailure) throw SecurityException("permission"); source },
            { if (workFailure) throw IOException("worker unavailable"); workers.addLast(it) }, mains::addLast)
        fun start() { source.end = { control.finish() }; control = recorder.start(meters::add, results::add) }
        fun worker() = workers.removeFirst()()
        fun main() { while (mains.isNotEmpty()) mains.removeFirst()() }
        fun files() = File(dir, "dictation").listFiles().orEmpty().toList()
        fun finished() { assertEquals(1, source.stops); assertEquals(1, source.releases) }
        override fun close() { dir.deleteRecursively() }
    }
    @Test fun privateCanonicalPcmTransfersOnceAfterSourceClosesAndCancelCannotDeleteHandedFile() = Fixture().use { f ->
        f.start(); f.worker(); f.main(); val file = f.results.single().getOrThrow()
        assertTrue(file.canonicalPath.startsWith(f.dir.canonicalPath + File.separator)); assertEquals("pcm", file.extension)
        assertArrayEquals(f.source.bytes, file.readBytes()); f.finished(); f.control.cancel(); assertTrue(file.exists())
    }
    @Test fun finishAndCancelAreIdempotentWhileWorkerRetainsReleaseOwnership() = Fixture().use { f ->
        f.start(); f.control.finish(); f.control.finish(); f.control.cancel(); f.control.cancel(); assertEquals(1, f.source.stops)
        assertEquals(0, f.source.releases); f.worker(); f.main(); f.finished(); assertTrue(f.results.isEmpty()); assertTrue(f.files().isEmpty())
    }
    @Test fun cancellationBeforeWorkerProducesNoProgressOrResultAndCleansFile() = Fixture().use { f ->
        f.start(); f.control.cancel(); f.worker(); f.main(); f.finished(); assertTrue(f.meters.isEmpty()); assertTrue(f.results.isEmpty()); assertTrue(f.files().isEmpty())
    }
    @Test fun cancellationDuringReadUnblocksAndCleansOwnedStream() = Fixture().use { f ->
        f.start(); f.source.onRead = { f.control.cancel() }; f.worker(); f.main()
        f.finished(); assertTrue(f.files().isEmpty()); assertTrue(f.results.isEmpty()); assertTrue(f.meters.isEmpty())
    }
    @Test fun cancelAfterWorkerSuppressesQueuedMeterAndFileDelivery() = Fixture().use { f ->
        f.start(); f.worker(); assertEquals(2, f.mains.size); f.control.cancel(); assertTrue(f.files().isEmpty()); f.main()
        assertTrue(f.results.isEmpty()); assertTrue(f.meters.isEmpty()); f.finished()
    }
    @Test fun slowUiHasOnlyOnePendingMeterAndReceivesLatestActualSampleTime() = Fixture().use { f ->
        f.source.bytes = ByteArray(640_000); f.source.bytes[f.source.bytes.lastIndex] = 16
        f.start(); f.worker()
        assertEquals("200 read chunks must coalesce to one meter plus completion", 2, f.mains.size)
        f.main(); assertEquals(1, f.meters.size); assertEquals(20, f.meters.single().seconds)
        assertTrue(f.meters.single().level > 0); assertTrue(f.results.single().isSuccess); f.finished()
    }
    @Test fun fullCaptureStopsAtExactTenMinuteByteBound() = Fixture().use { f ->
        f.source.bytes = ByteArray(PcmAudio.MAX_BYTES + 3200); f.start(); f.worker(); f.main()
        assertEquals(PcmAudio.MAX_BYTES.toLong(), f.results.single().getOrThrow().length()); assertEquals(PcmAudio.MAX_BYTES, f.source.offset)
        assertEquals(600, f.meters.single().seconds); f.finished()
    }
    @Test fun shortRecordingIsDiscardedAndHasNoSuccessfulHandoff() = Fixture().use { f ->
        f.source.bytes = ByteArray(3198); f.start(); f.worker(); f.main()
        assertEquals("Recording is too short", f.results.single().exceptionOrNull()!!.message); assertTrue(f.files().isEmpty()); f.finished()
    }
    @Test fun readFailureReleasesSourceAndDiscardsPartial() = Fixture().use { f ->
        f.source.readFailure = true; f.start(); f.worker(); f.main()
        assertEquals("capture unavailable", f.results.single().exceptionOrNull()!!.message); assertTrue(f.files().isEmpty()); f.finished()
    }
    @Test fun oddOrOversizedSourceChunkFailsBeforeWritingMalformedPcm() {
        for (n in listOf(1, 3201, 3202)) Fixture().use { f ->
            f.source.invalidCount = n; f.start(); f.worker(); f.main()
            assertEquals("Invalid PCM16 capture", f.results.single().exceptionOrNull()!!.message); assertTrue(f.files().isEmpty()); f.finished()
        }
    }
    @Test fun temporaryZeroReadDoesNotProduceFakeSamplesOrPrematureCompletion() = Fixture().use { f ->
        f.source.zeroOnce = true; f.start(); f.worker(); f.main()
        assertArrayEquals(f.source.bytes, f.results.single().getOrThrow().readBytes()); assertEquals(1, f.meters.size); f.finished()
    }
    @Test fun permissionStartupAndWorkerFailureCleanFileAndReleaseAcquiredSource() {
        Fixture().use { f ->
            f.openFailure = true
            try { f.start(); fail("Expected permission failure") } catch (_: SecurityException) {}
            assertTrue(f.files().isEmpty()); assertEquals(0, f.source.starts); assertEquals(0, f.source.releases)
        }
        Fixture().use { f ->
            f.source.startFailure = true
            try { f.start(); fail("Expected startup failure") } catch (_: IOException) {}
            assertTrue(f.files().isEmpty()); f.finished()
        }
        Fixture().use { f ->
            f.workFailure = true
            try { f.start(); fail("Expected scheduling failure") } catch (_: IOException) {}
            assertTrue(f.files().isEmpty()); f.finished()
        }
    }
    @Test fun cancellingOldJobCannotDeleteAnotherRecording() = Fixture().use { f ->
        f.start(); val old = f.control; val oldSource = f.source
        val secondSource = Source().apply { bytes = ByteArray(9600) }; lateinit var second: PcmRecorder.Control
        secondSource.end = { second.finish() }
        val next = PcmRecorder(f.dir, { secondSource }, f.workers::addLast, f.mains::addLast)
        second = next.start(f.meters::add, f.results::add); assertEquals(2, f.files().size)
        old.cancel(); f.worker(); assertEquals(1, f.files().size); f.worker(); f.main()
        assertEquals(9600L, f.results.single().getOrThrow().length()); assertEquals(1, f.files().size)
        assertEquals(1, oldSource.releases); assertEquals(1, secondSource.releases)
    }
}
