package ru.billyhargrove.pimobile.features.chat

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class TranscriptionSessionTest {
    private open class Port : TranscriptionSession.Port {
        class Job(val done: (Result<String>) -> Unit) : TranscriptionSession.Control {
            var cancels = 0; var onCancel: () -> Unit = {}
            override fun cancel() { cancels++; onCancel() }
        }
        val jobs = mutableListOf<Job>(); val files = mutableListOf<File>(); val discarded = mutableListOf<File>()
        override fun start(file: File, done: (Result<String>) -> Unit): TranscriptionSession.Control {
            files.add(file); return Job(done).also(jobs::add)
        }
        override fun discard(file: File) { discarded.add(file) }
    }
    private class Fixture(readOnly: Boolean = false, val port: Port = Port()) {
        val busy = mutableListOf<Boolean>(); val inserted = mutableListOf<String>(); val notices = mutableListOf<String>()
        var clears = 0; var active = true
        val owner = TranscriptionSession(readOnly, port, busy::add, { clears++ }, inserted::add, notices::add, { active })
        fun start(name: String = "recording.pcm") = owner.start(File(name))
    }
    @Test fun onlyOneJobStartsAndRejectingAnotherFileCannotDeleteActiveRecording() {
        val f = Fixture(); assertTrue(f.start()); assertFalse(f.start()); assertFalse(f.start("other.pcm"))
        assertEquals(listOf(File("recording.pcm")), f.port.files); assertEquals(listOf(File("other.pcm")), f.port.discarded)
        assertEquals(listOf(true), f.busy); assertEquals(1, f.clears)
    }
    @Test fun completionInsertsUntrimmedTextOnceAndClearsBusyWithoutSending() {
        val f = Fixture(); f.start(); val job = f.port.jobs.single()
        job.done(Result.success("  Recognized speech  ")); job.done(Result.success("duplicate")); job.done(Result.failure(Exception("late")))
        assertEquals(listOf("  Recognized speech  "), f.inserted); assertEquals(listOf(true, false), f.busy)
        assertTrue(f.notices.isEmpty()); assertEquals(0, job.cancels)
    }
    @Test fun blankResponseEndsSpinnerAndReportsNoSpeechWithoutInserting() {
        for (text in listOf("", " \n\t")) {
            val f = Fixture(); f.start(); f.port.jobs.single().done(Result.success(text))
            assertEquals(listOf("No speech detected"), f.notices); assertEquals(listOf(true, false), f.busy); assertTrue(f.inserted.isEmpty())
        }
    }
    @Test fun failureHasMeaningfulFallbackEvenWithoutExceptionMessage() {
        for ((cause, expected) in listOf(Exception("HTTP error") to "HTTP error", Exception() to "Could not transcribe recording",
            Exception(" ") to "Could not transcribe recording")) {
            val f = Fixture(); f.start(); f.port.jobs.single().done(Result.failure(cause))
            assertEquals(listOf(expected), f.notices); assertTrue(f.inserted.isEmpty()); assertEquals(listOf(true, false), f.busy)
        }
    }
    @Test fun cancelRetiresIdentityBeforeTransportAndLateResultCannotClearNewSpinner() {
        val f = Fixture(); f.start(); val old = f.port.jobs.single()
        old.onCancel = { old.done(Result.failure(Exception("cancel callback"))) }
        f.owner.cancel(); f.owner.cancel(); f.start("new.pcm"); val next = f.port.jobs.last()
        old.done(Result.success("old speech")); old.done(Result.failure(Exception("old error")))
        assertEquals(1, old.cancels); assertEquals(listOf(true, false, true), f.busy)
        assertTrue(f.inserted.isEmpty()); assertTrue(f.notices.isEmpty())
        next.done(Result.success("new speech")); assertEquals(listOf("new speech"), f.inserted); assertEquals(false, f.busy.last())
    }
    @Test fun closeCancelsOnceSuppressesCallbacksAndNeverRestarts() {
        val f = Fixture(); f.start(); val job = f.port.jobs.single(); f.owner.close(); f.owner.close()
        job.done(Result.success("late")); job.done(Result.failure(Exception("late"))); assertFalse(f.start("new.pcm"))
        assertEquals(1, job.cancels); assertEquals(listOf(File("new.pcm")), f.port.discarded)
        assertEquals(listOf(true, false), f.busy); assertTrue(f.inserted.isEmpty()); assertTrue(f.notices.isEmpty())
    }
    @Test fun readOnlyDiscardsPcmWithoutStartingOrTouchingDraft() {
        val f = Fixture(true); assertFalse(f.start()); assertTrue(f.port.jobs.isEmpty())
        assertEquals(listOf(File("recording.pcm")), f.port.discarded); assertTrue(f.busy.isEmpty()); assertEquals(0, f.clears)
    }
    @Test fun synchronousSuccessDoesNotCancelFinishedRequestOrLeaveBusy() {
        val port = object : Port() {
            override fun start(file: File, done: (Result<String>) -> Unit): TranscriptionSession.Control {
                val control = super.start(file, done); done(Result.success("sync")); return control
            }
        }
        val f = Fixture(port = port); f.start(); f.owner.close()
        assertEquals(listOf("sync"), f.inserted); assertEquals(listOf(true, false), f.busy); assertEquals(0, port.jobs.single().cancels)
    }
    @Test fun unexpectedStartupExceptionDiscardsFileAndLeavesStateReusable() {
        val f = Fixture(port = object : Port() {
            override fun start(file: File, done: (Result<String>) -> Unit): TranscriptionSession.Control { throw Exception("setup failed") }
        })
        f.start(); assertEquals(listOf(File("recording.pcm")), f.port.discarded)
        assertEquals(listOf("setup failed"), f.notices); assertEquals(listOf(true, false), f.busy)
        assertTrue(f.start("retry.pcm")); assertEquals(2, f.clears)
    }
    @Test fun closeInsideStartCancelsControlReturnedAfterClosure() {
        lateinit var owner: TranscriptionSession
        val port = object : Port() {
            override fun start(file: File, done: (Result<String>) -> Unit): TranscriptionSession.Control {
                val control = super.start(file, done); owner.close(); return control
            }
        }
        val f = Fixture(port = port); owner = f.owner; f.start(); port.jobs.single().done(Result.success("late"))
        assertEquals(1, port.jobs.single().cancels); assertTrue(f.inserted.isEmpty()); assertEquals(listOf(true, false), f.busy)
    }
    @Test fun finishingHostRejectsNewWorkAndConsumesLateResultQuietly() {
        val f = Fixture(); f.start(); f.active = false; f.port.jobs.single().done(Result.success("late speech"))
        assertEquals(listOf(true, false), f.busy); assertTrue(f.inserted.isEmpty()); assertTrue(f.notices.isEmpty())
        assertFalse(f.start("next.pcm")); assertEquals(listOf(File("next.pcm")), f.port.discarded)
    }
    @Test fun throwingCancellationCannotResurrectOrPreventClosing() {
        val f = Fixture(); f.start(); val job = f.port.jobs.single(); job.onCancel = { throw Exception("cancel failed") }
        f.owner.close(); job.done(Result.success("late")); assertFalse(f.start("late.pcm")); assertTrue(f.inserted.isEmpty())
        assertEquals(listOf(true, false), f.busy)
    }
}
