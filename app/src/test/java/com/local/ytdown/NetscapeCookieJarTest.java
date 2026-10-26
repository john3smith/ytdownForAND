package com.local.ytdown;

import org.junit.Test;
import static org.junit.Assert.*;

public class NetscapeCookieJarTest {
    @Test public void respectsHostSubdomainPathSecureExpiryAndHttpOnly() {
        NetscapeCookieJar jar = NetscapeCookieJar.parse(
                "#HttpOnly_.example.test\tTRUE\t/\tTRUE\t0\tsession\tsynthetic\n"
                + "www.example.test\tFALSE\t/private\tFALSE\t0\tpath\tspecific\n"
                + ".example.test\tTRUE\t/\tFALSE\t100\texpired\told\n");
        assertEquals("path=specific; session=synthetic", jar.headerFor("https://www.example.test/private/item", 100));
        assertEquals("session=synthetic", jar.headerFor("https://cdn.example.test/", 100));
        assertEquals("path=specific", jar.headerFor("http://www.example.test/private", 100));
        assertNull(jar.headerFor("http://cdn.example.test/", 100));
        assertEquals("session=synthetic", jar.headerFor("https://www.example.test/private-other", 100));
        assertNull(jar.headerFor("https://example.test.evil.test/", 100));
        assertNull(jar.headerFor("https://other.test/", 100));
        assertNull(jar.headerFor("https://user@example.test/", 100));
    }

    @Test public void hostOnlyCookiesDoNotReachSubdomainsOrCdns() {
        NetscapeCookieJar jar = NetscapeCookieJar.parse("x.com\tFALSE\t/\tTRUE\t0\tauth_token\tsynthetic\n");
        assertEquals("auth_token=synthetic", jar.headerFor("https://x.com/", 100));
        assertNull(jar.headerFor("https://video.twimg.com/video.mp4", 100));
        assertNull(jar.headerFor("https://sub.x.com/", 100));
    }

    @Test public void malformedValuesCannotInjectAnotherCookieOrHeader() {
        NetscapeCookieJar jar = NetscapeCookieJar.parse(
                ".example.test\tTRUE\t/\tTRUE\t0\tname\tvalue; other=injected\n"
                + ".example.test\tTRUE\t/\tTRUE\t0\tbad name\tvalue\n"
                + ".example.test\tTRUE\t/\tTRUE\twrong\tname\tvalue\n");
        assertNull(jar.headerFor("https://example.test/", 100));
    }
}
