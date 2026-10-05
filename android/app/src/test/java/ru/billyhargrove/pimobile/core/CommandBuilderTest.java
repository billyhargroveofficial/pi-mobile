package ru.billyhargrove.pimobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class CommandBuilderTest {

    private static ImagePayload png(int size) {
        byte[] data = new byte[size];
        data[0] = (byte) 0x89;
        data[1] = 'P';
        return new ImagePayload(data, "image/png");
    }

    @Test
    public void subscribeFrameCarriesOnlySessionId() throws Exception {
        JSONObject frame = CommandBuilder.subscribe("session-1");
        assertEquals("subscribe", frame.getString("type"));
        assertEquals("session-1", frame.getString("sessionId"));
        assertFalse(frame.has("token"));
    }

    @Test
    public void promptFrameMatchesTheContract() throws Exception {
        JSONObject frame = CommandBuilder.promptCommand(
                "s1", "req-1", "привет", null, null);
        assertEquals("command", frame.getString("type"));
        assertEquals("s1", frame.getString("sessionId"));
        assertEquals("req-1", frame.getString("requestId"));
        assertEquals("prompt", frame.getString("command"));
        assertEquals("привет", frame.getString("text"));
        assertEquals("followUp", frame.getString("behavior"));
        assertFalse(frame.has("images"));
        assertFalse(frame.toString().contains("token"));
    }

    @Test
    public void steerBehaviorIsSentWhenRequested() throws Exception {
        JSONObject frame = CommandBuilder.promptCommand(
                "s1", "req-2", "text", null, CommandBuilder.Behavior.STEER);
        assertEquals("steer", frame.getString("behavior"));
    }

    @Test
    public void imagesAreBase64EncodedWithMimeType() throws Exception {
        byte[] raw = new byte[]{1, 2, 3, 4};
        List<ImagePayload> images = new ArrayList<>();
        images.add(new ImagePayload(raw, "image/jpeg"));
        JSONObject frame = CommandBuilder.promptCommand("s1", "req-3", "x", images,
                CommandBuilder.Behavior.FOLLOW_UP);
        JSONArray array = frame.getJSONArray("images");
        assertEquals(1, array.length());
        JSONObject image = array.getJSONObject(0);
        assertEquals("image", image.getString("type"));
        assertEquals("image/jpeg", image.getString("mimeType"));
        assertEquals(Base64.getEncoder().encodeToString(raw), image.getString("data"));
    }

    @Test
    public void abortFrameHasNoPromptPayload() throws Exception {
        JSONObject frame = CommandBuilder.abortCommand("s1", "req-4");
        assertEquals("command", frame.getString("type"));
        assertEquals("abort", frame.getString("command"));
        assertEquals("s1", frame.getString("sessionId"));
        assertEquals("req-4", frame.getString("requestId"));
        assertFalse(frame.has("text"));
    }

    @Test
    public void moreThanThreeImagesAreRejected() {
        List<ImagePayload> images = Arrays.asList(png(10), png(10), png(10), png(10));
        try {
            CommandBuilder.promptCommand("s1", "req-5", "x", images, CommandBuilder.Behavior.FOLLOW_UP);
            fail("ожидалось исключение о лимите изображений");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("3"));
        }
    }

    @Test
    public void moreThanTenMegabytesIsRejected() {
        List<ImagePayload> images = new ArrayList<>();
        images.add(png(6 * 1024 * 1024));
        images.add(png(6 * 1024 * 1024));
        try {
            CommandBuilder.promptCommand("s1", "req-6", "x", images, CommandBuilder.Behavior.FOLLOW_UP);
            fail("ожидалось исключение о лимите размера");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void unsupportedMimeTypeIsRejected() {
        List<ImagePayload> images = new ArrayList<>();
        images.add(new ImagePayload(new byte[]{1, 2, 3}, "image/gif"));
        try {
            CommandBuilder.promptCommand("s1", "req-7", "x", images, CommandBuilder.Behavior.FOLLOW_UP);
            fail("ожидалось исключение о формате");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("image/gif"));
        }
    }

    @Test
    public void emptySessionOrRequestIdIsRejected() {
        try {
            CommandBuilder.subscribe("");
            fail("ожидалось исключение");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
        try {
            CommandBuilder.promptCommand("s1", " ", "x", null, null);
            fail("ожидалось исключение");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void blankTextIsSentAsEmptyStringNotDropped() throws Exception {
        JSONObject frame = CommandBuilder.promptCommand("s1", "req-8", null, null, null);
        assertEquals("", frame.getString("text"));
    }

    @Test
    public void frameTypeIsReadableWithoutExceptions() {
        assertEquals("ack", CommandBuilder.frameType("{\"type\":\"ack\"}"));
        assertEquals("", CommandBuilder.frameType("не json"));
        assertEquals("", CommandBuilder.frameType(null));
    }

    @Test
    public void ackParsingFollowsTheContract() throws Exception {
        Ack ok = AckParser.parse(new JSONObject(
                "{\"type\":\"ack\",\"requestId\":\"r1\",\"sessionId\":\"s1\",\"ok\":true}"));
        assertNotNull(ok);
        assertTrue(ok.ok());
        assertEquals("r1", ok.requestId());
        Ack failed = AckParser.parse(new JSONObject(
                "{\"type\":\"ack\",\"requestId\":\"r2\",\"sessionId\":\"s1\",\"ok\":false,\"error\":\"занято\"}"));
        assertNotNull(failed);
        assertFalse(failed.ok());
        assertEquals("занято", failed.error());
        assertNull(AckParser.parse(new JSONObject("{\"type\":\"catalog\"}")));
        assertEquals("плохой токен", AckParser.parseErrorMessage(
                new JSONObject("{\"type\":\"error\",\"error\":\"плохой токен\"}")));
        assertNull(AckParser.parseErrorMessage(new JSONObject("{\"type\":\"ack\"}")));
    }
}
