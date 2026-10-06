package ru.billyhargrove.pimobile.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Preserve wire checkpoints verbatim; newer canonical rows win history overlap. */
object ConversationFrames {
    @JvmStatic @Throws(JSONException::class)
    fun merge(prior: JSONObject?, frame: JSONObject): JSONObject {
        if (prior == null || frame.optString("type") == "snapshot") return JSONObject(frame.toString())
        val out = JSONObject(prior.toString())
        for (key in frame.keys()) if (key !in setOf("messages", "removedIds", "order", "resumed", "type")) out.put(key, frame.get(key))
        val rows = linkedMapOf<String, JSONObject>()
        add(rows, prior.optJSONArray("messages"))
        frame.optJSONArray("removedIds")?.let { removed -> for (i in 0 until removed.length()) rows.remove(removed.optString(i)) }
        add(rows, frame.optJSONArray("messages"))
        val order = frame.optJSONArray("order")
        if (order != null) {
            val tail = linkedMapOf<String, JSONObject>()
            for (i in 0 until order.length()) { val id = order.optString(i); rows.remove(id)?.let { tail[id] = it } }
            rows.putAll(tail)
        }
        val result = JSONArray(rows.values.drop((rows.size - 1500).coerceAtLeast(0)))
        if (order != null && result.length() > order.length() && prior.has("history")) out.put("history", prior.get("history"))
        return out.put("type", "snapshot").put("messages", result)
    }
    private fun add(rows: MutableMap<String, JSONObject>, array: JSONArray?) {
        for (i in 0 until (array?.length() ?: 0)) array?.optJSONObject(i)?.let { row ->
            row.optString("id").takeIf(String::isNotEmpty)?.let { rows[it] = row }
        }
    }
    @JvmStatic @Throws(JSONException::class)
    fun prepend(prior: JSONObject?, history: JSONObject): JSONObject? {
        if (prior == null || prior.optLong("epoch") != history.optLong("epoch")) return prior
        val base = JSONObject(prior.toString()).put("messages", history.optJSONArray("messages"))
        val out = merge(base, JSONObject().put("type", "messages").put("messages", prior.optJSONArray("messages")))
        if (history.has("history")) out.put("history", history.get("history"))
        return out
    }
}
