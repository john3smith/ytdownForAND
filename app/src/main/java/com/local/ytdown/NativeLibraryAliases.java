package com.local.ytdown;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Restores loader filenames after upstream runtime extraction, before any execution. */
final class NativeLibraryAliases {
    private NativeLibraryAliases() {}
    static void restore(Context context) throws Exception {
        File root = new File(context.getNoBackupFilesDir(), "youtubedl-android/packages/python/usr");
        File manifest = new File(root, "native-aliases.json");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(manifest)) { copy(in, bytes); }
        JSONArray aliases = new JSONArray(bytes.toString(StandardCharsets.UTF_8.name()));
        File lib = new File(root, "lib");
        for (int i = 0; i < aliases.length(); i++) {
            JSONObject entry = aliases.getJSONObject(i);
            File source = member(lib, entry.getString("source"));
            File destination = member(lib, entry.getString("name"));
            String expected = entry.getString("sha256");
            if (!expected.matches("[a-f0-9]{64}") || source.equals(destination)
                    || !expected.equals(hash(source))) throw new IOException("Invalid native alias source");
            if (destination.isFile() && expected.equals(hash(destination))) continue;
            File temp = File.createTempFile("native-alias-", ".tmp", lib);
            try {
                try (InputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(temp)) {
                    copy(in, out); out.getFD().sync();
                }
                if (!expected.equals(hash(temp))) throw new IOException("Native alias copy mismatch");
                android.system.Os.rename(temp.getAbsolutePath(), destination.getAbsolutePath());
            } finally { if (temp.exists() && !temp.delete()) temp.deleteOnExit(); }
        }
    }
    private static File member(File parent, String name) throws IOException {
        if (!name.matches("lib[A-Za-z0-9_+.-]+\\.so(?:\\.[0-9]+)*")) throw new IOException("Invalid native alias name");
        File file = new File(parent, name);
        if (!file.getCanonicalFile().getParentFile().equals(parent.getCanonicalFile())) throw new IOException("Native alias escapes directory");
        return file;
    }
    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[65536]; int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
    }
    private static String hash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return hex.toString();
    }
}
