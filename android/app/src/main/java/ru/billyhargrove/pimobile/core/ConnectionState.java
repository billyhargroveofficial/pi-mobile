package ru.billyhargrove.pimobile.core;

/** Explicit connection state shown in the UI. */
public enum ConnectionState {
    /** Nothing configured / intentionally offline. */
    IDLE,
    /** First attempt in flight. */
    CONNECTING,
    /** WebSocket (or REST) is usable. */
    CONNECTED,
    /** Socket dropped; a retry is scheduled. */
    RECONNECTING,
    /** User disconnected on purpose. */
    DISCONNECTED,
    /** Terminal failure (bad token, bad URL, repeated failures). */
    ERROR
}
