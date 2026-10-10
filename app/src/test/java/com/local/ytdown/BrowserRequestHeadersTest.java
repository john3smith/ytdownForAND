package com.local.ytdown;

import static org.junit.Assert.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class BrowserRequestHeadersTest {
    private static final String AGENT = "Mozilla/5.0 (Linux; Android 15; TestDevice Build/TEST; wv) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0.6367.219 Mobile Safari/537.36";

    @Test public void usesRealChromiumVersionWithoutWebViewMarkers() {
        String agent = BrowserRequestHeaders.chromeUserAgent(AGENT);
        assertNotNull(agent);
        assertFalse(agent.contains("; wv"));
        assertFalse(agent.contains("Version/4.0"));
        assertTrue(agent.contains("Chrome/124.0.6367.219"));
        assertTrue(agent.contains("Android 15"));
        assertTrue(agent.contains("Mobile Safari/537.36"));
    }

    @Test public void regularChromeAgentIsUnchanged() {
        String agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/140.0.0.0 Safari/537.36";
        assertEquals(agent, BrowserRequestHeaders.chromeUserAgent(agent));
    }

    @Test public void rejectsHeaderInjectionAndUnknownBrowserInsteadOfInventingOne() {
        assertNull(BrowserRequestHeaders.chromeUserAgent(null));
        assertNull(BrowserRequestHeaders.chromeUserAgent("Firefox/140.0"));
        assertNull(BrowserRequestHeaders.chromeUserAgent(AGENT + "\r\nCookie: secret"));
        assertNull(BrowserRequestHeaders.chromeUserAgent(AGENT + "\u0000"));
        assertNull(BrowserRequestHeaders.chromeUserAgent("a".repeat(1025) + AGENT));
    }

    @Test public void appliesOnlyToTrustedHttpsDomainBoundaries() {
        assertEquals(3, BrowserRequestHeaders.forUrl("https://www.pornhub.com/view_video.php?viewkey=test", AGENT).size());
        assertEquals(3, BrowserRequestHeaders.forUrl("https://PORNHUB.COM:443/", AGENT).size());
        for (String url : new String[]{"https://notpornhub.com/", "https://pornhub.com.evil.invalid/",
                "https://pornhub.com@evil.invalid/", "https://user@pornhub.com/", "http://pornhub.com/",
                "https://pornhub.com:444/", "https://youtu.be/test?next=pornhub.com", "invalid", null}) {
            assertTrue(BrowserRequestHeaders.forUrl(url, AGENT).isEmpty());
        }
    }

    @Test public void downloaderOptionsPreserveCookieAndTlsConfiguration() {
        Map<String, String> options = new LinkedHashMap<>();
        assertTrue(BrowserRequestHeaders.apply("https://www.pornhub.com/", AGENT, options::put));
        assertEquals(7, options.size());
        assertEquals("chrome", options.get("--impersonate"));
        assertFalse(options.containsKey("--user-agent"));
        assertEquals("30", options.get("--socket-timeout"));
        assertEquals("3", options.get("--fragment-retries"));
        assertEquals("https://www.pornhub.com/", options.get("--referer"));
        assertEquals("Accept-Language:ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7", options.get("--add-headers"));
        assertFalse(options.containsKey("--no-check-certificates"));
        assertFalse(options.containsKey("--cookies"));
    }

    @Test public void otherPlatformsAndUnavailableAgentKeepDefaults() {
        Map<String, String> options = new LinkedHashMap<>();
        assertFalse(BrowserRequestHeaders.apply("https://www.youtube.com/watch?v=test", AGENT, options::put));
        assertFalse(BrowserRequestHeaders.apply("https://www.pornhub.com/", null, options::put));
        assertTrue(options.isEmpty());
    }
}
