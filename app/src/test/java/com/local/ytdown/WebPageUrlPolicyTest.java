package com.local.ytdown;

import org.junit.Test;
import static org.junit.Assert.*;

public class WebPageUrlPolicyTest {
    @Test public void acceptsHttpsPagesWithoutChangingQueries() {
        assertTrue(WebPageUrlPolicy.isAllowed("https://www.youtube.com/watch?v=abc&list=xyz"));
        assertTrue(WebPageUrlPolicy.isAllowed(" https://example.org/path?q=a%20b#fragment "));
        assertTrue(WebPageUrlPolicy.isAllowed("HTTPS://example.org:8443/"));
    }
    @Test public void notLimitedToDownloadSupportedSites() {
        assertTrue(WebPageUrlPolicy.isAllowed("https://example.org/"));
    }
    @Test public void blocksLocalExecutableAndCleartextSchemes() {
        for (String url : new String[]{"http://example.org/", "javascript:alert(1)",
                "file:///sdcard/a.html", "content://app/private", "intent://example.org/",
                "data:text/html,hello", "about:blank"}) assertFalse(url, WebPageUrlPolicy.isAllowed(url));
    }
    @Test public void rejectsCredentialsAndMalformedAddresses() {
        for (String url : new String[]{"https://user:secret@example.org/", "https://user@example.org/",
                "https:///path", "https://", "example.org", "https://example.org:0/",
                "https://example.org:70000/", "https://example.org/a b", ""}) {
            assertFalse(url, WebPageUrlPolicy.isAllowed(url));
        }
        assertFalse(WebPageUrlPolicy.isAllowed(null));
    }
}
