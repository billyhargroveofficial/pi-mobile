package ru.billyhargrove.pimobile.ui

import android.content.Context
import ru.billyhargrove.pimobile.R
import ru.billyhargrove.pimobile.core.*

object StatusUi {
    @JvmStatic fun connectionLabel(c: Context, state: ConnectionState) = c.getString(when (state) {
        ConnectionState.CONNECTING -> R.string.state_connecting; ConnectionState.CONNECTED -> R.string.state_connected
        ConnectionState.RECONNECTING -> R.string.state_reconnecting; ConnectionState.DISCONNECTED -> R.string.state_disconnected
        ConnectionState.ERROR -> R.string.state_error; else -> R.string.state_idle
    })
    @JvmStatic fun connectionDotColor(c: Context, state: ConnectionState) = c.getColor(when (state) {
        ConnectionState.CONNECTED -> R.color.dot_ok; ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> R.color.dot_warn
        ConnectionState.ERROR -> R.color.dot_error; else -> R.color.dot_idle
    })
    @JvmStatic fun sessionDotColor(c: Context, status: SessionStatus, connected: Boolean) = c.getColor(when (status) {
        SessionStatus.RUNNING -> R.color.dot_ok; SessionStatus.IDLE -> if (connected) R.color.accent else R.color.dot_idle
        SessionStatus.OFFLINE -> R.color.dot_idle; else -> R.color.dot_warn
    })
    @JvmStatic fun sessionBadgeBackground(status: SessionStatus) = when (status) {
        SessionStatus.RUNNING -> R.drawable.bg_badge_ok; SessionStatus.IDLE, SessionStatus.OFFLINE -> R.drawable.bg_badge_neutral
        else -> R.drawable.bg_badge_warn
    }
    @JvmStatic fun sessionBadgeColor(c: Context, status: SessionStatus) = c.getColor(when (status) {
        SessionStatus.RUNNING -> R.color.success; SessionStatus.IDLE -> R.color.text_secondary
        SessionStatus.OFFLINE -> R.color.neutral; else -> R.color.warning
    })
    @JvmStatic fun localStateLabel(c: Context, state: ChatMessage.LocalState): String { return c.getString(when (state) {
        ChatMessage.LocalState.SENDING -> R.string.local_state_sending; ChatMessage.LocalState.ACCEPTED -> R.string.local_state_accepted
        ChatMessage.LocalState.FAILED -> R.string.local_state_failed; ChatMessage.LocalState.UNCERTAIN -> R.string.local_state_uncertain
        else -> return ""
    }) }
    @JvmStatic fun localStateColor(c: Context, state: ChatMessage.LocalState) = c.getColor(when (state) {
        ChatMessage.LocalState.ACCEPTED -> R.color.success; ChatMessage.LocalState.FAILED -> R.color.danger
        ChatMessage.LocalState.UNCERTAIN -> R.color.warning; else -> R.color.text_secondary
    })
    @JvmStatic fun sessionStatusLabel(c: Context, status: SessionStatus) = c.getString(when (status) {
        SessionStatus.RUNNING -> R.string.session_running; SessionStatus.IDLE -> R.string.session_idle
        SessionStatus.OFFLINE -> R.string.session_offline; else -> R.string.session_unknown
    })
    @JvmStatic fun roleLabel(c: Context, role: ChatMessage.Role) = c.getString(when (role) {
        ChatMessage.Role.USER -> R.string.role_user; ChatMessage.Role.ASSISTANT -> R.string.role_assistant
        ChatMessage.Role.TOOL_RESULT -> R.string.role_tool; ChatMessage.Role.CUSTOM -> R.string.role_custom
        else -> R.string.role_unknown
    })
}
