package ru.billyhargrove.pimobile.media;

import android.graphics.Bitmap;

import ru.billyhargrove.pimobile.core.ImagePayload;

/** An image staged in the composer, waiting to be sent. */
public final class Attachment {

    private final ImagePayload payload;
    private final Bitmap thumbnail;
    private final String displayName;

    public Attachment(ImagePayload payload, Bitmap thumbnail, String displayName) {
        this.payload = payload;
        this.thumbnail = thumbnail;
        this.displayName = displayName == null ? "изображение" : displayName;
    }

    public ImagePayload payload() {
        return payload;
    }

    public Bitmap thumbnail() {
        return thumbnail;
    }

    public String displayName() {
        return displayName;
    }
}
