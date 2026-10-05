package ru.billyhargrove.pimobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SnapshotParserTest {

    @Test
    public void parsesAWebSocketSnapshot() {
        String json = "{"
                + "\"type\":\"snapshot\",\"sessionId\":\"s1\",\"status\":\"running\",\"connected\":true,"
                + "\"truncated\":true,"
                + "\"messages\":["
                + " {\"id\":\"m1\",\"role\":\"user\",\"text\":\"привет\",\"images\":[{\"url\":\"/api/sessions/s1/media/aa\",\"mimeType\":\"image/png\"}]},"
                + " {\"id\":\"m2\",\"role\":\"assistant\",\"text\":\"готово\"},"
                + " {\"id\":\"m3\",\"role\":\"toolResult\",\"text\":\"ok\",\"toolName\":\"bash\"},"
                + " {\"id\":\"m4\",\"role\":\"custom\",\"text\":\"notice\"},"
                + " {\"id\":\"m5\",\"role\":\"something-else\",\"text\":\"?\"},"
                + " {\"id\":\"m6\"}"
                + "]}";
        Snapshot snapshot = SnapshotParser.parse(json);
        assertNotNull(snapshot);
        assertEquals("s1", snapshot.sessionId());
        assertEquals(SessionStatus.RUNNING, snapshot.status());
        assertTrue(snapshot.connected());
        assertTrue(snapshot.truncated());
        assertEquals(6, snapshot.messages().size());

        ChatMessage user = snapshot.messages().get(0);
        assertEquals(ChatMessage.Role.USER, user.role());
        assertEquals("привет", user.text());
        assertEquals(1, user.images().size());
        assertEquals("/api/sessions/s1/media/aa", user.images().get(0).url());
        assertEquals("image/png", user.images().get(0).mimeType());
        assertEquals(ChatMessage.LocalState.NONE, user.localState());

        assertEquals(ChatMessage.Role.ASSISTANT, snapshot.messages().get(1).role());
        assertEquals(ChatMessage.Role.TOOL_RESULT, snapshot.messages().get(2).role());
        assertEquals("bash", snapshot.messages().get(2).toolName());
        assertEquals(ChatMessage.Role.CUSTOM, snapshot.messages().get(3).role());
        assertEquals(ChatMessage.Role.UNKNOWN, snapshot.messages().get(4).role());
        assertEquals("", snapshot.messages().get(5).text());
    }

    @Test
    public void parsesTheRestSnapshotShapeWithoutAType() {
        Snapshot snapshot = SnapshotParser.parse(
                "{\"sessionId\":\"s2\",\"status\":\"idle\",\"messages\":[]}");
        assertNotNull(snapshot);
        assertEquals("s2", snapshot.sessionId());
        assertEquals(SessionStatus.IDLE, snapshot.status());
        assertFalse(snapshot.truncated());
    }

    @Test
    public void ignoresImagesWithoutUrlAndMessagesOfWrongType() {
        String json = "{\"type\":\"snapshot\",\"sessionId\":\"s3\",\"messages\":["
                + "{\"id\":\"m1\",\"role\":\"user\",\"text\":\"x\",\"images\":[{\"mimeType\":\"image/png\"},";
        assertNull(SnapshotParser.parse(json));
        Snapshot snapshot = SnapshotParser.parse(
                "{\"type\":\"snapshot\",\"sessionId\":\"s3\",\"messages\":[{\"role\":\"user\",\"images\":[{\"mimeType\":\"image/png\"}]}]}");
        assertNotNull(snapshot);
        assertEquals(0, snapshot.messages().get(0).images().size());
    }

    @Test
    public void rejectsUnrelatedPayloads() {
        assertNull(SnapshotParser.parse((String) null));
        assertNull(SnapshotParser.parse(""));
        assertNull(SnapshotParser.parse("{}"));
        assertNull(SnapshotParser.parse("не json"));
        assertNull(SnapshotParser.parse("{\"type\":\"catalog\"}"));
    }

    @Test
    public void missingMessagesMeansEmptyTranscript() {
        Snapshot snapshot = SnapshotParser.parse("{\"type\":\"snapshot\",\"sessionId\":\"s4\"}");
        assertNotNull(snapshot);
        assertTrue(snapshot.messages().isEmpty());
    }
}
