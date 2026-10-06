package ru.billyhargrove.pimobile.core

/** Presence flags prevent old gateways from resetting omitted state. */
class MessagesUpdate(sessionId: String?, messages: List<ChatMessage>?, removedIds: List<String>?,
    status: SessionStatus?, private val statusPresent: Boolean, private val connected: Boolean,
    private val connectedPresent: Boolean, private val truncated: Boolean, private val truncatedPresent: Boolean) {
    private val sessionId = sessionId.orEmpty()
    private val status = status ?: SessionStatus.UNKNOWN
    private val messages = java.util.Collections.unmodifiableList(ArrayList(messages.orEmpty()))
    private val removedIds = java.util.Collections.unmodifiableList(ArrayList(removedIds.orEmpty()))
    fun sessionId() = sessionId
    fun messages(): List<ChatMessage> = messages
    fun removedIds(): List<String> = removedIds
    fun status() = status
    fun hasStatus() = statusPresent
    fun connected() = connected
    fun hasConnected() = connectedPresent
    fun truncated() = truncated
    fun hasTruncated() = truncatedPresent
    fun isEmpty() = messages.isEmpty() && removedIds.isEmpty()
}
