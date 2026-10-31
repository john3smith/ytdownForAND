package com.local.ytdown;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.BooleanSupplier;

/** Re-select cookies at every redirect instead of forwarding a flattened header. */
final class ScopedHttpConnection {
    static HttpURLConnection open(String url, NetscapeCookieJar cookies, String userAgent) throws IOException {
        return open(url, cookies, userAgent, () -> false);
    }

    static HttpURLConnection open(String url, NetscapeCookieJar cookies, String userAgent,
                                  BooleanSupplier cancelled) throws IOException {
        URL current = new URL(url);
        for (int redirects = 0; redirects <= 5; redirects++) {
            if (cancelled.getAsBoolean()) throw new InterruptedIOException("metadata request cancelled");
            if (!(current.getProtocol().equalsIgnoreCase("https") || current.getProtocol().equalsIgnoreCase("http"))
                    || current.getUserInfo() != null) throw new IOException("unsupported media URL");
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            try {
                connection.setConnectTimeout(15_000);
                connection.setReadTimeout(30_000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", userAgent);
                connection.setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8");
                String header = cookies == null ? null : cookies.headerFor(current.toExternalForm(),
                        System.currentTimeMillis() / 1000);
                if (header != null) connection.setRequestProperty("Cookie", header);
                int status = connection.getResponseCode();
                if (cancelled.getAsBoolean()) throw new InterruptedIOException("metadata request cancelled");
                if (status >= 200 && status < 300) return connection;
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.trim().isEmpty()) throw new IOException("redirect has no location");
                    URL next = new URL(current, location);
                    if (current.getProtocol().equalsIgnoreCase("https") && !next.getProtocol().equalsIgnoreCase("https"))
                        throw new IOException("insecure redirect rejected");
                    current = next;
                } else {
                    throw new IOException("HTTP " + status);
                }
            } catch (IOException | RuntimeException failure) {
                connection.disconnect();
                throw failure;
            }
            connection.disconnect();
        }
        throw new IOException("too many redirects");
    }

    private ScopedHttpConnection() { }
}
