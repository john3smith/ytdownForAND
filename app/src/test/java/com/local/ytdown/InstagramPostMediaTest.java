package com.local.ytdown;

import static org.junit.Assert.*;
import org.json.JSONObject;
import org.junit.Test;
import java.io.IOException;

public class InstagramPostMediaTest {
    private JSONObject post(String fields) throws Exception {
        return new JSONObject("{\"code\":\"ABC\",\"pk\":\"66\"," + fields + "}");
    }

    @Test public void convertsShortcodesWithoutLongOverflow() {
        assertEquals("66", SocialImageDownloader.instagramMediaId("ABC"));
        assertEquals("64", SocialImageDownloader.instagramMediaId("BA"));
        assertEquals("63", SocialImageDownloader.instagramMediaId("_"));
        assertTrue(SocialImageDownloader.instagramMediaId("____________").length() > 19);
        assertThrows(IllegalArgumentException.class, () -> SocialImageDownloader.instagramMediaId("bad/code"));
    }

    @Test public void downloadsAllPhotoCarouselItemsAtHighestResolution() throws Exception {
        JSONObject post = post("\"media_type\":8,\"carousel_media_count\":2,\"carousel_media\":["
                + "{\"media_type\":1,\"image_versions2\":{\"candidates\":["
                + "{\"width\":150,\"height\":150,\"url\":\"https://cdn.example/preview.jpg\"},"
                + "{\"width\":1080,\"height\":1440,\"url\":\"https://cdn.example/original.jpg\"}]}},"
                + "{\"media_type\":1,\"display_url\":\"https://cdn.example/second.jpg\"}]");
        SocialImageDownloader.XMedia media = SocialImageDownloader.parseInstagramPost(post, "ABC");
        assertEquals(2, media.imageUrls.size());
        assertEquals("https://cdn.example/original.jpg", media.imageUrls.get(0));
        assertFalse(media.hasVideo);
        assertFalse(media.unresolvedVideo);
    }

    @Test public void mixedCarouselKeepsVideoCoverOutOfPhotos() throws Exception {
        JSONObject post = post("\"media_type\":8,\"carousel_media\":["
                + "{\"media_type\":1,\"display_url\":\"https://cdn.example/photo.jpg\"},"
                + "{\"media_type\":2,\"display_url\":\"https://cdn.example/cover.jpg\",\"video_versions\":["
                + "{\"width\":720,\"height\":1280,\"url\":\"https://cdn.example/video.mp4\"}]}]");
        SocialImageDownloader.XMedia media = SocialImageDownloader.parseInstagramPost(post, "ABC");
        assertEquals(1, media.imageUrls.size());
        assertEquals(1, media.videoUrls.size());
        assertTrue(media.hasVideo);
        assertFalse(media.unresolvedVideo);
    }

    @Test public void unavailableVideoNeverBecomesSuccessfulPhotoDownload() throws Exception {
        SocialImageDownloader.XMedia media = SocialImageDownloader.parseInstagramPost(post(
                "\"media_type\":2,\"display_url\":\"https://cdn.example/video-cover.jpg\""), "ABC");
        assertTrue(media.imageUrls.isEmpty());
        assertTrue(media.hasVideo);
        assertTrue(media.unresolvedVideo);
    }

    @Test public void rejectsPartialCarouselAndMissingImage() throws Exception {
        JSONObject partial = post("\"media_type\":8,\"carousel_media_count\":2,\"carousel_media\":["
                + "{\"media_type\":1,\"display_url\":\"https://cdn.example/a.jpg\"}]");
        assertThrows(IOException.class, () -> SocialImageDownloader.parseInstagramPost(partial, "ABC"));
        JSONObject missing = post("\"media_type\":1");
        assertThrows(IOException.class, () -> SocialImageDownloader.parseInstagramPost(missing, "ABC"));
    }

    @Test public void rejectsUnrelatedPostOrConflictingIdentity() throws Exception {
        JSONObject unrelated = post("\"media_type\":1,\"display_url\":\"https://cdn.example/a.jpg\"");
        assertThrows(IOException.class, () -> SocialImageDownloader.parseInstagramPost(unrelated, "ABD"));
        unrelated.put("pk", "67");
        assertThrows(IOException.class, () -> SocialImageDownloader.parseInstagramPost(unrelated, "ABC"));
    }
}
