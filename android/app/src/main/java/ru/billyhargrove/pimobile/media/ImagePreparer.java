package ru.billyhargrove.pimobile.media;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import ru.billyhargrove.pimobile.core.ImageGuard;
import ru.billyhargrove.pimobile.core.ImageMimeType;
import ru.billyhargrove.pimobile.core.ImagePayload;

/**
 * Turns a {@code content://} image picked with ACTION_OPEN_DOCUMENT into a
 * protocol-legal payload (png/jpeg/webp, total budget respected).
 *
 * <p>The type is read from magic bytes, never from the file name or from the
 * mime type the picker reports. If the file is too large for the remaining
 * budget, the image is downscaled and re-encoded until it fits – or the import
 * fails with a readable message.</p>
 */
public final class ImagePreparer {

    private static final int FIRST_DECODE_EDGE = 2048;
    private static final int MIN_DECODE_EDGE = 512;

    private ImagePreparer() {
    }

    public static ImagePayload prepare(ContentResolver resolver, Uri uri, long budgetBytes) throws IOException {
        if (resolver == null || uri == null) {
            throw new IOException("Файл недоступен");
        }
        long budget = Math.max(64 * 1024L, Math.min(budgetBytes, ImageGuard.MAX_TOTAL_BYTES));
        byte[] raw = readUpTo(resolver, uri, ImageGuard.MAX_TOTAL_BYTES + 1L);
        if (raw.length == 0) {
            throw new IOException("Пустой файл");
        }
        String mime = ImageMimeType.detect(raw);
        if (mime.isEmpty()) {
            throw new IOException("Формат не поддерживается: нужен PNG, JPEG или WebP");
        }
        if (raw.length <= budget) {
            return new ImagePayload(raw, mime);
        }
        return shrink(raw, mime, budget);
    }

    /** Small preview for the composer strip and for local bubbles. */
    public static Bitmap thumbnail(byte[] bytes, int maxEdge) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return decode(bytes, maxEdge);
    }

    public static String displayName(ContentResolver resolver, Uri uri) {
        if (resolver == null || uri == null) {
            return "изображение";
        }
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.isEmpty()) {
                        return name;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // fall through to the default name
        }
        return "изображение";
    }

    private static ImagePayload shrink(byte[] raw, String mime, long budget) throws IOException {
        Bitmap source = decode(raw, FIRST_DECODE_EDGE);
        if (source == null) {
            throw new IOException("Не удалось декодировать изображение");
        }
        String format = ImageMimeType.compressFormat(mime);
        try {
            for (int edge = FIRST_DECODE_EDGE; edge >= MIN_DECODE_EDGE; edge /= 2) {
                Bitmap scaled = scaleDown(source, edge);
                int[] qualities = "PNG".equals(format) ? new int[]{100} : new int[]{85, 70, 55, 40};
                for (int quality : qualities) {
                    byte[] encoded = encode(scaled, format, quality);
                    if (encoded.length <= budget) {
                        return new ImagePayload(encoded, mime);
                    }
                }
                if (scaled != source) {
                    scaled.recycle();
                }
            }
        } finally {
            source.recycle();
        }
        throw new IOException("Не удалось ужать изображение до лимита 10 МБ");
    }

    private static Bitmap decode(byte[] raw, int maxEdge) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(raw, 0, raw.length, bounds);
        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / sample > maxEdge) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        try {
            return BitmapFactory.decodeByteArray(raw, 0, raw.length, options);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private static Bitmap scaleDown(Bitmap source, int maxEdge) {
        int largest = Math.max(source.getWidth(), source.getHeight());
        if (largest <= maxEdge) {
            return source;
        }
        float ratio = (float) maxEdge / (float) largest;
        int width = Math.max(1, Math.round(source.getWidth() * ratio));
        int height = Math.max(1, Math.round(source.getHeight() * ratio));
        return Bitmap.createScaledBitmap(source, width, height, true);
    }

    private static byte[] encode(Bitmap bitmap, String format, int quality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Bitmap.CompressFormat compressFormat = "PNG".equals(format)
                ? Bitmap.CompressFormat.PNG
                : Bitmap.CompressFormat.JPEG;
        bitmap.compress(compressFormat, quality, out);
        return out.toByteArray();
    }

    private static byte[] readUpTo(ContentResolver resolver, Uri uri, long cap) throws IOException {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                throw new IOException("Не удалось открыть файл");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > cap) {
                    throw new IOException("Файл больше 10 МБ");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}
