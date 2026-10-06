package ru.billyhargrove.pimobile.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object MessagesParser {
    @JvmStatic fun isMessages(value: JSONObject?) = value?.optString("type", "") == "messages"
    @JvmStatic fun parse(json: String?): MessagesUpdate? {
        if (json.isNullOrBlank()) return null
        return try { parse(JSONObject(json)) } catch (_: JSONException) { null }
    }
    @JvmStatic fun parse(value: JSONObject?): MessagesUpdate? {
        if (!isMessages(value) || value == null) return null
        fun present(key: String) = value.has(key) && !value.isNull(key)
        return MessagesUpdate(CatalogParser.optString(value, "sessionId"), SnapshotParser.parseMessages(value.optJSONArray("messages")),
            removed(value.optJSONArray("removedIds")), SessionStatus.parse(value.optString("status", null)), present("status"),
            value.optBoolean("connected", false), present("connected"), value.optBoolean("truncated", false), present("truncated"))
    }
    private fun removed(array: JSONArray?): List<String> =
        (0 until (array?.length() ?: 0)).mapNotNull { index ->
            array?.opt(index)?.takeUnless { it == JSONObject.NULL }?.toString()?.takeIf(String::isNotEmpty)
        }
}
