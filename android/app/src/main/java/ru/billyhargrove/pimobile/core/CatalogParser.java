package ru.billyhargrove.pimobile.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses GET /api/catalog and the catalog frame the gateway sends right after a
 * WebSocket connect. The parser is deliberately forgiving: a malformed entry is
 * skipped instead of failing the whole catalog.
 */
public final class CatalogParser {

    private CatalogParser() {
    }

    public static boolean isCatalog(JSONObject object) {
        return object != null && "catalog".equals(object.optString("type", ""));
    }

    /** Returns {@code null} when the payload is not a catalog frame at all. */
    public static Catalog parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return parse(new JSONObject(json));
        } catch (JSONException e) {
            return null;
        }
    }

    /** Returns {@code null} when the object is not a catalog frame. */
    public static Catalog parse(JSONObject object) {
        if (!isCatalog(object)) {
            return null;
        }
        return new Catalog(
                parseWorkspaces(object.optJSONArray("workspaces")),
                parseSessions(object.optJSONArray("sessions")),
                parseTerminals(object.optJSONArray("terminals")));
    }

    private static List<Workspace> parseWorkspaces(JSONArray array) {
        List<Workspace> out = new ArrayList<>();
        if (array == null) {
            return out;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String id = optString(o, "id");
            if (id.isEmpty()) {
                continue;
            }
            out.add(new Workspace(id, optString(o, "name"), optString(o, "path")));
        }
        return out;
    }

    private static List<Session> parseSessions(JSONArray array) {
        List<Session> out = new ArrayList<>();
        if (array == null) {
            return out;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String id = optString(o, "id");
            if (id.isEmpty()) {
                continue;
            }
            out.add(new Session(
                    id,
                    optString(o, "title"),
                    optString(o, "cwd"),
                    optString(o, "workspaceId"),
                    optString(o, "terminalId"),
                    o.optBoolean("connected", false),
                    SessionStatus.parse(o.optString("status", null)),
                    optString(o, "model")));
        }
        return out;
    }

    private static List<TerminalInfo> parseTerminals(JSONArray array) {
        List<TerminalInfo> out = new ArrayList<>();
        if (array == null) {
            return out;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String id = optString(o, "id");
            if (id.isEmpty()) {
                continue;
            }
            out.add(new TerminalInfo(
                    id,
                    optString(o, "title"),
                    optString(o, "workspaceId"),
                    optString(o, "agent"),
                    o.optBoolean("connected", false)));
        }
        return out;
    }

    static String optString(JSONObject object, String key) {
        if (object == null || object.isNull(key)) {
            return "";
        }
        Object raw = object.opt(key);
        return raw == null ? "" : String.valueOf(raw);
    }
}
