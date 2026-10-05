package ru.billyhargrove.pimobile.core;

import java.util.Objects;

/**
 * A media reference coming from the gateway.
 *
 * <p>The {@code url} is either a same-origin relative path
 * ({@code /api/sessions/<id>/media/<sha256>}) or an absolute URL. Absolute URLs
 * pointing at another origin are never fetched with the bearer token; see
 * {@link MediaUrlPolicy}.</p>
 */
public final class ImageRef {

    private final String url;
    private final String mimeType;

    public ImageRef(String url, String mimeType) {
        this.url = url == null ? "" : url;
        this.mimeType = mimeType == null ? "" : mimeType;
    }

    public String url() {
        return url;
    }

    public String mimeType() {
        return mimeType;
    }

    public boolean isUsable() {
        return !url.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImageRef)) {
            return false;
        }
        ImageRef other = (ImageRef) o;
        return url.equals(other.url) && mimeType.equals(other.mimeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, mimeType);
    }

    @Override
    public String toString() {
        return "ImageRef{" + url + ", " + mimeType + "}";
    }
}
