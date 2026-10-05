package ru.billyhargrove.pimobile.core;

import java.util.Objects;

/**
 * An Orca terminal that is not bridged to Pi yet. Such entries are strictly
 * read-only in the app and always labelled "extension required".
 */
public final class TerminalInfo {

    private final String id;
    private final String title;
    private final String workspaceId;
    private final String agent;
    private final boolean connected;

    public TerminalInfo(String id, String title, String workspaceId, String agent, boolean connected) {
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
        this.workspaceId = workspaceId == null ? "" : workspaceId;
        this.agent = agent == null ? "" : agent;
        this.connected = connected;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public String agent() {
        return agent;
    }

    public boolean connected() {
        return connected;
    }

    public String displayTitle() {
        if (!title.isEmpty()) {
            return title;
        }
        return id.isEmpty() ? "Terminal" : id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TerminalInfo)) {
            return false;
        }
        TerminalInfo other = (TerminalInfo) o;
        return connected == other.connected
                && id.equals(other.id)
                && title.equals(other.title)
                && workspaceId.equals(other.workspaceId)
                && agent.equals(other.agent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, workspaceId, agent, connected);
    }
}
