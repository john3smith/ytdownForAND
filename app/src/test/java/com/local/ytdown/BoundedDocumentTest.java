package com.local.ytdown;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;

public class BoundedDocumentTest {
    @Test public void readsUtf8AtTheExactBoundary() throws Exception {
        byte[] data = "문서".getBytes(StandardCharsets.UTF_8);
        assertEquals("문서", BoundedDocument.read(new ByteArrayInputStream(data), data.length, data.length));
    }

    @Test public void rejectsKnownAndUnknownOversizedBodies() throws Exception {
        for (long length : new long[]{-1, 5}) {
            try {
                BoundedDocument.read(new ByteArrayInputStream(new byte[5]), length, 4);
                fail("oversized metadata accepted");
            } catch (IOException expected) { }
        }
        assertEquals("", BoundedDocument.read(new ByteArrayInputStream(new byte[0]), -1, 4));
    }

    @Test public void cancelledDocumentDoesNotReadOrAcceptEmptyResult() throws Exception {
        InputStream input = new InputStream() {
            @Override public int read() { throw new AssertionError("cancelled request read the body"); }
        };
        try {
            BoundedDocument.read(input, -1, 4, () -> true);
            fail("cancelled document accepted");
        } catch (InterruptedIOException expected) { }
    }

    @Test public void cancellationDuringDataOrEofReadRejectsTheResult() throws Exception {
        for (int response : new int[]{1, -1}) {
            AtomicBoolean cancelled = new AtomicBoolean();
            InputStream input = new InputStream() {
                @Override public int read() { throw new AssertionError("unexpected byte read"); }
                @Override public int read(byte[] data, int offset, int length) {
                    cancelled.set(true);
                    data[offset] = 'x';
                    return response;
                }
            };
            try {
                BoundedDocument.read(input, -1, 4, cancelled::get);
                fail("cancelled result accepted");
            } catch (InterruptedIOException expected) { }
        }
    }
}
