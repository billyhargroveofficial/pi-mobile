package ru.billyhargrove.pimobile.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Protocol limits for outgoing images: max 3 images and max 10 MB decoded in
 * total (both limits come from docs/protocol.md).
 */
public final class ImageGuard {

    public static final int MAX_IMAGES = 3;
    public static final long MAX_TOTAL_BYTES = 10L * 1024L * 1024L;

    public static final Set<String> ALLOWED_MIME_TYPES = Collections.unmodifiableSet(
            new HashSet<>(java.util.Arrays.asList("image/png", "image/jpeg", "image/webp")));

    private ImageGuard() {
    }

    public static boolean isAllowedMimeType(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        return ALLOWED_MIME_TYPES.contains(mimeType.trim().toLowerCase(Locale.ROOT));
    }

    public static long totalBytes(List<ImagePayload> images) {
        long sum = 0L;
        if (images == null) {
            return 0L;
        }
        for (ImagePayload image : images) {
            if (image != null) {
                sum += image.size();
            }
        }
        return sum;
    }

    public static long remainingBytes(List<ImagePayload> images) {
        return Math.max(0L, MAX_TOTAL_BYTES - totalBytes(images));
    }

    /** @throws IllegalArgumentException with a Russian, user-showable message */
    public static void validate(List<ImagePayload> images) {
        if (images == null || images.isEmpty()) {
            return;
        }
        if (images.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("No more than " + MAX_IMAGES + " images per message");
        }
        for (ImagePayload image : images) {
            if (image == null) {
                throw new IllegalArgumentException("Empty image");
            }
            if (!isAllowedMimeType(image.mimeType())) {
                throw new IllegalArgumentException("Unsupported format: " + image.mimeType());
            }
            if (image.size() <= 0) {
                throw new IllegalArgumentException("Empty image file");
            }
        }
        long total = totalBytes(images);
        if (total > MAX_TOTAL_BYTES) {
            throw new IllegalArgumentException(
                    "Total image size of " + (total / (1024 * 1024)) + " MB exceeds the 10 MB limit");
        }
    }
}
