package ru.billyhargrove.pimobile.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Bad individual entries are skipped without discarding the whole catalog. */
object CatalogParser {
    @JvmStatic fun isCatalog(value: JSONObject?) = value?.optString("type", "") == "catalog"
    @JvmStatic fun parse(json: String?): Catalog? {
        if (json.isNullOrBlank()) return null
        return try { parse(JSONObject(json)) } catch (_: JSONException) { null }
    }
    @JvmStatic fun parse(value: JSONObject?): Catalog? {
        if (!isCatalog(value) || value == null) return null
        return Catalog(entries(value.optJSONArray("workspaces")) { item ->
            Workspace(optString(item, "id"), optString(item, "name"), optString(item, "path"))
        }, entries(value.optJSONArray("sessions")) { item ->
            Session(optString(item, "id"), optString(item, "title"), optString(item, "cwd"),
                optString(item, "workspaceId"), optString(item, "terminalId"), item.optBoolean("connected", false),
                SessionStatus.parse(item.optString("status", null)), optString(item, "model"))
        }, entries(value.optJSONArray("terminals")) { item ->
            TerminalInfo(optString(item, "id"), optString(item, "title"), optString(item, "workspaceId"),
                optString(item, "agent"), item.optBoolean("connected", false))
        })
    }
    private fun <T> entries(array: JSONArray?, create: (JSONObject) -> T): List<T> =
        (0 until (array?.length() ?: 0)).mapNotNull { index ->
            array?.optJSONObject(index)?.takeIf { optString(it, "id").isNotEmpty() }?.let(create)
        }
    @JvmStatic fun optString(value: JSONObject?, key: String): String =
        if (value == null || value.isNull(key)) "" else value.opt(key)?.toString().orEmpty()
}
