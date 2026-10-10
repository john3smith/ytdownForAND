package com.local.ytdown;

import android.content.Context;
import android.text.TextUtils;
import android.webkit.CookieManager;
import android.util.AtomicFile;
import androidx.webkit.CookieManagerCompat;
import androidx.webkit.WebViewFeature;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
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
    static final String PORNHUB = "pornhub";

    private AuthCookieStore() {
    }

    static String platformForUrl(String value) {
        String host;
        try {
            if (value == null) return null;
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            if (uri.getHost() == null || uri.getUserInfo() != null || scheme == null
                    || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
                return null;
            }
            host = uri.getHost().toLowerCase(Locale.US);
        } catch (IllegalArgumentException error) {
            return null;
        }
        if (host.equals("youtu.be") || hostMatches(host, "youtube.com")
                || hostMatches(host, "youtube-nocookie.com")) {
            return YOUTUBE;
        }
        if (hostMatches(host, "twitter.com") || hostMatches(host, "x.com")) {
            return X;
        }
        if (hostMatches(host, "instagram.com") || hostMatches(host, "instagr.am")) {
            return INSTAGRAM;
        }
        return hostMatches(host, "pornhub.com") ? PORNHUB : null;
    }

    private static boolean hostMatches(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    static boolean isTrustedLoginPage(String url, String platform) {
        if (url == null || platform == null || !platform.equals(platformForUrl(url))) return false;
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && (uri.getPort() == -1 || uri.getPort() == 443);
        } catch (IllegalArgumentException error) {
            return false;
        }
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
            case PORNHUB:
                return "https://www.pornhub.com/login";
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
            case PORNHUB:
                return "***";
            default:
                return platform;
        }
    }

    static File exportCookies(Context context, String platform) throws IOException {
        return exportCookies(context, platform, false);
    }

    static File exportCookies(Context context, String platform, boolean browserLoginConfirmed)
            throws IOException {
        File file = cookieFile(context, platform);
        if (PORNHUB.equals(platform) && !browserLoginConfirmed) {
            // Reuse only a session explicitly confirmed inside the trusted login page.
            // Age-consent/guest cookies alone must never be labelled an account login.
            return hasSavedCookies(context, platform) ? file : null;
        }
        LinkedHashMap<String, NetscapeCookie> entries = new LinkedHashMap<>();
        CookieManager manager = CookieManager.getInstance();
        long nowSeconds = System.currentTimeMillis() / 1000;
        // Other platforms have legacy direct-media cookie readers. Keep their
        // export format unchanged; the detailed format is scoped to this fix.
        boolean detailed = PORNHUB.equals(platform)
                && WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO);
        for (CookieSource source : sources(platform)) {
            if (detailed) {
                for (String header : CookieManagerCompat.getCookieInfo(manager, source.url)) {
                    NetscapeCookie entry = NetscapeCookie.parse(source.url, header, nowSeconds);
                    if (entry != null) entries.put(entry.key(), entry);
                }
                continue;
            }
            String raw = manager.getCookie(source.url);
            if (TextUtils.isEmpty(raw)) {
                continue;
            }
            for (String pair : raw.split(";\\s*")) {
                NetscapeCookie entry = PORNHUB.equals(platform)
                        ? NetscapeCookie.fallback(source.url, pair, nowSeconds)
                        : NetscapeCookie.parse(source.url,
                                pair + "; Domain=" + source.domain + "; Path=/; Secure", nowSeconds);
                if (entry != null) entries.put(entry.key(), entry);
            }
        }

        boolean authenticated = PORNHUB.equals(platform)
                && browserLoginConfirmed && !entries.isEmpty();
        for (NetscapeCookie entry : entries.values()) {
            if (isAuthenticationCookie(platform, entry.name)) {
                authenticated = true;
                break;
            }
        }
        if (!authenticated) {
            if (!PORNHUB.equals(platform)) file.delete();
            return null;
        }
        File directory = file.getParentFile();
        if (directory != null && !directory.exists() && !directory.mkdirs()) {
            throw new IOException("로그인 정보 폴더를 만들 수 없습니다.");
        }
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream stream = atomic.startWrite();
        try {
            OutputStreamWriter writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
            writer.write("# Netscape HTTP Cookie File\n");
            for (NetscapeCookie entry : entries.values()) writer.write(entry.line());
            writer.flush();
            atomic.finishWrite(stream);
        } catch (IOException | RuntimeException error) {
            atomic.failWrite(stream);
            throw error;
        }
        return file;
    }

    static boolean hasSavedCookies(Context context, String platform) {
        File file = cookieFile(context, platform);
        return file.isFile() && file.length() > 32;
    }

    static void clearCookies(Context context, String platform) {
        CookieManager manager = CookieManager.getInstance();
        boolean detailed = PORNHUB.equals(platform)
                && WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO);
        for (CookieSource source : sources(platform)) {
            if (detailed) {
                for (String header : CookieManagerCompat.getCookieInfo(manager, source.url)) {
                    NetscapeCookie entry = NetscapeCookie.parse(source.url, header,
                            System.currentTimeMillis() / 1000);
                    if (entry != null) manager.setCookie(source.url, entry.expirationHeader());
                }
                continue;
            }
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
                // Older WebViews do not expose domain scope. Remove only this
                // platform's observed names in both host-only and domain form.
                manager.setCookie(source.url, name + "=; Max-Age=0; Path=/; Secure");
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
            case PORNHUB:
                result.add(new CookieSource("https://pornhub.com/", ".pornhub.com"));
                result.add(new CookieSource("https://www.pornhub.com/", ".pornhub.com"));
                result.add(new CookieSource("https://www.pornhub.com/view_video.php", ".pornhub.com"));
                result.add(new CookieSource("https://www.pornhub.com/login", ".pornhub.com"));
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

}
