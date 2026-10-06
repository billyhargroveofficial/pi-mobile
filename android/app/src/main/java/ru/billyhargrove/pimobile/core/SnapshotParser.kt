package ru.billyhargrove.pimobile.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object SnapshotParser {
    @JvmStatic fun isSnapshot(value: JSONObject?) = value?.optString("type", "") == "snapshot"
    @JvmStatic fun parse(json: String?): Snapshot? {
        if (json.isNullOrBlank()) return null
        return try { parse(JSONObject(json)) } catch (_: JSONException) { null }
    }
    @JvmStatic fun parse(value: JSONObject?): Snapshot? {
        if (value == null) return null
        val type = value.optString("type", "")
        if (type.isNotEmpty() && type != "snapshot") return null
        if (!isSnapshot(value) && !value.has("messages") && !value.has("sessionId")) return null
        return Snapshot(CatalogParser.optString(value, "sessionId"), SessionStatus.parse(value.optString("status", null)),
            value.optBoolean("connected", false), parseMessages(value.optJSONArray("messages")), value.optBoolean("truncated", false))
    }
    @JvmStatic fun parseMessages(array: JSONArray?): List<ChatMessage> =
        (0 until (array?.length() ?: 0)).mapNotNull { index -> array?.optJSONObject(index)?.let { item ->
            ChatMessage(CatalogParser.optString(item, "id"), ChatMessage.Role.parse(CatalogParser.optString(item, "role")),
                CatalogParser.optString(item, "text"), parseImages(item.optJSONArray("images")),
                CatalogParser.optString(item, "toolName").ifEmpty { null }, ChatMessage.LocalState.NONE, "", item.optString("toolStatus", "done"))
                .withPresentation(item.optString("turnId", "legacy"), item.optString("phase", "answer"),
                    item.optString("preview", ""), item.optString("documentPath", ""))
        } }
    @JvmStatic fun parseImages(array: JSONArray?): List<ImageRef> =
        (0 until (array?.length() ?: 0)).mapNotNull { index -> array?.optJSONObject(index)?.let { item ->
            CatalogParser.optString(item, "url").takeIf(String::isNotEmpty)?.let { ImageRef(it, CatalogParser.optString(item, "mimeType")) }
        } }
}
