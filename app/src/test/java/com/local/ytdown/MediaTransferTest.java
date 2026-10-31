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

    @Test public void rejectsEarlyEofInsteadOfCompletingPartialMedia() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThrows(EOFException.class, () -> MediaTransfer.copy(
                new ByteArrayInputStream(new byte[1024]), out,
                2048, 0, 1, () -> false, value -> {}));
        assertEquals(1024, out.size());
    }

    @Test public void rejectsEmptyMediaWithKnownOrUnknownLength() {
        for (long length : new long[]{0, -1}) {
            assertThrows(IOException.class, () -> MediaTransfer.copy(
                    new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(),
                    length, 0, 1, () -> false, value -> {}));
        }
    }

    @Test public void rejectsBytesBeyondDeclaredLength() {
        assertThrows(IOException.class, () -> MediaTransfer.copy(
                new ByteArrayInputStream(new byte[1024]), new ByteArrayOutputStream(),
                512, 0, 1, () -> false, value -> {}));
    }

    @Test public void cancellationDuringEofReadCannotReportSuccess() {
        boolean[] cancelled = {false};
        InputStream input = new ByteArrayInputStream(new byte[]{1}) {
            @Override public synchronized int read(byte[] buffer, int offset, int length) {
                int count = super.read(buffer, offset, length);
                if (count == -1) cancelled[0] = true;
                return count;
            }
        };
        assertThrows(InterruptedException.class, () -> MediaTransfer.copy(input,
                new ByteArrayOutputStream(), 1, 0, 1, () -> cancelled[0], value -> {}));
    }
}
