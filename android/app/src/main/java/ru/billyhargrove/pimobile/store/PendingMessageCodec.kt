package ru.billyhargrove.pimobile.store

import org.json.JSONArray
import org.json.JSONObject
import ru.billyhargrove.pimobile.core.ChatMessage
import ru.billyhargrove.pimobile.core.ImageRef

/** Private receipt format; accepts the original Java records without payload bytes. */
object PendingMessageCodec {
    @JvmStatic fun decode(value: String): List<ChatMessage> {
        val records = runCatching { JSONArray(value) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until records.length()) {
                val receipt = runCatching {
                    val record = records.getJSONObject(index)
                    val id = record.getString("id").takeIf { it.isNotEmpty() } ?: return@runCatching null
                    var state = ChatMessage.LocalState.valueOf(record.getString("state"))
                    if (state == ChatMessage.LocalState.NONE) return@runCatching null
                    if (state == ChatMessage.LocalState.SENDING) state = ChatMessage.LocalState.UNCERTAIN
                    val mimes = record.optJSONArray("images")
                    val urls = record.optJSONArray("imageUrls")
                    val images = buildList {
                        if (mimes != null) for (image in 0 until mimes.length()) {
                            val savedUrl = urls?.optString(image, "").orEmpty()
                            val url = if (savedUrl.matches(Regex("local:[0-9]+"))) savedUrl else "local:$image"
                            add(ImageRef(url, mimes.getString(image)))
                        }
                    }
                    ChatMessage.local(id, record.getString("text"), images, state).apply {
                        if (record.optBoolean("queued")) withQueue((0 until (record.optJSONArray("queueBaseline")?.length() ?: 0))
                            .mapNotNull { record.optJSONArray("queueBaseline")?.optString(it)?.takeIf(String::isNotEmpty) }.takeLast(1500))
                    }
                }.getOrNull()
                if (receipt != null) add(receipt)
            }
        }
    }

    @JvmStatic fun encode(messages: List<ChatMessage>): String = JSONArray().apply {
        for (message in messages) if (message.isLocal && message.requestId().isNotEmpty()) {
            put(JSONObject().put("id", message.requestId()).put("text", message.text())
                .put("state", message.localState().name)
                .put("images", JSONArray(message.images().map { it.mimeType() }))
                .put("imageUrls", JSONArray(message.images().map { it.url() }))
                .put("queued", message.queued()).put("queueBaseline", JSONArray(message.queueBaseline())))
        }
    }.toString()
}
