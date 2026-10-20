package com.local.ytdown;

import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

public class MediaTransferTest {
    @Test public void copyPreservesBytesAndCoalescesProgress() throws Exception {
        byte[] bytes = new byte[16 * 1024 * 1024];
        new Random(17).nextBytes(bytes);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> updates = new ArrayList<>();
        assertEquals(bytes.length, MediaTransfer.copy(new ByteArrayInputStream(bytes), out,
                bytes.length, 1, 3, () -> false, updates::add));
        assertArrayEquals(bytes, out.toByteArray());
        assertTrue(updates.size() <= 35);
        assertEquals(Integer.valueOf(67), updates.get(updates.size() - 1));
        for (int i = 1; i < updates.size(); i++) assertTrue(updates.get(i) > updates.get(i - 1));
    }
    @Test public void unknownLengthDoesNotFloodUpdates() throws Exception {
        List<Integer> updates = new ArrayList<>();
        MediaTransfer.copy(new ByteArrayInputStream(new byte[200000]), new ByteArrayOutputStream(),
                -1, 0, 1, () -> false, updates::add);
        assertEquals(Collections.singletonList(0), updates);
    }
    @Test public void cancellationDoesNotWriteAnotherChunk() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            MediaTransfer.copy(new ByteArrayInputStream(new byte[200000]), out,
                    200000, 0, 1, () -> out.size() > 0, value -> {});
            fail("Expected cancellation");
        } catch (InterruptedException expected) {
            assertEquals(64 * 1024, out.size());
        }
    }
}
