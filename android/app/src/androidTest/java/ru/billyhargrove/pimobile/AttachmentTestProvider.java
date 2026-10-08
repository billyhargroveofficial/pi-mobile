package ru.billyhargrove.pimobile;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/** Test-APK-only provider in its own process: platform classes only, no target APK runtime. */
public final class AttachmentTestProvider extends ContentProvider {
    private int queries, opens;
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) {
        return "portrait".equals(uri.getLastPathSegment()) ? "image/png" : "application/octet-stream";
    }
    @Override public synchronized MatrixCursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        queries++;
        MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME});
        cursor.addRow(new Object[]{"portrait".equals(uri.getLastPathSegment()) ? "portrait.png" : "../notes\n.txt"});
        return cursor;
    }
    @Override public synchronized ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        opens++;
        boolean portrait = "portrait".equals(uri.getLastPathSegment());
        File file = new File(getContext().getCacheDir(), portrait ? "attachment-fixture-portrait.png" : "attachment-fixture-notes.bin");
        try (FileOutputStream output = new FileOutputStream(file)) {
            if (portrait) {
                Bitmap bitmap = Bitmap.createBitmap(80, 160, Bitmap.Config.ARGB_8888);
                try {
                    bitmap.eraseColor(0xff315de8);
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("Cannot encode fixture");
                } finally { bitmap.recycle(); }
            } else output.write(new byte[]{0, 1, 127, -1, 5});
        } catch (IOException error) { throw new FileNotFoundException(error.getMessage()); }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public synchronized Bundle call(String method, String arg, Bundle extras) {
        if ("reset".equals(method)) { queries = 0; opens = 0; }
        if ("cleanup".equals(method)) {
            new File(getContext().getCacheDir(), "attachment-fixture-portrait.png").delete();
            new File(getContext().getCacheDir(), "attachment-fixture-notes.bin").delete();
        }
        Bundle result = new Bundle(); result.putInt("queries", queries); result.putInt("opens", opens);
        return result;
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
