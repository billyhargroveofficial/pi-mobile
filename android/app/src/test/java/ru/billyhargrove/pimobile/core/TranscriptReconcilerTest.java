package ru.billyhargrove.pimobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TranscriptReconcilerTest {

    private static ChatMessage remoteUser(String id, String text) {
        return ChatMessage.remote(id, ChatMessage.Role.USER, text, null, null);
    }

    private static ChatMessage remoteAssistant(String id, String text) {
        return ChatMessage.remote(id, ChatMessage.Role.ASSISTANT, text, null, null);
    }

    private static ChatMessage local(String requestId, String text, ChatMessage.LocalState state) {
        return ChatMessage.local(requestId, text, null, state);
    }

    @Test
    public void serverEchoReplacesTheLocalBubble() {
        List<ChatMessage> snapshot = Arrays.asList(remoteUser("m1", "привет"), remoteAssistant("m2", "ок"));
        List<ChatMessage> local = Collections.singletonList(local("r1", "привет", ChatMessage.LocalState.ACCEPTED));
        List<ChatMessage> merged = TranscriptReconciler.merge(snapshot, local);
        assertEquals(2, merged.size());
        assertEquals("m1", merged.get(0).id());
    }

    @Test
    public void unconfirmedBubbleStaysVisible() {
        List<ChatMessage> snapshot = Collections.singletonList(remoteAssistant("m2", "ок"));
        List<ChatMessage> local = Collections.singletonList(local("r1", "привет", ChatMessage.LocalState.SENDING));
        List<ChatMessage> merged = TranscriptReconciler.merge(snapshot, local);
        assertEquals(2, merged.size());
        assertEquals("local:r1", merged.get(1).id());
    }

    @Test
    public void uncertainBubbleIsNotDroppedWhileItIsMissingFromTheSnapshot() {
        List<ChatMessage> snapshot = Collections.emptyList();
        List<ChatMessage> local = Collections.singletonList(local("r1", "привет", ChatMessage.LocalState.UNCERTAIN));
        List<ChatMessage> merged = TranscriptReconciler.merge(snapshot, local);
        assertEquals(1, merged.size());
        assertTrue(TranscriptReconciler.isUncertain(merged.get(0)));
    }

    @Test
    public void uncertainBubbleDisappearsWhenTheSnapshotProvesItArrived() {
        List<ChatMessage> snapshot = Collections.singletonList(remoteUser("m1", "привет"));
        List<ChatMessage> local = Collections.singletonList(local("r1", "привет", ChatMessage.LocalState.UNCERTAIN));
        List<ChatMessage> merged = TranscriptReconciler.merge(snapshot, local);
        assertEquals(1, merged.size());
        assertEquals("m1", merged.get(0).id());
    }

    @Test
    public void failedBubbleIsAlwaysKept() {
        List<ChatMessage> snapshot = Collections.singletonList(remoteUser("m0", "привет"));
        List<ChatMessage> local = Collections.singletonList(local("r1", "привет", ChatMessage.LocalState.FAILED));
        List<ChatMessage> merged = TranscriptReconciler.merge(snapshot, local);
        assertEquals(2, merged.size());
        assertEquals(ChatMessage.LocalState.FAILED, merged.get(1).localState());
    }

    @Test
    public void duplicateTextsAreMatchedOneForOne() {
        List<ChatMessage> snapshot = new ArrayList<>();
        snapshot.add(remoteUser("m1", "да"));
        List<ChatMessage> local = Arrays.asList(
                local("r1", "да", ChatMessage.LocalState.ACCEPTED),
                local("r2", "да", ChatMessage.LocalState.SENDING));
        List<ChatMessage> merged = TranscriptReconciler.merge(snapshot, local);
        assertEquals(2, merged.size());
        assertEquals("local:r2", merged.get(1).id());

        snapshot.add(remoteUser("m2", "да"));
        List<ChatMessage> mergedBoth = TranscriptReconciler.merge(snapshot, local);
        assertEquals(2, mergedBoth.size());
        assertEquals("m1", mergedBoth.get(0).id());
        assertEquals("m2", mergedBoth.get(1).id());
    }

    @Test
    public void assistantTextIsNeverConsumed() {
        List<ChatMessage> snapshot = Collections.singletonList(remoteAssistant("m2", "готово"));
        List<ChatMessage> local = Collections.singletonList(local("r1", "готово", ChatMessage.LocalState.ACCEPTED));
        List<ChatMessage> merged = TranscriptReconciler.merge(snapshot, local);
        assertEquals(2, merged.size());
    }

    @Test
    public void emptyInputsAreHandled() {
        assertTrue(TranscriptReconciler.merge(null, null).isEmpty());
        assertTrue(TranscriptReconciler.merge(Collections.<ChatMessage>emptyList(), null).isEmpty());
        assertEquals(1, TranscriptReconciler.merge(null,
                Collections.singletonList(local("r1", "x", ChatMessage.LocalState.SENDING))).size());
    }

    @Test
    public void isUncertainOnlyForSendingAndUncertainStates() {
        assertTrue(TranscriptReconciler.isUncertain(local("r", "x", ChatMessage.LocalState.SENDING)));
        assertTrue(TranscriptReconciler.isUncertain(local("r", "x", ChatMessage.LocalState.UNCERTAIN)));
        assertFalse(TranscriptReconciler.isUncertain(local("r", "x", ChatMessage.LocalState.ACCEPTED)));
        assertFalse(TranscriptReconciler.isUncertain(local("r", "x", ChatMessage.LocalState.FAILED)));
        assertFalse(TranscriptReconciler.isUncertain(remoteUser("m", "x")));
        assertFalse(TranscriptReconciler.isUncertain(null));
    }
}
