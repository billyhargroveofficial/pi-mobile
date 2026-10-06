package ru.billyhargrove.pimobile.core

/** Explicit connection lifecycle; never infer success from configuration alone. */
enum class ConnectionState { IDLE, CONNECTING, CONNECTED, RECONNECTING, DISCONNECTED, ERROR }
