package ru.billyhargrove.pimobile.store

import android.content.Context
import ru.billyhargrove.pimobile.core.ChatMessage

/** Existing private, host/session-scoped text receipts; never an automatic replay queue. */
class PendingMessages(context: Context, host: String, session: String) {
    private val preferences = context.getSharedPreferences("mobile-outbox", Context.MODE_PRIVATE)
    private val key = "$host|$session"
    private var last = ""

    fun load(): List<ChatMessage> = PendingMessageCodec.decode(preferences.getString(key, "[]") ?: "[]")

    fun save(messages: List<ChatMessage>) {
        val value = PendingMessageCodec.encode(messages)
        if (value == last) return
        last = value
        preferences.edit().apply {
            if (value == "[]") remove(key) else putString(key, value)
        }.apply()
    }
}
