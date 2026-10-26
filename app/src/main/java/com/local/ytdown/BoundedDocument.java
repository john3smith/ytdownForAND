package com.local.ytdown;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Limits metadata documents only; media streams keep their separate transfer policy. */
final class BoundedDocument {
    static final int MAX_BYTES = 16 * 1024 * 1024;

    static String read(InputStream input, long declaredLength) throws IOException {
        return read(input, declaredLength, MAX_BYTES);
    }

    static String read(InputStream input, long declaredLength, int limit) throws IOException {
        if (limit < 0 || declaredLength > limit) throw new IOException("metadata document too large");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() > limit - count) throw new IOException("metadata document too large");
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private BoundedDocument() { }
}
