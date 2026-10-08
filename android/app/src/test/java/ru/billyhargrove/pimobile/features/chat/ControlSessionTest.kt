package ru.billyhargrove.pimobile.features.chat

import org.junit.Assert.*
import org.junit.Test

class ControlSessionTest {
    private class Fixture(readOnly: Boolean = false) {
        val sent = mutableListOf<ControlSession.Command>()
        val cleared = mutableListOf<String>()
        val notices = mutableListOf<String>()
        var stops = 0
        var caps = setOf("mcp", "name")
        var send: (ControlSession.Command) -> String? = { sent.add(it); "r${sent.size}" }
        var stop: () -> String? = { "a${++stops}" }
        val owner = ControlSession(readOnly, { it in caps }, { send(it) }, { stop() }, cleared::add, notices::add)
    }
    @Test fun parserPreservesRecognizedSyntaxAndTypedTrimmedName() {
        val f = Fixture(); for (text in listOf("hello", "/mcps", "/mcp extra", "/name\tvalue", "/names value")) assertFalse(f.owner.handle(text, false))
        assertTrue(f.owner.handle("  /name   New name  \n", false)); assertEquals(listOf(ControlSession.Command.Rename("New name")), f.sent)
        assertTrue(f.owner.acknowledged("r1", true, "")); assertEquals(listOf("/name   New name"), f.cleared); assertEquals("Session renamed", f.notices.single())
    }
    @Test fun blockedCommandsNeverFallThroughOrWriteAndPreserveTheDraft() {
        val f = Fixture(); f.caps = emptySet(); assertTrue(f.owner.handle("/mcp", false)); assertTrue(f.sent.isEmpty())
        f.caps = setOf("mcp", "name"); assertTrue(f.owner.handle("/name", false)); assertTrue(f.owner.handle("/mcp", true))
        assertTrue(f.sent.isEmpty()); assertTrue(f.cleared.isEmpty()); assertEquals(3, f.notices.size)
    }
    @Test fun readOnlyBlocksControlsAndStopIncludingDirectCalls() {
        val f = Fixture(true); assertTrue(f.owner.handle("/name Name", false)); assertTrue(f.owner.handle("/mcp", false)); f.owner.abort()
        assertTrue(f.sent.isEmpty()); assertEquals(0, f.stops); assertTrue(f.notices.isEmpty()); assertTrue(f.cleared.isEmpty())
    }
    @Test fun mcpIsAcceptedOnceOnlyForItsOwnCommandAndRemainsPendingThroughAck() {
        val f = Fixture(); f.owner.handle("/mcp", false)
        assertFalse(f.owner.receivedMcp("foreign")); assertTrue(f.owner.receivedMcp("r1")); assertFalse(f.owner.receivedMcp("r1"))
        assertTrue(f.owner.handle("/name Later", false)); assertEquals(1, f.sent.size)
        assertTrue(f.owner.acknowledged("r1", true, "")); assertEquals(listOf("/mcp"), f.cleared)
        assertFalse(f.owner.acknowledged("r1", false, "late")); assertFalse(f.owner.uncertain("r1", "late")); assertFalse(f.owner.receivedMcp("r1"))
        f.owner.handle("/name Later", false); assertFalse(f.owner.receivedMcp("r2")); f.owner.acknowledged("r2", true, "")
        assertEquals(listOf("/mcp", "/name Later"), f.cleared)
    }
    @Test fun successfulMcpAckWithoutBodyKeepsDraftAndAllowsExplicitRetry() {
        val f = Fixture(); f.owner.handle("/mcp", false); f.owner.acknowledged("r1", true, "")
        assertEquals(listOf("Pi returned no MCP status"), f.notices); assertTrue(f.cleared.isEmpty())
        assertFalse(f.owner.receivedMcp("r1")); f.owner.handle("/mcp", false); assertEquals(2, f.sent.size)
    }
    @Test fun rejectionAndTimeoutRetireOnlyOwnedCommandWithoutClearingDraft() {
        for (timeout in listOf(false, true)) {
            val f = Fixture(); f.owner.handle("/name Name", false); assertFalse(f.owner.acknowledged("unknown", true, ""))
            if (timeout) assertTrue(f.owner.uncertain("r1", "timeout")) else assertTrue(f.owner.acknowledged("r1", false, "rejected"))
            assertTrue(f.cleared.isEmpty()); assertFalse(f.owner.acknowledged("r1", true, ""))
            f.owner.handle("/name Later", false); assertEquals(2, f.sent.size)
        }
    }
    @Test fun repeatedStopClicksWriteOnceUntilOwnedAckAndNeverClearDraft() {
        val f = Fixture(); repeat(20) { f.owner.abort() }; assertEquals(1, f.stops)
        assertFalse(f.owner.acknowledged("other", true, "")); f.owner.abort(); assertEquals(1, f.stops)
        assertTrue(f.owner.acknowledged("a1", true, "")); assertFalse(f.owner.uncertain("a1", "late")); f.owner.abort(); assertEquals(2, f.stops)
        assertTrue(f.owner.acknowledged("a2", false, "denied")); assertEquals("Pi rejected the command: denied", f.notices.last()); assertTrue(f.cleared.isEmpty())
    }
    @Test fun stopAndControlAreIndependentAndWrongKindCannotProduceMcp() {
        val f = Fixture(); f.owner.handle("/mcp", false); f.owner.abort(); assertEquals(1, f.sent.size); assertEquals(1, f.stops)
        assertFalse(f.owner.receivedMcp("a1")); f.owner.uncertain("a1", "timeout")
        assertTrue(f.owner.receivedMcp("r1")); f.owner.acknowledged("r1", true, ""); assertEquals(listOf("/mcp"), f.cleared)
    }
    @Test fun detachedLedgerPreservesLiveAndReportsMissingWithoutWritingAgain() {
        val f = Fixture(); f.owner.handle("/mcp", false); f.owner.abort(); f.owner.reconcile(setOf("r1", "a1"))
        assertEquals(listOf("Stop requested"), f.notices); f.owner.reconcile(emptySet()); f.owner.reconcile(emptySet())
        assertEquals(3, f.notices.size); assertEquals(1, f.sent.size); assertEquals(1, f.stops); assertTrue(f.cleared.isEmpty())
        assertFalse(f.owner.receivedMcp("r1")); assertFalse(f.owner.acknowledged("a1", true, ""))
    }
    @Test fun nullEmptyAndThrownSetupReleaseOnlyTheNewTicket() {
        for (failure in 0..2) {
            val f = Fixture(); var sends = 0; var stops = 0
            f.send = { sends++; when (failure) { 0 -> null; 1 -> ""; else -> error("synthetic") } }
            f.stop = { stops++; when (failure) { 0 -> null; 1 -> ""; else -> error("synthetic") } }
            repeat(2) { f.owner.handle("/mcp", false); f.owner.abort() }
            assertEquals(2, sends); assertEquals(2, stops); assertEquals(4, f.notices.size); assertTrue(f.cleared.isEmpty())
        }
    }
    @Test fun closedOwnerRejectsLateResultsAndAllFurtherWrites() {
        val f = Fixture(); f.owner.handle("/mcp", false); f.owner.abort(); f.notices.clear(); f.owner.close(); f.owner.close()
        assertFalse(f.owner.receivedMcp("r1")); assertFalse(f.owner.acknowledged("r1", true, "")); assertFalse(f.owner.acknowledged("a1", false, "late")); assertFalse(f.owner.uncertain("a1", "late"))
        f.owner.handle("/name Later", false); f.owner.abort(); f.owner.reconcile(emptySet())
        assertEquals(1, f.sent.size); assertEquals(1, f.stops); assertTrue(f.notices.isEmpty()); assertTrue(f.cleared.isEmpty())
    }
    @Test fun reentrantCloseDuringTransportSetupCannotResurrectTicketOrEmitSuccess() {
        val f = Fixture(); f.send = { f.owner.close(); "late" }; assertTrue(f.owner.handle("/mcp", false))
        assertFalse(f.owner.receivedMcp("late")); assertFalse(f.owner.acknowledged("late", true, "")); assertTrue(f.notices.isEmpty())
        val g = Fixture(); g.stop = { g.owner.close(); "late" }; g.owner.abort()
        assertFalse(g.owner.acknowledged("late", false, "late")); assertTrue(g.notices.isEmpty())
    }
    @Test fun reservationPreventsReentrantDuplicateTransportWrites() {
        val f = Fixture(); var calls = 0
        f.send = { calls++; f.owner.handle("/mcp", false); "r1" }; f.owner.handle("/mcp", false); assertEquals(1, calls)
        f.stop = { calls++; f.owner.abort(); "a1" }; f.owner.abort(); assertEquals(2, calls)
    }
    @Test fun terminalCallbackRetiresTicketBeforeDraftCallbackStartsAnotherCommand() {
        lateinit var owner: ControlSession; var sends = 0
        owner = ControlSession(false, { true }, { "r${++sends}" }, { null }, { owner.handle("/mcp", false) }, {})
        owner.handle("/name Name", false); owner.acknowledged("r1", true, "")
        assertEquals(2, sends); assertFalse(owner.receivedMcp("r1")); assertTrue(owner.receivedMcp("r2"))
    }
}
