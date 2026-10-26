package com.local.ytdown;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
}
