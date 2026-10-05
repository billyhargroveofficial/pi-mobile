package ru.billyhargrove.pimobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ImageGuardTest {

    private static ImagePayload image(int size, String mime) {
        return new ImagePayload(new byte[size], mime);
    }

    @Test
    public void acceptsUpToThreeImages() {
        List<ImagePayload> images = Arrays.asList(
                image(100, "image/png"),
                image(100, "image/jpeg"),
                image(100, "image/webp"));
        ImageGuard.validate(images);
        assertEquals(300, ImageGuard.totalBytes(images));
    }

    @Test
    public void rejectsAFourthImage() {
        List<ImagePayload> images = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            images.add(image(10, "image/png"));
        }
        try {
            ImageGuard.validate(images);
            fail("ожидалось исключение");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("3"));
        }
    }

    @Test
    public void rejectsMoreThanTenMegabytesInTotal() {
        List<ImagePayload> images = Arrays.asList(
                image(6 * 1024 * 1024, "image/png"),
                image(5 * 1024 * 1024, "image/png"));
        try {
            ImageGuard.validate(images);
            fail("ожидалось исключение");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("10"));
        }
    }

    @Test
    public void acceptsExactlyTenMegabytes() {
        List<ImagePayload> images = Collections.singletonList(
                image(10 * 1024 * 1024, "image/png"));
        ImageGuard.validate(images);
        assertEquals(0, ImageGuard.remainingBytes(images));
    }

    @Test
    public void rejectsUnsupportedMimeTypes() {
        assertFalse(ImageGuard.isAllowedMimeType("image/gif"));
        assertFalse(ImageGuard.isAllowedMimeType(null));
        assertFalse(ImageGuard.isAllowedMimeType(""));
        assertTrue(ImageGuard.isAllowedMimeType("IMAGE/PNG"));
        assertTrue(ImageGuard.isAllowedMimeType("image/jpeg"));
        assertTrue(ImageGuard.isAllowedMimeType("image/webp"));
    }

    @Test
    public void rejectsEmptyPayloads() {
        try {
            ImageGuard.validate(Collections.singletonList(image(0, "image/png")));
            fail("ожидалось исключение");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Empty"));
        }
        try {
            ImageGuard.validate(Arrays.asList(image(1, "image/png"), (ImagePayload) null));
            fail("ожидалось исключение");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Empty"));
        }
    }

    @Test
    public void emptyListsAreValid() {
        ImageGuard.validate(null);
        ImageGuard.validate(Collections.<ImagePayload>emptyList());
        assertEquals(ImageGuard.MAX_TOTAL_BYTES, ImageGuard.remainingBytes(null));
    }
}
