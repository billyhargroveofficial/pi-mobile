package ru.billyhargrove.pimobile.features.chat

import ru.billyhargrove.pimobile.core.Ack
import ru.billyhargrove.pimobile.core.ChatMessage
import ru.billyhargrove.pimobile.core.CommandBuilder
import ru.billyhargrove.pimobile.core.ImageGuard
import ru.billyhargrove.pimobile.core.ImagePayload
import ru.billyhargrove.pimobile.core.ImageRef
import ru.billyhargrove.pimobile.core.TranscriptReconciler
import ru.billyhargrove.pimobile.media.Attachment

/**
 * Main-thread owner of one session's optimistic receipts and retained draft bytes.
 * Only send and explicitly requested retry can call the sender. A socket write
 * creates a SENDING receipt; ACK means acceptance, never task completion.
 * Persistence contains text receipts only, so reentry never replays a command.
 */
class ChatOutbox(
    private val sessionId: String,
    private val readOnly: Boolean,
    private val sender: PromptSender,
    initialReceipts: List<ChatMessage>
) {
    fun interface PromptSender {
        fun send(sessionId: String, text: String, payloads: List<ImagePayload>, behavior: CommandBuilder.Behavior): String?
    }

    class Draft internal constructor(
        val text: String,
        attachments: List<Attachment>,
        val behavior: CommandBuilder.Behavior
    ) {
        val attachments: List<Attachment> = attachments.toList()
        internal fun payloads() = attachments.map { it.payload() }
    }

    enum class SendStatus { WRITTEN, NO_CONNECTION, EMPTY, READ_ONLY, MISSING_DRAFT, NOT_RETRYABLE }
    data class SendResult(val status: SendStatus, val requestId: String = "")
    data class AckResult(val handled: Boolean, val rejectedText: String? = null)
    enum class RestoreStatus { RESTORED, REATTACH_REQUIRED, COMPOSER_OCCUPIED, UNKNOWN, READ_ONLY }
    data class RestoreResult(val status: RestoreStatus, val text: String = "", val draft: Draft? = null)

    private val receipts = linkedMapOf<String, ChatMessage>()
    private val drafts = linkedMapOf<String, Draft>()
    private var canonicalKeys = emptyList<String>()
    private val restoredQueue = initialReceipts.filter { it.queued() }.mapTo(hashSetOf()) { it.requestId() }

    init {
        for (receipt in initialReceipts) {
            if (!receipt.isLocal || receipt.requestId().isEmpty()) continue
            receipts[receipt.requestId()] = if (receipt.localState() == ChatMessage.LocalState.SENDING)
                receipt.withLocalState(ChatMessage.LocalState.UNCERTAIN) else receipt
        }
    }

    fun send(text: String, attachments: List<Attachment>, behavior: CommandBuilder.Behavior, queued: Boolean = false): SendResult {
        if (readOnly) return SendResult(SendStatus.READ_ONLY)
        if (text.isBlank() && attachments.isEmpty()) return SendResult(SendStatus.EMPTY)
        return write(Draft(text, attachments, behavior), queued && behavior == CommandBuilder.Behavior.FOLLOW_UP)
    }

    fun retry(requestId: String): SendResult {
        if (readOnly) return SendResult(SendStatus.READ_ONLY)
        val receipt = receipts[requestId] ?: return SendResult(SendStatus.MISSING_DRAFT)
        if (receipt.localState() != ChatMessage.LocalState.FAILED && receipt.localState() != ChatMessage.LocalState.UNCERTAIN)
            return SendResult(SendStatus.NOT_RETRYABLE)
        val draft = drafts[requestId] ?: return SendResult(SendStatus.MISSING_DRAFT)
        // The original receipt remains visible so a manual repeat cannot imply
        // that the original uncertain command was cancelled or never accepted.
        return write(draft)
    }

    private fun write(draft: Draft, queued: Boolean = false): SendResult {
        val requestId = sender.send(sessionId, draft.text, draft.payloads(), draft.behavior)
            ?.takeIf { it.isNotEmpty() } ?: return SendResult(SendStatus.NO_CONNECTION)
        drafts[requestId] = draft
        val text = buildString {
            append(draft.text)
            for (attachment in draft.attachments) if (attachment.payload().isFile) {
                if (isNotEmpty()) append('\n')
                append("📎 ").append(attachment.payload().fileName())
            }
        }
        // Keep source indices: an image after a file still uses that image's
        // thumbnail, not the file's null thumbnail.
        val images = draft.attachments.mapIndexedNotNull { index, attachment ->
            val payload = attachment.payload()
            if (!payload.isFile && ImageGuard.isAllowedMimeType(payload.mimeType()))
                ImageRef("local:$index", payload.mimeType()) else null
        }
        receipts[requestId] = ChatMessage.local(requestId, text, images, ChatMessage.LocalState.SENDING)
            .withQueue(if (queued) canonicalKeys else null)
        return SendResult(SendStatus.WRITTEN, requestId)
    }

    fun acknowledge(ack: Ack): AckResult {
        if (ack.sessionId() != sessionId) return AckResult(false)
        val receipt = receipts[ack.requestId()] ?: return AckResult(false)
        if (receipt.localState() != ChatMessage.LocalState.SENDING && receipt.localState() != ChatMessage.LocalState.UNCERTAIN)
            return AckResult(false)
        receipts[ack.requestId()] = receipt.withLocalState(
            if (ack.ok()) ChatMessage.LocalState.ACCEPTED else ChatMessage.LocalState.FAILED
        ).apply { if (!ack.ok()) withQueue(null) }
        return AckResult(true, if (ack.ok()) null else drafts[ack.requestId()]?.text)
    }

    fun uncertain(requestId: String, eventSessionId: String): Boolean {
        if (eventSessionId != sessionId) return false
        val receipt = receipts[requestId] ?: return false
        if (receipt.localState() != ChatMessage.LocalState.SENDING) return false
        receipts[requestId] = receipt.withLocalState(ChatMessage.LocalState.UNCERTAIN)
        return true
    }

    fun restore(requestId: String, composerOccupied: Boolean): RestoreResult {
        if (readOnly) return RestoreResult(RestoreStatus.READ_ONLY)
        val receipt = receipts[requestId] ?: return RestoreResult(RestoreStatus.UNKNOWN)
        if (composerOccupied) return RestoreResult(RestoreStatus.COMPOSER_OCCUPIED)
        if (receipt.localState() != ChatMessage.LocalState.FAILED && receipt.localState() != ChatMessage.LocalState.UNCERTAIN)
            return RestoreResult(RestoreStatus.UNKNOWN)
        val draft = drafts.remove(requestId)
        receipts.remove(requestId)
        return if (draft == null) RestoreResult(RestoreStatus.REATTACH_REQUIRED, receipt.text())
        else RestoreResult(RestoreStatus.RESTORED, draft.text, draft)
    }

    fun thumbnail(requestId: String, sourceIndex: Int) =
        drafts[requestId]?.attachments?.getOrNull(sourceIndex)?.thumbnail()

    fun localReceipts(): List<ChatMessage> = receipts.values.toList()
    fun queuedMessages(): List<ChatMessage> = receipts.values.filter { it.queued() }
    fun queueRestored(requestId: String) = requestId in restoredQueue

    /** Canonical echo wins. Keep pending ACK identity even if its bubble is hidden. */
    fun reconcile(canonical: List<ChatMessage>): List<ChatMessage> {
        canonicalKeys = canonical.map { it.stableKey() }.takeLast(1500)
        // Each NEW user echo consumes one queued request. An old identical prompt
        // or a repeated snapshot cannot eat another entry. ACK is not consumption.
        val consumed = hashSetOf<String>()
        for ((id, receipt) in receipts) if (receipt.queued()) {
            val baseline = receipt.queueBaseline().toCollection(linkedSetOf())
            val echo = canonical.firstOrNull { it.role() == ChatMessage.Role.USER && it.stableKey() !in baseline &&
                it.stableKey() !in consumed && TranscriptReconciler.sameContent(it, receipt) }
            if (echo != null) { consumed.add(echo.stableKey()); receipts[id] = receipt.withLocalState(receipt.localState()).withQueue(null) }
            else receipts[id] = receipt.withLocalState(receipt.localState()).withQueue((baseline + consumed).toList().takeLast(1500))
        }
        val pendingQueue = queuedMessages().mapTo(hashSetOf()) { it.requestId() }
        val merged = TranscriptReconciler.merge(canonical, localReceipts().filter { it.requestId() !in pendingQueue })
        val visible = merged.filter { it.isLocal }.mapTo(hashSetOf()) { it.requestId() }.apply { addAll(pendingQueue) }
        receipts.entries.removeAll { (id, receipt) ->
            receipt.localState() != ChatMessage.LocalState.SENDING && id !in visible
        }
        drafts.keys.retainAll(receipts.keys)
        return merged
    }
}
