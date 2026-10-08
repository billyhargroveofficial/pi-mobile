package ru.billyhargrove.pimobile.features.chat

import org.junit.Assert.*
import org.junit.Test

class TranscriptImageSessionTest {
    private class Port {
        val replies = mutableListOf<(String?, String) -> Unit>()
        fun load(done: (String?, String) -> Unit) { replies.add(done) }
    }
    @Test fun repeatedStartReservesOnePendingLoad() {
        val port = Port(); val binding = TranscriptImageSession(false, port::load)
        repeat(40) { binding.start() }; assertEquals(1, port.replies.size)
        port.replies.single()("pixels", ""); repeat(40) { binding.start() }
        assertEquals(1, port.replies.size); assertEquals("pixels", binding.state.bitmap)
    }
    @Test fun localAttachmentNeverCallsRemotePort() {
        val port = Port(); val binding = TranscriptImageSession(true, port::load)
        repeat(40) { binding.start() }; binding.close(); binding.start()
        assertTrue(port.replies.isEmpty()); assertNull(binding.state.bitmap); assertEquals("", binding.state.error)
    }
    @Test fun synchronousCacheResultDoesNotPermitReentrantLoad() {
        var calls = 0; lateinit var binding: TranscriptImageSession<String>
        binding = TranscriptImageSession(false) { done -> calls++; binding.start(); done("cached", "") }
        binding.start(); assertEquals(1, calls); assertEquals("cached", binding.state.bitmap)
    }
    @Test fun firstTerminalSuccessCannotBeOverwrittenByLateFailureOrPixels() {
        val port = Port(); val binding = TranscriptImageSession(false, port::load); binding.start()
        val reply = port.replies.single(); reply("first", "ignored error"); reply(null, "late"); reply("second", "")
        assertEquals("first", binding.state.bitmap); assertEquals("", binding.state.error)
    }
    @Test fun terminalFailureDoesNotAutomaticallyRetryOrAcceptLateSuccess() {
        val port = Port(); val binding = TranscriptImageSession(false, port::load); binding.start()
        port.replies.single()(null, "blocked"); binding.start(); port.replies.single()("late", "")
        assertNull(binding.state.bitmap); assertEquals("blocked", binding.state.error); assertEquals(1, port.replies.size)
    }
    @Test fun closeBeforeStartPreventsLoading() {
        val port = Port(); val binding = TranscriptImageSession(false, port::load); binding.close(); binding.start()
        assertTrue(port.replies.isEmpty())
    }
    @Test fun closePendingBindingRejectsLateResultWithoutAffectingReplacement() {
        val oldPort = Port(); val nextPort = Port()
        val old = TranscriptImageSession(false, oldPort::load); old.start(); old.close()
        val next = TranscriptImageSession(false, nextPort::load); next.start(); nextPort.replies.single()("new", "")
        oldPort.replies.single()("old", ""); oldPort.replies.single()(null, "late failure")
        assertNull(old.state.bitmap); assertEquals("", old.state.error); assertEquals("new", next.state.bitmap)
    }
    @Test fun closeReleasesOwnedReferenceAndNeverReopens() {
        val pixels = Any(); val binding = TranscriptImageSession<Any>(false) { it(pixels, "") }
        binding.start(); assertSame(pixels, binding.state.bitmap); binding.close(); binding.close(); binding.start()
        assertNull(binding.state.bitmap); assertEquals("", binding.state.error)
    }
    @Test fun setupFailureIsVisibleAndRemainsSingleFlight() {
        var calls = 0
        val binding = TranscriptImageSession<String>(false) { calls++; throw IllegalStateException("setup failure") }
        binding.start(); binding.start(); assertEquals(1, calls); assertEquals("setup failure", binding.state.error)
    }
    @Test fun setupFailureAfterSynchronousSuccessCannotOverwriteIt() {
        val binding = TranscriptImageSession<String>(false) { it("cached", ""); throw IllegalStateException("late setup") }
        binding.start(); assertEquals("cached", binding.state.bitmap); assertEquals("", binding.state.error)
    }
    @Test fun closingDuringPortSetupRejectsItsSynchronousReply() {
        lateinit var binding: TranscriptImageSession<String>
        binding = TranscriptImageSession(false) { binding.close(); it("late", "") }
        binding.start(); assertNull(binding.state.bitmap)
    }
}
