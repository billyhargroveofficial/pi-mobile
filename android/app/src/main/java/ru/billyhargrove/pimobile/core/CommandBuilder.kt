package ru.billyhargrove.pimobile.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Base64

/** Fresh request identities are supplied by the owner; no retries occur here. */
object CommandBuilder {
    enum class Behavior(private val wire: String) {
        FOLLOW_UP("followUp"), STEER("steer");
        fun wire() = wire
    }
    @JvmStatic fun subscribe(sessionId: String?): JSONObject {
        requireId(sessionId, "sessionId")
        return frame("Could not build subscription") { put("type", "subscribe").put("sessionId", sessionId) }
    }
    @JvmStatic fun promptCommand(sessionId: String?, requestId: String?, text: String?,
        images: List<ImagePayload?>?, behavior: Behavior?): JSONObject {
        requireId(sessionId, "sessionId"); requireId(requestId, "requestId")
        if (images != null) {
            require(images.size <= 3 && ImageGuard.totalBytes(images) <= ImageGuard.MAX_TOTAL_BYTES) {
                "Up to 3 attachments, totaling no more than 10 MB"
            }
            for (payload in images) {
                if (payload != null && payload.isFile) {
                    val name = payload.fileName().orEmpty()
                    require(payload.size() > 0 && name.isNotBlank() && name.length <= 200 &&
                        name.none { it == '/' || it == '\\' || it.code in 0..31 }) { "Invalid file" }
                } else ImageGuard.validate(listOf(payload))
            }
        }
        return frame("Could not build prompt command") {
            put("type", "command").put("sessionId", sessionId).put("requestId", requestId)
                .put("command", "prompt").put("text", text.orEmpty())
            if (!images.isNullOrEmpty()) {
                val encodedImages = JSONArray(); val files = JSONArray()
                for (nullable in images) {
                    val payload = requireNotNull(nullable)
                    val item = JSONObject().put("data", Base64.getEncoder().encodeToString(payload.bytes()))
                    if (payload.isFile) files.put(item.put("name", payload.fileName()))
                    else encodedImages.put(item.put("type", "image").put("mimeType", payload.mimeType()))
                }
                put("images", encodedImages)
                if (files.length() > 0) put("files", files)
            }
            put("behavior", (behavior ?: Behavior.FOLLOW_UP).wire())
        }
    }
    @JvmStatic fun abortCommand(sessionId: String?, requestId: String?): JSONObject {
        requireId(sessionId, "sessionId"); requireId(requestId, "requestId")
        return frame("Could not build abort command") {
            put("type", "command").put("sessionId", sessionId).put("requestId", requestId).put("command", "abort")
        }
    }
    @JvmStatic fun frameType(json: String?): String = if (json.isNullOrBlank()) "" else
        try { JSONObject(json).optString("type", "") } catch (_: JSONException) { "" }
    private fun frame(error: String, body: JSONObject.() -> Unit): JSONObject =
        try { JSONObject().apply(body) } catch (cause: JSONException) { throw IllegalStateException(error, cause) }
    private fun requireId(value: String?, name: String) { require(!value.isNullOrBlank()) { "Empty $name" } }
}
