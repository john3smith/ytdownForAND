package com.local.ytdown;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;

final class DownloadLogStore {
    private static final String PREFS = "download_log";
    private static final String KEY_TEXT = "text";
    private static final String KEY_ENTRIES = "entries_v2";
    private static final String SYSTEM = "system";
    private static final int MAX_CHARS = 60_000;
    private static final long RETENTION_MILLIS = 24L * 60 * 60 * 1000;

    private DownloadLogStore() {
    }

    static final class Entry {
        final String key;
        final String url;
        String text;

        Entry(String key, String url, String text) {
            this.key = key;
            this.url = url;
            this.text = text;
        }

        String summary() {
            String trimmed = text.trim();
            int line = trimmed.lastIndexOf('\n');
            return trimmed.substring(line + 1);
        }
    }

    static synchronized void start(Context context, String url, String format) {
        append(context, url, "---------------- 새 다운로드 ----------------");
        append(context, url, "주소: " + safeUrl(url));
        append(context, url, "선택 포맷: " + format);
    }

    static synchronized void append(Context context, String message) {
        append(context, null, message);
    }

    static synchronized void append(Context context, String url, String message) {
        List<Entry> entries = load(context);
        String key = url == null ? SYSTEM : identity(url);
        Entry entry = null;
        for (Entry candidate : entries) {
            if (candidate.key.equals(key)) { entry = candidate; break; }
        }
        if (entry == null) entry = new Entry(key, url == null ? "" : safeUrl(url), "");
        entries.remove(entry);
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)
                .format(new Date()) + "  " + clean(message) + "\n";
        entry.text += line;
        entries.add(entry);
        save(context, entries);
    }

    static synchronized String read(Context context) {
        StringBuilder text = new StringBuilder();
        for (Entry entry : load(context)) text.append(entry.text);
        return text.toString();
    }

    static synchronized List<Entry> links(Context context) {
        List<Entry> entries = load(context);
        entries.removeIf(entry -> entry.key.equals(SYSTEM));
        Collections.reverse(entries);
        return entries;
    }

    static synchronized String systemLog(Context context) {
        for (Entry entry : load(context)) if (entry.key.equals(SYSTEM)) return entry.text;
        return "";
    }

    private static List<Entry> load(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<Entry> entries = new ArrayList<>();
        if (!preferences.contains(KEY_ENTRIES)) {
            // Old logs removed URL queries, so separate legacy sessions rather than
            // incorrectly combining different YouTube watch links.
            String legacy = retainRecent(preferences.getString(KEY_TEXT, ""), System.currentTimeMillis());
            Entry current = new Entry(SYSTEM, "", "");
            for (String line : legacy.split("(?<=\n)")) {
                if (line.contains("---------------- 새 다운로드 ----------------")) {
                    if (!current.text.isEmpty()) entries.add(current);
                    current = new Entry("legacy:" + entries.size(), "", "");
                }
                if (line.length() > 25 && line.substring(21).startsWith("주소: ")) {
                    String url = line.substring(25).trim();
                    // Query-less watch URLs have lost their video ID in old versions.
                    String key = url.matches("https?://([^/]+\\.)?youtube\\.com/watch")
                            ? current.key : identity(url);
                    current = new Entry(key, url, current.text);
                }
                current.text += line;
            }
            if (!current.text.isEmpty()) entries.add(current);
            List<Entry> merged = new ArrayList<>();
            for (Entry entry : entries) {
                Entry previous = null;
                for (Entry candidate : merged) if (candidate.key.equals(entry.key)) previous = candidate;
                if (previous != null) {
                    entry.text = previous.text + entry.text;
                    merged.remove(previous);
                }
                merged.add(entry);
            }
            entries = merged;
        } else {
            try {
                JSONArray array = new JSONArray(preferences.getString(KEY_ENTRIES, "[]"));
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.getJSONObject(i);
                    entries.add(new Entry(item.getString("key"), item.getString("url"), item.getString("text")));
                }
            } catch (JSONException malformed) {
                // Do not overwrite unreadable persisted data.
                throw new IllegalStateException("다운로드 로그를 읽을 수 없습니다.", malformed);
            }
        }
        save(context, entries);
        return entries;
    }

    private static void save(Context context, List<Entry> entries) {
        int remaining = MAX_CHARS;
        long now = System.currentTimeMillis();
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = entries.get(i);
            entry.text = retainRecent(entry.text, now);
            if (entry.text.length() > remaining) {
                int newline = entry.text.indexOf('\n', entry.text.length() - remaining);
                entry.text = newline < 0 ? "" : entry.text.substring(newline + 1);
            }
            remaining -= entry.text.length();
            if (entry.text.isEmpty()) entries.remove(i);
        }
        JSONArray array = new JSONArray();
        try {
            for (Entry entry : entries) array.put(new JSONObject().put("key", entry.key)
                    .put("url", entry.url).put("text", entry.text));
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String encoded = array.toString();
        if (!encoded.equals(preferences.getString(KEY_ENTRIES, null)) || preferences.contains(KEY_TEXT)) {
            preferences.edit().putString(KEY_ENTRIES, encoded).remove(KEY_TEXT).apply();
        }
    }

    private static String identity(String url) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(url.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder();
            for (byte value : digest) key.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            return key.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static String retainRecent(String text, long nowMillis) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        long cutoff = nowMillis - RETENTION_MILLIS;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA);
        format.setLenient(false);
        StringBuilder kept = new StringBuilder(text.length());
        int offset = 0;
        while (offset < text.length()) {
            int end = text.indexOf('\n', offset);
            if (end < 0) {
                end = text.length();
            }
            if (end - offset >= 19) {
                try {
                    Date timestamp = format.parse(text.substring(offset, offset + 19));
                    if (timestamp != null && timestamp.getTime() >= cutoff) {
                        kept.append(text, offset, end);
                        if (end < text.length()) {
                            kept.append('\n');
                        }
                    }
                } catch (ParseException ignored) {
                    // Undated or malformed legacy entries cannot be retained safely.
                }
            }
            offset = end + 1;
        }
        return kept.toString();
    }

    static synchronized void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_TEXT).remove(KEY_ENTRIES).apply();
    }

    private static String safeUrl(String value) {
        try {
            URI uri = URI.create(value);
            String query = null;
            String host = uri.getHost();
            String publicId = host != null && (host.equals("youtube.com") || host.endsWith(".youtube.com"))
                    ? "v" : host != null && (host.equals("pornhub.com") || host.endsWith(".pornhub.com"))
                    ? "viewkey" : null;
            if (publicId != null && uri.getRawQuery() != null) {
                for (String parameter : uri.getRawQuery().split("&")) {
                    if (parameter.matches(publicId + "=[A-Za-z0-9_-]+")) query = parameter;
                }
            }
            return new URI(uri.getScheme(), null, host, uri.getPort(), uri.getPath(), query, null)
                    .toString();
        } catch (Exception ignored) {
            return "주소 형식 확인 불가";
        }
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String clean = value.replace('\r', ' ').replace('\n', ' ').trim();
        return clean.length() <= 500 ? clean : clean.substring(0, 497) + "...";
    }
}
