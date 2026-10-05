package ru.billyhargrove.pimobile.core;

/**
 * Sniffs the real image type from magic bytes. The file name and the value the
 * picker reports are never trusted, because the gateway accepts exactly three
 * mime types (png/jpeg/webp).
 */
public final class ImageMimeType {

    public static final String PNG = "image/png";
    public static final String JPEG = "image/jpeg";
    public static final String WEBP = "image/webp";
    public static final String UNKNOWN = "";

    private ImageMimeType() {
    }

    public static String detect(byte[] data) {
        if (data == null || data.length < 12) {
            return UNKNOWN;
        }
        if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return PNG;
        }
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return WEBP;
        }
        return UNKNOWN;
    }

    /** Extension used when re-encoding a downscaled bitmap. */
    public static String compressFormat(String mimeType) {
        return PNG.equals(mimeType) ? "PNG" : "JPEG";
    }
}
