package ru.billyhargrove.pimobile.core;

/** Result of a client command, as carried by {type:'ack'}. */
public final class Ack {

    private final String requestId;
    private final String sessionId;
    private final boolean ok;
    private final String error;

    public Ack(String requestId, String sessionId, boolean ok, String error) {
        this.requestId = requestId == null ? "" : requestId;
        this.sessionId = sessionId == null ? "" : sessionId;
        this.ok = ok;
        this.error = error == null ? "" : error;
    }

    public String requestId() {
        return requestId;
    }

    public String sessionId() {
        return sessionId;
    }

    public boolean ok() {
        return ok;
    }

    public String error() {
        return error;
    }

    @Override
    public String toString() {
        return "Ack{" + requestId + ", ok=" + ok + ", error=" + error + "}";
    }
}
