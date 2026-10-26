package com.local.ytdown;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScopedHttpConnectionTest {
    @Test public void redirectRecomputesCookiesForTheNewHostAndPath() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        AtomicReference<String> initialCookie = new AtomicReference<>();
        AtomicReference<String> redirectedCookie = new AtomicReference<>();
        server.createContext("/private/start", exchange -> {
            initialCookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
            exchange.getResponseHeaders().add("Location", "http://localhost:" + port + "/public/end");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/public/end", exchange -> {
            redirectedCookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            NetscapeCookieJar jar = NetscapeCookieJar.parse(
                    "127.0.0.1\tFALSE\t/private\tFALSE\t0\tprivate\tsynthetic\n"
                    + "localhost\tFALSE\t/public\tFALSE\t0\tpublic\tsynthetic\n");
            HttpURLConnection connection = ScopedHttpConnection.open(
                    "http://127.0.0.1:" + port + "/private/start", jar, "SyntheticTest");
            try { assertEquals(200, connection.getResponseCode()); }
            finally { connection.disconnect(); }
            assertEquals("private=synthetic", initialCookie.get());
            assertEquals("public=synthetic", redirectedCookie.get());
        } finally { server.stop(0); }
    }

    @Test public void redirectLoopsAreBounded() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/loop", exchange -> {
            exchange.getResponseHeaders().add("Location", "/loop");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.start();
        try {
            try {
                ScopedHttpConnection.open("http://127.0.0.1:" + server.getAddress().getPort() + "/loop", null, "SyntheticTest");
                fail("redirect loop accepted");
            } catch (java.io.IOException expected) { }
        } finally { server.stop(0); }
    }
}
