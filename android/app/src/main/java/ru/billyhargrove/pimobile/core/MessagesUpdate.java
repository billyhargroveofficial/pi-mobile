package ru.billyhargrove.pimobile.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Incremental transcript frame:
 * {@code {type:'messages',sessionId,messages:[new or changed ChatMessage],removedIds:[id],status,connected,truncated}}.
 *
 * <p>The gateway sends a full snapshot on every subscribe/reconnect and only
 * changed messages afterwards. A frame carries new messages, changed messages
 * (same {@code id}) and ids that must be dropped.</p>
 *
 * <p>{@code status}, {@code connected} and {@code truncated} are tracked together
 * with their presence so an older gateway that omits one of them cannot silently
 * reset the state the UI already knows.</p>
 */
public final class MessagesUpdate {

    private final String sessionId;
    private final List<ChatMessage> messages;
    private final List<String> removedIds;
    private final SessionStatus status;
    private final boolean statusPresent;
    private final boolean connected;
    private final boolean connectedPresent;
    private final boolean truncated;
    private final boolean truncatedPresent;

    public MessagesUpdate(String sessionId,
                          List<ChatMessage> messages,
                          List<String> removedIds,
                          SessionStatus status,
                          boolean statusPresent,
                          boolean connected,
                          boolean connectedPresent,
                          boolean truncated,
                          boolean truncatedPresent) {
        this.sessionId = sessionId == null ? "" : sessionId;
        this.messages = Collections.unmodifiableList(
                messages == null ? new ArrayList<ChatMessage>() : new ArrayList<>(messages));
        this.removedIds = Collections.unmodifiableList(
                removedIds == null ? new ArrayList<String>() : new ArrayList<>(removedIds));
        this.status = status == null ? SessionStatus.UNKNOWN : status;
        this.statusPresent = statusPresent;
        this.connected = connected;
        this.connectedPresent = connectedPresent;
        this.truncated = truncated;
        this.truncatedPresent = truncatedPresent;
    }

    public String sessionId() {
        return sessionId;
    }

    public List<ChatMessage> messages() {
        return messages;
    }

    public List<String> removedIds() {
        return removedIds;
    }

    public SessionStatus status() {
        return status;
    }

    public boolean hasStatus() {
        return statusPresent;
    }

    public boolean connected() {
        return connected;
    }

    public boolean hasConnected() {
        return connectedPresent;
    }

    public boolean truncated() {
        return truncated;
    }

    public boolean hasTruncated() {
        return truncatedPresent;
    }

    public boolean isEmpty() {
        return messages.isEmpty() && removedIds.isEmpty();
    }
}
