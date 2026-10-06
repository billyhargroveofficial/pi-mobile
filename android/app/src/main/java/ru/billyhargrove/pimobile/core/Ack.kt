package ru.billyhargrove.pimobile.core

class Ack(requestId: String?, sessionId: String?, ok: Boolean, error: String?) {
    private val requestId = requestId.orEmpty()
    private val sessionId = sessionId.orEmpty()
    private val ok = ok
    private val error = error.orEmpty()
    fun requestId() = requestId
    fun sessionId() = sessionId
    fun ok() = ok
    fun error() = error
    override fun toString() = "Ack{$requestId, ok=$ok, error=$error}"
}
