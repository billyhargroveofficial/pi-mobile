package ru.billyhargrove.pimobile.features.voice

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import ru.billyhargrove.pimobile.core.PcmAudio

class RecordingSessionTest {
    private open class Wire : RecordingSession.Port {
        lateinit var meter: (PcmAudio.Meter) -> Unit; lateinit var done: (Result<File>) -> Unit; lateinit var timer: () -> Unit
        var starts = 0; var finishes = 0; var cancels = 0; var timerCancels = 0; val discarded = mutableListOf<File>()
        val control = object : RecordingSession.Control {
            override fun finish() { finishes++ }
            override fun cancel() { cancels++ }
        }
        override fun start(meter: (PcmAudio.Meter) -> Unit, done: (Result<File>) -> Unit): RecordingSession.Control {
            starts++; this.meter = meter; this.done = done; return control
        }
        override fun deadline(delayMs: Long, action: () -> Unit): () -> Unit {
            assertEquals(600_000L, delayMs); timer = action; return { timerCancels++ }
        }
        override fun discard(file: File) { discarded.add(file) }
    }
    private class Fixture(val wire: Wire = Wire()) {
        val ready = mutableListOf<File>(); val errors = mutableListOf<String>(); val shown = mutableListOf<Boolean>()
        val owner = RecordingSession(wire, ready::add, errors::add, shown::add)
        init { owner.start() }
    }
    @Test fun startIsSingleFlightAndRecordingOwnsOneDeadline() {
        val f = Fixture(); f.owner.start(); assertEquals(1, f.wire.starts)
        assertEquals(RecordingSession.Phase.LISTENING, f.owner.phase); assertEquals(listOf(true), f.shown)
    }
    @Test fun progressIsBoundedAndKeeps48MostRecentFiniteLevels() {
        val f = Fixture()
        repeat(60) { f.wire.meter(PcmAudio.Meter(it, it / 100f)) }
        assertEquals(48, f.owner.levels.size); assertEquals(.12f, f.owner.levels.first()); assertEquals(.59f, f.owner.levels.last())
        f.wire.meter(PcmAudio.Meter(-1, Float.NaN)); assertEquals(59, f.owner.seconds); assertEquals(0f, f.owner.levels.last())
        f.wire.meter(PcmAudio.Meter(999, 5f)); assertEquals(600, f.owner.seconds); assertEquals(1f, f.owner.levels.last())
    }
    @Test fun finishOnlyStopsOnceAndLateMeterDoesNotChangeFinishingPanel() {
        val f = Fixture(); f.owner.finish(); f.owner.finish(); f.wire.timer(); f.wire.meter(PcmAudio.Meter(99, 1f))
        assertEquals(1, f.wire.finishes); assertEquals(1, f.wire.timerCancels)
        assertEquals(RecordingSession.Phase.FINISHING, f.owner.phase); assertEquals(0, f.owner.seconds); assertTrue(f.ready.isEmpty())
    }
    @Test fun deadlineFinishesAndOldTimerCannotFinishCancelledRecording() {
        val f = Fixture(); f.wire.timer(); assertEquals(1, f.wire.finishes)
        val c = Fixture(); c.owner.cancel(); c.wire.timer(); assertEquals(0, c.wire.finishes); assertEquals(1, c.wire.cancels)
    }
    @Test fun completionTransfersExactlyOnceAndDoesNotDeleteAcceptedFileOnDuplicate() {
        val f = Fixture(); val file = File("accepted.pcm"); f.owner.finish(); f.wire.done(Result.success(file))
        f.wire.done(Result.success(file)); f.wire.done(Result.failure(Exception("late"))); f.owner.cancel(); f.wire.timer()
        assertEquals(listOf(file), f.ready); assertTrue(f.errors.isEmpty()); assertTrue(f.wire.discarded.isEmpty())
        assertEquals(listOf(true, false, false), f.shown); assertEquals(0, f.wire.cancels)
    }
    @Test fun unexpectedSecondArtifactIsDiscardedWithoutReplacingAcceptedFile() {
        val f = Fixture(); val file = File("accepted.pcm"); val other = File("other.pcm")
        f.wire.done(Result.success(file)); f.wire.done(Result.success(other)); assertEquals(listOf(file), f.ready); assertEquals(listOf(other), f.wire.discarded)
    }
    @Test fun cancelDiscardsLateArtifactAndNeverReturnsDraftOrError() {
        val f = Fixture(); f.owner.cancel(); f.owner.cancel(); f.wire.meter(PcmAudio.Meter(12, 1f))
        val file = File("late.pcm"); f.wire.done(Result.success(file)); f.wire.done(Result.failure(Exception("late")))
        assertEquals(1, f.wire.cancels); assertEquals(1, f.wire.timerCancels); assertEquals(listOf(file), f.wire.discarded)
        assertEquals(0, f.owner.seconds); assertTrue(f.ready.isEmpty()); assertTrue(f.errors.isEmpty())
    }
    @Test fun cancelDuringFinishingRetiresPendingResultAndTimer() {
        val f = Fixture(); f.owner.finish(); f.owner.cancel(); f.wire.done(Result.success(File("late.pcm")))
        assertEquals(1, f.wire.finishes); assertEquals(1, f.wire.cancels); assertEquals(1, f.wire.timerCancels); assertTrue(f.ready.isEmpty())
    }
    @Test fun failureEndsPanelWithoutTransferringOrRetrying() {
        val f = Fixture(); f.wire.done(Result.failure(Exception("short"))); f.owner.start(); f.owner.finish()
        assertEquals(listOf("short"), f.errors); assertEquals(listOf(true, false), f.shown); assertEquals(1, f.wire.starts); assertTrue(f.ready.isEmpty())
    }
    @Test fun startupFailureNeverShowsListeningAndCanBeCancelledQuietly() {
        val f = Fixture(object : Wire() { override fun start(meter: (PcmAudio.Meter) -> Unit, done: (Result<File>) -> Unit): RecordingSession.Control { throw Exception("permission") } })
        assertEquals(listOf("permission"), f.errors); assertEquals(listOf(false), f.shown); assertEquals(RecordingSession.Phase.ENDED, f.owner.phase)
        f.owner.cancel(); assertTrue(f.ready.isEmpty())
    }
    @Test fun synchronousSuccessDoesNotArmTimerOrCancelHandedFile() {
        val file = File("sync.pcm")
        val f = Fixture(object : Wire() {
            override fun start(meter: (PcmAudio.Meter) -> Unit, done: (Result<File>) -> Unit): RecordingSession.Control { done(Result.success(file)); return control }
        })
        assertEquals(listOf(file), f.ready); assertEquals(0, f.wire.timerCancels); assertEquals(0, f.wire.cancels); assertEquals(RecordingSession.Phase.ENDED, f.owner.phase)
    }
    @Test fun schedulingFailureRetiresAndCancelsActiveSource() {
        val f = Fixture(object : Wire() { override fun deadline(delayMs: Long, action: () -> Unit): () -> Unit { throw Exception("timer unavailable") } })
        assertEquals(1, f.wire.cancels); assertEquals(listOf("timer unavailable"), f.errors); assertEquals(listOf(true, false), f.shown)
        f.wire.done(Result.success(File("late.pcm"))); assertTrue(f.ready.isEmpty())
    }
    @Test fun stopFailureRetiresAndCancelsSourceBeforeLateResult() {
        val f = Fixture(object : Wire() {
            override fun start(meter: (PcmAudio.Meter) -> Unit, done: (Result<File>) -> Unit): RecordingSession.Control {
                super.start(meter, done)
                return object : RecordingSession.Control {
                    override fun finish() { throw Exception("stop failed") }
                    override fun cancel() { cancels++ }
                }
            }
        })
        f.owner.finish(); f.wire.done(Result.success(File("late.pcm"))); assertEquals(1, f.wire.cancels)
        assertEquals(listOf("stop failed"), f.errors); assertTrue(f.ready.isEmpty())
    }
}
