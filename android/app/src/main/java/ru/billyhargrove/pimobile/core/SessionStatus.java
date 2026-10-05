package ru.billyhargrove.pimobile.core;

/** Session activity as reported by the gateway catalog. */
public enum SessionStatus {
    IDLE,
    RUNNING,
    OFFLINE,
    UNKNOWN;

    public static SessionStatus parse(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        String v = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (v.equals("idle")) {
            return IDLE;
        }
        if (v.equals("running")) {
            return RUNNING;
        }
        if (v.equals("offline")) {
            return OFFLINE;
        }
        return UNKNOWN;
    }

    /** Wire value, kept for tests and for building stable UI keys. */
    public String wire() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
