package ru.billyhargrove.pimobile.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Base64;
import java.util.List;

/**
 * Builds the client frames described in docs/protocol.md.
 *
 * <p>Every command carries a fresh {@code requestId}; the UI stores it and waits
 * for the matching ack. Nothing here retries: an un-acked command is reported as
 * uncertain and is never replayed automatically.</p>
 */
public final class CommandBuilder {

    public enum Behavior {
        FOLLOW_UP("followUp"),
        STEER("steer");

        private final String wire;

        Behavior(String wire) {
            this.wire = wire;
        }

        public String wire() {
            return wire;
        }
    }

    private CommandBuilder() {
    }

    public static JSONObject subscribe(String sessionId) {
        requireId(sessionId, "sessionId");
        JSONObject o = new JSONObject();
        try {
            o.put("type", "subscribe");
            o.put("sessionId", sessionId);
        } catch (JSONException e) {
            throw new IllegalStateException("Could not build subscription", e);
        }
        return o;
    }

    public static JSONObject promptCommand(String sessionId,
                                           String requestId,
                                           String text,
                                           List<ImagePayload> images,
                                           Behavior behavior) {
        requireId(sessionId, "sessionId");
        requireId(requestId, "requestId");
        if(images!=null){if(images.size()>3||ImageGuard.totalBytes(images)>ImageGuard.MAX_TOTAL_BYTES)throw new IllegalArgumentException("Up to 3 attachments, totaling no more than 10 MB");for(ImagePayload p:images){if(p!=null&&p.isFile()){if(p.size()==0||p.fileName().isBlank()||p.fileName().length()>200||p.fileName().matches(".*[\\\\/\\x00-\\x1f].*"))throw new IllegalArgumentException("Invalid file");}else ImageGuard.validate(java.util.Collections.singletonList(p));}}
        JSONObject o = new JSONObject();
        try {
            o.put("type", "command");
            o.put("sessionId", sessionId);
            o.put("requestId", requestId);
            o.put("command", "prompt");
            o.put("text", text == null ? "" : text);
            if (images != null && !images.isEmpty()) {
                JSONArray array = new JSONArray(),files=new JSONArray();
                for (ImagePayload image : images) {
                    JSONObject item = new JSONObject();
                    item.put("data", Base64.getEncoder().encodeToString(image.bytes()));
                    if(image.isFile()){item.put("name",image.fileName());files.put(item);}else{item.put("type", "image");item.put("mimeType", image.mimeType());array.put(item);}
                }
                o.put("images", array);
                if(files.length()>0)o.put("files",files);
            }
            o.put("behavior", (behavior == null ? Behavior.FOLLOW_UP : behavior).wire());
        } catch (JSONException e) {
            throw new IllegalStateException("Could not build prompt command", e);
        }
        return o;
    }

    public static JSONObject abortCommand(String sessionId, String requestId) {
        requireId(sessionId, "sessionId");
        requireId(requestId, "requestId");
        JSONObject o = new JSONObject();
        try {
            o.put("type", "command");
            o.put("sessionId", sessionId);
            o.put("requestId", requestId);
            o.put("command", "abort");
        } catch (JSONException e) {
            throw new IllegalStateException("Could not build abort command", e);
        }
        return o;
    }

    /** Wire type of a frame, or "" when it cannot be read. */
    public static String frameType(String json) {
        if (json == null || json.trim().isEmpty()) {
            return "";
        }
        try {
            return new JSONObject(json).optString("type", "");
        } catch (JSONException e) {
            return "";
        }
    }

    private static void requireId(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty " + name);
        }
    }
}
