package com.local.ytdown;
import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.io.InterruptedIOException;
import org.junit.Test;

public class StreamFileIdentityTest {
    @Test public void hashesContentAndDistinguishesSameSizeFiles() throws Exception {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                StreamFileIdentity.sha256(new ByteArrayInputStream(new byte[]{97,98,99}), () -> false));
        assertNotEquals(StreamFileIdentity.sha256(new ByteArrayInputStream(new byte[]{1}), () -> false),
                StreamFileIdentity.sha256(new ByteArrayInputStream(new byte[]{2}), () -> false));
        assertEquals(64, StreamFileIdentity.key("한글 영상.mp4").length());
    }
    @Test public void cancellationStopsHashing() throws Exception {
        try {
            StreamFileIdentity.sha256(new ByteArrayInputStream(new byte[]{1}), () -> true);
            fail("Cancellation must stop publication");
        } catch (InterruptedIOException expected) { }
    }
}
