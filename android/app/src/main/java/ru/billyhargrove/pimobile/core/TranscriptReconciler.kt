package ru.billyhargrove.pimobile.core

/** Consume each canonical echo once; explicitly rejected receipts remain recoverable. */
object TranscriptReconciler {
    private val resizeMetadata = Regex("(?:\\s*\\[Image: original \\d+x\\d+, displayed at \\d+x\\d+\\. Multiply coordinates by [0-9.]+ to map to original image\\.\\])+$")
    @JvmStatic fun merge(baseMessages: List<ChatMessage>?, localMessages: List<ChatMessage>?): List<ChatMessage> {
        val result = ArrayList(baseMessages.orEmpty())
        val available = baseMessages.orEmpty().filter { it.role() == ChatMessage.Role.USER }.groupingBy(::key).eachCount().toMutableMap()
        for (local in localMessages.orEmpty()) {
            val key = key(local); val count = available[key] ?: 0
            if (local.localState() == ChatMessage.LocalState.FAILED || count == 0) result.add(local)
            else available[key] = count - 1
        }
        return result
    }
    @JvmStatic fun isUncertain(message: ChatMessage?) = message != null &&
        message.localState() in setOf(ChatMessage.LocalState.SENDING, ChatMessage.LocalState.UNCERTAIN)
    private fun key(message: ChatMessage): String {
        var text = message.text()
        if (message.hasImages()) {
            text = resizeMetadata.replace(text, "").replace(Regex("\\s+$"), "")
            if (text == "Посмотри изображение") text = ""
        }
        return "${message.images().size}:$text"
    }
}
