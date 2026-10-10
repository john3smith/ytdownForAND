package com.local.ytdown;

import org.json.JSONTokener;

/** Parse title events only, never print or persist media URLs/metadata JSON. */
final class DownloadTitleParser {
    static final String MARKER = "YTDownTitle=";
    private DownloadTitleParser() { }

    static String parse(String line) {
        if (line == null || line.length() > 8192 || !line.startsWith(MARKER)) return null;
        try {
            JSONTokener parser = new JSONTokener(line.substring(MARKER.length()));
            Object value = parser.nextValue();
            if (!(value instanceof String) || parser.nextClean() != 0) return null;
            String title = ((String) value).trim();
            return title.isEmpty() ? null : title;
        } catch (Exception invalid) {
            return null;
        }
    }
}
