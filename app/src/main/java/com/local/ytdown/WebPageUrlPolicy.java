package com.local.ytdown;

import java.net.URI;

/** Web pages are separate from download eligibility; never accept local/intent URLs. */
final class WebPageUrlPolicy {
    private WebPageUrlPolicy() { }

    static boolean isAllowed(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        try {
            URI uri = URI.create(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null && !uri.getHost().isEmpty()
                    && uri.getRawUserInfo() == null
                    && (uri.getPort() == -1 || (uri.getPort() > 0 && uri.getPort() <= 65535));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
