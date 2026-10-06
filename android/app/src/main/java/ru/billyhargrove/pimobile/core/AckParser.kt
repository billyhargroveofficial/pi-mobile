package ru.billyhargrove.pimobile.core

import org.json.JSONObject
object AckParser {
    @JvmStatic fun isAck(value: JSONObject?) = value?.optString("type", "") == "ack"
    @JvmStatic fun parse(value: JSONObject?): Ack? {
        if (!isAck(value) || value == null) return null
        return Ack(CatalogParser.optString(value, "requestId"), CatalogParser.optString(value, "sessionId"),
            value.optBoolean("ok", false), CatalogParser.optString(value, "error"))
    }
    @JvmStatic fun parseErrorMessage(value: JSONObject?): String? =
        if (value?.optString("type", "") == "error") CatalogParser.optString(value, "error") else null
}
