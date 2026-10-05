package ru.billyhargrove.pimobile.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A transcript snapshot. A snapshot always REPLACES the visible transcript for
 * its session id; it is never appended to blindly.
 */
public final class Snapshot {

    private final String sessionId;
    private final SessionStatus status;
    private final boolean connected;
    private final List<ChatMessage> messages;
    private final boolean truncated;

    public Snapshot(String sessionId,
                    SessionStatus status,
                    boolean connected,
                    List<ChatMessage> messages,
                    boolean truncated) {
        this.sessionId = sessionId == null ? "" : sessionId;
        this.status = status == null ? SessionStatus.UNKNOWN : status;
        this.connected = connected;
        this.messages = Collections.unmodifiableList(
                messages == null ? new ArrayList<ChatMessage>() : new ArrayList<>(messages));
        this.truncated = truncated;
    }

    public String sessionId() {
        return sessionId;
    }

    public SessionStatus status() {
        return status;
    }

    public boolean connected() {
        return connected;
    }

    public List<ChatMessage> messages() {
        return messages;
    }

    /** True when the gateway dropped older messages (max recent 200). */
    public boolean truncated() {
        return truncated;
    }
}
