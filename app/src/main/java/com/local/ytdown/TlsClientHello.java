package com.local.ytdown;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/** Handles only the first plaintext handshake record; never decrypts application data. */
final class TlsClientHello {
    static final int MAX_RECORD = 16384;

    private TlsClientHello() { }

    static byte[] readRecord(InputStream input) throws IOException {
        byte[] header = readExactly(input, 5);
        int length = u16(header, 3);
        if (header[0] != 22 || header[1] != 3 || length < 4 || length > MAX_RECORD) {
            throw new IOException("Invalid initial TLS handshake record");
        }
        byte[] record = Arrays.copyOf(header, 5 + length);
        byte[] payload = readExactly(input, length);
        System.arraycopy(payload, 0, record, 5, length);
        if (record[5] != 1) throw new IOException("Initial TLS record is not ClientHello");
        return record;
    }

    static byte[][] split(byte[] record) throws IOException {
        if (record == null || record.length < 9 || record[0] != 22 || record[1] != 3
                || record[5] != 1 || u16(record, 3) != record.length - 5
                || record.length - 5 > MAX_RECORD) {
            throw new IOException("Invalid ClientHello record");
        }
        int splitAt = serverNameSplit(record);
        if (splitAt <= 5 || splitAt >= record.length) splitAt = 6;
        return new byte[][]{fragment(record, 5, splitAt), fragment(record, splitAt, record.length)};
    }

    // Return a split inside the SNI hostname, without retaining or logging the hostname.
    private static int serverNameSplit(byte[] data) {
        try {
            Cursor cursor = new Cursor(data, 9, data.length);
            cursor.skip(34); // legacy version + random
            cursor.skip(cursor.byteValue());
            cursor.skip(cursor.shortValue());
            cursor.skip(cursor.byteValue());
            int extensionsLength = cursor.shortValue();
            int end = cursor.position + extensionsLength;
            if (end != data.length) return -1;
            while (cursor.position < end) {
                int type = cursor.shortValue();
                int size = cursor.shortValue();
                int extensionEnd = cursor.position + size;
                if (extensionEnd > end) return -1;
                if (type == 0) {
                    int namesSize = cursor.shortValue();
                    if (cursor.position + namesSize != extensionEnd) return -1;
                    while (cursor.position < extensionEnd) {
                        int nameType = cursor.byteValue();
                        int nameSize = cursor.shortValue();
                        if (nameSize > extensionEnd - cursor.position) return -1;
                        if (nameType == 0 && nameSize >= 2) return cursor.position + nameSize / 2;
                        cursor.skip(nameSize);
                    }
                }
                cursor.position = extensionEnd;
            }
        } catch (IOException invalid) {
            // A fragmented/unknown ClientHello is split at the handshake boundary instead.
        }
        return -1;
    }

    private static byte[] fragment(byte[] record, int from, int to) {
        int length = to - from;
        byte[] result = new byte[5 + length];
        System.arraycopy(record, 0, result, 0, 3);
        result[3] = (byte) (length >>> 8);
        result[4] = (byte) length;
        System.arraycopy(record, from, result, 5, length);
        return result;
    }

    static byte[] readExactly(InputStream input, int count) throws IOException {
        byte[] result = new byte[count];
        int offset = 0;
        while (offset < count) {
            int read = input.read(result, offset, count - offset);
            if (read < 0) throw new IOException("Incomplete TLS record");
            if (read == 0) continue;
            offset += read;
        }
        return result;
    }

    private static int u16(byte[] data, int offset) {
        return ((data[offset] & 255) << 8) | (data[offset + 1] & 255);
    }

    private static final class Cursor {
        final byte[] data;
        final int end;
        int position;
        Cursor(byte[] data, int position, int end) {
            this.data = data; this.position = position; this.end = end;
        }
        int byteValue() throws IOException { require(1); return data[position++] & 255; }
        int shortValue() throws IOException { require(2); int value = u16(data, position); position += 2; return value; }
        void skip(int count) throws IOException { require(count); position += count; }
        void require(int count) throws IOException {
            if (count < 0 || count > end - position) throw new IOException("Invalid ClientHello length");
        }
    }
}
