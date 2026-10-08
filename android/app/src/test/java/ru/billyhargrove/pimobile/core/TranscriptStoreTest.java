package ru.billyhargrove.pimobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TranscriptStoreTest {

    private static ChatMessage msg(String id, ChatMessage.Role role, String text) {
        return ChatMessage.remote(id, role, text, null, null);
    }

    private static ChatMessage user(String id, String text) {
        return msg(id, ChatMessage.Role.USER, text);
    }

    private static ChatMessage assistant(String id, String text) {
        return msg(id, ChatMessage.Role.ASSISTANT, text);
    }

    private static List<String> texts(List<ChatMessage> messages) {
        List<String> out = new ArrayList<>();
        for (ChatMessage message : messages) {
            out.add(message.text());
        }
        return out;
    }

    @Test
    public void snapshotDefinesTheInitialOrder() {
        TranscriptStore store = new TranscriptStore();
        TranscriptStore.ChangeSet changeSet = store.replaceAll(Arrays.asList(
                user("m1", "привет"), assistant("m2", "привет!"), user("m3", "ещё")));
        assertEquals(3, store.size());
        assertEquals(3, changeSet.transcript().size());
        assertEquals(Arrays.asList("привет", "привет!", "ещё"), texts(store.transcript()));
        assertTrue(store.hasUniqueIds());
    }

    @Test
    public void changedMessageIsUpdatedInPlaceAndKeepsItsPosition() {
        TranscriptStore store = new TranscriptStore();
        store.replaceAll(Arrays.asList(user("m1", "вопрос"), assistant("m2", "печ")));
        TranscriptStore.ChangeSet changeSet = store.apply(
                Collections.singletonList(assistant("m2", "печатаю ответ")), null);
        assertEquals(1, changeSet.updated());
        assertEquals(0, changeSet.added());
        assertEquals(0, changeSet.removed());
        assertEquals(Arrays.asList("вопрос", "печатаю ответ"), texts(store.transcript()));
        assertTrue(changeSet.tailTouched());
    }

    @Test
    public void newMessagesAreAppendedAndReportedAsTail() {
        TranscriptStore store = new TranscriptStore();
        store.replaceAll(Collections.singletonList(user("m1", "вопрос")));
        TranscriptStore.ChangeSet changeSet = store.apply(
                Arrays.asList(assistant("m2", "часть 1")), null);
        assertEquals(1, changeSet.added());
        assertTrue(changeSet.appendedOnly());
        assertTrue(changeSet.tailTouched());
        assertEquals(2, store.size());
        assertEquals("часть 1", store.transcript().get(1).text());
        assertEquals("часть 1", store.byId("m2").text());
    }

    @Test
    public void removedIdsAreDroppedWithoutReorderingTheRest() {
        TranscriptStore store = new TranscriptStore();
        store.replaceAll(Arrays.asList(
                user("m1", "a"), assistant("m2", "b"), user("m3", "c")));
        TranscriptStore.ChangeSet changeSet = store.apply(null, Collections.singletonList("m2"));
        assertEquals(1, changeSet.removed());
        assertEquals(Arrays.asList("a", "c"), texts(store.transcript()));
        assertNull(store.byId("m2"));
        assertFalse(changeSet.tailTouched());
        assertFalse(changeSet.appendedOnly());
    }

    @Test
    public void midTranscriptChangeDoesNotLookLikeTailChange() {
        TranscriptStore store = new TranscriptStore();
        store.replaceAll(Arrays.asList(user("m1", "a"), assistant("m2", "b"), user("m3", "c")));
        TranscriptStore.ChangeSet changeSet = store.apply(
                Collections.singletonList(assistant("m2", "b изменено")), null);
        assertEquals(1, changeSet.updated());
        assertFalse(changeSet.tailTouched());
        assertEquals(Arrays.asList("a", "b изменено", "c"), texts(store.transcript()));
    }

    @Test
    public void resendingTheSameMessageIsNotCountedAsAChange() {
        TranscriptStore store = new TranscriptStore();
        store.replaceAll(Collections.singletonList(user("m1", "a")));
        TranscriptStore.ChangeSet changeSet = store.apply(
                Collections.singletonList(user("m1", "a")), null);
        assertEquals(0, changeSet.updated());
        assertTrue(changeSet.isEmpty());
        assertFalse(changeSet.tailTouched());
    }

    @Test
    public void aWholeBurstOfUpdatesProducesOneOrderedTranscript() {
        TranscriptStore store = new TranscriptStore();
        store.replaceAll(Arrays.asList(user("m1", "старт")));
        store.apply(Collections.singletonList(assistant("m2", "")), null);
        store.apply(Collections.singletonList(assistant("m2", "думаю")), null);
        store.apply(Collections.singletonList(assistant("m2", "думаю дальше")), null);
        store.apply(Collections.singletonList(assistant("m2", "готово")), null);
        assertEquals(2, store.size());
        assertEquals(Arrays.asList("старт", "готово"), texts(store.transcript()));
        assertTrue(store.hasUniqueIds());
    }

    @Test
    public void snapshotResetsEverythingIncludingRemovedMessages() {
        TranscriptStore store = new TranscriptStore();
        store.replaceAll(Arrays.asList(user("m1", "a"), assistant("m2", "b")));
        store.apply(Collections.singletonList(assistant("m2", "b2")), null);
        store.replaceAll(Collections.singletonList(user("m9", "после реконнекта")));
        assertEquals(1, store.size());
        assertEquals("после реконнекта", store.transcript().get(0).text());
        assertNull(store.byId("m2"));
    }

    @Test
    public void messagesWithoutIdFallBackToRoleAndTextAndStayDistinct() {
        TranscriptStore store = new TranscriptStore();
        ChatMessage anonymous = ChatMessage.remote("", ChatMessage.Role.CUSTOM, "notice", null, null);
        store.replaceAll(Arrays.asList(anonymous, anonymous));
        assertEquals(2, store.size());
        // An identical id-less message is treated as the same entry (idempotent),
        // so it neither duplicates the row nor counts as a content change.
        TranscriptStore.ChangeSet changeSet = store.apply(
                Collections.singletonList(ChatMessage.remote("", ChatMessage.Role.CUSTOM, "notice", null, null)), null);
        assertEquals(0, changeSet.updated());
        assertTrue(changeSet.isEmpty());
        assertEquals(2, store.size());
    }

    @Test
    public void revisionOnlyMovesWhenSomethingChanged() {
        TranscriptStore store = new TranscriptStore();
        store.replaceAll(Collections.singletonList(user("m1", "a")));
        long afterSnapshot = store.revision();
        store.apply(Collections.singletonList(user("m1", "a")), null);
        assertEquals(afterSnapshot, store.revision());
        store.apply(Collections.singletonList(user("m1", "b")), null);
        assertEquals(afterSnapshot + 1, store.revision());
    }

    @Test
    public void nullAndEmptyInputsAreSafe() {
        TranscriptStore store = new TranscriptStore();
        assertTrue(store.isEmpty());
        store.replaceAll(null);
        assertTrue(store.isEmpty());
        TranscriptStore.ChangeSet changeSet = store.apply(null, null);
        assertTrue(changeSet.isEmpty());
        store.apply(Arrays.asList((ChatMessage) null), Arrays.asList((String) null, ""));
        assertTrue(store.isEmpty());
    }

    @Test
    public void updateForUnknownIdAppends() {
        TranscriptStore store = new TranscriptStore();
        store.replaceAll(Collections.singletonList(user("m1", "a")));
        TranscriptStore.ChangeSet changeSet = store.apply(
                Collections.singletonList(assistant("m7", "новое")), null);
        assertEquals(1, changeSet.added());
        assertEquals(Arrays.asList("a", "новое"), texts(store.transcript()));
    }

    @Test public void contextIndexTracksDuplicateIdsRoleChangesRemovalAndReset() {
        TranscriptStore store = new TranscriptStore();
        store.replaceAll(Arrays.asList(user("u", "first"), assistant("u", "replaced"), user("u2", "kept")));
        assertTrue(store.getHasUserContext());
        store.apply(Collections.singletonList(assistant("u2", "now assistant")), null);
        assertFalse(store.getHasUserContext());
        store.apply(Arrays.asList(user("u", "prompt"), user("u", "prompt")), null);
        assertTrue(store.getHasUserContext());
        store.apply(null, Arrays.asList("missing", "u", "u")); assertFalse(store.getHasUserContext());
        store.apply(Collections.singletonList(user("new", "new")), null); assertTrue(store.getHasUserContext());
        store.replaceAll(Collections.singletonList(assistant("tail", "tail"))); assertFalse(store.getHasUserContext());
        store.apply(Collections.singletonList(user("new", "new")), null); store.clear(); assertFalse(store.getHasUserContext());
    }
    @Test public void prependedContextUsesLiveRoleAndDeduplicatesOverlappingPages() {
        TranscriptStore store = new TranscriptStore(); store.replaceAll(Collections.singletonList(assistant("live", "current")));
        store.prepend(Arrays.asList(user("live", "stale role"), assistant("old", "old")));
        assertFalse(store.getHasUserContext()); assertEquals("current", store.byId("live").text());
        store.prepend(Arrays.asList(user("u", "old prompt"), user("u", "duplicate")));
        assertTrue(store.getHasUserContext());
        store.prepend(Collections.singletonList(user("u", "repeated page")));
        store.apply(null, Collections.singletonList("u")); assertFalse(store.getHasUserContext());
    }
    @Test public void anonymousUserRowsRemainContextUntilAllDistinctRowsAreRemovedBySnapshot() {
        TranscriptStore store = new TranscriptStore(); ChatMessage anonymous = user("", "prompt");
        store.replaceAll(Arrays.asList(anonymous, anonymous, null)); assertTrue(store.getHasUserContext());
        store.apply(Collections.singletonList(anonymous), null); assertTrue(store.getHasUserContext());
        store.replaceAll(Collections.emptyList()); assertFalse(store.getHasUserContext());
    }
    @Test public void contextIndexMatchesCanonicalTranscriptAcrossMixedOperations() {
        TranscriptStore store = new TranscriptStore(); java.util.Random random = new java.util.Random(417);
        for (int step = 0; step < 2000; step++) {
            ChatMessage row = msg("m" + random.nextInt(50), random.nextBoolean() ? ChatMessage.Role.USER : ChatMessage.Role.ASSISTANT, "s" + step);
            switch (random.nextInt(5)) {
                case 0: store.replaceAll(Arrays.asList(row, assistant(row.id(), "last wins"), user("other", "prompt"))); break;
                case 1: store.apply(Collections.singletonList(row), Arrays.asList("m" + random.nextInt(50), "other")); break;
                case 2: store.prepend(Arrays.asList(row, user(row.id(), "overlap"))); break;
                case 3: store.apply(Collections.singletonList(row), null); break;
                default: store.clear(); break;
            }
            assertEquals("Canonical context at step " + step, store.transcript().stream().anyMatch(m -> m.role() == ChatMessage.Role.USER), store.getHasUserContext());
        }
    }
}
