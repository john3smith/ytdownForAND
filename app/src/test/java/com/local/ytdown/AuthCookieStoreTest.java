package com.local.ytdown;

import static org.junit.Assert.*;
import org.junit.Test;

public class AuthCookieStoreTest {
    @Test public void maskedPlatformIsMappedFromOnlyItsRealHost() {
        assertEquals(AuthCookieStore.PORNHUB, AuthCookieStore.platformForUrl(
                "https://www.pornhub.com/view_video.php?viewkey=ph123abc"));
        assertEquals(AuthCookieStore.PORNHUB, AuthCookieStore.platformForUrl("https://fr.pornhub.com/login"));
        assertNull(AuthCookieStore.platformForUrl("https://notpornhub.com/login"));
        assertNull(AuthCookieStore.platformForUrl("https://pornhub.com.evil.test/login"));
        assertNull(AuthCookieStore.platformForUrl("https://evil.test/?next=https://pornhub.com"));
        assertNull(AuthCookieStore.platformForUrl("https://evil.test@pornhub.com/login"));
    }

    @Test public void trackingParametersCannotSelectAnotherSitesCookies() {
        assertEquals(AuthCookieStore.PORNHUB, AuthCookieStore.platformForUrl(
                "https://pornhub.com/view_video.php?viewkey=ph123abc&ref=https://x.com"));
        assertEquals(AuthCookieStore.INSTAGRAM, AuthCookieStore.platformForUrl(
                "https://instagram.com/p/abc/?ref=youtube.com"));
        assertNull(AuthCookieStore.platformForUrl("https://example.test/?ref=x.com"));
    }

    @Test public void existingPlatformMappingsArePreserved() {
        assertEquals(AuthCookieStore.YOUTUBE, AuthCookieStore.platformForUrl("https://youtu.be/abc"));
        assertEquals(AuthCookieStore.YOUTUBE, AuthCookieStore.platformForUrl("https://m.youtube.com/watch?v=abc"));
        assertEquals(AuthCookieStore.X, AuthCookieStore.platformForUrl("https://twitter.com/test/status/123"));
        assertEquals(AuthCookieStore.X, AuthCookieStore.platformForUrl("https://x.com/test/status/123"));
        assertEquals(AuthCookieStore.INSTAGRAM, AuthCookieStore.platformForUrl("https://www.instagram.com/p/abc/"));
    }

    @Test public void invalidValuesDoNotExportAnyPlatformsCookies() {
        for (String value : new String[]{null, "", "invalid", "javascript:alert(1)", "file:///x.com"}) {
            assertNull(AuthCookieStore.platformForUrl(value));
        }
    }

    @Test public void loginUiIsMaskedAndUsesHttps() {
        assertEquals("***", AuthCookieStore.displayName(AuthCookieStore.PORNHUB));
        assertEquals("https://www.pornhub.com/login", AuthCookieStore.loginUrl(AuthCookieStore.PORNHUB));
    }

    @Test public void loginConfirmationRequiresTrustedHttpsOrigin() {
        assertTrue(AuthCookieStore.isTrustedLoginPage("https://www.pornhub.com/", AuthCookieStore.PORNHUB));
        assertFalse(AuthCookieStore.isTrustedLoginPage("http://www.pornhub.com/", AuthCookieStore.PORNHUB));
        assertFalse(AuthCookieStore.isTrustedLoginPage("https://pornhub.com:8443/", AuthCookieStore.PORNHUB));
        assertFalse(AuthCookieStore.isTrustedLoginPage("https://evil.test/?ref=pornhub.com", AuthCookieStore.PORNHUB));
        assertFalse(AuthCookieStore.isTrustedLoginPage(null, AuthCookieStore.PORNHUB));
        assertFalse(AuthCookieStore.isTrustedLoginPage("https://www.pornhub.com/", null));
    }
}
