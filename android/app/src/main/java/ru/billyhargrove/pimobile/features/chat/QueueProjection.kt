package ru.billyhargrove.pimobile.features.chat

import ru.billyhargrove.pimobile.core.ChatMessage
import ru.billyhargrove.pimobile.core.TranscriptReconciler

/** Pure ordered queue consumption. ACK is acceptance; only a new canonical user echo consumes it. */
internal object QueueProjection {
    private const val BASELINE_LIMIT = 1500
    private data class User(val key: String, val message: ChatMessage)

    fun tailKeys(canonical: List<ChatMessage>): List<String> = buildList {
        for (index in (canonical.size - BASELINE_LIMIT).coerceAtLeast(0) until canonical.size)
            add(canonical[index].stableKey())
    }

    fun reconcile(canonical: List<ChatMessage>, receipts: List<ChatMessage>): List<ChatMessage> {
        if (receipts.none { it.queued() }) return receipts
        val users = canonical.mapNotNull { if (it.role() == ChatMessage.Role.USER) User(it.stableKey(), it) else null }
        val consumed = hashSetOf<String>()
        var changed: MutableList<ChatMessage>? = null
        for ((index, receipt) in receipts.withIndex()) if (receipt.queued()) {
            val baseline = receipt.queueBaseline().toCollection(linkedSetOf())
            val echo = users.firstOrNull { it.key !in baseline && it.key !in consumed && TranscriptReconciler.sameContent(it.message, receipt) }
            val next = if (echo != null) {
                consumed.add(echo.key); receipt.withLocalState(receipt.localState()).withQueue(null)
            } else {
                val keys = (baseline + consumed).toList().takeLast(BASELINE_LIMIT)
                if (keys == receipt.queueBaseline()) receipt else receipt.withLocalState(receipt.localState()).withQueue(keys)
            }
            if (next !== receipt) {
                if (changed == null) changed = receipts.toMutableList()
                changed[index] = next
            }
        }
        return changed ?: receipts
    }
}
