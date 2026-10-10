package com.local.ytdown;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class LocalHttpsRelayTest {
    @Test public void tunnelPreservesPayloadAndReleasesResources() throws Exception {
        byte[] original = TlsClientHelloTest.hello("www.pornhub.com");
        byte[] opaque = new byte[]{23,3,3,0,3,12,13,14};
        try (ServerSocket mock = new ServerSocket(0,4,InetAddress.getByName("127.0.0.1"));
             LocalHttpsRelay relay = new LocalHttpsRelay(host -> new Socket("127.0.0.1",mock.getLocalPort()))) {
            CompletableFuture<byte[]> bytes = new CompletableFuture<>();
            Thread server = new Thread(() -> {
                try (Socket peer=mock.accept()) {
                    peer.setSoTimeout(3000);
                    ByteArrayOutputStream reconstructed = new ByteArrayOutputStream();
                    for (int i=0; i<2; i++) {
                        byte[] header=TlsClientHello.readExactly(peer.getInputStream(),5);
                        int length=((header[3]&255)<<8)|(header[4]&255);
                        reconstructed.write(TlsClientHello.readExactly(peer.getInputStream(),length));
                    }
                    assertArrayEquals(opaque,TlsClientHello.readExactly(peer.getInputStream(),opaque.length));
                    peer.getOutputStream().write(opaque); peer.getOutputStream().flush();
                    bytes.complete(reconstructed.toByteArray());
                } catch(Throwable error) { bytes.completeExceptionally(error); }
            });
            server.setDaemon(true); server.start();
            try(Socket client=new Socket("127.0.0.1",URI.create(relay.proxyUrl()).getPort())) {
                client.setSoTimeout(3000);
                client.getOutputStream().write("CONNECT www.pornhub.com:443 HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                String header=readHeader(client.getInputStream()); assertTrue(header.startsWith("HTTP/1.1 200"));
                client.getOutputStream().write(original); client.getOutputStream().write(opaque); client.getOutputStream().flush();
                assertArrayEquals(opaque,TlsClientHello.readExactly(client.getInputStream(),opaque.length));
                assertArrayEquals(java.util.Arrays.copyOfRange(original,5,original.length),bytes.get(4,TimeUnit.SECONDS));
                assertEquals(1,relay.fragmentedCount());
            }
        }
    }
    @Test public void arbitraryHostsNeverReachConnector() throws Exception {
        try(LocalHttpsRelay relay=new LocalHttpsRelay(host -> { throw new AssertionError("Must not connect"); })) {
            try(Socket client=new Socket("127.0.0.1",URI.create(relay.proxyUrl()).getPort())) {
                client.setSoTimeout(3000);
                client.getOutputStream().write("CONNECT localhost:443 HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                assertTrue(readHeader(client.getInputStream()).startsWith("HTTP/1.1 403"));
            }
        }
    }
    @Test public void closingRelayStopsListener() throws Exception {
        LocalHttpsRelay relay=new LocalHttpsRelay(); int port=URI.create(relay.proxyUrl()).getPort();
        relay.close(); relay.close();
        try(Socket ignored=new Socket("127.0.0.1",port)) { fail("closed listener must refuse connection"); }
        catch(java.io.IOException expected) { }
    }
    private static String readHeader(InputStream input) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); int tail=0;
        while(out.size()<4096) { int b=input.read(); if(b<0)throw new java.io.IOException();
            out.write(b); tail=(tail<<8)|b; if(tail==0x0d0a0d0a)return out.toString("US-ASCII"); }
        throw new java.io.IOException();
    }
}
