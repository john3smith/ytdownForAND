package com.local.ytdown;

import static org.junit.Assert.*;
import org.junit.Test;

public class NetscapeCookieTest {
    private static final String URL = "https://www.pornhub.com/view_video.php";
    private static final long NOW = 1700000000;

    @Test public void preservesDomainPathSecurityExpiryAndHttpOnly() {
        NetscapeCookie cookie = NetscapeCookie.parse(URL,
                "session=example; Domain=.pornhub.com; Path=/view_video.php; Secure; HttpOnly; Max-Age=120", NOW);
        assertNotNull(cookie);
        assertEquals("#HttpOnly_.pornhub.com\tTRUE\t/view_video.php\tTRUE\t1700000120\tsession\texample\n", cookie.line());
    }

    @Test public void hostOnlyDoesNotBecomeWildcardOrChangeSecureFlag() {
        NetscapeCookie cookie = NetscapeCookie.parse(URL, "guest=value", NOW);
        assertEquals("www.pornhub.com\tFALSE\t/\tFALSE\t0\tguest\tvalue\n", cookie.line());
    }

    @Test public void pathIsPartOfDuplicateKey() {
        NetscapeCookie one = NetscapeCookie.parse(URL, "session=first; Path=/", NOW);
        NetscapeCookie two = NetscapeCookie.parse(URL, "session=second; Path=/view_video.php", NOW);
        assertNotEquals(one.key(), two.key());
    }

    @Test public void rejectsExpiredAndMalformedCookies() {
        for (String header : new String[]{"id=x; Max-Age=0", "id=x; Max-Age=-1",
                "id=x; Expires=Thu, 01 Jan 1970 00:00:00 GMT", "id=x; Expires=invalid",
                "id=x; Domain=com", "id=x; Domain=notpornhub.com", "id=x; Domain=x.com",
                "id=x; Domain=pornhub.com.evil.test", "id=x\r\nother=secret", "id=x\tsecret",
                "bad name=x", "id=x; Partitioned", "id=x; Domain=pornhub.com; Domain=evil.test"}) {
            assertNull(header, NetscapeCookie.parse(URL, header, NOW));
        }
    }

    @Test public void maxAgeOverridesPastExpiresAndLargeValuesDoNotOverflow() {
        assertNotNull(NetscapeCookie.parse(URL,
                "id=x; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=60", NOW));
        assertEquals(Long.MAX_VALUE, NetscapeCookie.parse(URL,
                "id=x; Max-Age=9223372036854775807", NOW).expires);
    }

    @Test public void parsesBrowserDateAndEqualsInValue() {
        NetscapeCookie cookie = NetscapeCookie.parse(URL,
                "id=a=b==; Expires=Wed, 15 Nov 2023 00:00:00 GMT; Domain=.pornhub.com", NOW);
        assertNotNull(cookie);
        assertEquals("a=b==", cookie.value);
        assertEquals(1700006400L, cookie.expires);
    }

    @Test public void chromiumAlwaysSerializesDomainEvenForHostOnlyCookies() {
        NetscapeCookie cookie = NetscapeCookie.parse(URL,
                "__Host-id=x; Domain=www.pornhub.com; Secure; Path=/; HttpOnly", NOW);
        assertNotNull(cookie);
        assertFalse(cookie.includeSubdomains);
        assertEquals("www.pornhub.com", cookie.domain);
        assertTrue(cookie.line().startsWith("#HttpOnly_www.pornhub.com\tFALSE\t"));
        assertNull(NetscapeCookie.parse(URL, "id=x; Domain=pornhub.com", NOW));
    }

    @Test public void cookiePrefixRulesAndFallbackStayConservative() {
        assertNull(NetscapeCookie.parse(URL, "__Secure-id=x", NOW));
        assertNull(NetscapeCookie.parse(URL, "__Host-id=x; Secure; Domain=pornhub.com; Path=/", NOW));
        assertNull(NetscapeCookie.parse(URL, "__Host-id=x; Secure; Path=/login", NOW));
        assertNotNull(NetscapeCookie.parse(URL, "__Host-id=x; Secure; Path=/", NOW));
        NetscapeCookie fallback = NetscapeCookie.fallback(URL, "id=x", NOW);
        assertFalse(fallback.includeSubdomains);
        assertEquals("www.pornhub.com", fallback.domain);
    }

    @Test public void unsafeSourceOrOverlongHeaderIsRejected() {
        assertNull(NetscapeCookie.parse("http://www.pornhub.com/", "id=x", NOW));
        assertNull(NetscapeCookie.parse("https://user@www.pornhub.com/", "id=x", NOW));
        assertNull(NetscapeCookie.parse(URL, "id=" + "x".repeat(16384), NOW));
    }

    @Test public void logoutExpiresTheExactHostOrDomainPathWithoutCookieValues() {
        NetscapeCookie hostOnly = NetscapeCookie.parse(URL,
                "id=privatevalue; Domain=www.pornhub.com; Path=/login; Secure; HttpOnly", NOW);
        assertEquals("id=; Max-Age=0; Path=/login; Secure; HttpOnly", hostOnly.expirationHeader());
        NetscapeCookie domain = NetscapeCookie.parse(URL,
                "id=privatevalue; Domain=.pornhub.com; Path=/; Secure", NOW);
        assertEquals("id=; Max-Age=0; Path=/; Domain=.pornhub.com; Secure", domain.expirationHeader());
        assertFalse(domain.expirationHeader().contains("privatevalue"));
    }

    @Test public void legacyOtherPlatformMetadataKeepsExistingReadersCompatible() {
        NetscapeCookie cookie = NetscapeCookie.parse("https://www.instagram.com/",
                "sessionid=fixture; Domain=.instagram.com; Path=/; Secure", NOW);
        assertEquals(".instagram.com\tTRUE\t/\tTRUE\t0\tsessionid\tfixture\n", cookie.line());
        assertFalse(cookie.httpOnly);
    }
}
