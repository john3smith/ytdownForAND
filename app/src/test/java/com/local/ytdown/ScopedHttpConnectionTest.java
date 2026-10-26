package com.local.ytdown;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScopedHttpConnectionTest {
    @Test public void redirectRecomputesCookiesForTheNewHostAndPath() throws Exception {
        AtomicReference<String> initialCookie = new AtomicReference<>();
        AtomicReference<String> redirectedCookie = new AtomicReference<>();
        try (FixtureServer server = new FixtureServer((path, headers, port) -> {
            if (path.equals("/private/start")) {
                initialCookie.set(headers.get("cookie"));
                return redirect("http://localhost:" + port + "/public/end");
            }
            assertEquals("/public/end", path);
            redirectedCookie.set(headers.get("cookie"));
            return "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
        })) {
            NetscapeCookieJar jar = NetscapeCookieJar.parse(
                    "127.0.0.1\tFALSE\t/private\tFALSE\t0\tprivate\tsynthetic\n"
                    + "localhost\tFALSE\t/public\tFALSE\t0\tpublic\tsynthetic\n");
            HttpURLConnection connection = ScopedHttpConnection.open(
                    "http://127.0.0.1:" + server.port() + "/private/start", jar, "SyntheticTest");
            try { assertEquals(200, connection.getResponseCode()); }
            finally { connection.disconnect(); }
            assertEquals("private=synthetic", initialCookie.get());
            assertEquals("public=synthetic", redirectedCookie.get());
            assertEquals(2, server.requests.get());
        }
    }

    @Test public void redirectLoopsAreBounded() throws Exception {
        try (FixtureServer server = new FixtureServer((path, headers, port) -> {
            assertEquals("/loop", path);
            return redirect("/loop");
        })) {
            try {
                ScopedHttpConnection.open("http://127.0.0.1:" + server.port() + "/loop", null, "SyntheticTest");
                fail("redirect loop accepted");
            } catch (IOException expected) {
                assertEquals("too many redirects", expected.getMessage());
            }
            assertEquals(6, server.requests.get());
        }
    }

    private static String redirect(String location) {
        return "HTTP/1.1 302 Found\r\nLocation: " + location
                + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
    }

    private interface Handler {
        String response(String path, Map<String, String> headers, int port);
    }

    /** Uses Android-visible java.net APIs; every socket and worker has bounded cleanup. */
    private static final class FixtureServer implements AutoCloseable {
        private final ServerSocket listener = new ServerSocket();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicInteger requests = new AtomicInteger();
        private final Thread worker;
        private volatile boolean closed;
        private volatile Socket active;

        FixtureServer(Handler handler) throws IOException {
            listener.bind(new InetSocketAddress("127.0.0.1", 0));
            listener.setSoTimeout(500);
            worker = new Thread(() -> serve(handler), "synthetic-http-fixture");
            worker.setDaemon(true);
            worker.start();
        }

        int port() { return listener.getLocalPort(); }

        private void serve(Handler handler) {
            while (!closed) {
                Socket socket;
                try { socket = listener.accept(); }
                catch (SocketTimeoutException idle) { continue; }
                catch (IOException error) {
                    if (!closed) failure.compareAndSet(null, error);
                    return;
                }
                try (Socket client = socket) {
                    active = client;
                    if (closed) return;
                    client.setSoTimeout(2000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                            client.getInputStream(), StandardCharsets.US_ASCII));
                    String request = reader.readLine();
                    if (request == null) throw new IOException("missing fixture request");
                    String[] parts = request.split(" ", 3);
                    if (parts.length != 3 || !parts[0].equals("GET"))
                        throw new IOException("invalid fixture request");
                    Map<String, String> headers = new HashMap<>();
                    String line;
                    int total = request.length();
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        total += line.length();
                        if (total > 16384) throw new IOException("fixture headers too large");
                        int separator = line.indexOf(':');
                        if (separator <= 0) throw new IOException("invalid fixture header");
                        headers.put(line.substring(0, separator).toLowerCase(Locale.US),
                                line.substring(separator + 1).trim());
                    }
                    if (line == null) throw new IOException("incomplete fixture headers");
                    requests.incrementAndGet();
                    byte[] response = handler.response(parts[1], headers, port())
                            .getBytes(StandardCharsets.US_ASCII);
                    client.getOutputStream().write(response);
                    client.getOutputStream().flush();
                } catch (Throwable error) {
                    if (!(closed && error instanceof SocketException)) failure.compareAndSet(null, error);
                    return;
                } finally { active = null; }
            }
        }

        @Override public void close() throws Exception {
            closed = true;
            listener.close();
            Socket client = active;
            if (client != null) client.close();
            worker.join(5000);
            assertFalse("fixture worker did not stop", worker.isAlive());
            Throwable error = failure.get();
            if (error != null) throw new AssertionError("fixture failed", error);
        }
    }
}
