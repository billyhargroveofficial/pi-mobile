package ru.billyhargrove.pimobile.core

/** Safe profile metadata for presentation. Never contains a credential or encrypted envelope. */
data class ConnectionProfile(val id: String, val name: String, val baseUrl: String, val hasToken: Boolean)
