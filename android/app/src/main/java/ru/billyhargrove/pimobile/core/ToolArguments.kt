package ru.billyhargrove.pimobile.core

import org.json.*
import java.util.Locale

/** Single-line summaries exclude credential keys and raw JSON containers. */
object ToolArguments {
    private val secretPattern = Regex(".*(token|password|secret|authorization|api.?key|headers).*")
    private val whitespace = Regex("\\s+")
    private val home = Regex("^/(Users|home)/[^/]+/")
    @JvmStatic fun format(tool: String?, raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val input = raw.trim()
        if (!input.startsWith('{') && !input.startsWith('[')) return line(input)
        return try {
            val parsed = JSONTokener(input).nextValue()
            if (parsed !is JSONObject) return value(parsed)
            val name = tool?.lowercase(Locale.ROOT).orEmpty()
            if ("bash" in name || "shell" in name) return first(parsed, "command", "cmd", "script")
            val path = first(parsed, "path", "file_path", "filePath")
            if (path.isNotEmpty()) return buildList {
                add(home.replaceFirst(path, "~/"))
                if (parsed.has("offset")) add("со строки ${value(parsed.opt("offset"))}")
                if (parsed.has("limit")) add("${value(parsed.opt("limit"))} строк")
                parsed.optJSONArray("edits")?.let { add("правок: ${it.length()}") }
                if (parsed.has("pattern")) add("найти: ${value(parsed.opt("pattern"))}")
            }.joinToString(" · ")
            for (key in listOf("search_query", "image_query", "queries")) parsed.optJSONArray(key)?.let { array ->
                return (0 until minOf(array.length(), 4)).joinToString(" · ") { index ->
                    val query = array.opt(index)
                    if (query is JSONObject) first(query, "q", "query") else value(query)
                }
            }
            val query = first(parsed, "q", "query", "pattern")
            if (query.isNotEmpty()) return query
            val parts = mutableListOf<String>()
            val keys = parsed.keys()
            while (keys.hasNext() && parts.size < 4) {
                val key = keys.next()
                if (secret(key)) continue
                val summary = value(parsed.opt(key))
                if (summary.isNotEmpty()) parts.add("$key: $summary")
            }
            parts.joinToString(" · ")
        } catch (_: JSONException) { "Аргументы недоступны" }
    }
    private fun secret(key: String) = secretPattern.matches(key.lowercase(Locale.ROOT))
    private fun first(value: JSONObject, vararg keys: String): String {
        for (key in keys) value(value.opt(key)).takeIf(String::isNotEmpty)?.let { return it }
        return ""
    }
    private fun value(item: Any?): String = when {
        item == null || item == JSONObject.NULL -> ""
        item is JSONObject -> {
            val parts = mutableListOf<String>(); val keys = item.keys()
            while (keys.hasNext() && parts.size < 3) {
                val key = keys.next(); val child = item.opt(key)
                if (!secret(key) && child !is JSONObject && child !is JSONArray) parts.add("$key: ${line(java.lang.String.valueOf(child))}")
            }
            parts.joinToString(", ").ifEmpty { "параметры" }
        }
        item is JSONArray -> buildList {
            for (index in 0 until minOf(item.length(), 3)) add(value(item.opt(index)))
            if (item.length() > 3) add("ещё ${item.length() - 3}")
        }.joinToString(", ")
        else -> line(item.toString())
    }
    private fun line(text: String) = whitespace.replace(text, " ").trim().let { if (it.length > 500) it.substring(0, 497) + "…" else it }
}
