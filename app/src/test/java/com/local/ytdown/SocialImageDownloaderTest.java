package com.local.ytdown;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

public class SocialImageDownloaderTest {
    @Test
    public void extractsXUsernameFromProfileAndStatusUrls() {
        assertEquals("tester", SocialImageDownloader.xUsernameFromUrl(
                "https://x.com/tester/media"));
        assertEquals("Tester", SocialImageDownloader.xUsernameFromUrl(
                "https://twitter.com/Tester/status/123456"));
    }

    @Test
    public void rejectsReservedXPathsAsAccounts() {
        assertThrows(IllegalArgumentException.class,
                () -> SocialImageDownloader.xUsernameFromUrl("https://x.com/home"));
    }

    @Test
    public void detectsSupportedAccountPlatforms() {
        assertEquals(AuthCookieStore.X, SocialImageDownloader.accountPlatformForUrl(
                "https://x.com/Tester/media"));
        assertEquals(AuthCookieStore.INSTAGRAM, SocialImageDownloader.accountPlatformForUrl(
                "https://www.instagram.com/test.user/?hl=ko"));
        assertEquals("test.user", SocialImageDownloader.instagramUsernameFromUrl(
                "https://www.instagram.com/test.user/?hl=ko"));
        assertEquals("test.user", SocialImageDownloader.instagramUsernameFromUrl(
                "https://www.instagram.com/test.user/reels/?hl=ko"));
        assertEquals(AuthCookieStore.INSTAGRAM, SocialImageDownloader.accountPlatformForUrl(
                "https://www.instagram.com/reel/ABC123/"));
        assertEquals(AuthCookieStore.INSTAGRAM, SocialImageDownloader.accountPlatformForUrl(
                "https://www.instagram.com/p/Ddym_hrJl60/"));
        assertEquals(null, SocialImageDownloader.accountPlatformForUrl(
                "https://www.instagram.com.evil.example/p/Ddym_hrJl60/"));
    }

    @Test
    public void extractsAccountOwnerFromInstagramPostEmbed() {
        String html = "<a class=\"UsernameText\">dungbelle_</a>"
                + "<div data-media-type=\"GraphImage\"></div>";

        assertEquals("dungbelle_",
                SocialImageDownloader.instagramUsernameFromEmbedHtml(html));
        assertEquals("Ddym_hrJl60", SocialImageDownloader.instagramShortcodeFromMediaUrl(
                "https://www.instagram.com/p/Ddym_hrJl60/"));
        assertThrows(IllegalStateException.class, () ->
                SocialImageDownloader.instagramUsernameFromEmbedHtml(
                        "<a class=\"UsernameText\">bad/user</a>"));
    }

    @Test
    public void parsesMediaDetailsPhotosAndVideos() throws Exception {
        JSONObject tweet = new JSONObject("{\"mediaDetails\":["
                + "{\"type\":\"photo\",\"media_url_https\":\"https://pbs.twimg.com/media/a.jpg\"},"
                + "{\"type\":\"video\",\"media_url_https\":\"https://pbs.twimg.com/v.jpg\"}]}" );

        SocialImageDownloader.XMedia result = SocialImageDownloader.parseXMedia(tweet);

        assertEquals(1, result.imageUrls.size());
        assertEquals("https://pbs.twimg.com/media/a.jpg?name=orig", result.imageUrls.get(0));
        assertTrue(result.hasVideo);
    }

    @Test
    public void unknownMediaDetailsFallsBackToVideoDownloader() throws Exception {
        JSONObject tweet = new JSONObject("{\"mediaDetails\":[{"
                + "\"media_url_https\":\"https://pbs.twimg.com/ext_tw_video_thumb/a.jpg\","
                + "\"video_info\":{\"variants\":[{\"url\":\"https://video.twimg.com/a.mp4\"}]}}]}");

        SocialImageDownloader.XMedia result = SocialImageDownloader.parseXMedia(tweet);

        assertTrue(result.imageUrls.isEmpty());
        assertTrue(result.hasVideo);
        assertEquals(1, result.videoUrls.size());
        assertEquals("https://video.twimg.com/a.mp4", result.videoUrls.get(0));
    }

    @Test
    public void selectsHighestBitrateForEveryXVideoItem() throws Exception {
        JSONObject tweet = new JSONObject("{\"mediaDetails\":["
                + "{\"type\":\"video\",\"video_info\":{\"variants\":["
                + "{\"bitrate\":256000,\"content_type\":\"video/mp4\","
                + "\"url\":\"https://video.twimg.com/first-low.mp4\"},"
                + "{\"bitrate\":2176000,\"content_type\":\"video/mp4\","
                + "\"url\":\"https://video.twimg.com/first-high.mp4\"}]}},"
                + "{\"type\":\"animated_gif\",\"video_info\":{\"variants\":["
                + "{\"bitrate\":0,\"content_type\":\"video/mp4\","
                + "\"url\":\"https://video.twimg.com/second.mp4\"}]}}]}");

        SocialImageDownloader.XMedia result = SocialImageDownloader.parseXMedia(tweet);

        assertEquals(2, result.videoUrls.size());
        assertEquals("https://video.twimg.com/first-high.mp4", result.videoUrls.get(0));
        assertEquals("https://video.twimg.com/second.mp4", result.videoUrls.get(1));
        assertTrue(result.imageUrls.isEmpty());
    }

    @Test
    public void parsesNewPhotosArrayAndReplacesExistingSize() throws Exception {
        JSONObject tweet = new JSONObject("{\"photos\":["
                + "{\"url\":\"https://pbs.twimg.com/media/a?format=jpg&name=small\"},"
                + "{\"media_url_https\":\"https://pbs.twimg.com/media/b.png\"}]}" );

        SocialImageDownloader.XMedia result = SocialImageDownloader.parseXMedia(tweet);

        assertEquals(2, result.imageUrls.size());
        assertEquals("https://pbs.twimg.com/media/a?format=jpg&name=orig",
                result.imageUrls.get(0));
        assertFalse(result.hasVideo);
    }

    @Test
    public void parsesExtendedEntitiesFallback() throws Exception {
        JSONObject tweet = new JSONObject("{\"extended_entities\":{\"media\":["
                + "{\"type\":\"photo\",\"media_url_https\":\"https://pbs.twimg.com/media/a.jpg\"}]}}" );

        SocialImageDownloader.XMedia result = SocialImageDownloader.parseXMedia(tweet);

        assertEquals(1, result.imageUrls.size());
        assertFalse(result.hasVideo);
    }

    @Test
    public void parsesVisibilityWrappedGraphQlPhoto() throws Exception {
        JSONObject response = new JSONObject("{\"data\":{\"tweetResult\":{\"result\":{"
                + "\"__typename\":\"TweetWithVisibilityResults\",\"tweet\":{\"legacy\":{"
                + "\"extended_entities\":{\"media\":[{\"type\":\"photo\","
                + "\"media_url_https\":\"https://pbs.twimg.com/media/wrapped.png\"}]}}}}}}}");

        SocialImageDownloader.XMedia result = SocialImageDownloader.parseXMedia(response);

        assertEquals(1, result.imageUrls.size());
        assertEquals("https://pbs.twimg.com/media/wrapped.png?name=orig",
                result.imageUrls.get(0));
        assertFalse(result.hasVideo);
    }

    @Test
    public void parsesSensitiveQuotedVideoFromAuthenticatedTweetResult() throws Exception {
        JSONObject response = new JSONObject("{\"data\":{\"tweetResult\":{\"result\":{"
                + "\"__typename\":\"TweetWithVisibilityResults\",\"tweet\":{"
                + "\"legacy\":{\"possibly_sensitive\":true},\"quoted_status_result\":{"
                + "\"result\":{\"__typename\":\"Tweet\",\"legacy\":{"
                + "\"extended_entities\":{\"media\":[{\"type\":\"video\","
                + "\"video_info\":{\"variants\":["
                + "{\"bitrate\":256000,\"content_type\":\"video/mp4\","
                + "\"url\":\"https://video.twimg.com/low.mp4\"},"
                + "{\"bitrate\":2176000,\"content_type\":\"video/mp4\","
                + "\"url\":\"https://video.twimg.com/high.mp4\"}]}}]}}}}}}}}}}}");

        SocialImageDownloader.XMedia result = SocialImageDownloader.parseXMedia(response);

        assertEquals(1, result.videoUrls.size());
        assertEquals("https://video.twimg.com/high.mp4", result.videoUrls.get(0));
        assertTrue(result.hasVideo);
        assertFalse(result.unresolvedVideo);
        assertTrue(SocialImageDownloader.isXSensitiveTweet(response));
        assertEquals("TweetWithVisibilityResults",
                SocialImageDownloader.xTweetResultType(response));
    }

    @Test
    public void buildsCurrentTweetResultVariables() throws Exception {
        JSONObject variables = SocialImageDownloader.tweetResultVariables("2099123579099189418");

        assertEquals("2099123579099189418", variables.getString("tweetId"));
        assertFalse(variables.getBoolean("withCommunity"));
        assertFalse(variables.getBoolean("includePromotedContent"));
        assertFalse(variables.getBoolean("withVoice"));
    }

    @Test
    public void parsesSensitiveXStatusHtmlImagesAndIgnoresProfilePictures() {
        String html = "<link rel=\"preload\" as=\"image\" href=\""
                + "https://pbs.twimg.com/media/photo-id?format=webp&amp;name=240x240\">"
                + "<img src=\"https://pbs.twimg.com/profile_images/avatar.jpg\">"
                + "<img src=\"https://pbs.twimg.com/media/photo-id?format=jpg&amp;name=small\">"
                + "<link rel=\"canonical\" href=\"https://x.com/tester/status/123456\">";

        SocialImageDownloader.XMedia result =
                SocialImageDownloader.parseXStatusHtmlMedia(html);

        assertEquals(1, result.imageUrls.size());
        assertEquals("https://pbs.twimg.com/media/photo-id?format=jpg&name=orig",
                result.imageUrls.get(0));
        assertFalse(result.hasVideo);
        assertEquals("tester", SocialImageDownloader.parseXStatusHtmlUsername(html));
    }

    @Test
    public void rejectsAgeRestrictedXPlaceholderImage() {
        String html = "<meta name=\"rating\" content=\"adult\">"
                + "<meta property=\"og:description\" content=\"Age-restricted adult content. "
                + "To view this media, you’ll need to log in to X.\">"
                + "<img src=\"https://pbs.twimg.com/media/GxJIrSUagAAK-ZP"
                + "?format=jpg&amp;name=240x240\">";

        SocialImageDownloader.XMedia result =
                SocialImageDownloader.parseXStatusHtmlMedia(html);

        assertTrue(result.imageUrls.isEmpty());
        assertTrue(result.videoUrls.isEmpty());
        assertTrue(result.hasVideo);
        assertTrue(result.unresolvedVideo);
        assertTrue(result.loginRequired);
    }

    @Test
    public void doesNotTreatXVideoDnsPrefetchAsPostVideo() {
        SocialImageDownloader.XMedia result =
                SocialImageDownloader.parseXStatusHtmlMedia(
                        "<link rel=\"dns-prefetch\" href=\"https://video.twimg.com\">");

        assertFalse(result.hasVideo);
        assertFalse(result.unresolvedVideo);
        assertFalse(result.loginRequired);
    }

    @Test
    public void detectsVideoMarkersInXStatusHtmlFallback() {
        SocialImageDownloader.XMedia result =
                SocialImageDownloader.parseXStatusHtmlMedia(
                        "<video src=\"https://video.twimg.com/ext_tw_video/file.mp4\"></video>");

        assertTrue(result.imageUrls.isEmpty());
        assertTrue(result.hasVideo);
        assertEquals(1, result.videoUrls.size());
    }

    @Test
    public void parsesEveryVideoAndPhotoFromInstagramSidecar() {
        String html = "{\"edge_sidecar_to_children\":{\"edges\":["
                + "{\"node\":{\"is_video\":true,\"video_url\":"
                + "\"https://cdn.example/one.mp4\"}},"
                + "{\"node\":{\"is_video\":false,\"display_url\":"
                + "\"https://cdn.example/photo.jpg\"}},"
                + "{\"node\":{\"is_video\":true,\"video_url\":"
                + "\"https://cdn.example/two.mp4\"}}]}}";

        SocialImageDownloader.XMedia result =
                SocialImageDownloader.parseInstagramEmbedMedia(html, false);

        assertEquals(1, result.imageUrls.size());
        assertEquals(2, result.videoUrls.size());
        assertTrue(result.hasVideo);
    }

    @Test
    public void parsesInstagramSidecarEscapedInsideEmbedScript() {
        String sidecar = "{\"edge_sidecar_to_children\":{\"edges\":["
                + "{\"node\":{\"is_video\":false,\"display_url\":"
                + "\"https://cdn.example/first.jpg\"}},"
                + "{\"node\":{\"is_video\":false,\"display_url\":"
                + "\"https://cdn.example/second.jpg\"}}]}}";
        String embedHtml = "<script>" + sidecar.replace("\"", "\\\"")
                .replace("/", "\\/") + "</script>";

        SocialImageDownloader.XMedia result = SocialImageDownloader.parseInstagramEmbedMedia(
                SocialImageDownloader.normalizeInstagramData(embedHtml), false);

        assertEquals(2, result.imageUrls.size());
        assertEquals("https://cdn.example/first.jpg", result.imageUrls.get(0));
        assertEquals("https://cdn.example/second.jpg", result.imageUrls.get(1));
    }

    @Test
    public void parsesInstagramCarouselWithDoublyEscapedUnicodeUrl() {
        String sidecar = "{\"edge_sidecar_to_children\":{\"edges\":["
                + "{\"node\":{\"is_video\":false,\"display_url\":"
                + "\"https://cdn.example/photo.jpg?token=%3D\"}}]}}";
        String doubleEscapedUnicode = "\\" + "\\" + "u00253D";
        String embedHtml = "<script>" + sidecar.replace("%3D", doubleEscapedUnicode)
                .replace("\"", "\\\"").replace("/", "\\/") + "</script>";

        SocialImageDownloader.XMedia result = SocialImageDownloader.parseInstagramEmbedMedia(
                SocialImageDownloader.normalizeInstagramData(embedHtml), false);

        assertEquals(1, result.imageUrls.size());
        assertEquals("https://cdn.example/photo.jpg?token=%3D", result.imageUrls.get(0));
    }

    @Test
    public void ignoresAmbiguousInstagramThumbnailWhenFallbackIsDisabled() {
        SocialImageDownloader.XMedia result =
                SocialImageDownloader.parseInstagramEmbedMedia(
                        "<img class=\"EmbeddedMediaImage\" src=\"https://cdn.example/200.jpg\">",
                        false);

        assertTrue(result.imageUrls.isEmpty());
        assertTrue(result.videoUrls.isEmpty());
    }

    @Test
    public void acceptsConfirmedInstagramPhotoWithoutTryingVideoExtraction() {
        String html = "<div data-media-type=\"GraphImage\">"
                + "<img class=\"EmbeddedMediaImage\" "
                + "src=\"https://cdn.example/full-photo.jpg\"></div>";

        SocialImageDownloader.XMedia result =
                SocialImageDownloader.parseInstagramEmbedMedia(html, false, false);

        assertEquals(1, result.imageUrls.size());
        assertEquals("https://cdn.example/full-photo.jpg", result.imageUrls.get(0));
        assertTrue(result.videoUrls.isEmpty());
        assertFalse(result.hasVideo);
        assertFalse(result.unresolvedVideo);
    }

    @Test
    public void acceptsConfirmedPhotoEvenWhenInstagramUrlUsesReelRoute() {
        String html = "<div data-media-type=\"GraphImage\">"
                + "<img class=\"EmbeddedMediaImage\" "
                + "src=\"https://cdn.example/full-photo.jpg\"></div>";
        SocialImageDownloader.XMedia result =
                SocialImageDownloader.parseInstagramEmbedMedia(html, false, true);
        assertEquals(1, result.imageUrls.size());
        assertFalse(result.hasVideo);
        assertFalse(result.unresolvedVideo);
    }

    @Test
    public void classifiesPhotoAndCarouselAsPostsDespiteClipsMetadata() throws Exception {
        JSONObject response = new JSONObject("{\"data\":{\"user\":{\"username\":\"tester\","
                + "\"edge_owner_to_timeline_media\":{\"edges\":["
                + "{\"node\":{\"code\":\"PHOTO1\",\"media_type\":1,"
                + "\"clips_metadata\":null}},"
                + "{\"node\":{\"code\":\"CAROUSEL2\",\"media_type\":8,"
                + "\"clips_metadata\":null}}]}}}}");
        List<String> urls = SocialImageDownloader.parseInstagramProfileMediaUrls(
                response, "tester");
        assertEquals("https://www.instagram.com/p/PHOTO1/", urls.get(0));
        assertEquals("https://www.instagram.com/p/CAROUSEL2/", urls.get(1));
    }

    @Test
    public void acceptsPhotoOnlyCarouselEvenWhenInstagramUrlUsesReelRoute() {
        String html = "<div data-media-type=\"GraphSidecar\"></div>"
                + "{\"carousel_media\":["
                + "{\"media_type\":1,\"image_versions2\":{\"candidates\":["
                + "{\"width\":1440,\"height\":1920,\"url\":\"https://cdn.example/a.jpg\"}]}},"
                + "{\"media_type\":1,\"image_versions2\":{\"candidates\":["
                + "{\"width\":1440,\"height\":1920,\"url\":\"https://cdn.example/b.jpg\"}]}}]}";
        SocialImageDownloader.XMedia result =
                SocialImageDownloader.parseInstagramEmbedMedia(html, false, true);
        assertEquals(2, result.imageUrls.size());
        assertFalse(result.hasVideo);
        assertFalse(result.unresolvedVideo);
    }

    @Test
    public void doesNotTreatUnknownInstagramEmbedImageAsConfirmedPhoto() {
        String html = "<div data-media-type=\"GraphSidecar\">"
                + "<img class=\"EmbeddedMediaImage\" "
                + "src=\"https://cdn.example/poster.jpg\"></div>";

        SocialImageDownloader.XMedia result =
                SocialImageDownloader.parseInstagramEmbedMedia(html, false, false);

        assertTrue(result.imageUrls.isEmpty());
    }

    @Test
    public void neverTreatsReelPosterAsDownloadablePhoto() {
        SocialImageDownloader.XMedia result =
                SocialImageDownloader.parseInstagramEmbedMedia(
                        "<img class=\"EmbeddedMediaImage\" src=\"https://cdn.example/200.jpg\">",
                        true, true);

        assertTrue(result.imageUrls.isEmpty());
        assertTrue(result.videoUrls.isEmpty());
        assertTrue(result.hasVideo);
        assertTrue(result.unresolvedVideo);
    }

    @Test
    public void parsesInstagramVideoMetaRegardlessOfAttributeOrderAndQuoteStyle() {
        SocialImageDownloader.XMedia result =
                SocialImageDownloader.parseInstagramEmbedMedia(
                        "<meta content='https://cdn.example/video.mp4?x=1&amp;y=2' "
                                + "property='og:video:secure_url'>",
                        false, true);

        assertEquals(1, result.videoUrls.size());
        assertEquals("https://cdn.example/video.mp4?x=1&y=2", result.videoUrls.get(0));
        assertTrue(result.imageUrls.isEmpty());
        assertFalse(result.unresolvedVideo);
    }

    @Test
    public void parsesModernInstagramVideoVersionsForReel() {
        String html = "{\"video_versions\":["
                + "{\"width\":360,\"height\":640,\"url\":\"https://cdn.example/low.mp4\"},"
                + "{\"width\":1080,\"height\":1920,\"url\":\"https://cdn.example/high.mp4\"}]}";

        SocialImageDownloader.XMedia result =
                SocialImageDownloader.parseInstagramEmbedMedia(html, false, true);

        assertEquals(1, result.videoUrls.size());
        assertEquals("https://cdn.example/high.mp4", result.videoUrls.get(0));
        assertTrue(result.hasVideo);
        assertFalse(result.unresolvedVideo);
    }

    @Test
    public void parsesEveryModernInstagramCarouselItem() {
        String html = "{\"carousel_media\":["
                + "{\"media_type\":1,\"image_versions2\":{\"candidates\":["
                + "{\"width\":200,\"height\":200,\"url\":\"https://cdn.example/thumb.jpg\"},"
                + "{\"width\":1080,\"height\":1350,\"url\":\"https://cdn.example/photo.jpg\"}]}},"
                + "{\"media_type\":2,\"video_versions\":["
                + "{\"width\":720,\"height\":1280,\"url\":\"https://cdn.example/video.mp4\"}]}]}";

        SocialImageDownloader.XMedia result =
                SocialImageDownloader.parseInstagramEmbedMedia(html, false, false);

        assertEquals(1, result.imageUrls.size());
        assertEquals("https://cdn.example/photo.jpg", result.imageUrls.get(0));
        assertEquals(1, result.videoUrls.size());
        assertEquals("https://cdn.example/video.mp4", result.videoUrls.get(0));
        assertTrue(result.hasVideo);
        assertFalse(result.unresolvedVideo);
    }

    @Test
    public void parsesEscapedXPhotoFromTombstonePage() {
        String html = "https:\\/\\/pbs.twimg.com\\/media\\/GxJIrSUagAAK-ZP"
                + "?format=jpg\\u0026name=240x240";

        SocialImageDownloader.XMedia result =
                SocialImageDownloader.parseXStatusHtmlMedia(html);

        assertEquals(1, result.imageUrls.size());
        assertEquals("https://pbs.twimg.com/media/GxJIrSUagAAK-ZP?format=jpg&name=orig",
                result.imageUrls.get(0));
    }

    @Test
    public void parsesInstagramProfileMediaForOnlyTheRequestedAccount() throws Exception {
        JSONObject response = new JSONObject("{\"data\":{\"user\":{"
                + "\"username\":\"tester\",\"edge_owner_to_timeline_media\":{"
                + "\"edges\":["
                + "{\"node\":{\"shortcode\":\"PHOTO1\",\"__typename\":\"GraphImage\"}},"
                + "{\"node\":{\"code\":\"REEL2\",\"product_type\":\"clips\","
                + "\"user\":{\"username\":\"tester\"}}},"
                + "{\"node\":{\"code\":\"OTHER3\",\"media_type\":1,"
                + "\"user\":{\"username\":\"another\"}}}]}}}}}");

        List<String> urls = SocialImageDownloader.parseInstagramProfileMediaUrls(
                response, "Tester");

        assertEquals(2, urls.size());
        assertEquals("https://www.instagram.com/p/PHOTO1/", urls.get(0));
        assertEquals("https://www.instagram.com/reel/REEL2/", urls.get(1));
    }

    @Test
    public void parsesModernInstagramTimelineAndPagination() throws Exception {
        JSONObject firstPage = new JSONObject("{\"data\":{"
                + "\"xdt_api__v1__feed__user_timeline_graphql_connection\":{"
                + "\"edges\":[{\"node\":{\"code\":\"FIRST1\",\"media_type\":1}},"
                + "{\"node\":{\"code\":\"REEL2\",\"product_type\":\"clips\","
                + "\"user\":{\"username\":\"tester\"}}},"
                + "{\"node\":{\"code\":\"OTHER3\",\"media_type\":1,"
                + "\"user\":{\"username\":\"other\"}}}],"
                + "\"page_info\":{\"has_next_page\":true}}}}" );
        List<String> urls = SocialImageDownloader.parseInstagramProfileMediaUrls(
                firstPage, "tester");
        assertEquals(2, urls.size());
        assertEquals("https://www.instagram.com/p/FIRST1/", urls.get(0));
        assertEquals("https://www.instagram.com/reel/REEL2/", urls.get(1));
        assertEquals(Boolean.TRUE, SocialImageDownloader.instagramProfileHasMore(firstPage));

        JSONObject finalPage = new JSONObject("{\"data\":{\"user\":{"
                + "\"edge_owner_to_timeline_media\":{\"page_info\":{"
                + "\"has_next_page\":false}}}}}");
        assertEquals(Boolean.FALSE, SocialImageDownloader.instagramProfileHasMore(finalPage));
    }

    @Test
    public void ignoresStaleInstagramPaginationFromAlreadySeenMedia() {
        assertEquals(Boolean.FALSE, SocialImageDownloader.relevantInstagramPagination(
                false, 15, 15));
        assertEquals(null, SocialImageDownloader.relevantInstagramPagination(
                true, 1, 0));
        assertEquals(Boolean.TRUE, SocialImageDownloader.relevantInstagramPagination(
                true, 12, 12));
        assertEquals(Boolean.FALSE, SocialImageDownloader.relevantInstagramPagination(
                false, 1, 0));
    }

    @Test
    public void countsInstagramCarouselAsOnePostNotSeparateChildPosts() throws Exception {
        JSONObject response = new JSONObject("{\"data\":{\"user\":{\"username\":\"tester\","
                + "\"edge_owner_to_timeline_media\":{\"edges\":[{\"node\":{"
                + "\"code\":\"PARENT\",\"media_type\":8,\"carousel_media\":["
                + "{\"code\":\"CHILD1\",\"media_type\":1},"
                + "{\"code\":\"CHILD2\",\"media_type\":2}]}}]}}}}");
        List<String> urls = SocialImageDownloader.parseInstagramProfileMediaUrls(
                response, "tester");
        assertEquals(1, urls.size());
        assertEquals("https://www.instagram.com/p/PARENT/", urls.get(0));
    }

    @Test
    public void parsesProfileMediaNewestFirstAndIgnoresOtherAccounts() throws Exception {
        String html = "<script id=\"__NEXT_DATA__\" type=\"application/json\">"
                + "{\"props\":{\"tweets\":["
                + "{\"id_str\":\"100\",\"user\":{\"screen_name\":\"tester\"},"
                + "\"extended_entities\":{\"media\":[{\"type\":\"photo\"}]}},"
                + "{\"id_str\":\"300\",\"user\":{\"screen_name\":\"Tester\"},"
                + "\"mediaDetails\":[{\"type\":\"video\"}]},"
                + "{\"id_str\":\"400\",\"user\":{\"screen_name\":\"other\"},"
                + "\"mediaDetails\":[{\"type\":\"photo\"}]},"
                + "{\"id_str\":\"250\",\"user\":{\"screen_name\":\"tester\"},"
                + "\"entities\":{\"media\":[]}},"
                + "{\"id_str\":\"225\",\"user\":{\"screen_name\":\"tester\"},"
                + "\"retweeted_status\":{\"id_str\":\"210\"},"
                + "\"extended_entities\":{\"media\":[{\"type\":\"photo\"}]}},"
                + "{\"id_str\":\"200\",\"user\":{\"screen_name\":\"tester\"}}]}}"
                + "</script>";

        List<String> urls = SocialImageDownloader.parseXProfileMediaUrls(html, "tester");

        assertEquals(2, urls.size());
        assertEquals("https://x.com/tester/status/300", urls.get(0));
        assertEquals("https://x.com/tester/status/100", urls.get(1));
    }

    @Test
    public void parsesAuthenticatedMediaPageAndBottomCursor() throws Exception {
        JSONObject response = new JSONObject("{\"data\":{\"user\":{\"result\":{"
                + "\"timeline_v2\":{\"timeline\":{\"instructions\":[{\"entries\":["
                + "{\"entryId\":\"tweet-300\",\"content\":{\"itemContent\":{"
                + "\"tweet_results\":{\"result\":{\"rest_id\":\"300\"}}}}},"
                + "{\"entryId\":\"cursor-bottom\",\"content\":{"
                + "\"cursorType\":\"Bottom\",\"value\":\"next-page\"}}"
                + "]}]}}}}}}}");

        SocialImageDownloader.XMediaPage page =
                SocialImageDownloader.parseXUserMediaPage(response);

        assertEquals(1, page.statusIds.size());
        assertEquals("300", page.statusIds.get(0));
        assertEquals("next-page", page.bottomCursor);
    }

    @Test
    public void parsesModuleAndVisibilityWrappedTweetsWithoutDuplicates() throws Exception {
        JSONObject response = new JSONObject("{\"data\":{\"user\":{\"result\":{"
                + "\"timeline_v2\":{\"timeline\":{\"instructions\":[{\"entries\":[{"
                + "\"entryId\":\"profile-conversation-200\",\"content\":{\"items\":["
                + "{\"item\":{\"itemContent\":{\"tweet_results\":{\"result\":{"
                + "\"__typename\":\"TweetWithVisibilityResults\","
                + "\"tweet\":{\"legacy\":{\"id_str\":\"200\"}}}}}}},"
                + "{\"item\":{\"itemContent\":{\"tweet_results\":{\"result\":{"
                + "\"rest_id\":\"200\"}}}}}]}}]}]}}}}}}}");

        SocialImageDownloader.XMediaPage page =
                SocialImageDownloader.parseXUserMediaPage(response);

        assertEquals(1, page.statusIds.size());
        assertEquals("200", page.statusIds.get(0));
    }

    @Test
    public void filtersOriginalTimelineToPostsContainingMedia() throws Exception {
        JSONObject mediaTweet = new JSONObject()
                .put("rest_id", "300")
                .put("legacy", new JSONObject().put("extended_entities",
                        new JSONObject().put("media",
                                new JSONArray().put(new JSONObject().put("type", "photo")))));
        JSONObject textTweet = new JSONObject()
                .put("rest_id", "200")
                .put("legacy", new JSONObject().put("full_text", "text only"));
        JSONArray entries = new JSONArray()
                .put(timelineEntry(mediaTweet))
                .put(timelineEntry(textTweet));
        JSONObject response = new JSONObject().put("data",
                new JSONObject().put("user", new JSONObject().put("result",
                        new JSONObject().put("timeline_v2",
                                new JSONObject().put("timeline",
                                        new JSONObject().put("instructions",
                                                new JSONArray().put(
                                                        new JSONObject().put("entries", entries))))))));

        List<String> ids = SocialImageDownloader.parseXTimelineMediaStatusIds(response);

        assertEquals(1, ids.size());
        assertEquals("300", ids.get(0));
    }

    private static JSONObject timelineEntry(JSONObject tweet) throws Exception {
        return new JSONObject().put("content", new JSONObject().put("itemContent",
                new JSONObject().put("tweet_results",
                        new JSONObject().put("result", tweet))));
    }
}
