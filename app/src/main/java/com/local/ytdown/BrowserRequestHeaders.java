package com.local.ytdown;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/** Browser-compatible HTTP headers, not TLS/browser impersonation. */
final class BrowserRequestHeaders {
    private BrowserRequestHeaders() { }

    static String chromeUserAgent(String webViewAgent) {
        if (webViewAgent == null || webViewAgent.length() > 1024
                || !webViewAgent.matches("[\\x20-\\x7e]+")
                || !webViewAgent.matches(".*\\bChrome/[0-9]+(?:\\.[0-9]+)*.*")) return null;
        // Keep the device and installed Chromium version; do not invent a version.
        return webViewAgent.replaceAll(";\\s*wv\\b", "")
                .replaceAll("\\bVersion/[0-9]+(?:\\.[0-9]+)*\\s*", "");
    }

    static Map<String, String> forUrl(String url, String webViewAgent) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
                return Collections.emptyMap();
            }
            host = host.toLowerCase(Locale.US);
            if (!(host.equals("pornhub.com") || host.endsWith(".pornhub.com"))) {
                return Collections.emptyMap();
            }
            String agent = chromeUserAgent(webViewAgent);
            if (agent == null) return Collections.emptyMap();
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("User-Agent", agent);
            headers.put("Referer", "https://www.pornhub.com/");
            headers.put("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
            return Collections.unmodifiableMap(headers);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            return Collections.emptyMap();
        }
    }

    static boolean apply(String url, String webViewAgent, BiConsumer<String, String> option) {
        Map<String, String> headers = forUrl(url, webViewAgent);
        if (headers.isEmpty()) return false;
        option.accept("--user-agent", headers.get("User-Agent"));
        option.accept("--referer", headers.get("Referer"));
        option.accept("--add-headers", "Accept-Language:" + headers.get("Accept-Language"));
        return true;
    }
}
