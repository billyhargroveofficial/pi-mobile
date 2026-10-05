package ru.billyhargrove.pimobile.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable catalog snapshot: workspaces, sessions and unbridged terminals. */
public final class Catalog {

    private final List<Workspace> workspaces;
    private final List<Session> sessions;
    private final List<TerminalInfo> terminals;

    public Catalog(List<Workspace> workspaces, List<Session> sessions, List<TerminalInfo> terminals) {
        this.workspaces = Collections.unmodifiableList(
                workspaces == null ? new ArrayList<Workspace>() : new ArrayList<>(workspaces));
        this.sessions = Collections.unmodifiableList(
                sessions == null ? new ArrayList<Session>() : new ArrayList<>(sessions));
        this.terminals = Collections.unmodifiableList(
                terminals == null ? new ArrayList<TerminalInfo>() : new ArrayList<>(terminals));
    }

    public static Catalog empty() {
        return new Catalog(null, null, null);
    }

    public List<Workspace> workspaces() {
        return workspaces;
    }

    public List<Session> sessions() {
        return sessions;
    }

    public List<TerminalInfo> terminals() {
        return terminals;
    }

    public Session findSession(String id) {
        if (id == null) {
            return null;
        }
        for (Session s : sessions) {
            if (id.equals(s.id())) {
                return s;
            }
        }
        return null;
    }
}
