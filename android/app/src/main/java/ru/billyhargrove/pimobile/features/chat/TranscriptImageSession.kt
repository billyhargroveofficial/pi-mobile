package ru.billyhargrove.pimobile.features.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** One image binding. Opaque pixels and an injected loader keep lifecycle policy platform-free. */
internal class TranscriptImageSession<B : Any>(private val local: Boolean, private val load: ((B?, String) -> Unit) -> Unit) {
    data class State<B>(val bitmap: B? = null, val error: String = "")
    var state by mutableStateOf(State<B>()); private set
    private var started = false
    private var completed = false
    private var closed = false

    fun start() {
        if (closed || started || local) return
        // Reserve before calling the port: cache hits may complete synchronously.
        started = true
        try { load(::accept) }
        catch (error: Exception) { accept(null, error.message ?: "Could not load image") }
    }
    private fun accept(bitmap: B?, error: String) {
        if (closed || completed) return
        completed = true
        state = State(bitmap, if (bitmap == null) error else "")
    }
    fun close() {
        closed = true
        // Retire callbacks and release this binding's reference, without recycling shared cache bytes.
        state = State()
    }
}
