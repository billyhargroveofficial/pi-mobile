package ru.billyhargrove.pimobile.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link Catalog} into the grouped list the UI renders.
 *
 * <p>Orca identities group sessions (and unbridged terminals), while sessions
 * that live outside Orca are grouped by their working directory, exactly like the
 * protocol describes.</p>
 */
public final class SessionGrouping {

    private SessionGrouping() {
    }

    public static List<CatalogRow> build(Catalog catalog) {
        List<CatalogRow> rows = new ArrayList<>();
        if (catalog == null) {
            return rows;
        }

        Map<String, List<Session>> sessionsByGroup = new LinkedHashMap<>();
        Map<String, List<TerminalInfo>> terminalsByGroup = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, String> subtitles = new LinkedHashMap<>();

        // 1. Orca workspaces first, in the order the gateway returned them.
        List<String> order = new ArrayList<>();
        for (Workspace workspace : catalog.workspaces()) {
            String key = "ws:" + workspace.id();
            order.add(key);
            labels.put(key, workspace.displayName());
            subtitles.put(key, workspace.path());
            sessionsByGroup.put(key, new ArrayList<Session>());
            terminalsByGroup.put(key, new ArrayList<TerminalInfo>());
        }

        Map<String, String> workspaceKeyById = new LinkedHashMap<>();
        for (Workspace workspace : catalog.workspaces()) {
            workspaceKeyById.put(workspace.id(), "ws:" + workspace.id());
        }

        // 2. Sessions: inside a workspace when the id is known, otherwise grouped by cwd.
        for (Session session : catalog.sessions()) {
            String key = session.hasWorkspace() ? workspaceKeyById.get(session.workspaceId()) : null;
            if (key == null) {
                key = cwdKey(session.cwd());
                if (!labels.containsKey(key)) {
                    order.add(key);
                    labels.put(key, cwdLabel(session.cwd()));
                    subtitles.put(key, "Вне Orca");
                    sessionsByGroup.put(key, new ArrayList<Session>());
                    terminalsByGroup.put(key, new ArrayList<TerminalInfo>());
                }
            }
            sessionsByGroup.get(key).add(session);
        }

        // 3. Terminals that are not bridged to Pi attach to their workspace.
        for (TerminalInfo terminal : catalog.terminals()) {
            String key = workspaceKeyById.get(terminal.workspaceId());
            if (key == null) {
                key = "ws-other";
                if (!labels.containsKey(key)) {
                    order.add(key);
                    labels.put(key, "Прочие терминалы");
                    subtitles.put(key, "Вне Orca");
                    sessionsByGroup.put(key, new ArrayList<Session>());
                    terminalsByGroup.put(key, new ArrayList<TerminalInfo>());
                }
            }
            if (!terminalsByGroup.containsKey(key)) {
                terminalsByGroup.put(key, new ArrayList<TerminalInfo>());
            }
            terminalsByGroup.get(key).add(terminal);
        }

        // 4. Keep every Orca workspace visible, including empty ones.
        for (String key : order) {
            List<Session> sessions = sessionsByGroup.get(key);
            List<TerminalInfo> terminals = terminalsByGroup.get(key);
            if (sessions == null) {
                sessions = Collections.emptyList();
            }
            if (terminals == null) {
                terminals = Collections.emptyList();
            }
            rows.add(CatalogRow.header(key, labels.get(key), subtitles.get(key), sessions.size() + terminals.size()));
            List<Session> sorted = new ArrayList<>(sessions);
            Collections.sort(sorted, SESSION_ORDER);
            for (Session session : sorted) {
                rows.add(CatalogRow.session(session));
            }
            for (TerminalInfo terminal : terminals) {
                rows.add(CatalogRow.terminal(terminal));
            }
        }
        return rows;
    }

    private static final Comparator<Session> SESSION_ORDER = new Comparator<Session>() {
        @Override
        public int compare(Session a, Session b) {
            int byRank = Integer.compare(a.sortRank(), b.sortRank());
            if (byRank != 0) {
                return byRank;
            }
            int byTitle = a.displayTitle().compareToIgnoreCase(b.displayTitle());
            if (byTitle != 0) {
                return byTitle;
            }
            return a.id().compareTo(b.id());
        }
    };

    private static String cwdKey(String cwd) {
        return "cwd:" + (cwd == null || cwd.isEmpty() ? "-" : cwd);
    }

    private static String cwdLabel(String cwd) {
        if (cwd == null || cwd.isEmpty()) {
            return "Без рабочей папки";
        }
        int slash = cwd.lastIndexOf('/');
        String tail = slash >= 0 && slash + 1 < cwd.length() ? cwd.substring(slash + 1) : cwd;
        return tail.isEmpty() ? cwd : tail;
    }
}
