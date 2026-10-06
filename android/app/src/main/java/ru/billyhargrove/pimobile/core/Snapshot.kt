package ru.billyhargrove.pimobile.core

/** A snapshot replaces this session's canonical transcript. */
class Snapshot(sessionId: String?, status: SessionStatus?, private val connected: Boolean,
    messages: List<ChatMessage>?, private val truncated: Boolean) {
    private val sessionId = sessionId.orEmpty()
    private val status = status ?: SessionStatus.UNKNOWN
    private val messages = java.util.Collections.unmodifiableList(ArrayList(messages.orEmpty()))
    fun sessionId() = sessionId
    fun status() = status
    fun connected() = connected
    fun messages(): List<ChatMessage> = messages
    fun truncated() = truncated
}
