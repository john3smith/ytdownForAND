package com.local.ytdown;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;

final class DownloadLogStore {
    private static final String PREFS = "download_log";
    private static final String KEY_TEXT = "text";
    private static final int MAX_CHARS = 60_000;
    private static final long RETENTION_MILLIS = 24L * 60 * 60 * 1000;

    private DownloadLogStore() {
    }

    static synchronized void start(Context context, String url, String format) {
        append(context, "---------------- 새 다운로드 ----------------");
        append(context, "주소: " + safeUrl(url));
        append(context, "선택 포맷: " + format);
    }

    static synchronized void append(Context context, String message) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String current = preferences.getString(KEY_TEXT, "");
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)
                .format(new Date()) + "  " + clean(message) + "\n";
        String updated = retainRecent(current + line, System.currentTimeMillis());
        if (updated.length() > MAX_CHARS) {
            int start = updated.length() - MAX_CHARS;
            int nextLine = updated.indexOf('\n', start);
            updated = updated.substring(nextLine >= 0 ? nextLine + 1 : start);
        }
        preferences.edit().putString(KEY_TEXT, updated).apply();
    }

    static synchronized String read(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String current = preferences.getString(KEY_TEXT, "");
        String recent = retainRecent(current, System.currentTimeMillis());
        if (!recent.equals(current)) {
            preferences.edit().putString(KEY_TEXT, recent).apply();
        }
        return recent;
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
                .edit().remove(KEY_TEXT).apply();
    }

    private static String safeUrl(String value) {
        try {
            URI uri = URI.create(value);
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null)
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
