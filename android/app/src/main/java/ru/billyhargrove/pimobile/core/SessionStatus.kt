package ru.billyhargrove.pimobile.core

enum class SessionStatus {
    IDLE, RUNNING, OFFLINE, UNKNOWN;
    fun wire() = name.lowercase(java.util.Locale.ROOT)
    companion object {
        @JvmStatic fun parse(raw: String?) = when (raw?.trim()?.lowercase(java.util.Locale.ROOT)) {
            "idle" -> IDLE; "running" -> RUNNING; "offline" -> OFFLINE; else -> UNKNOWN
        }
    }
}
