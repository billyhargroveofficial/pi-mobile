package ru.billyhargrove.pimobile.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Parses the {@code {type:'messages',…}} incremental frame. */
public final class MessagesParser {

    private MessagesParser() {
    }

    public static boolean isMessages(JSONObject object) {
        return object != null && "messages".equals(object.optString("type", ""));
    }

    /** @return {@code null} when the payload is not a messages frame. */
    public static MessagesUpdate parse(JSONObject object) {
        if (!isMessages(object)) {
            return null;
        }
        return new MessagesUpdate(
                CatalogParser.optString(object, "sessionId"),
                SnapshotParser.parseMessages(object.optJSONArray("messages")),
                parseRemovedIds(object.optJSONArray("removedIds")),
                SessionStatus.parse(object.optString("status", null)),
                object.has("status") && !object.isNull("status"),
                object.optBoolean("connected", false),
                object.has("connected") && !object.isNull("connected"),
                object.optBoolean("truncated", false),
                object.has("truncated") && !object.isNull("truncated"));
    }

    public static MessagesUpdate parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return parse(new JSONObject(json));
        } catch (JSONException e) {
            return null;
        }
    }

    private static List<String> parseRemovedIds(JSONArray array) {
        List<String> out = new ArrayList<>();
        if (array == null) {
            return out;
        }
        for (int i = 0; i < array.length(); i++) {
            Object raw = array.opt(i);
            if (raw == null || raw == JSONObject.NULL) {
                continue;
            }
            String id = String.valueOf(raw);
            if (!id.isEmpty()) {
                out.add(id);
            }
        }
        return out;
    }
}
