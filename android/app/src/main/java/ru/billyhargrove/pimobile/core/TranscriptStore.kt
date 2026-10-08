package ru.billyhargrove.pimobile.core

/** One canonical ordered map; replacing an existing row never moves its position. */
class TranscriptStore {
    class ChangeSet(private val transcript: List<ChatMessage>, private val added: Int, private val updated: Int,
        private val removed: Int, private val tailTouched: Boolean) {
        fun transcript() = transcript
        fun added() = added
        fun updated() = updated
        fun removed() = removed
        fun appendedOnly() = added > 0 && updated == 0 && removed == 0
        fun tailTouched() = tailTouched
        val isEmpty: Boolean get() = added == 0 && updated == 0 && removed == 0
    }
    private val byKey = linkedMapOf<String, ChatMessage>()
    private var revision = 0L
    private var anonymousSequence = 0
    private var userCount = 0
    val hasUserContext: Boolean get() = userCount > 0
    fun replaceAll(messages: List<ChatMessage?>?): ChangeSet {
        val oldSize = byKey.size
        byKey.clear(); anonymousSequence = 0; userCount = 0
        for (message in messages.orEmpty()) if (message != null) put(uniqueKey(message), message)
        revision++
        return ChangeSet(transcript(), byKey.size, 0, oldSize, byKey.isNotEmpty())
    }
    fun apply(changed: List<ChatMessage?>?, removedIds: List<String?>?): ChangeSet {
        var added = 0; var updated = 0; var removed = 0
        val touched = hashSetOf<String>()
        for (id in removedIds.orEmpty()) if (!id.isNullOrEmpty()) byKey.remove(id)?.let { userCount -= user(it); removed++ }
        for (message in changed.orEmpty()) {
            if (message == null) continue
            val key = message.id().ifEmpty { message.stableKey() }
            val existing = byKey[key]
            if (existing != null && (message.id().isNotEmpty() || existing.id().isEmpty())) {
                if (existing != message) { put(key, message); updated++; touched.add(key) }
            } else {
                val unique = uniqueKey(message)
                put(unique, message); added++; touched.add(unique)
            }
        }
        if (added > 0 || updated > 0 || removed > 0) revision++
        return ChangeSet(transcript(), added, updated, removed, byKey.keys.lastOrNull() in touched)
    }
    fun prepend(older: List<ChatMessage?>) {
        val merged = linkedMapOf<String, ChatMessage>()
        for (message in older) if (message != null) merged[message.stableKey()] = message
        merged.putAll(byKey); byKey.clear(); byKey.putAll(merged); revision++
        userCount = byKey.values.sumOf { user(it) }
    }
    fun transcript(): List<ChatMessage> = ArrayList(byKey.values)
    fun size() = byKey.size
    val isEmpty: Boolean get() = byKey.isEmpty()
    fun revision() = revision
    fun byId(id: String?): ChatMessage? = if (id.isNullOrEmpty()) null else byKey[id]
    fun clear() { byKey.clear(); anonymousSequence = 0; userCount = 0; revision++ }
    private fun user(message: ChatMessage?) = if (message?.role() == ChatMessage.Role.USER) 1 else 0
    private fun put(key: String, message: ChatMessage) { userCount += user(message) - user(byKey.put(key, message)) }
    private fun uniqueKey(message: ChatMessage): String {
        if (message.id().isNotEmpty()) return message.id()
        val base = message.stableKey()
        if (base !in byKey) return base
        var candidate: String
        do { candidate = "$base#${++anonymousSequence}" } while (candidate in byKey)
        return candidate
    }
    fun hasUniqueIds() = byKey.values.filter { it.id().isNotEmpty() }.map { it.id() }.let { it.size == it.toSet().size }
}
