package ru.billyhargrove.pimobile.features.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import ru.billyhargrove.pimobile.core.ChatMessage
import ru.billyhargrove.pimobile.core.SessionStatus
import ru.billyhargrove.pimobile.core.TranscriptPresentation
import ru.billyhargrove.pimobile.core.TranscriptStore
import java.util.Collections

/** Canonical transcript and shared projection. Request routing, wire scope and actual scrolling remain outside. */
internal class TranscriptSession(
    private val overlay: (List<ChatMessage>) -> List<ChatMessage>,
    private val checkpoint: () -> Unit,
    private val published: (List<TranscriptPresentation.Item>, List<TranscriptPresentation.Item>, Boolean, Boolean) -> Unit,
    private val rendered: (Boolean, Boolean) -> Unit
) {
    private val store = TranscriptStore()
    private val presentation = TranscriptPresentation()
    private var canonical: List<ChatMessage> = emptyList()
    var items by mutableStateOf<List<TranscriptPresentation.Item>>(emptyList()); private set
    val hasUserContext get() = store.hasUserContext

    fun status(value: SessionStatus) { presentation.sessionStatus(value); items = presentation.items() }
    fun metadata(value: JSONObject, animateUpdates: Boolean) {
        presentation.metadata(value)
        publish(animateUpdates, false)
    }
    fun toggle(group: String) { presentation.toggle(group); items = presentation.items() }
    fun snapshot(messages: List<ChatMessage>, status: SessionStatus, resetMetadata: JSONObject?,
        animateUpdates: Boolean, animateAdded: Boolean) {
        if (resetMetadata != null) presentation.reset()
        val change = store.replaceAll(messages)
        canonical = Collections.unmodifiableList(change.transcript())
        render(change, animateUpdates, animateAdded, status, resetMetadata)
    }
    fun messages(messages: List<ChatMessage>, removed: List<String>, status: SessionStatus, animateUpdates: Boolean) {
        val change = store.apply(messages, removed)
        canonical = Collections.unmodifiableList(change.transcript())
        render(change, animateUpdates, true, status)
    }
    fun prepend(messages: List<ChatMessage>, metadata: JSONObject) {
        store.prepend(messages)
        canonical = Collections.unmodifiableList(store.transcript())
        render(metadata = metadata)
    }
    fun render() = render(null, false, false)
    private fun render(change: TranscriptStore.ChangeSet? = null, animateUpdates: Boolean = false,
        animateAdded: Boolean = false, status: SessionStatus? = null, metadata: JSONObject? = null) {
        presentation.update(overlay(canonical), status, metadata)
        // Preserve the existing receipt/checkpoint boundary before any visible publication.
        checkpoint()
        publish(animateUpdates, animateAdded)
        rendered(items.isNotEmpty(), change?.tailTouched() == true)
    }
    private fun publish(animateUpdates: Boolean, animateAdded: Boolean) {
        val next = presentation.items()
        published(items, next, animateUpdates, animateAdded)
        items = next
    }
}
