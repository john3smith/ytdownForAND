package com.local.ytdown;

import java.net.InetAddress;
import java.util.Locale;

final class HttpsRelayPolicy {
    static final String[] DOMAINS = {"pornhub.com", "phncdn.com", "phprcdn.com"};
    private HttpsRelayPolicy() { }

    static boolean allowedHost(String host) {
        if (host == null || host.length() > 253 || !host.matches("[A-Za-z0-9.-]+")) return false;
        String lower = host.toLowerCase(Locale.US);
        for (String label : lower.split("\\.", -1)) {
            if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) return false;
        }
        for (String domain : DOMAINS) {
            if (lower.equals(domain) || lower.endsWith("." + domain)) return true;
        }
        return false;
    }

    static String connectHost(String header) {
        if (header == null || header.length() > 4096 || !header.endsWith("\r\n\r\n")) return null;
        int end = header.indexOf("\r\n");
        if (end < 0) return null;
        String[] parts = header.substring(0, end).split(" ", -1);
        if (parts.length != 3 || !parts[0].equals("CONNECT")
                || !(parts[2].equals("HTTP/1.1") || parts[2].equals("HTTP/1.0"))) return null;
        if (!parts[1].endsWith(":443")) return null;
        String host = parts[1].substring(0, parts[1].length() - 4);
        return allowedHost(host) ? host.toLowerCase(Locale.US) : null;
    }

    static boolean publicAddress(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (bytes.length == 16) return (bytes[0] & 0xfe) != 0xfc;
        int a = bytes[0] & 255, b = bytes[1] & 255, c = bytes[2] & 255;
        return a != 0 && a < 224 && !(a == 100 && b >= 64 && b <= 127)
                && !(a == 198 && (b == 18 || b == 19))
                && !(a == 192 && b == 0 && (c == 0 || c == 2))
                && !(a == 198 && b == 51 && c == 100)
                && !(a == 203 && b == 0 && c == 113);
    }
}
