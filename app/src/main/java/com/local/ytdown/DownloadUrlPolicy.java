package com.local.ytdown;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Shared validation for typed, pasted and shared download links. */
final class DownloadUrlPolicy {
    private DownloadUrlPolicy() {
    }

    static boolean isSupported(String value) {
        URI uri = parse(value);
        if (uri == null) return false;
        String host = uri.getHost().toLowerCase(Locale.US);
        if (isHost(host, "pornhub.com")) {
            return (uri.getPort() == -1 || uri.getPort() == 80 || uri.getPort() == 443)
                    && videoId(uri) != null;
        }
        return host.equals("youtu.be") || isHost(host, "youtube.com")
                || isHost(host, "youtube-nocookie.com") || isHost(host, "x.com")
                || isHost(host, "twitter.com") || isHost(host, "instagram.com")
                || isHost(host, "instagr.am");
    }

    static String normalize(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        URI uri = parse(trimmed);
        if (uri != null && isHost(uri.getHost().toLowerCase(Locale.US), "pornhub.com")
                && isSupported(trimmed)) {
            // The upstream extractor requires viewkey first. Drop tracking parameters,
            // use HTTPS and normalize embed/localized links to one video URL.
            return "https://www.pornhub.com/view_video.php?viewkey=" + videoId(uri);
        }
        return trimmed;
    }

    private static URI parse(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            if (uri.getHost() == null || uri.getUserInfo() != null || scheme == null
                    || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
                return null;
            }
            return uri;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static boolean isHost(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    private static String videoId(URI uri) {
        String path = uri.getPath();
        if (path != null && path.matches("/embed/[a-z0-9]+/?")) {
            return path.substring("/embed/".length()).replace("/", "");
        }
        if (!("/view_video.php".equals(path) || "/video/show".equals(path))) return null;
        String query = uri.getRawQuery();
        if (query == null) return null;
        String id = null;
        try {
            for (String parameter : query.split("&")) {
                String[] pair = parameter.split("=", 2);
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name());
                if (!"viewkey".equals(key)) continue;
                if (id != null || pair.length != 2) return null;
                id = URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name());
                if (!id.matches("[a-z0-9]{1,128}")) return null;
            }
            return id;
        } catch (IllegalArgumentException | java.io.UnsupportedEncodingException error) {
            return null;
        }
    }
}
