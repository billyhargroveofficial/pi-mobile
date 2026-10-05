package ru.billyhargrove.pimobile.core;

import java.util.Objects;

/** A live (or recently live) Pi session as reported by GET /api/catalog. */
public final class Session {

    private final String id;
    private final String title;
    private final String cwd;
    private final String workspaceId;
    private final String terminalId;
    private final boolean connected;
    private final SessionStatus status;
    private final String model;

    public Session(String id,
                   String title,
                   String cwd,
                   String workspaceId,
                   String terminalId,
                   boolean connected,
                   SessionStatus status,
                   String model) {
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
        this.cwd = cwd == null ? "" : cwd;
        this.workspaceId = workspaceId == null ? "" : workspaceId;
        this.terminalId = terminalId == null ? "" : terminalId;
        this.connected = connected;
        this.status = status == null ? SessionStatus.UNKNOWN : status;
        this.model = model == null ? "" : model;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String cwd() {
        return cwd;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public String terminalId() {
        return terminalId;
    }

    public boolean connected() {
        return connected;
    }

    public SessionStatus status() {
        return status;
    }

    public String model() {
        return model;
    }

    public boolean hasWorkspace() {
        return !workspaceId.isEmpty();
    }

    public String displayTitle() {
        if (!title.isEmpty()) {
            return title;
        }
        return id.isEmpty() ? "Сессия" : id;
    }

    /** Sort key inside a group: running first, then idle, then everything else. */
    public int sortRank() {
        switch (status) {
            case RUNNING:
                return 0;
            case IDLE:
                return 1;
            case OFFLINE:
                return 2;
            default:
                return 3;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Session)) {
            return false;
        }
        Session other = (Session) o;
        return connected == other.connected
                && id.equals(other.id)
                && title.equals(other.title)
                && cwd.equals(other.cwd)
                && workspaceId.equals(other.workspaceId)
                && terminalId.equals(other.terminalId)
                && status == other.status
                && model.equals(other.model);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, cwd, workspaceId, terminalId, connected, status, model);
    }

    @Override
    public String toString() {
        return "Session{" + id + ", " + title + ", " + status + "}";
    }
}
