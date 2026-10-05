package ru.billyhargrove.pimobile.ui;

import android.content.Context;

import ru.billyhargrove.pimobile.R;
import ru.billyhargrove.pimobile.core.ChatMessage;
import ru.billyhargrove.pimobile.core.ConnectionState;
import ru.billyhargrove.pimobile.core.SessionStatus;

/** Maps protocol enums onto Russian labels and colours. */
public final class StatusUi {

    private StatusUi() {
    }

    public static String connectionLabel(Context context, ConnectionState state) {
        switch (state) {
            case CONNECTING:
                return context.getString(R.string.state_connecting);
            case CONNECTED:
                return context.getString(R.string.state_connected);
            case RECONNECTING:
                return context.getString(R.string.state_reconnecting);
            case DISCONNECTED:
                return context.getString(R.string.state_disconnected);
            case ERROR:
                return context.getString(R.string.state_error);
            case IDLE:
            default:
                return context.getString(R.string.state_idle);
        }
    }

    public static int connectionDotColor(Context context, ConnectionState state) {
        switch (state) {
            case CONNECTED:
                return context.getColor(R.color.dot_ok);
            case CONNECTING:
            case RECONNECTING:
                return context.getColor(R.color.dot_warn);
            case ERROR:
                return context.getColor(R.color.dot_error);
            case DISCONNECTED:
            case IDLE:
            default:
                return context.getColor(R.color.dot_idle);
        }
    }

    public static int sessionDotColor(Context context, SessionStatus status, boolean connected) {
        switch (status) {
            case RUNNING:
                return context.getColor(R.color.dot_ok);
            case IDLE:
                return connected ? context.getColor(R.color.accent) : context.getColor(R.color.dot_idle);
            case OFFLINE:
                return context.getColor(R.color.dot_idle);
            default:
                return context.getColor(R.color.dot_warn);
        }
    }

    public static int sessionBadgeBackground(SessionStatus status) {
        switch (status) {
            case RUNNING:
                return R.drawable.bg_badge_ok;
            case IDLE:
                return R.drawable.bg_badge_neutral;
            case OFFLINE:
                return R.drawable.bg_badge_neutral;
            default:
                return R.drawable.bg_badge_warn;
        }
    }

    public static int sessionBadgeColor(Context context, SessionStatus status) {
        switch (status) {
            case RUNNING:
                return context.getColor(R.color.success);
            case IDLE:
                return context.getColor(R.color.text_secondary);
            case OFFLINE:
                return context.getColor(R.color.neutral);
            default:
                return context.getColor(R.color.warning);
        }
    }

    public static String localStateLabel(Context context, ChatMessage.LocalState state) {
        switch (state) {
            case SENDING:
                return context.getString(R.string.local_state_sending);
            case ACCEPTED:
                return context.getString(R.string.local_state_accepted);
            case FAILED:
                return context.getString(R.string.local_state_failed);
            case UNCERTAIN:
                return context.getString(R.string.local_state_uncertain);
            default:
                return "";
        }
    }

    public static int localStateColor(Context context, ChatMessage.LocalState state) {
        switch (state) {
            case ACCEPTED:
                return context.getColor(R.color.success);
            case FAILED:
                return context.getColor(R.color.danger);
            case UNCERTAIN:
                return context.getColor(R.color.warning);
            case SENDING:
            default:
                return context.getColor(R.color.text_secondary);
        }
    }

    public static String sessionStatusLabel(Context context, SessionStatus status) {
        switch (status) {
            case RUNNING:
                return context.getString(R.string.session_running);
            case IDLE:
                return context.getString(R.string.session_idle);
            case OFFLINE:
                return context.getString(R.string.session_offline);
            default:
                return context.getString(R.string.session_unknown);
        }
    }

    public static String roleLabel(Context context, ChatMessage.Role role) {
        switch (role) {
            case USER:
                return context.getString(R.string.role_user);
            case ASSISTANT:
                return context.getString(R.string.role_assistant);
            case TOOL_RESULT:
                return context.getString(R.string.role_tool);
            case CUSTOM:
                return context.getString(R.string.role_custom);
            default:
                return context.getString(R.string.role_unknown);
        }
    }
}
