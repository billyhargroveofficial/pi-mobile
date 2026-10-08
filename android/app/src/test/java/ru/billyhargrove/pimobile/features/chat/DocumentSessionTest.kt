package ru.billyhargrove.pimobile.features.chat

import org.junit.Assert.*
import org.junit.Test

class DocumentSessionTest {
    private class Fixture {
        val reads = mutableListOf<String>(); val documents = mutableListOf<DocumentSession.Document>(); val notices = mutableListOf<String>()
        var read: (String) -> String? = { "r${reads.size}" }
        var onReady: (DocumentSession.Document) -> Unit = {}
        val owner = DocumentSession({ path -> reads.add(path); read(path) }, { documents.add(it); onReady(it) }, notices::add)
    }
    @Test fun matchingDataCompletesOneReadAndPreservesReturnedCanonicalPath() {
        val f = Fixture(); assertTrue(f.owner.open("docs/../README.md"))
        assertTrue(f.owner.received("r1", "README.md", "text")); assertFalse(f.owner.owns("r1"))
        assertEquals(listOf(DocumentSession.Document("README.md", "text")), f.documents); assertTrue(f.notices.isEmpty())
    }
    @Test fun duplicateDataAckAndTimeoutCannotReopenOrShowOldError() {
        val f = Fixture(); f.owner.open("one.md"); f.owner.received("r1", "one.md", "body")
        assertFalse(f.owner.received("r1", "one.md", "duplicate")); assertFalse(f.owner.acknowledged("r1", false, "late error"))
        assertFalse(f.owner.uncertain("r1", "late timeout")); assertEquals(1, f.documents.size); assertTrue(f.notices.isEmpty())
    }
    @Test fun foreignIdsNeverResolvePendingRead() {
        val f = Fixture(); f.owner.open("one.md")
        assertFalse(f.owner.received("foreign", "bad.md", "bad")); assertFalse(f.owner.acknowledged("foreign", false, "bad"))
        assertFalse(f.owner.uncertain("foreign", "bad")); assertTrue(f.owner.owns("r1")); assertTrue(f.documents.isEmpty()); assertTrue(f.notices.isEmpty())
    }
    @Test fun pendingReadIsSingleFlightAndDoesNotReplaceChosenPath() {
        val f = Fixture(); f.owner.open("one.md"); assertFalse(f.owner.open("two.md"))
        assertEquals(listOf("one.md"), f.reads); assertEquals(listOf("A file is already loading"), f.notices)
        f.owner.received("r1", "", "body"); assertEquals("one.md", f.documents.single().path)
    }
    @Test fun cancellationAllowsFreshReadButRetiredDataCannotAffectIt() {
        val f = Fixture(); f.owner.open("old.md"); f.owner.cancel(); f.owner.cancel(); assertTrue(f.owner.open("new.md"))
        assertFalse(f.owner.received("r1", "old.md", "late")); assertFalse(f.owner.uncertain("r1", "late")); assertTrue(f.owner.owns("r2"))
        assertTrue(f.owner.received("r2", "new.md", "fresh")); assertEquals("new.md", f.documents.single().path); assertTrue(f.notices.isEmpty())
    }
    @Test fun closeRetiresReadAndBlocksFutureReads() {
        val f = Fixture(); f.owner.open("old.md"); f.owner.close(); f.owner.close()
        assertFalse(f.owner.received("r1", "old.md", "late")); assertFalse(f.owner.open("new.md")); assertEquals(1, f.reads.size)
    }
    @Test fun rejectionAndTimeoutPreserveExistingDocumentAndAllowExplicitRetry() {
        val f = Fixture(); f.owner.open("old.md"); f.owner.received("r1", "old.md", "old"); f.owner.open("new.md")
        assertTrue(f.owner.acknowledged("r2", false, "file unavailable")); assertEquals(listOf("file unavailable"), f.notices)
        f.owner.open("new.md"); assertTrue(f.owner.uncertain("r3", "disconnected"))
        assertEquals(1, f.documents.size); assertEquals("File could not be loaded: disconnected", f.notices.last()); assertEquals(3, f.reads.size)
    }
    @Test fun successfulAckWithoutDocumentCannotInventAnEmptyPreview() {
        val f = Fixture(); f.owner.open("one.md"); assertTrue(f.owner.acknowledged("r1", true, ""))
        assertTrue(f.documents.isEmpty()); assertEquals(listOf("Pi returned no document"), f.notices); assertFalse(f.owner.received("r1", "one.md", "late"))
    }
    @Test fun missingLiveTicketOnReturnReportsUnknownWithoutReplay() {
        val f = Fixture(); f.owner.open("one.md"); f.owner.reconcile(emptySet()); f.owner.reconcile(emptySet())
        assertEquals(1, f.reads.size); assertEquals(listOf("File could not be loaded: The result arrived while this screen was inactive"), f.notices)
        assertFalse(f.owner.received("r1", "one.md", "late")); assertTrue(f.owner.open("two.md"))
    }
    @Test fun stillLiveTicketIsRetainedAcrossReconciliation() {
        val f = Fixture(); f.owner.open("one.md"); f.owner.reconcile(setOf("r1")); f.owner.reconcile(setOf("r1"))
        assertTrue(f.owner.owns("r1")); assertEquals(1, f.reads.size); assertTrue(f.notices.isEmpty())
    }
    @Test fun completedTicketIsRetiredBeforeOpeningNextDocumentFromReadyCallback() {
        val f = Fixture(); f.onReady = { assertTrue(f.owner.open("linked.md")) }
        f.owner.open("one.md"); f.owner.received("r1", "one.md", "body")
        assertTrue(f.owner.owns("r2")); assertFalse(f.owner.acknowledged("r1", true, "")); assertEquals(listOf("one.md", "linked.md"), f.reads)
    }
    @Test fun disconnectedAndSetupFailureNeverLeaveBusyTicket() {
        val f = Fixture(); f.read = { null }; assertFalse(f.owner.open("one.md")); assertEquals("Pi must be connected to preview files", f.notices.last())
        f.read = { throw IllegalArgumentException("bad path") }; assertFalse(f.owner.open("one.md")); assertEquals("File could not be loaded: bad path", f.notices.last())
        f.read = { "r3" }; assertTrue(f.owner.open("three.md"))
    }
    @Test fun closeDuringReadSetupCannotArmReturnedRequestOrShowSetupError() {
        val f = Fixture(); f.read = { f.owner.close(); "returned" }; assertFalse(f.owner.open("one.md"))
        assertFalse(f.owner.owns("returned")); assertTrue(f.notices.isEmpty())
        val failed = Fixture(); failed.read = { failed.owner.close(); throw IllegalStateException("late setup") }
        assertFalse(failed.owner.open("one.md")); assertTrue(failed.notices.isEmpty())
    }
    @Test fun relativeLinksKeepParentAndAbsoluteOrSchemedLinksKeepTheirTarget() {
        val doc = DocumentSession.Document("docs/readme.md", "body")
        assertEquals("docs/next.md", doc.resolve("next.md")); assertEquals("docs/../notes.markdown#part", doc.resolve("../notes.markdown#part"))
        for (link in listOf("/absolute.md", "https://example.invalid/read", "file:///notes.md", "mailto:invalid")) assertEquals(link, doc.resolve(link))
        assertEquals("next.md", DocumentSession.Document("readme.md", "body").resolve("next.md"))
    }
}
