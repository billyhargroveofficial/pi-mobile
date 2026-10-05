package ru.billyhargrove.pimobile.core;

/** One row of the catalog screen: a group header, a session or a bare terminal. */
public final class CatalogRow {

    public enum Kind {
        HEADER,
        SESSION,
        TERMINAL
    }

    private final Kind kind;
    private final String key;
    private final String title;
    private final String subtitle;
    private final String sessionId;
    private final SessionStatus status;
    private final boolean connected;
    private final boolean readOnly;
    private final String badge;
    private final int count;

    private CatalogRow(Kind kind,
                       String key,
                       String title,
                       String subtitle,
                       String sessionId,
                       SessionStatus status,
                       boolean connected,
                       boolean readOnly,
                       String badge,
                       int count) {
        this.kind = kind;
        this.key = key;
        this.title = title;
        this.subtitle = subtitle;
        this.sessionId = sessionId;
        this.status = status;
        this.connected = connected;
        this.readOnly = readOnly;
        this.badge = badge;
        this.count = count;
    }

    public static CatalogRow header(String key, String title, String subtitle, int count) {
        return new CatalogRow(Kind.HEADER, key, title, subtitle, "", SessionStatus.UNKNOWN, false, false, "", count);
    }

    public static CatalogRow session(Session session) {
        return new CatalogRow(
                Kind.SESSION,
                "session:" + session.id(),
                session.displayTitle(),
                buildSessionSubtitle(session),
                session.id(),
                session.status(),
                session.connected(),
                false,
                statusBadge(session.status()),
                0);
    }

    public static CatalogRow terminal(TerminalInfo terminal) {
        String subtitle = terminal.agent().isEmpty() ? "Агент не указан" : terminal.agent();
        return new CatalogRow(
                Kind.TERMINAL,
                "terminal:" + terminal.id(),
                terminal.displayTitle(),
                subtitle,
                "",
                terminal.connected() ? SessionStatus.IDLE : SessionStatus.OFFLINE,
                terminal.connected(),
                true,
                "требуется расширение",
                0);
    }

    private static String buildSessionSubtitle(Session session) {
        StringBuilder sb = new StringBuilder();
        if (!session.cwd().isEmpty()) {
            sb.append(session.cwd());
        }
        if (!session.model().isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(session.model());
        }
        if (!session.connected() && sb.length() == 0) {
            sb.append("Процесс не подключён");
        }
        return sb.toString();
    }

    private static String statusBadge(SessionStatus status) {
        switch (status) {
            case RUNNING:
                return "работает";
            case IDLE:
                return "ожидает";
            case OFFLINE:
                return "офлайн";
            default:
                return "";
        }
    }

    public Kind kind() {
        return kind;
    }

    /** Stable identity for adapter diffing. */
    public String key() {
        return key;
    }

    public String title() {
        return title;
    }

    public String subtitle() {
        return subtitle;
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

    /** Bare Orca terminals are read-only until the Pi extension bridges them. */
    public boolean readOnly() {
        return readOnly;
    }

    public String badge() {
        return badge;
    }

    public int count() {
        return count;
    }

    public boolean isHeader() {
        return kind == Kind.HEADER;
    }
}
