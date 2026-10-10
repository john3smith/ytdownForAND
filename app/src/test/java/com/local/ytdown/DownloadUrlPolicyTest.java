package com.local.ytdown;

import static org.junit.Assert.*;
import org.junit.Test;

public class DownloadUrlPolicyTest {
    private static final String VIDEO = "https://www.pornhub.com/view_video.php?viewkey=ph123abc";

    @Test public void recognizesVideoLinksAndLocalizedHosts() {
        assertTrue(DownloadUrlPolicy.isSupported(VIDEO));
        assertTrue(DownloadUrlPolicy.isSupported(VIDEO.replace("www.", "")));
        assertTrue(DownloadUrlPolicy.isSupported(VIDEO.replace("www.", "m.")));
        assertTrue(DownloadUrlPolicy.isSupported(VIDEO.replace("www.", "fr.")));
        assertTrue(DownloadUrlPolicy.isSupported(VIDEO.replace("www.pornhub.com", "WWW.PORNHUB.COM")));
        assertTrue(DownloadUrlPolicy.isSupported("https://pornhub.com/video/show?viewkey=12345"));
    }

    @Test public void recognizesEmbedAndTrackingParameters() {
        assertTrue(DownloadUrlPolicy.isSupported("https://pornhub.com/embed/ph123abc"));
        assertEquals(VIDEO, DownloadUrlPolicy.normalize("https://m.pornhub.com/embed/ph123abc/"));
        assertEquals(VIDEO, DownloadUrlPolicy.normalize(
                "http://fr.pornhub.com/view_video.php?utm_source=share&viewkey=ph123abc#player"));
        assertEquals(VIDEO, DownloadUrlPolicy.normalize(
                "https://pornhub.com/view_video.php?viewkey=ph123abc&ref=https%3A%2F%2Fx.com"));
    }

    @Test public void doesNotAllowImpostorHostsOrCredentials() {
        assertFalse(DownloadUrlPolicy.isSupported(VIDEO.replace("pornhub.com", "notpornhub.com")));
        assertFalse(DownloadUrlPolicy.isSupported(VIDEO.replace("pornhub.com", "pornhub.com.evil.test")));
        assertFalse(DownloadUrlPolicy.isSupported(VIDEO.replace("www.pornhub.com", "pornhub.com@evil.test")));
        assertFalse(DownloadUrlPolicy.isSupported(VIDEO.replace("www.pornhub.com", "evil.test@pornhub.com")));
        assertFalse(DownloadUrlPolicy.isSupported(VIDEO.replace("pornhub.com", "pornhubpremium.com")));
        assertFalse(DownloadUrlPolicy.isSupported(VIDEO.replace("pornhub.com", "pornhub.com:8443")));
    }

    @Test public void rejectsProfilesAndSearchPagesInsteadOfDownloadingAnAccount() {
        for (String path : new String[]{"/", "/users/example", "/model/example", "/video/search?search=test"}) {
            assertFalse(path, DownloadUrlPolicy.isSupported("https://pornhub.com" + path));
        }
    }

    @Test public void rejectsMissingMalformedAndAmbiguousVideoIds() {
        for (String query : new String[]{"", "?other=test", "?viewkey=", "?viewkey=abc/def",
                "?viewkey=abc&viewkey=def", "?viewkey=abc%26def", "?viewkey=abc%ZZ", "?viewkey=abc+def"}) {
            assertFalse(query, DownloadUrlPolicy.isSupported("https://pornhub.com/view_video.php" + query));
        }
    }

    @Test public void malformedInputFailsSafely() {
        for (String value : new String[]{null, "", " ", "not a link", "//pornhub.com/embed/abc",
                "file:///view_video.php?viewkey=abc", "javascript:alert(1)", "https://[broken"}) {
            assertFalse(DownloadUrlPolicy.isSupported(value));
        }
        assertEquals("", DownloadUrlPolicy.normalize(null));
    }

    @Test public void existingProvidersAndTheirUrlsAreUnchanged() {
        for (String url : new String[]{"https://youtu.be/abc", "https://www.youtube.com/watch?v=abc",
                "https://m.youtube.com/shorts/abc", "https://www.youtube-nocookie.com/embed/abc",
                "https://x.com/example/status/123", "https://twitter.com/example/status/123",
                "https://www.instagram.com/p/abc/", "https://instagr.am/p/abc/"}) {
            assertTrue(url, DownloadUrlPolicy.isSupported(url));
            assertEquals(url, DownloadUrlPolicy.normalize(url));
        }
    }

    @Test public void trimmedUrlsAndEncodedIdsAreCanonicalized() {
        assertTrue(DownloadUrlPolicy.isSupported("  " + VIDEO + "  "));
        assertEquals(VIDEO, DownloadUrlPolicy.normalize("  " + VIDEO + "  "));
        assertEquals(VIDEO, DownloadUrlPolicy.normalize(
                "https://pornhub.com/view_video.php?viewkey=%70h123abc"));
        assertEquals(VIDEO, DownloadUrlPolicy.normalize(DownloadUrlPolicy.normalize(VIDEO)));
    }
}
