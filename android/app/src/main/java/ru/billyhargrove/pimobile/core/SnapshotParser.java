package ru.billyhargrove.pimobile.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a {type:'snapshot'} frame (WebSocket) and the body of
 * GET /api/sessions/&lt;encoded-id&gt;.
 */
public final class SnapshotParser {

    private SnapshotParser() {
    }

    public static boolean isSnapshot(JSONObject object) {
        return object != null && "snapshot".equals(object.optString("type", ""));
    }

    /** Returns {@code null} when the payload cannot be understood. */
    public static Snapshot parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return parse(new JSONObject(json));
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * REST snapshots carry no {@code type} field, so a bare object is accepted
     * as long as it looks like a snapshot (has {@code messages} or {@code sessionId}).
     */
    public static Snapshot parse(JSONObject object) {
        if (object == null) {
            return null;
        }
        // Any explicit type other than "snapshot" is a different frame
        // (catalog/messages/ack/error) and must not be read as a snapshot.
        String type = object.optString("type", "");
        if (!type.isEmpty() && !"snapshot".equals(type)) {
            return null;
        }
        boolean looksLikeSnapshot = isSnapshot(object)
                || object.has("messages")
                || object.has("sessionId");
        if (!looksLikeSnapshot) {
            return null;
        }
        return new Snapshot(
                CatalogParser.optString(object, "sessionId"),
                SessionStatus.parse(object.optString("status", null)),
                object.optBoolean("connected", false),
                parseMessages(object.optJSONArray("messages")),
                object.optBoolean("truncated", false));
    }

    public static List<ChatMessage> parseMessages(JSONArray array) {
        List<ChatMessage> out = new ArrayList<>();
        if (array == null) {
            return out;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null) {
                continue;
            }
            out.add(new ChatMessage(
                    CatalogParser.optString(o, "id"),
                    ChatMessage.Role.parse(CatalogParser.optString(o, "role")),
                    CatalogParser.optString(o, "text"),
                    parseImages(o.optJSONArray("images")),
                    emptyToNull(CatalogParser.optString(o, "toolName")),
                    ChatMessage.LocalState.NONE,
                    "", o.optString("toolStatus", "done")));
        }
        return out;
    }

    public static List<ImageRef> parseImages(JSONArray array) {
        List<ImageRef> out = new ArrayList<>();
        if (array == null) {
            return out;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String url = CatalogParser.optString(o, "url");
            if (url.isEmpty()) {
                continue;
            }
            out.add(new ImageRef(url, CatalogParser.optString(o, "mimeType")));
        }
        return out;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
