package com.local.ytdown;

import android.content.Context;
import android.text.TextUtils;
import android.webkit.CookieManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class AuthCookieStore {
    static final String YOUTUBE = "youtube";
    static final String X = "x";
    static final String INSTAGRAM = "instagram";

    private AuthCookieStore() {
    }

    static String platformForUrl(String value) {
        String lower = value.toLowerCase(Locale.US);
        if (lower.contains("youtu.be") || lower.contains("youtube.com")) {
            return YOUTUBE;
        }
        if (lower.contains("twitter.com") || lower.contains("x.com")) {
            return X;
        }
        if (lower.contains("instagram.com") || lower.contains("instagr.am")) {
            return INSTAGRAM;
        }
        return null;
    }

    static String loginUrl(String platform) {
        switch (platform) {
            case YOUTUBE:
                return "https://accounts.google.com/ServiceLogin?service=youtube"
                        + "&continue=https%3A%2F%2Fwww.youtube.com%2F";
            case X:
                return "https://x.com/i/flow/login";
            case INSTAGRAM:
                return "https://www.instagram.com/accounts/login/";
            default:
                throw new IllegalArgumentException("Unsupported platform: " + platform);
        }
    }

    static String displayName(String platform) {
        switch (platform) {
            case YOUTUBE:
                return "YouTube";
            case X:
                return "X";
            case INSTAGRAM:
                return "Instagram";
            default:
                return platform;
        }
    }

    static File exportCookies(Context context, String platform) throws IOException {
        LinkedHashMap<String, CookieEntry> entries = new LinkedHashMap<>();
        for (CookieSource source : sources(platform)) {
            String raw = CookieManager.getInstance().getCookie(source.url);
            if (TextUtils.isEmpty(raw)) {
                continue;
            }
            for (String pair : raw.split(";\\s*")) {
                int separator = pair.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String name = pair.substring(0, separator).trim();
                String value = pair.substring(separator + 1).trim();
                if (!name.isEmpty()) {
                    entries.put(source.domain + "\t" + name,
                            new CookieEntry(source.domain, name, value));
                }
            }
        }

        File file = cookieFile(context, platform);
        boolean authenticated = false;
        for (CookieEntry entry : entries.values()) {
            if (isAuthenticationCookie(platform, entry.name)) {
                authenticated = true;
                break;
            }
        }
        if (!authenticated) {
            file.delete();
            return null;
        }
        File directory = file.getParentFile();
        if (directory != null && !directory.exists() && !directory.mkdirs()) {
            throw new IOException("로그인 정보 폴더를 만들 수 없습니다.");
        }
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write("# Netscape HTTP Cookie File\n");
            for (CookieEntry entry : entries.values()) {
                writer.write(entry.domain);
                writer.write("\tTRUE\t/\tTRUE\t0\t");
                writer.write(entry.name);
                writer.write('\t');
                writer.write(entry.value);
                writer.write('\n');
            }
        }
        return file;
    }

    static boolean hasSavedCookies(Context context, String platform) {
        File file = cookieFile(context, platform);
        return file.isFile() && file.length() > 32;
    }

    static void clearCookies(Context context, String platform) {
        CookieManager manager = CookieManager.getInstance();
        for (CookieSource source : sources(platform)) {
            String raw = manager.getCookie(source.url);
            if (TextUtils.isEmpty(raw)) {
                continue;
            }
            for (String pair : raw.split(";\\s*")) {
                int separator = pair.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String name = pair.substring(0, separator).trim();
                manager.setCookie(source.url,
                        name + "=; Max-Age=0; Domain=" + source.domain
                                + "; Path=/; Secure; SameSite=None");
            }
        }
        manager.flush();
        cookieFile(context, platform).delete();
    }

    private static File cookieFile(Context context, String platform) {
        return new File(new File(context.getFilesDir(), "cookies"), platform + ".txt");
    }

    private static boolean isAuthenticationCookie(String platform, String name) {
        switch (platform) {
            case YOUTUBE:
                return name.equals("SAPISID") || name.equals("LOGIN_INFO")
                        || name.equals("__Secure-1PAPISID")
                        || name.equals("__Secure-3PAPISID");
            case X:
                return name.equals("auth_token");
            case INSTAGRAM:
                return name.equals("sessionid");
            default:
                return false;
        }
    }

    private static List<CookieSource> sources(String platform) {
        ArrayList<CookieSource> result = new ArrayList<>();
        switch (platform) {
            case YOUTUBE:
                result.add(new CookieSource("https://www.youtube.com/", ".youtube.com"));
                break;
            case X:
                result.add(new CookieSource("https://x.com/", ".x.com"));
                result.add(new CookieSource("https://twitter.com/", ".twitter.com"));
                break;
            case INSTAGRAM:
                result.add(new CookieSource(
                        "https://www.instagram.com/", ".instagram.com"));
                break;
            default:
                throw new IllegalArgumentException("Unsupported platform: " + platform);
        }
        return result;
    }

    private static final class CookieSource {
        final String url;
        final String domain;

        CookieSource(String url, String domain) {
            this.url = url;
            this.domain = domain;
        }
    }

    private static final class CookieEntry {
        final String domain;
        final String name;
        final String value;

        CookieEntry(String domain, String name, String value) {
            this.domain = domain;
            this.name = name;
            this.value = value;
        }
    }
}
