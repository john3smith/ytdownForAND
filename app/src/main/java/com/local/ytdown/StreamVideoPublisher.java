package com.local.ytdown;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import androidx.annotation.RequiresApi;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.BooleanSupplier;

/** Commit verified private staging files to Downloads without broad storage permissions. */
@RequiresApi(29)
final class StreamVideoPublisher {
    private StreamVideoPublisher() { }
    static Uri publish(Context context, File file, BooleanSupplier cancelled) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        SharedPreferences receipts = context.getSharedPreferences("stream_exports", Context.MODE_PRIVATE);
        String key = StreamFileIdentity.key(file.getName());
        String hash;
        try (InputStream input = new FileInputStream(file)) {
            hash = StreamFileIdentity.sha256(input, cancelled);
        }
        String previous = receipts.getString(key, null);
        if (previous != null) {
            Uri uri = Uri.parse(previous);
            if ("content".equals(uri.getScheme()) && "media".equals(uri.getAuthority())) {
                try (InputStream input = resolver.openInputStream(uri)) {
                    if (input != null && hash.equals(StreamFileIdentity.sha256(input, cancelled))) return uri;
                } catch (IOException | SecurityException stale) {
                    if (cancelled.getAsBoolean()) throw new java.io.InterruptedIOException("Download cancelled");
                    // Missing/changed old exports are never overwritten or deleted.
                }
            }
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, file.getName());
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime == null ? "application/octet-stream" : mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/YTDown/");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
        if (uri == null) throw new IOException("Unable to create download item");
        boolean committed = false;
        try {
            try (InputStream input = new FileInputStream(file); OutputStream output = resolver.openOutputStream(uri, "w")) {
                if (output == null) throw new IOException("Unable to open download item");
                byte[] buffer = new byte[65536];
                int length;
                while ((length = input.read(buffer)) >= 0) {
                    if (cancelled.getAsBoolean()) throw new java.io.InterruptedIOException("Download cancelled");
                    output.write(buffer, 0, length);
                }
            }
            try (ParcelFileDescriptor descriptor = resolver.openFileDescriptor(uri, "r")) {
                if (descriptor == null || descriptor.getStatSize() != file.length()) {
                    throw new IOException("Download item size mismatch");
                }
            }
            try (InputStream input = resolver.openInputStream(uri)) {
                if (input == null || !hash.equals(StreamFileIdentity.sha256(input, cancelled))) {
                    throw new IOException("Download item integrity mismatch");
                }
            }
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (resolver.update(uri, values, null, null) != 1) throw new IOException("Download commit failed");
            committed = true;
            receipts.edit().putString(key, uri.toString()).apply();
            return uri;
        } finally {
            if (!committed) resolver.delete(uri, null, null); // Only this attempt's newly created incomplete item.
        }
    }
}
