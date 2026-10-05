package ru.billyhargrove.pimobile.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One transcript entry.
 *
 * <p>Messages parsed from a snapshot are {@link LocalState#NONE}. Messages the
 * user typed locally carry a {@link LocalState} and a request id so the UI can
 * tell "accepted by the gateway" from "delivered to the agent" and can avoid
 * replaying uncertain commands.</p>
 */
public final class ChatMessage {

    public enum Role {
        USER,
        ASSISTANT,
        TOOL_RESULT,
        CUSTOM,
        UNKNOWN;

        public static Role parse(String raw) {
            if (raw == null) {
                return UNKNOWN;
            }
            String v = raw.trim().toLowerCase(Locale.ROOT);
            if (v.equals("user")) {
                return USER;
            }
            if (v.equals("assistant")) {
                return ASSISTANT;
            }
            if (v.equals("toolresult") || v.equals("tool_result") || v.equals("tool")) {
                return TOOL_RESULT;
            }
            if (v.equals("custom")) {
                return CUSTOM;
            }
            return UNKNOWN;
        }
    }

    /**
     * Lifecycle of a locally authored message.
     * <ul>
     *   <li>{@code SENDING} – written to the socket, ack not seen yet.</li>
     *   <li>{@code ACCEPTED} – gateway answered {@code ok:true}.</li>
     *   <li>{@code FAILED} – gateway answered {@code ok:false}.</li>
     *   <li>{@code UNCERTAIN} – no ack before the timeout; never auto-resend.</li>
     * </ul>
     */
    public enum LocalState {
        NONE,
        SENDING,
        ACCEPTED,
        FAILED,
        UNCERTAIN
    }

    private final String id;
    private final Role role;
    private final String text;
    private final List<ImageRef> images;
    private final String toolName;
    private final String toolStatus;
    private final LocalState localState;
    private final String requestId;

    public ChatMessage(String id,
                       Role role,
                       String text,
                       List<ImageRef> images,
                       String toolName,
                       LocalState localState,
                       String requestId) {
        this(id,role,text,images,toolName,localState,requestId,"done");
    }

    public ChatMessage(String id, Role role, String text, List<ImageRef> images, String toolName, LocalState localState, String requestId, String toolStatus) {
        this.toolStatus = toolStatus == null ? "done" : toolStatus;
        this.id = id == null ? "" : id;
        this.role = role == null ? Role.UNKNOWN : role;
        this.text = text == null ? "" : text;
        this.images = Collections.unmodifiableList(
                images == null ? new ArrayList<ImageRef>() : new ArrayList<>(images));
        this.toolName = toolName == null ? "" : toolName;
        this.localState = localState == null ? LocalState.NONE : localState;
        this.requestId = requestId == null ? "" : requestId;
    }

    public static ChatMessage remote(String id, Role role, String text, List<ImageRef> images, String toolName) {
        return new ChatMessage(id, role, text, images, toolName, LocalState.NONE, "");
    }

    public static ChatMessage local(String requestId, String text, List<ImageRef> images, LocalState state) {
        return new ChatMessage("local:" + requestId, Role.USER, text, images, null, state, requestId);
    }

    public String id() {
        return id;
    }

    public Role role() {
        return role;
    }

    public String text() {
        return text;
    }

    public List<ImageRef> images() {
        return images;
    }

    public String toolName() {
        return toolName;
    }

    public String toolStatus() { return toolStatus; }

    public LocalState localState() {
        return localState;
    }

    public String requestId() {
        return requestId;
    }

    public boolean isLocal() {
        return localState != LocalState.NONE;
    }

    public boolean hasImages() {
        return !images.isEmpty();
    }

    public ChatMessage withLocalState(LocalState state) {
        return new ChatMessage(id, role, text, images, toolName, state, requestId, toolStatus);
    }

    /** Stable identity for diffing the transcript inside the adapter. */
    public String stableKey() {
        if (!id.isEmpty()) {
            return id;
        }
        return role + ":" + Integer.toHexString(text.hashCode());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChatMessage)) {
            return false;
        }
        ChatMessage other = (ChatMessage) o;
        return id.equals(other.id)
                && role == other.role
                && text.equals(other.text)
                && images.equals(other.images)
                && toolName.equals(other.toolName)
                && toolStatus.equals(other.toolStatus)
                && localState == other.localState
                && requestId.equals(other.requestId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, role, text, images, toolName, toolStatus, localState, requestId);
    }

    @Override
    public String toString() {
        return "ChatMessage{" + id + ", " + role + ", " + localState + ", " + text.length() + " chars}";
    }
}
