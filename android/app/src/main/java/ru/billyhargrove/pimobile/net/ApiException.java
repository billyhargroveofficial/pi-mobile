package ru.billyhargrove.pimobile.net;

import java.io.IOException;

/** HTTP failure with a Russian, user-showable message. */
public final class ApiException extends IOException {

    private final int httpCode;

    public ApiException(String message) {
        this(message, 0, null);
    }

    public ApiException(String message, int httpCode, Throwable cause) {
        super(message, cause);
        this.httpCode = httpCode;
    }

    public int httpCode() {
        return httpCode;
    }

    public boolean isAuthFailure() {
        return httpCode == 401 || httpCode == 403;
    }
}
