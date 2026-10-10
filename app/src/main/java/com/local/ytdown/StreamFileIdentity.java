package com.local.ytdown;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.BooleanSupplier;

final class StreamFileIdentity {
    private StreamFileIdentity() { }
    static String key(String name) {
        MessageDigest digest = digest();
        return hex(digest.digest(name.getBytes(StandardCharsets.UTF_8)));
    }
    static String sha256(InputStream input, BooleanSupplier cancelled) throws IOException {
        MessageDigest digest = digest();
        byte[] buffer = new byte[65536];
        int length;
        while ((length = input.read(buffer)) >= 0) {
            if (cancelled.getAsBoolean()) throw new java.io.InterruptedIOException("Download cancelled");
            digest.update(buffer, 0, length);
        }
        return hex(digest.digest());
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(64);
        for (byte b : bytes) value.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return value.toString();
    }
}
