package ru.billyhargrove.pimobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ImageMimeTypeTest {

    private static byte[] png() {
        byte[] data = new byte[16];
        data[0] = (byte) 0x89;
        data[1] = 'P';
        data[2] = 'N';
        data[3] = 'G';
        return data;
    }

    private static byte[] jpeg() {
        byte[] data = new byte[16];
        data[0] = (byte) 0xFF;
        data[1] = (byte) 0xD8;
        data[2] = (byte) 0xFF;
        return data;
    }

    private static byte[] webp() {
        byte[] data = new byte[16];
        data[0] = 'R';
        data[1] = 'I';
        data[2] = 'F';
        data[3] = 'F';
        data[8] = 'W';
        data[9] = 'E';
        data[10] = 'B';
        data[11] = 'P';
        return data;
    }

    @Test
    public void detectsTheThreeAllowedFormats() {
        assertEquals(ImageMimeType.PNG, ImageMimeType.detect(png()));
        assertEquals(ImageMimeType.JPEG, ImageMimeType.detect(jpeg()));
        assertEquals(ImageMimeType.WEBP, ImageMimeType.detect(webp()));
    }

    @Test
    public void rejectsEverythingElse() {
        assertEquals(ImageMimeType.UNKNOWN, ImageMimeType.detect(new byte[]{'G', 'I', 'F', '8', 0, 0, 0, 0, 0, 0, 0, 0}));
        assertEquals(ImageMimeType.UNKNOWN, ImageMimeType.detect(new byte[]{1, 2, 3}));
        assertEquals(ImageMimeType.UNKNOWN, ImageMimeType.detect(null));
        assertEquals(ImageMimeType.UNKNOWN, ImageMimeType.detect(new byte[0]));
    }

    @Test
    public void compressFormatKeepsPngAndUsesJpegOtherwise() {
        assertEquals("PNG", ImageMimeType.compressFormat(ImageMimeType.PNG));
        assertEquals("JPEG", ImageMimeType.compressFormat(ImageMimeType.JPEG));
        assertEquals("JPEG", ImageMimeType.compressFormat(ImageMimeType.WEBP));
        assertNotEquals("PNG", ImageMimeType.compressFormat(ImageMimeType.WEBP));
        assertTrue(ImageGuard.isAllowedMimeType(ImageMimeType.PNG));
    }
}
