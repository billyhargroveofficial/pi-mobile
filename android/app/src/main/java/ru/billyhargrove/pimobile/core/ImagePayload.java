package ru.billyhargrove.pimobile.core;

import java.util.Arrays;

/** Raw image bytes plus their mime type, ready to be base64-encoded. */
public final class ImagePayload {

    private final byte[] data;
    private final String mimeType;

    public ImagePayload(byte[] data, String mimeType) {
        this.data = data == null ? new byte[0] : Arrays.copyOf(data, data.length);
        this.mimeType = mimeType == null ? "" : mimeType;
    }

    public byte[] bytes() {
        return Arrays.copyOf(data, data.length);
    }

    public String mimeType() {
        return mimeType;
    }

    public int size() {
        return data.length;
    }
}
