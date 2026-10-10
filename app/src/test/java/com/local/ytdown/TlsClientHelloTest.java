package com.local.ytdown;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public class TlsClientHelloTest {
    static byte[] hello(String hostname) throws Exception {
        byte[] name = hostname.getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(new byte[]{3,3}); body.write(new byte[32]);
        body.write(0); body.write(new byte[]{0,2,0x13,1}); body.write(new byte[]{1,0});
        int extensionLength = 9 + name.length;
        body.write(extensionLength >> 8); body.write(extensionLength);
        body.write(new byte[]{0,0});
        int snLength = 5 + name.length;
        body.write(snLength >> 8); body.write(snLength);
        int listLength = 3 + name.length;
        body.write(listLength >> 8); body.write(listLength); body.write(0);
        body.write(name.length >> 8); body.write(name.length); body.write(name);
        byte[] contents = body.toByteArray();
        ByteArrayOutputStream record = new ByteArrayOutputStream();
        int payloadLength = 4 + contents.length;
        record.write(new byte[]{22,3,1,(byte)(payloadLength >> 8),(byte)payloadLength,
                1,(byte)(contents.length >> 16),(byte)(contents.length >> 8),(byte)contents.length});
        record.write(contents);
        return record.toByteArray();
    }
    @Test public void handshakeBytesAreUnchangedAndSniIsSplit() throws Exception {
        byte[] original = hello("www.pornhub.com");
        byte[][] records = TlsClientHello.split(original);
        ByteArrayOutputStream combined = new ByteArrayOutputStream();
        for (byte[] record : records) {
            assertEquals(record.length - 5, ((record[3] & 255) << 8) | (record[4] & 255));
            assertTrue(record.length > 5);
            combined.write(record, 5, record.length - 5);
            assertFalse(new String(record, StandardCharsets.US_ASCII).contains("www.pornhub.com"));
        }
        assertArrayEquals(Arrays.copyOfRange(original, 5, original.length), combined.toByteArray());
    }
    @Test public void completeFirstRecordIsReadWithoutConsumingNextRecord() throws Exception {
        byte[] original = hello("www.pornhub.com");
        ByteArrayOutputStream all = new ByteArrayOutputStream(); all.write(original); all.write(42);
        ByteArrayInputStream input = new ByteArrayInputStream(all.toByteArray());
        assertArrayEquals(original, TlsClientHello.readRecord(input)); assertEquals(42, input.read());
    }
    @Test public void truncatedAndNonHandshakeInputsAreRejected() throws Exception {
        byte[] original = hello("www.pornhub.com");
        for (int size : new int[]{0,1,4,5,original.length-1}) {
            try { TlsClientHello.readRecord(new ByteArrayInputStream(Arrays.copyOf(original,size))); fail(); }
            catch (IOException expected) { }
        }
        original[0] = 23;
        try { TlsClientHello.split(original); fail(); } catch (IOException expected) { }
    }
    @Test public void malformedExtensionLengthsDoNotCrashOrModifyHandshake() throws Exception {
        byte[] original = hello("www.pornhub.com");
        original[47] = (byte)255;
        byte[][] split = TlsClientHello.split(original);
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        for (byte[] part : split) payload.write(part,5,part.length-5);
        assertArrayEquals(Arrays.copyOfRange(original,5,original.length),payload.toByteArray());
    }
    @Test public void randomMalformedRecordsRemainBounded() throws Exception {
        Random random = new Random(20261010);
        for (int iteration=0; iteration<200; iteration++) {
            byte[] record = new byte[9 + random.nextInt(2048)]; random.nextBytes(record);
            record[0]=22; record[1]=3; record[3]=(byte)((record.length-5)>>8);
            record[4]=(byte)(record.length-5); record[5]=1;
            byte[][] split = TlsClientHello.split(record);
            assertEquals(record.length+5,split[0].length+split[1].length);
        }
    }
}
