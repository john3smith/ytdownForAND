package com.local.ytdown;

import java.net.URI;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Converts Chromium's canonical cookie metadata, not arbitrary response headers. */
final class NetscapeCookie {
    final String domain, path, name, value;
    final boolean includeSubdomains, secure, httpOnly;
    final long expires;

    private NetscapeCookie(String domain, boolean includeSubdomains, String path,
                           boolean secure, boolean httpOnly, long expires, String name, String value) {
        this.domain = domain;
        this.includeSubdomains = includeSubdomains;
        this.path = path;
        this.secure = secure;
        this.httpOnly = httpOnly;
        this.expires = expires;
        this.name = name;
        this.value = value;
    }

    static NetscapeCookie parse(String sourceUrl, String header, long nowSeconds) {
        try {
            URI source = URI.create(sourceUrl);
            String host = source.getHost();
            if (host == null || !"https".equalsIgnoreCase(source.getScheme())
                    || source.getUserInfo() != null || !safe(header)) return null;
            host = host.toLowerCase(Locale.US);
            String[] parts = header.split(";", -1);
            int separator = parts[0].indexOf('=');
            if (separator <= 0) return null;
            String name = parts[0].substring(0, separator).trim();
            String value = parts[0].substring(separator + 1).trim();
            if (!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) return null;
            String domain = host;
            boolean domainSet = false, includeSubdomains = false, secure = false, httpOnly = false;
            String requestPath = source.getPath();
            int lastSlash = requestPath == null ? -1 : requestPath.lastIndexOf('/');
            String path = lastSlash > 0 ? requestPath.substring(0, lastSlash) : "/";
            Long expires = null, maxAge = null;
            for (int i = 1; i < parts.length; i++) {
                String[] attribute = parts[i].trim().split("=", 2);
                String key = attribute[0].trim().toLowerCase(Locale.US);
                String setting = attribute.length == 2 ? attribute[1].trim() : "";
                switch (key) {
                    case "domain":
                        if (domainSet) return null;
                        domainSet = true;
                        domain = setting.toLowerCase(Locale.US);
                        // Chromium includes a Domain attribute even for host-only cookies.
                        // Its canonical leading dot, not attribute presence, identifies scope.
                        includeSubdomains = domain.startsWith(".");
                        if (includeSubdomains) domain = domain.substring(1);
                        if (!domain.matches("[a-z0-9-]+(?:\\.[a-z0-9-]+)+")
                                || !(host.equals(domain) || host.endsWith("." + domain))) return null;
                        if (!includeSubdomains && !host.equals(domain)) return null;
                        // Do not turn a source cookie into a TLD-wide cookie.
                        String base = AuthCookieStore.platformForUrl("https://" + domain + "/");
                        if (base == null || !base.equals(AuthCookieStore.platformForUrl(sourceUrl))) return null;
                        break;
                    case "path":
                        if (setting.startsWith("/")) path = setting;
                        break;
                    case "secure": secure = true; break;
                    case "httponly": httpOnly = true; break;
                    case "partitioned": return null; // Netscape has no partition key representation.
                    case "max-age": maxAge = Long.parseLong(setting); break;
                    case "expires":
                        expires = parseDate(setting);
                        if (expires == null) return null;
                        break;
                    default: break; // SameSite is enforced by WebView, not a Netscape jar.
                }
            }
            long expiration = expires == null ? 0 : expires;
            if (maxAge != null) {
                if (maxAge <= 0) return null;
                expiration = maxAge > Long.MAX_VALUE - nowSeconds ? Long.MAX_VALUE : nowSeconds + maxAge;
            }
            if ((maxAge != null || expires != null) && expiration <= nowSeconds) return null;
            if (name.startsWith("__Secure-") && !secure) return null;
            if (name.startsWith("__Host-") && (!secure || includeSubdomains || !"/".equals(path))) return null;
            return new NetscapeCookie(includeSubdomains ? "." + domain : domain, includeSubdomains, path,
                    secure, httpOnly, expiration, name, value);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    /** Older WebViews expose no attributes. Keep each observed cookie host-only. */
    static NetscapeCookie fallback(String sourceUrl, String pair, long nowSeconds) {
        return parse(sourceUrl, pair + "; Path=/; Secure", nowSeconds);
    }

    String key() { return domain + "\t" + path + "\t" + name; }

    String expirationHeader() {
        return name + "=; Max-Age=0; Path=" + path
                + (includeSubdomains ? "; Domain=" + domain : "")
                + (secure ? "; Secure" : "") + (httpOnly ? "; HttpOnly" : "");
    }

    String line() {
        return (httpOnly ? "#HttpOnly_" : "") + domain + "\t"
                + (includeSubdomains ? "TRUE" : "FALSE") + "\t" + path + "\t"
                + (secure ? "TRUE" : "FALSE") + "\t" + expires + "\t" + name + "\t" + value + "\n";
    }

    private static boolean safe(String text) {
        if (text == null || text.length() > 16384) return false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch < 0x20 || ch > 0x7e) return false;
        }
        return true;
    }

    private static Long parseDate(String value) {
        for (String pattern : new String[]{"EEE, dd MMM yyyy HH:mm:ss zzz",
                "EEE, dd-MMM-yyyy HH:mm:ss zzz", "EEE, dd MMM yy HH:mm:ss zzz"}) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setLenient(false);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
            ParsePosition position = new ParsePosition(0);
            Date parsed = format.parse(value, position);
            if (parsed != null && position.getIndex() == value.length()) return parsed.getTime() / 1000;
        }
        return null;
    }
}
