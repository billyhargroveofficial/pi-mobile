package ru.billyhargrove.pimobile.core;

import org.json.JSONObject;

/** Parses {type:'ack',requestId,sessionId,ok,error?}. */
public final class AckParser {

    private AckParser() {
    }

    public static boolean isAck(JSONObject object) {
        return object != null && "ack".equals(object.optString("type", ""));
    }

    public static Ack parse(JSONObject object) {
        if (!isAck(object)) {
            return null;
        }
        return new Ack(
                CatalogParser.optString(object, "requestId"),
                CatalogParser.optString(object, "sessionId"),
                object.optBoolean("ok", false),
                CatalogParser.optString(object, "error"));
    }

    /** Error frames use {type:'error',error:string}. */
    public static String parseErrorMessage(JSONObject object) {
        if (object == null || !"error".equals(object.optString("type", ""))) {
            return null;
        }
        return CatalogParser.optString(object, "error");
    }
}
