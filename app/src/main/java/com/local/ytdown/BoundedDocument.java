package com.local.ytdown;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

/** Limits metadata documents only; media streams keep their separate transfer policy. */
final class BoundedDocument {
    static final int MAX_BYTES = 16 * 1024 * 1024;

    static String read(InputStream input, long declaredLength) throws IOException {
        return read(input, declaredLength, MAX_BYTES);
    }

    static String read(InputStream input, long declaredLength, int limit) throws IOException {
        return read(input, declaredLength, limit, () -> false);
    }

    static String read(InputStream input, long declaredLength, int limit,
                       BooleanSupplier cancelled) throws IOException {
        if (limit < 0 || declaredLength > limit) throw new IOException("metadata document too large");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while (true) {
            if (cancelled.getAsBoolean()) throw new InterruptedIOException("metadata request cancelled");
            count = input.read(buffer);
            if (cancelled.getAsBoolean()) throw new InterruptedIOException("metadata request cancelled");
            if (count == -1) break;
            if (output.size() > limit - count) throw new IOException("metadata document too large");
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private BoundedDocument() { }
}
