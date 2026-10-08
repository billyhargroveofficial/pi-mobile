package ru.billyhargrove.pimobile.features.chat

import org.junit.Assert.*
import org.junit.Test

class ComposerSessionTest {
    private data class Value(val text: String, val start: Int, val end: Int = start, val composition: Pair<Int, Int>? = null)
    private val editor = object : ComposerSession.Editor<Value> {
        override fun text(value: Value) = value.text
        override fun selectionStart(value: Value) = value.start
        override fun create(text: String, caret: Int) = Value(text, caret)
    }
    private fun owner(readOnly: Boolean = false) = ComposerSession<Value, Any>(readOnly, editor)

    @Test fun externalEditorValueRetainsSelectionAndCompositionWithoutRebuilding() {
        val s = owner(); val value = Value("draft", 4, 1, 1 to 4)
        s.value = value
        assertSame(value, s.value); assertTrue(s.occupied); assertFalse(s.voice)
    }
    @Test fun ordinarySendConsumesTheSubmittedTextAndBytesOnlyOnce() {
        val s = owner(); s.value = Value("draft", 2); s.prepared(listOf(Any()))
        val submitted = s.submission()!!; s.sent(submitted)
        assertEquals(Value("", 0), s.value); assertTrue(s.attachments.isEmpty())
        s.value = Value("next", 4); s.sent(submitted); assertEquals("next", s.value.text)
    }
    @Test fun changedAndChangedBackTextCannotBeConsumedByAnOlderSubmission() {
        val s = owner(); s.value = Value("draft", 5); val old = s.submission()!!
        s.value = Value("next", 4); s.value = Value("draft", 5); s.sent(old)
        assertEquals("draft", s.value.text)
    }
    @Test fun selectionOrCompositionChangesRetireAnOlderSubmission() {
        for (newValue in listOf(Value("draft", 1, 4), Value("draft", 5, composition = 0 to 5))) {
            val s = owner(); s.value = Value("draft", 5); val old = s.submission()!!
            s.value = newValue; s.sent(old); assertSame(newValue, s.value)
        }
    }
    @Test fun changedAndChangedBackAttachmentsRetireAnOlderSubmission() {
        val s = owner(); val file = Any(); s.prepared(listOf(file)); val old = s.submission()!!
        s.removeAttachment(0); s.prepared(listOf(file)); s.sent(old)
        assertSame(file, s.attachments.single())
    }
    @Test fun ticketFromAnotherOwnerCannotClearThisDraft() {
        val first = owner(); val second = owner()
        first.value = Value("draft", 5); second.value = Value("draft", 5)
        second.sent(first.submission()!!); assertEquals("draft", second.value.text)
    }
    @Test fun equalEditorAndNoOpAttachmentEventsKeepTheSubmittedRevisionAndList() {
        val s = owner(); val file = Any(); s.value = Value("draft", 5); s.prepared(listOf(file))
        val list = s.attachments; val submitted = s.submission()!!
        repeat(100) { s.value = Value("draft", 5); s.prepared(emptyList()); s.removeAttachment(-1); s.removeAttachment(1) }
        assertSame(list, s.attachments); s.sent(submitted); assertFalse(s.occupied)
    }
    @Test fun preparationIsSingleFlightBlocksRemovalSubmissionAndRestore() {
        val s = owner(); val file = Any(); s.prepared(listOf(file)); assertTrue(s.beginPreparing())
        assertFalse(s.beginPreparing()); assertNull(s.submission()); s.removeAttachment(0)
        assertSame(file, s.attachments.single()); assertFalse(s.restore("old"))
        s.prepared(emptyList()); assertFalse(s.preparing); assertNotNull(s.submission())
    }
    @Test fun preparedAndRestoredListsAreCopiedReadOnlyButRetainExactValues() {
        val file = Any(); val batch = mutableListOf(file); val s = owner(); s.prepared(batch); batch.clear()
        assertSame(file, s.attachments.single())
        try { (s.attachments as MutableList<Any>).clear(); fail("Published list was mutable") } catch (_: UnsupportedOperationException) {}
        val restored = owner(); assertTrue(restored.restore("retry", s.attachments)); s.removeAttachment(0)
        assertSame(file, restored.attachments.single()); assertEquals(Value("retry", 5), restored.value)
    }
    @Test fun whitespaceIsOccupiedForRestoreButStillOffersVoice() {
        val s = owner(); s.value = Value("  ", 2)
        assertTrue(s.occupied); assertTrue(s.voice); assertFalse(s.restore("rejected"))
        s.value = Value("", 0); s.transcriptionChanged(true)
        assertTrue(s.restore("rejected")); assertTrue(s.transcribing)
    }
    @Test fun attachmentsAloneAreOccupiedAndSuppressVoice() {
        val s = owner(); s.prepared(listOf(Any()))
        assertTrue(s.occupied); assertFalse(s.voice); assertFalse(s.restore("old"))
    }
    @Test fun dictationUsesSelectionStartAndPreservesExistingInsertionSpacing() {
        val s = owner(); s.value = Value("abcd", 2, 4, 0 to 2); s.insertDictation("words")
        assertEquals(Value("ab wordscd", 8), s.value)
        s.value = Value("tail", 0); s.insertDictation("head"); assertEquals(Value("headtail", 4), s.value)
    }
    @Test fun dictationClampsCaretAndBlankRecognitionDoesNotRewriteEditor() {
        val s = owner(); val initial = Value("abc", -4, 2, 0 to 2); s.value = initial
        s.insertDictation(" \n"); assertSame(initial, s.value)
        s.insertDictation("x"); assertEquals(Value("xabc", 1), s.value)
        s.value = Value("abc", 40); s.insertDictation("x"); assertEquals(Value("abc x", 5), s.value)
    }
    @Test fun skillAndMatchingControlClearOnlyTextAndNeverAttachments() {
        val s = owner(); val file = Any(); s.prepared(listOf(file)); s.selectSkill("review")
        assertEquals(Value("\$review ", 8), s.value)
        s.value = Value(" /name New ", 4); s.clearMatchingText("/name Old"); assertEquals(" /name New ", s.value.text)
        s.clearMatchingText("/name New"); assertEquals(Value("", 0), s.value); assertSame(file, s.attachments.single())
    }
    @Test fun readOnlyGuardsPolicyButAcceptsAnExternalRestoredEditorValue() {
        val s = owner(true); val value = Value("kept", 1, 3); s.value = value
        assertFalse(s.editable); assertFalse(s.beginPreparing()); assertNull(s.submission()); assertFalse(s.restore("old"))
        s.prepared(listOf(Any())); s.removeAttachment(0); s.insertDictation("late"); s.selectSkill("x"); s.clearMatchingText("kept")
        assertSame(value, s.value); assertTrue(s.attachments.isEmpty())
    }
    @Test fun closeRetiresAllDraftCallbacksAndBusyFlagsWithoutDestroyingValues() {
        val s = owner(); val value = Value("kept", 1, 3, 0 to 3); val file = Any()
        s.value = value; s.prepared(listOf(file)); val submitted = s.submission()!!
        s.beginPreparing(); s.transcriptionChanged(true); s.close(); s.close()
        s.value = Value("late", 4); s.prepared(listOf(Any())); s.transcriptionChanged(true)
        s.insertDictation("late"); s.removeAttachment(0); s.selectSkill("late"); s.clearMatchingText("kept"); s.sent(submitted)
        assertSame(value, s.value); assertSame(file, s.attachments.single()); assertFalse(s.preparing); assertFalse(s.transcribing)
        assertFalse(s.editable); assertFalse(s.beginPreparing()); assertNull(s.submission()); assertFalse(s.restore("late"))
    }
}
