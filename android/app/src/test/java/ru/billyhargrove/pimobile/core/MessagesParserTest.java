package ru.billyhargrove.pimobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class MessagesParserTest {

    @Test
    public void parsesAnIncrementalFrame() throws Exception {
        String json = "{"
                + "\"type\":\"messages\","
                + "\"sessionId\":\"s1\","
                + "\"messages\":["
                + "  {\"id\":\"m2\",\"role\":\"assistant\",\"text\":\"готово\"},"
                + "  {\"id\":\"m3\",\"role\":\"user\",\"text\":\"спасибо\",\"images\":[{\"url\":\"/api/sessions/s1/media/aa\",\"mimeType\":\"image/png\"}]}"
                + "],"
                + "\"removedIds\":[\"m1\"],"
                + "\"status\":\"running\",\"connected\":true,\"truncated\":false"
                + "}";
        MessagesUpdate update = MessagesParser.parse(json);
        assertNotNull(update);
        assertEquals("s1", update.sessionId());
        assertEquals(2, update.messages().size());
        assertEquals("m2", update.messages().get(0).id());
        assertEquals(ChatMessage.Role.ASSISTANT, update.messages().get(0).role());
        assertEquals(1, update.messages().get(1).images().size());
        assertEquals(1, update.removedIds().size());
        assertEquals("m1", update.removedIds().get(0));
        assertEquals(SessionStatus.RUNNING, update.status());
        assertTrue(update.hasStatus());
        assertTrue(update.connected());
        assertTrue(update.hasConnected());
        assertFalse(update.truncated());
        assertTrue(update.hasTruncated());
        assertFalse(update.isEmpty());
    }

    @Test
    public void missingOptionalFieldsAreReportedAsAbsent() throws Exception {
        MessagesUpdate update = MessagesParser.parse(new JSONObject(
                "{\"type\":\"messages\",\"sessionId\":\"s2\",\"messages\":[]}"));
        assertNotNull(update);
        assertFalse(update.hasStatus());
        assertFalse(update.hasConnected());
        assertFalse(update.hasTruncated());
        assertTrue(update.isEmpty());
    }

    @Test
    public void removalOnlyFrameIsValid() {
        MessagesUpdate update = MessagesParser.parse(
                "{\"type\":\"messages\",\"sessionId\":\"s3\",\"removedIds\":[\"a\",\"b\",null,\"\"]}");
        assertNotNull(update);
        assertTrue(update.messages().isEmpty());
        assertEquals(2, update.removedIds().size());
        assertFalse(update.isEmpty());
    }

    @Test
    public void rejectsOtherFrameTypes() {
        assertNull(MessagesParser.parse("{\"type\":\"snapshot\",\"sessionId\":\"s\"}"));
        assertNull(MessagesParser.parse("{\"type\":\"catalog\"}"));
        assertNull(MessagesParser.parse("{\"type\":\"ack\"}"));
        assertNull(MessagesParser.parse((JSONObject) null));
        assertNull(MessagesParser.parse((String) null));
        assertNull(MessagesParser.parse(""));
        assertNull(MessagesParser.parse("не json"));
    }

    @Test
    public void messagesFrameIsNotMistakenForASnapshot() {
        String json = "{\"type\":\"messages\",\"sessionId\":\"s1\",\"messages\":[]}";
        assertNull(SnapshotParser.parse(json));
        assertNotNull(MessagesParser.parse(json));
    }
}
