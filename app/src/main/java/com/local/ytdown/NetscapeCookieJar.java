package com.local.ytdown;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Request-scoped Netscape cookie selection; never flatten a jar across hosts. */
final class NetscapeCookieJar {
    private static final int MAX_FILE_BYTES = 1024 * 1024;
    private static final int MAX_HEADER_CHARS = 16 * 1024;
    private final List<Entry> entries;

    private NetscapeCookieJar(List<Entry> entries) { this.entries = entries; }

    static NetscapeCookieJar load(File file) {
        if (file == null || !file.isFile()) return null;
        try (FileInputStream input = new FileInputStream(file)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() > MAX_FILE_BYTES - count) throw new IOException("cookie jar too large");
                bytes.write(buffer, 0, count);
            }
            return parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException invalid) { return null; }
    }

    static NetscapeCookieJar parse(String text) {
        List<Entry> entries = new ArrayList<>();
        if (text.length() > MAX_FILE_BYTES) return new NetscapeCookieJar(entries);
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.startsWith("#HttpOnly_") ? raw.substring(10) : raw;
            if (line.isEmpty() || line.startsWith("#") || line.length() > MAX_HEADER_CHARS) continue;
            String[] fields = line.split("\\t", -1);
            if (fields.length != 7 || !flag(fields[1]) || !flag(fields[3])) continue;
            String domain = fields[0].toLowerCase(Locale.US);
            if (domain.startsWith(".")) domain = domain.substring(1);
            if (!domain.matches("[a-z0-9-]+(?:\\.[a-z0-9-]+)*") || !fields[2].startsWith("/")) continue;
            if (fields[1].equals("TRUE") && domain.indexOf('.') < 0) continue;
            if (!fields[5].matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+") || !safeValue(fields[6])) continue;
            try {
                long expires = Long.parseLong(fields[4]);
                if (expires < 0) continue;
                entries.add(new Entry(domain, fields[1].equals("TRUE"), fields[2],
                        fields[3].equals("TRUE"), expires, fields[5] + "=" + fields[6]));
            } catch (NumberFormatException ignored) { }
        }
        // More specific paths must precede cookies with the same name at '/'.
        entries.sort(Comparator.comparingInt((Entry entry) -> entry.path.length()).reversed());
        return new NetscapeCookieJar(entries);
    }

    String headerFor(String url, long nowSeconds) {
        URI request;
        try { request = URI.create(url); }
        catch (IllegalArgumentException invalid) { return null; }
        String scheme = request.getScheme(), host = request.getHost();
        if (host == null || request.getUserInfo() != null ||
                !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) return null;
        host = host.toLowerCase(Locale.US);
        String path = request.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        StringBuilder header = new StringBuilder();
        for (Entry entry : entries) {
            if (!(host.equals(entry.domain) || (entry.subdomains && host.endsWith("." + entry.domain)))) continue;
            if (entry.secure && !"https".equalsIgnoreCase(scheme)) continue;
            if (entry.expires != 0 && entry.expires <= nowSeconds) continue;
            if (!(path.equals(entry.path) || (path.startsWith(entry.path) &&
                    (entry.path.endsWith("/") || path.charAt(entry.path.length()) == '/')))) continue;
            int added = entry.pair.length() + (header.length() == 0 ? 0 : 2);
            if (header.length() > MAX_HEADER_CHARS - added) return null;
            if (header.length() > 0) header.append("; ");
            header.append(entry.pair);
        }
        return header.length() == 0 ? null : header.toString();
    }

    private static boolean flag(String value) { return value.equals("TRUE") || value.equals("FALSE"); }
    private static boolean safeValue(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < 0x20 || ch > 0x7e || ch == ';') return false;
        }
        return true;
    }

    private static final class Entry {
        final String domain, path, pair;
        final boolean subdomains, secure;
        final long expires;
        Entry(String domain, boolean subdomains, String path, boolean secure, long expires, String pair) {
            this.domain = domain; this.subdomains = subdomains; this.path = path;
            this.secure = secure; this.expires = expires; this.pair = pair;
        }
    }
}
