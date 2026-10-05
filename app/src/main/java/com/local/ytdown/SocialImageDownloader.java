package com.local.ytdown;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SocialImageDownloader {
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 13) "
            + "AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36";
    private static final String INSTAGRAM_EMBED_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                    + "AppleWebKit/605.1.15 (KHTML, like Gecko) "
                    + "Version/17.0 Safari/605.1.15";
    private static final Pattern INSTAGRAM_PATH = Pattern.compile(
            "instagram\\.com/(p|reel|tv)/([A-Za-z0-9_-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSTAGRAM_ACCOUNT = Pattern.compile(
            "https?://(?:www\\.)?(?:instagram\\.com|instagr\\.am)/"
                    + "([A-Za-z0-9._]{1,30})(?:/reels)?/?(?:[?#].*)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INSTAGRAM_MEDIA_URL = Pattern.compile(
            "https?://(?:www\\.)?instagram\\.com/(p|reel|tv)/([A-Za-z0-9_-]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern X_STATUS = Pattern.compile(
            "(?:x|twitter)\\.com/[^/]+/status/(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern X_ACCOUNT = Pattern.compile(
            "https?://(?:www\\.|mobile\\.)?(?:x|twitter)\\.com/([A-Za-z0-9_]{1,15})(?:/|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEXT_DATA = Pattern.compile(
            "<script[^>]+id=\\\"__NEXT_DATA__\\\"[^>]*>(.*?)</script>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern X_MAIN_SCRIPT = Pattern.compile(
            "https://abs\\.twimg\\.com/responsive-web/client-web(?:-legacy)?/main\\.[^\\\"'<>]+\\.js",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern X_BEARER_TOKEN = Pattern.compile(
            "AAAAAAAA[A-Za-z0-9%_-]{80,}");
    private static final Pattern X_STATUS_HTML_IMAGE = Pattern.compile(
            "https://pbs\\.twimg\\.com/media/[A-Za-z0-9_-]+"
                    + "(?:\\.(?:jpe?g|png|webp|gif|avif))?(?:\\?[^\\\"'<>\\s]*)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern X_STATUS_HTML_URL = Pattern.compile(
            "https://(?:www\\.)?(?:x|twitter)\\.com/([A-Za-z0-9_]{1,15})/status/\\d+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern X_STATUS_HTML_VIDEO = Pattern.compile(
            "(?:https://video\\.twimg\\.com/(?:amplify_video|ext_tw_video|tweet_video)/|"
                    + "/amplify_video/|/ext_tw_video/|/tweet_video/|"
                    + "data-testid=\\\"video)", Pattern.CASE_INSENSITIVE);
    private static final Pattern X_STATUS_HTML_VIDEO_URL = Pattern.compile(
            "https://video\\.twimg\\.com/[^\\\"'<>\\s]+?\\.mp4(?:\\?[^\\\"'<>\\s]*)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED_VALUE = Pattern.compile("\\\"([^\\\"]+)\\\"");
    private static final Pattern MEDIA_TYPE = Pattern.compile(
            "data-media-type=\\\"([^\\\"]+)\\\"");
    private static final Pattern EMBEDDED_IMAGE = Pattern.compile(
            "class=\\\"EmbeddedMediaImage\\\"[^>]*src=\\\"([^\\\"]+)\\\"");
    private static final Pattern USERNAME = Pattern.compile(
            "class=\\\"UsernameText\\\">([^<]+)<");
    private static final Pattern INSTAGRAM_JSON_VIDEO = Pattern.compile(
            "\\\"video_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INSTAGRAM_META_VIDEO = Pattern.compile(
            "<meta[^>]+property=\\\"og:video(?::secure_url)?\\\"[^>]+content=\\\"([^\\\"]+)\\\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern META_TAG = Pattern.compile(
            "<meta\\b[^>]*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_ATTRIBUTE = Pattern.compile(
            "([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*([\\\"'])(.*?)\\2",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    interface Listener {
        void onTitle(String title);

        void onProgress(int progress);

        default void onLog(String message) {
        }

        boolean isCancelled();
    }

    static final class LoginRequiredException extends IllegalStateException {
        LoginRequiredException(String message) {
            super(message);
        }
    }

    static final class Result {
        final int imageCount;
        final int videoCount;
        final boolean hasVideo;
        final boolean unresolvedVideo;

        Result(int imageCount, int videoCount, boolean hasVideo, boolean unresolvedVideo) {
            this.imageCount = imageCount;
            this.videoCount = videoCount;
            this.hasVideo = hasVideo;
            this.unresolvedVideo = unresolvedVideo;
        }
    }

    static final class XMedia {
        final List<String> imageUrls;
        final List<String> videoUrls;
        final boolean hasVideo;
        final boolean unresolvedVideo;
        final boolean loginRequired;

        XMedia(List<String> imageUrls, List<String> videoUrls,
               boolean hasVideo, boolean unresolvedVideo) {
            this(imageUrls, videoUrls, hasVideo, unresolvedVideo, false);
        }

        XMedia(List<String> imageUrls, List<String> videoUrls,
               boolean hasVideo, boolean unresolvedVideo, boolean loginRequired) {
            this.imageUrls = imageUrls;
            this.videoUrls = videoUrls;
            this.hasVideo = hasVideo;
            this.unresolvedVideo = unresolvedVideo;
            this.loginRequired = loginRequired;
        }
    }

    static final class XProfileMedia {
        final String username;
        final List<String> statusUrls;

        XProfileMedia(String username, List<String> statusUrls) {
            this.username = username;
            this.statusUrls = statusUrls;
        }
    }

    static final class XMediaPage {
        final List<String> statusIds;
        final String bottomCursor;

        XMediaPage(List<String> statusIds, String bottomCursor) {
            this.statusIds = statusIds;
            this.bottomCursor = bottomCursor;
        }
    }

    private static final class XGraphQlOperation {
        final String name;
        final String queryId;
        final String bearerToken;
        final JSONObject features;
        final JSONObject fieldToggles;

        XGraphQlOperation(String name, String queryId, String bearerToken,
                          JSONObject features, JSONObject fieldToggles) {
            this.name = name;
            this.queryId = queryId;
            this.bearerToken = bearerToken;
            this.features = features;
            this.fieldToggles = fieldToggles;
        }
    }

    private static final class XWebClient {
        final XGraphQlOperation userByScreenName;
        final XGraphQlOperation userMedia;
        final XGraphQlOperation tweetResultByRestId;

        XWebClient(XGraphQlOperation userByScreenName, XGraphQlOperation userMedia,
                   XGraphQlOperation tweetResultByRestId) {
            this.userByScreenName = userByScreenName;
            this.userMedia = userMedia;
            this.tweetResultByRestId = tweetResultByRestId;
        }
    }

    private SocialImageDownloader() {
    }

    static boolean supports(String url) {
        return INSTAGRAM_PATH.matcher(url).find() || X_STATUS.matcher(url).find();
    }

    static boolean isXAccountOrStatusUrl(String url) {
        return X_ACCOUNT.matcher(url).find();
    }

    static String accountPlatformForUrl(String url) {
        try {
            xUsernameFromUrl(url);
            return AuthCookieStore.X;
        } catch (IllegalArgumentException ignored) {
            // Try the next supported account URL type.
        }
        try {
            instagramUsernameFromUrl(url);
            return AuthCookieStore.INSTAGRAM;
        } catch (IllegalArgumentException ignored) {
            return instagramShortcodeFromMediaUrl(url) == null
                    ? null : AuthCookieStore.INSTAGRAM;
        }
    }

    static String instagramShortcodeFromMediaUrl(String sourceUrl) {
        try {
            URI uri = URI.create(sourceUrl);
            String host = uri.getHost();
            if (host == null || !(host.equalsIgnoreCase("instagram.com")
                    || host.equalsIgnoreCase("www.instagram.com"))) {
                return null;
            }
            Matcher media = INSTAGRAM_MEDIA_URL.matcher(sourceUrl);
            return media.find() ? media.group(2) : null;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    static String instagramUsernameFromEmbedHtml(String html) {
        String username = firstGroup(USERNAME, normalizeInstagramData(html));
        if (username != null) {
            username = username.trim().replaceFirst("^@", "");
        }
        if (username == null || !username.matches("[A-Za-z0-9._]{1,30}")) {
            throw new IllegalStateException("Instagram 게시물의 작성자를 확인하지 못했습니다.");
        }
        return username;
    }

    static String resolveInstagramPostOwner(String sourceUrl, File cookieFile)
            throws Exception {
        String shortcode = instagramShortcodeFromMediaUrl(sourceUrl);
        if (shortcode == null) {
            throw new IllegalArgumentException("Instagram 게시물 주소를 확인하지 못했습니다.");
        }
        String html = readText("https://www.instagram.com/p/" + shortcode
                        + "/embed/captioned/", cookieHeader(cookieFile),
                INSTAGRAM_EMBED_USER_AGENT);
        return instagramUsernameFromEmbedHtml(html);
    }

    static String xUsernameFromUrl(String sourceUrl) {
        Matcher account = X_ACCOUNT.matcher(sourceUrl);
        if (!account.find() || isReservedXPath(account.group(1))) {
            throw new IllegalArgumentException("X 계정 주소를 확인하지 못했습니다.");
        }
        return account.group(1);
    }

    static String instagramUsernameFromUrl(String sourceUrl) {
        Matcher account = INSTAGRAM_ACCOUNT.matcher(sourceUrl);
        if (!account.matches() || isReservedInstagramPath(account.group(1))) {
            throw new IllegalArgumentException("Instagram 계정 주소를 확인하지 못했습니다.");
        }
        return account.group(1);
    }

    static XProfileMedia discoverXProfileMedia(String sourceUrl, File cookieFile) throws Exception {
        String cookies = cookieHeader(cookieFile);
        String csrfToken = cookieValue(cookies, "ct0");
        if (isEmpty(cookieValue(cookies, "auth_token")) || isEmpty(csrfToken)) {
            throw new IllegalStateException("X 로그인이 필요합니다.");
        }
        String username = resolveXUsername(sourceUrl);
        XWebClient client = loadXWebClient(username, cookies);
        JSONObject user = queryXGraphQl(client.userByScreenName, cookies, csrfToken,
                userVariables(username));
        JSONObject userResult = user.optJSONObject("data") == null ? null
                : user.optJSONObject("data").optJSONObject("user");
        userResult = userResult == null ? null : userResult.optJSONObject("result");
        if (userResult == null || isEmpty(userResult.optString("rest_id"))) {
            throw new IllegalStateException("X 계정 정보를 확인하지 못했습니다.");
        }
        String userId = userResult.optString("rest_id");
        JSONObject core = userResult.optJSONObject("core");
        String canonicalUsername = core == null ? null : core.optString("screen_name");
        if (!isEmpty(canonicalUsername)) {
            username = canonicalUsername;
        }

        Set<String> statusIds = new LinkedHashSet<>();
        Set<String> seenCursors = new LinkedHashSet<>();
        String cursor = null;
        do {
            JSONObject page = queryXGraphQl(client.userMedia, cookies, csrfToken,
                    userMediaVariables(userId, cursor));
            XMediaPage parsed = parseXUserMediaPage(page);
            statusIds.addAll(parsed.statusIds);
            cursor = parsed.bottomCursor;
        } while (!isEmpty(cursor) && seenCursors.add(cursor));

        List<String> urls = new ArrayList<>();
        for (String statusId : statusIds) {
            urls.add("https://x.com/" + username + "/status/" + statusId);
        }
        return new XProfileMedia(username, urls);
    }

    private static XWebClient loadXWebClient(String username, String cookies) throws Exception {
        return loadXWebClientFromPage("https://x.com/" + username + "/media", cookies);
    }

    private static XWebClient loadXWebClientForStatus(String cookies) throws Exception {
        return loadXWebClientFromPage("https://x.com/home", cookies);
    }

    private static XWebClient loadXWebClientFromPage(String pageUrl, String cookies)
            throws Exception {
        String html = readText(pageUrl, cookies);
        Matcher script = X_MAIN_SCRIPT.matcher(html);
        if (!script.find()) {
            throw new IllegalStateException("X 웹 클라이언트 정보를 찾지 못했습니다.");
        }
        String javascript = readText(decodeHtml(script.group()), null);
        Matcher bearer = X_BEARER_TOKEN.matcher(javascript);
        if (!bearer.find()) {
            throw new IllegalStateException("X 인증 정보를 찾지 못했습니다.");
        }
        String bearerToken = URLDecoder.decode(
                bearer.group(), StandardCharsets.UTF_8.name());
        XGraphQlOperation tweetResultByRestId = null;
        try {
            tweetResultByRestId = parseXOperation(
                    javascript, "TweetResultByRestId", bearerToken);
        } catch (IllegalStateException ignored) {
            // Account discovery does not depend on the single-tweet operation.
        }
        return new XWebClient(
                parseXOperation(javascript, "UserByScreenName", bearerToken),
                parseXOperation(javascript, "UserMedia", bearerToken),
                tweetResultByRestId);
    }

    private static XGraphQlOperation parseXOperation(String javascript, String name,
                                                      String bearerToken) {
        Pattern pattern = Pattern.compile(
                "queryId:\"([^\"]+)\",operationName:\"" + Pattern.quote(name)
                        + "\",operationType:\"query\",metadata:\\{featureSwitches:\\[(.*?)\\],fieldToggles:\\[(.*?)\\]",
                Pattern.DOTALL);
        Matcher operation = pattern.matcher(javascript);
        if (!operation.find()) {
            throw new IllegalStateException("X " + name + " 작업 정보를 찾지 못했습니다.");
        }
        return new XGraphQlOperation(name, operation.group(1), bearerToken,
                jsonFlags(operation.group(2), true), jsonFlags(operation.group(3), false));
    }

    private static JSONObject jsonFlags(String source, boolean value) {
        JSONObject result = new JSONObject();
        Matcher names = QUOTED_VALUE.matcher(source);
        while (names.find()) {
            try {
                result.put(names.group(1), value);
            } catch (Exception ignored) {
                // JSONObject only rejects invalid numeric values, which booleans cannot contain.
            }
        }
        return result;
    }

    private static JSONObject userVariables(String username) throws Exception {
        JSONObject variables = new JSONObject();
        variables.put("screen_name", username);
        variables.put("withSafetyModeUserFields", true);
        return variables;
    }

    private static JSONObject userMediaVariables(String userId, String cursor) throws Exception {
        JSONObject variables = new JSONObject();
        variables.put("userId", userId);
        variables.put("count", 100);
        variables.put("includePromotedContent", false);
        variables.put("withClientEventToken", false);
        variables.put("withBirdwatchNotes", false);
        variables.put("withVoice", true);
        if (!isEmpty(cursor)) {
            variables.put("cursor", cursor);
        }
        return variables;
    }

    static JSONObject tweetResultVariables(String statusId) throws Exception {
        JSONObject variables = new JSONObject();
        variables.put("tweetId", statusId);
        variables.put("withCommunity", false);
        variables.put("includePromotedContent", false);
        variables.put("withVoice", false);
        return variables;
    }

    private static JSONObject queryXGraphQl(XGraphQlOperation operation, String cookies,
                                             String csrfToken, JSONObject variables)
            throws Exception {
        String url = "https://x.com/i/api/graphql/" + operation.queryId + "/" + operation.name
                + "?variables=" + encodeQuery(variables.toString())
                + "&features=" + encodeQuery(operation.features.toString())
                + "&fieldToggles=" + encodeQuery(operation.fieldToggles.toString());
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8");
        connection.setRequestProperty("Authorization", "Bearer " + operation.bearerToken);
        connection.setRequestProperty("Cookie", cookies);
        connection.setRequestProperty("x-csrf-token", csrfToken);
        connection.setRequestProperty("x-twitter-active-user", "yes");
        connection.setRequestProperty("x-twitter-auth-type", "OAuth2Session");
        connection.setRequestProperty("x-twitter-client-language", "ko");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("X API HTTP " + status);
            }
            return new JSONObject(readConnectionText(connection));
        } finally {
            connection.disconnect();
        }
    }

    private static String encodeQuery(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
    }

    static XMediaPage parseXUserMediaPage(JSONObject root) {
        return parseXTimelinePage(root, false, null);
    }

    static List<String> parseXTimelineMediaStatusIds(JSONObject root) {
        return parseXTimelineMediaStatusIds(root, null);
    }

    static List<String> parseXTimelineMediaStatusIds(JSONObject root, String username) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(
                parseXTimelinePage(root, true, username).statusIds);
        collectGraphMediaStatusIds(root, username, ids);
        return new ArrayList<>(ids);
    }

    private static XMediaPage parseXTimelinePage(JSONObject root, boolean requireMedia,
                                                  String username) {
        List<String> statusIds = new ArrayList<>();
        String bottomCursor = null;
        JSONObject data = root.optJSONObject("data");
        JSONObject user = data == null ? null : data.optJSONObject("user");
        JSONObject result = user == null ? null : user.optJSONObject("result");
        JSONObject timelineV2 = result == null ? null : result.optJSONObject("timeline_v2");
        JSONObject timeline = timelineV2 == null ? null : timelineV2.optJSONObject("timeline");
        if (timeline == null && result != null) {
            timeline = result.optJSONObject("timeline");
        }
        JSONArray instructions = timeline == null ? null : timeline.optJSONArray("instructions");
        if (instructions == null) {
            return new XMediaPage(statusIds, null);
        }
        for (int i = 0; i < instructions.length(); i++) {
            JSONObject instruction = instructions.optJSONObject(i);
            JSONArray entries = instruction == null ? null : instruction.optJSONArray("entries");
            if (entries == null) {
                continue;
            }
            for (int j = 0; j < entries.length(); j++) {
                JSONObject entry = entries.optJSONObject(j);
                if (entry == null) {
                    continue;
                }
                JSONObject content = entry.optJSONObject("content");
                if (content == null) {
                    continue;
                }
                if ("Bottom".equalsIgnoreCase(content.optString("cursorType"))) {
                    bottomCursor = content.optString("value");
                }
                addXTimelineStatus(content.optJSONObject("itemContent"), statusIds,
                        requireMedia, username);
                JSONArray items = content.optJSONArray("items");
                if (items != null) {
                    for (int k = 0; k < items.length(); k++) {
                        JSONObject moduleItem = items.optJSONObject(k);
                        JSONObject item = moduleItem == null
                                ? null : moduleItem.optJSONObject("item");
                        addXTimelineStatus(item == null
                                ? null : item.optJSONObject("itemContent"), statusIds,
                                requireMedia, username);
                    }
                }
            }
        }
        return new XMediaPage(new ArrayList<>(new LinkedHashSet<>(statusIds)), bottomCursor);
    }

    private static void addXTimelineStatus(JSONObject itemContent, List<String> statusIds,
                                           boolean requireMedia, String username) {
        JSONObject tweetResults = itemContent == null
                ? null : itemContent.optJSONObject("tweet_results");
        JSONObject tweet = tweetResults == null ? null : tweetResults.optJSONObject("result");
        if (tweet != null && "TweetWithVisibilityResults".equals(tweet.optString("__typename"))) {
            tweet = tweet.optJSONObject("tweet");
        }
        if (tweet == null) {
            return;
        }
        String id = tweet.optString("rest_id");
        JSONObject legacy = tweet.optJSONObject("legacy");
        if (isEmpty(id) && legacy != null) {
            id = legacy.optString("id_str");
        }
        if (!isEmpty(id)
                && (!requireMedia || graphTweetHasMedia(tweet))
                && graphTweetMatchesUsername(tweet, username)) {
            statusIds.add(id);
        }
    }

    private static boolean graphTweetHasMedia(JSONObject tweet) {
        if (tweetHasMedia(tweet)) {
            return true;
        }
        JSONObject legacy = tweet.optJSONObject("legacy");
        return legacy != null && tweetHasMedia(legacy);
    }

    private static boolean graphTweetMatchesUsername(JSONObject tweet, String username) {
        if (isEmpty(username)) {
            return true;
        }
        JSONObject core = tweet.optJSONObject("core");
        JSONObject userResults = core == null ? null : core.optJSONObject("user_results");
        JSONObject user = userResults == null ? null : userResults.optJSONObject("result");
        if (user == null) {
            return true;
        }
        JSONObject userCore = user.optJSONObject("core");
        JSONObject userLegacy = user.optJSONObject("legacy");
        String screenName = userCore == null ? null : userCore.optString("screen_name");
        if (isEmpty(screenName) && userLegacy != null) {
            screenName = userLegacy.optString("screen_name");
        }
        return isEmpty(screenName) || username.equalsIgnoreCase(screenName);
    }

    private static void collectGraphMediaStatusIds(Object value, String username,
                                                   Set<String> statusIds) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String id = object.optString("rest_id");
            JSONObject legacy = object.optJSONObject("legacy");
            if (isEmpty(id) && legacy != null) {
                id = legacy.optString("id_str");
            }
            if (!isEmpty(id) && graphTweetHasMedia(object)
                    && graphTweetMatchesUsername(object, username)) {
                statusIds.add(id);
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                collectGraphMediaStatusIds(object.opt(keys.next()), username, statusIds);
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int index = 0; index < array.length(); index++) {
                collectGraphMediaStatusIds(array.opt(index), username, statusIds);
            }
        }
    }

    private static String resolveXUsername(String sourceUrl) throws Exception {
        Matcher account = X_ACCOUNT.matcher(sourceUrl);
        if (!account.find()) {
            throw new IllegalArgumentException("X 계정 또는 게시물 링크가 아닙니다.");
        }
        String username = account.group(1);
        Matcher status = X_STATUS.matcher(sourceUrl);
        if (status.find()) {
            try {
                JSONObject user = readXTweet(status.group(1)).optJSONObject("user");
                String author = user == null ? null : user.optString("screen_name");
                if (!isEmpty(author)) {
                    return author;
                }
            } catch (Exception error) {
                if (isReservedXPath(username)) {
                    throw error;
                }
            }
        }
        if (!isReservedXPath(username)) {
            return username;
        }
        throw new IllegalArgumentException("게시물 작성자 계정을 확인하지 못했습니다.");
    }

    private static boolean isReservedXPath(String value) {
        String lower = value.toLowerCase(Locale.US);
        return lower.equals("i") || lower.equals("home") || lower.equals("explore")
                || lower.equals("search") || lower.equals("messages")
                || lower.equals("settings");
    }

    private static boolean isReservedInstagramPath(String value) {
        String lower = value.toLowerCase(Locale.US);
        return lower.equals("p") || lower.equals("reel") || lower.equals("reels")
                || lower.equals("tv") || lower.equals("stories")
                || lower.equals("explore") || lower.equals("accounts")
                || lower.equals("direct") || lower.equals("about")
                || lower.equals("developer") || lower.equals("legal")
                || lower.equals("web") || lower.equals("api")
                || lower.equals("challenge");
    }

    static List<String> parseInstagramProfileMediaUrls(JSONObject root, String username) {
        Map<String, String> urls = new LinkedHashMap<>();
        collectInstagramProfileMedia(root, username, false, false, urls);
        return new ArrayList<>(urls.values());
    }

    static Boolean instagramProfileHasMore(JSONObject root) {
        return findInstagramTimelinePagination(root, false);
    }

    static Boolean relevantInstagramPagination(Boolean hasMore, int responseMediaCount,
                                               int newMediaCount) {
        if (hasMore == null || responseMediaCount == 0) {
            return null;
        }
        // A later response for an already-known item must not reopen pagination
        // after the profile timeline has explicitly reported its final page.
        if (hasMore && newMediaCount == 0) {
            return null;
        }
        return hasMore;
    }

    private static Boolean findInstagramTimelinePagination(Object value, boolean inTimeline) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            if (inTimeline) {
                JSONObject pageInfo = object.optJSONObject("page_info");
                if (pageInfo != null && pageInfo.has("has_next_page")) {
                    return pageInfo.optBoolean("has_next_page");
                }
                for (String key : new String[]{"more_available", "has_more"}) {
                    if (object.has(key)) {
                        return object.optBoolean(key);
                    }
                }
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                boolean childTimeline = inTimeline || isInstagramTimelineKey(key)
                        || isInstagramProfileTimelineKey(key);
                Boolean result = findInstagramTimelinePagination(object.opt(key), childTimeline);
                if (result != null) {
                    return result;
                }
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int index = 0; index < array.length(); index++) {
                Boolean result = findInstagramTimelinePagination(array.opt(index), inTimeline);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static void collectInstagramProfileMedia(Object value, String username,
                                                      boolean inheritedOwner,
                                                      boolean inCarousel,
                                                      Map<String, String> urls) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String directOwner = instagramMediaOwner(object);
            boolean owned = isEmpty(directOwner)
                    ? inheritedOwner : username.equalsIgnoreCase(directOwner);
            String objectUsername = object.optString("username");
            boolean profileContext = (isEmpty(directOwner) ? inheritedOwner : owned)
                    || username.equalsIgnoreCase(objectUsername);
            String code = firstNonEmpty(object, "code", "shortcode");
            if (!inCarousel && owned && isInstagramMediaObject(object, code)) {
                addInstagramMediaUrl(urls, instagramMediaUrl(object, code));
            }

            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                boolean childOwner = isInstagramProfileTimelineKey(key) || (profileContext
                        && (inheritedOwner || isInstagramTimelineKey(key)));
                boolean childCarousel = inCarousel || key.equalsIgnoreCase("carousel_media")
                        || key.equalsIgnoreCase("edge_sidecar_to_children");
                collectInstagramProfileMedia(object.opt(key), username,
                        childOwner, childCarousel, urls);
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int index = 0; index < array.length(); index++) {
                collectInstagramProfileMedia(array.opt(index), username,
                        inheritedOwner, inCarousel, urls);
            }
        }
    }

    private static String instagramMediaOwner(JSONObject object) {
        for (String key : new String[]{"user", "owner"}) {
            JSONObject owner = object.optJSONObject(key);
            if (owner != null && !isEmpty(owner.optString("username"))) {
                return owner.optString("username");
            }
        }
        return null;
    }

    private static boolean isInstagramMediaObject(JSONObject object, String code) {
        if (isEmpty(code) || !code.matches("[A-Za-z0-9_-]+")) {
            return false;
        }
        String typeName = object.optString("__typename");
        return object.has("media_type") || object.has("product_type")
                || object.has("is_video") || object.has("image_versions2")
                || object.has("display_url") || object.has("video_versions")
                || object.has("carousel_media") || object.has("edge_sidecar_to_children")
                || typeName.startsWith("Graph");
    }

    private static boolean isInstagramTimelineKey(String key) {
        String lower = key.toLowerCase(Locale.US);
        return lower.contains("timeline") || lower.equals("items")
                || lower.equals("edges") || lower.equals("feed_items");
    }

    private static boolean isInstagramProfileTimelineKey(String key) {
        return key.equalsIgnoreCase("xdt_api__v1__feed__user_timeline_graphql_connection");
    }

    private static String instagramMediaUrl(JSONObject object, String code) {
        int mediaType = object.optInt("media_type", -1);
        String typename = object.optString("__typename");
        if (mediaType == 1 || mediaType == 8 || "GraphImage".equals(typename)
                || "GraphSidecar".equals(typename)) {
            return "https://www.instagram.com/p/" + code + "/";
        }
        String permalink = object.optString("permalink");
        Matcher permalinkMatcher = INSTAGRAM_MEDIA_URL.matcher(permalink);
        if (permalinkMatcher.find()) {
            String type = permalinkMatcher.group(1).equalsIgnoreCase("p") ? "p" : "reel";
            return "https://www.instagram.com/" + type + "/"
                    + permalinkMatcher.group(2) + "/";
        }
        String productType = object.optString("product_type").toLowerCase(Locale.US);
        String type = productType.equals("clips") || productType.equals("reels")
                ? "reel" : "p";
        return "https://www.instagram.com/" + type + "/" + code + "/";
    }

    private static void addInstagramMediaUrl(Map<String, String> urls, String url) {
        Matcher matcher = INSTAGRAM_MEDIA_URL.matcher(url);
        if (!matcher.find()) {
            return;
        }
        String type = matcher.group(1).equalsIgnoreCase("p") ? "p" : "reel";
        String code = matcher.group(2);
        String normalized = "https://www.instagram.com/" + type + "/" + code + "/";
        String current = urls.get(code);
        if (current == null || (type.equals("reel") && current.contains("/p/"))) {
            urls.put(code, normalized);
        }
    }

    static List<String> parseXProfileMediaUrls(String html, String username) throws Exception {
        Matcher script = NEXT_DATA.matcher(html);
        if (!script.find()) {
            throw new IllegalStateException("X 계정 타임라인을 해석하지 못했습니다.");
        }
        JSONObject root = new JSONObject(script.group(1));
        Set<String> statusIds = new LinkedHashSet<>();
        collectProfileMediaStatusIds(root, username, statusIds);
        List<String> sorted = new ArrayList<>(statusIds);
        Collections.sort(sorted, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                return new BigInteger(right).compareTo(new BigInteger(left));
            }
        });
        List<String> urls = new ArrayList<>();
        for (String statusId : sorted) {
            urls.add("https://x.com/" + username + "/status/" + statusId);
        }
        return urls;
    }

    private static void collectProfileMediaStatusIds(Object value, String username,
                                                     Set<String> statusIds) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            JSONObject user = object.optJSONObject("user");
            String id = object.optString("id_str");
            if (!isEmpty(id) && user != null
                    && username.equalsIgnoreCase(user.optString("screen_name"))
                    && object.optJSONObject("retweeted_status") == null
                    && tweetHasMedia(object)) {
                statusIds.add(id);
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                collectProfileMediaStatusIds(object.opt(keys.next()), username, statusIds);
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                collectProfileMediaStatusIds(array.opt(i), username, statusIds);
            }
        }
    }

    private static boolean tweetHasMedia(JSONObject tweet) {
        if (hasItems(tweet.optJSONArray("mediaDetails"))
                || hasItems(tweet.optJSONArray("photos"))) {
            return true;
        }
        JSONObject extended = tweet.optJSONObject("extended_entities");
        if (extended != null && hasItems(extended.optJSONArray("media"))) {
            return true;
        }
        JSONObject entities = tweet.optJSONObject("entities");
        return entities != null && hasItems(entities.optJSONArray("media"));
    }

    private static boolean hasItems(JSONArray array) {
        return array != null && array.length() > 0;
    }

    static Result download(Context context, String url, File outputDirectory, File cookieFile,
                           boolean allowAmbiguousFallback, boolean downloadDirectVideos,
                           Listener listener) throws Exception {
        Matcher xMatcher = X_STATUS.matcher(url);
        if (xMatcher.find()) {
            return downloadXImages(context, xMatcher.group(1), outputDirectory,
                    cookieFile, allowAmbiguousFallback, downloadDirectVideos, listener);
        }
        Matcher instagramMatcher = INSTAGRAM_PATH.matcher(url);
        if (instagramMatcher.find()) {
            boolean reelOrVideoRoute = !"p".equalsIgnoreCase(instagramMatcher.group(1));
            return downloadInstagramImages(context, instagramMatcher.group(2), outputDirectory,
                    cookieFile, allowAmbiguousFallback, downloadDirectVideos,
                    reelOrVideoRoute, listener);
        }
        return new Result(0, 0, true, true);
    }

    private static Result downloadXImages(Context context, String statusId, File outputDirectory,
                                          File cookieFile, boolean allowAmbiguousFallback,
                                          boolean downloadDirectVideos, Listener listener)
            throws Exception {
        listener.onLog("X 사진 검사 시작");
        JSONObject tweet = null;
        List<String> images = new ArrayList<>();
        List<String> videos = new ArrayList<>();
        boolean hasVideo = false;
        boolean unresolvedVideo = false;
        boolean loginRequired = false;
        boolean imagesFromHtmlFallback = false;
        String savedCookies = cookieHeader(cookieFile);
        boolean hasAuthToken = !isEmpty(cookieValue(savedCookies, "auth_token"));
        boolean hasCsrfToken = !isEmpty(cookieValue(savedCookies, "ct0"));
        boolean authenticatedGraphQlSucceeded = false;
        String authenticatedGraphQlFailure = null;
        try {
            tweet = readXTweet(statusId);
            XMedia media = parseXMedia(tweet);
            images.addAll(media.imageUrls);
            videos.addAll(media.videoUrls);
            hasVideo = media.hasVideo;
            unresolvedVideo = media.unresolvedVideo;
            listener.onLog("X 공개 응답: 사진 " + images.size() + "개, 직접 영상 "
                    + videos.size() + "개, 영상 표시=" + hasVideo);
        } catch (Exception error) {
            listener.onLog("X 공개 응답 실패: " + error.getClass().getSimpleName());
            // Sensitive posts can be returned as TweetTombstone by the public endpoint.
        }

        if (images.isEmpty() && videos.isEmpty() && hasAuthToken && hasCsrfToken) {
            listener.onLog("X 로그인 GraphQL 검사 시작: auth_token=true, ct0=true");
            try {
                XWebClient client = loadXWebClientForStatus(savedCookies);
                if (client.tweetResultByRestId == null) {
                    throw new IllegalStateException(
                            "X TweetResultByRestId 작업 정보를 찾지 못했습니다.");
                }
                JSONObject authenticatedTweet = queryXGraphQl(
                        client.tweetResultByRestId, savedCookies,
                        cookieValue(savedCookies, "ct0"), tweetResultVariables(statusId));
                authenticatedGraphQlSucceeded = true;
                XMedia authenticatedMedia = parseXMedia(authenticatedTweet);
                images.addAll(authenticatedMedia.imageUrls);
                videos.addAll(authenticatedMedia.videoUrls);
                hasVideo = authenticatedMedia.hasVideo;
                unresolvedVideo = authenticatedMedia.unresolvedVideo;
                tweet = authenticatedTweet;
                String resultType = xTweetResultType(authenticatedTweet);
                String graphQlError = firstXGraphQlError(authenticatedTweet);
                listener.onLog("X 로그인 GraphQL 응답: type=" + resultType
                        + ", 사진 " + authenticatedMedia.imageUrls.size()
                        + "개, 직접 영상 " + authenticatedMedia.videoUrls.size()
                        + "개, 영상 표시=" + authenticatedMedia.hasVideo
                        + (TextUtils.isEmpty(graphQlError) ? "" : ", 오류=" + graphQlError));
                if (isXSensitiveTweet(authenticatedTweet)
                        && (!authenticatedMedia.imageUrls.isEmpty()
                        || !authenticatedMedia.videoUrls.isEmpty())) {
                    listener.onLog("X 민감 콘텐츠 확인: 로그인 응답의 실제 미디어를 사용");
                }
            } catch (Exception error) {
                authenticatedGraphQlFailure = errorSummary(error);
                listener.onLog("X 로그인 GraphQL 실패: " + authenticatedGraphQlFailure);
            }
        } else if (images.isEmpty() && videos.isEmpty()
                && (!TextUtils.isEmpty(savedCookies) || allowAmbiguousFallback)) {
            listener.onLog("X 로그인 GraphQL 생략: auth_token=" + hasAuthToken
                    + ", ct0=" + hasCsrfToken);
        }

        String htmlUsername = null;
        if (images.isEmpty() && videos.isEmpty() && allowAmbiguousFallback) {
            List<String> cookieCandidates = new ArrayList<>();
            cookieCandidates.add(null);
            if (!TextUtils.isEmpty(savedCookies)) {
                cookieCandidates.add(savedCookies);
            }
            for (String cookies : cookieCandidates) {
                try {
                    listener.onLog(cookies == null
                            ? "X 공개 페이지 사진 검사" : "X 로그인 페이지 사진 검사");
                    String html = readText("https://x.com/i/status/" + statusId, cookies);
                    XMedia htmlMedia = parseXStatusHtmlMedia(html);
                    loginRequired |= htmlMedia.loginRequired;
                    images.addAll(htmlMedia.imageUrls);
                    videos.addAll(htmlMedia.videoUrls);
                    if (htmlMedia.loginRequired) {
                        hasVideo = true;
                        unresolvedVideo = true;
                    } else if (!htmlMedia.imageUrls.isEmpty()
                            || !htmlMedia.videoUrls.isEmpty()) {
                        hasVideo = htmlMedia.hasVideo;
                        unresolvedVideo = htmlMedia.unresolvedVideo;
                    }
                    listener.onLog("X 페이지 응답: 사진 " + htmlMedia.imageUrls.size()
                            + "개, 직접 영상 " + htmlMedia.videoUrls.size()
                            + "개, 영상 표시=" + htmlMedia.hasVideo
                            + ", 로그인 필요=" + htmlMedia.loginRequired);
                    if (htmlMedia.loginRequired) {
                        listener.onLog(cookies == null
                                ? "X 공개 페이지의 제한 이미지를 무시하고 로그인 정보로 재시도"
                                : "저장된 X 로그인으로도 연령 제한 미디어에 접근하지 못함");
                    }
                    if (TextUtils.isEmpty(htmlUsername)) {
                        htmlUsername = parseXStatusHtmlUsername(html);
                    }
                    if (!images.isEmpty() || !videos.isEmpty()) {
                        imagesFromHtmlFallback = !images.isEmpty();
                        break;
                    }
                } catch (Exception error) {
                    listener.onLog("X 페이지 검사 실패: "
                            + error.getClass().getSimpleName());
                    // A public and an authenticated page can fail independently.
                }
            }
        }
        images = new ArrayList<>(new LinkedHashSet<>(images));
        videos = new ArrayList<>(new LinkedHashSet<>(videos));
        if (images.isEmpty() && videos.isEmpty()) {
            if (loginRequired) {
                if (!hasAuthToken || !hasCsrfToken) {
                    throw new LoginRequiredException(
                            "연령 제한 X 게시물입니다. 저장된 로그인 쿠키에서 "
                                    + (!hasAuthToken ? "auth_token" : "ct0")
                                    + "을 찾지 못했습니다. 앱에서 X 로그인을 다시 저장해 주세요.");
                }
                if (!authenticatedGraphQlSucceeded) {
                    throw new LoginRequiredException(
                            "X 로그인 쿠키는 확인했지만 로그인 미디어 API 조회에 실패했습니다: "
                                    + (TextUtils.isEmpty(authenticatedGraphQlFailure)
                                    ? "원인 미확인" : authenticatedGraphQlFailure));
                }
                throw new LoginRequiredException(
                        "X 로그인 API는 응답했지만 이 민감 게시물의 실제 미디어를 반환하지 않았습니다. "
                                + "다운로드 로그의 GraphQL 응답 type과 오류를 확인해 주세요.");
            }
            listener.onLog("직접 저장할 X 미디어 없음");
            return new Result(0, 0, hasVideo, unresolvedVideo);
        }

        JSONObject user = tweet == null ? null : tweet.optJSONObject("user");
        String username = user == null ? "" : user.optString("screen_name");
        if (TextUtils.isEmpty(username)) {
            username = htmlUsername;
        }
        String title = TextUtils.isEmpty(username)
                ? "X " + statusId : "X @" + username + " " + statusId;
        listener.onTitle(title);
        if (!images.isEmpty()) {
            downloadImages(context, images, outputDirectory, title,
                    imagesFromHtmlFallback, listener);
        }
        if (downloadDirectVideos && !videos.isEmpty()) {
            downloadVideos(context, videos, outputDirectory, title,
                    cookieHeader(cookieFile), listener);
        }
        return new Result(images.size(), downloadDirectVideos ? videos.size() : 0,
                hasVideo, unresolvedVideo);
    }

    private static JSONObject readXTweet(String statusId) throws Exception {
        Throwable lastError = null;
        for (String token : new String[]{"a", "0", "1"}) {
            String endpoint = "https://cdn.syndication.twimg.com/tweet-result?id=" + statusId
                    + "&lang=ko&token=" + token;
            try {
                JSONObject tweet = new JSONObject(readText(endpoint, null));
                if (tweet.length() > 0 && !"TweetTombstone".equals(
                        tweet.optString("__typename"))) {
                    return tweet;
                }
            } catch (Throwable error) {
                lastError = error;
            }
        }
        if (lastError instanceof Exception) {
            throw (Exception) lastError;
        }
        throw new IllegalStateException("X 게시물 정보를 가져오지 못했습니다.");
    }

    static XMedia parseXMedia(JSONObject tweet) {
        List<String> images = new ArrayList<>();
        List<String> videos = new ArrayList<>();
        boolean[] unresolvedVideo = new boolean[]{false};
        boolean hasVideo = collectXMediaTree(
                tweet, images, videos, unresolvedVideo, 0);

        return new XMedia(new ArrayList<>(new LinkedHashSet<>(images)),
                new ArrayList<>(new LinkedHashSet<>(videos)), hasVideo,
                unresolvedVideo[0]);
    }

    private static boolean collectXMediaTree(JSONObject root, List<String> images,
                                             List<String> videos,
                                             boolean[] unresolvedVideo, int depth) {
        if (root == null || depth > 4) {
            return false;
        }
        JSONObject mediaTweet = unwrapXMediaTweet(root);
        boolean hasVideo = collectXMediaObject(
                mediaTweet, images, videos, unresolvedVideo);
        JSONObject legacy = mediaTweet.optJSONObject("legacy");
        if (legacy != null) {
            hasVideo |= collectXMediaObject(legacy, images, videos, unresolvedVideo);
        }
        for (String key : new String[]{"quoted_status_result", "retweeted_status_result"}) {
            JSONObject wrapper = mediaTweet.optJSONObject(key);
            JSONObject result = wrapper == null ? null : wrapper.optJSONObject("result");
            if (result != null) {
                hasVideo |= collectXMediaTree(
                        result, images, videos, unresolvedVideo, depth + 1);
            }
        }
        for (String key : new String[]{"quoted_tweet", "retweeted_status"}) {
            JSONObject nested = mediaTweet.optJSONObject(key);
            if (nested == null && legacy != null) {
                nested = legacy.optJSONObject(key);
            }
            if (nested != null) {
                hasVideo |= collectXMediaTree(
                        nested, images, videos, unresolvedVideo, depth + 1);
            }
        }
        return hasVideo;
    }

    static String xTweetResultType(JSONObject root) {
        JSONObject data = root.optJSONObject("data");
        JSONObject tweetResult = data == null ? null : data.optJSONObject("tweetResult");
        JSONObject result = tweetResult == null ? null : tweetResult.optJSONObject("result");
        return result == null ? "missing" : result.optString("__typename", "unknown");
    }

    static boolean isXSensitiveTweet(JSONObject root) {
        JSONObject tweet = unwrapXMediaTweet(root);
        JSONObject legacy = tweet.optJSONObject("legacy");
        return tweet.optBoolean("possibly_sensitive")
                || (legacy != null && legacy.optBoolean("possibly_sensitive"));
    }

    private static String firstXGraphQlError(JSONObject root) {
        JSONArray errors = root.optJSONArray("errors");
        if (errors == null || errors.length() == 0) {
            return null;
        }
        JSONObject first = errors.optJSONObject(0);
        if (first == null) {
            return "unknown";
        }
        String code = first.optString("code");
        String message = first.optString("message");
        return (isEmpty(code) ? "" : "code " + code + " ")
                + (isEmpty(message) ? "unknown" : message);
    }

    private static String errorSummary(Throwable error) {
        String message = error.getMessage();
        if (TextUtils.isEmpty(message)) {
            return error.getClass().getSimpleName();
        }
        String summary = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (summary.length() > 180) {
            summary = summary.substring(0, 180) + "...";
        }
        return error.getClass().getSimpleName() + ": " + summary;
    }

    private static JSONObject unwrapXMediaTweet(JSONObject root) {
        JSONObject current = root;
        JSONObject data = current.optJSONObject("data");
        JSONObject tweetResult = data == null ? null : data.optJSONObject("tweetResult");
        JSONObject result = tweetResult == null ? null : tweetResult.optJSONObject("result");
        if (result != null) {
            current = result;
        }
        if ("TweetWithVisibilityResults".equals(current.optString("__typename"))) {
            JSONObject visibleTweet = current.optJSONObject("tweet");
            if (visibleTweet != null) {
                current = visibleTweet;
            }
        }
        return current;
    }

    private static boolean collectXMediaObject(JSONObject tweet, List<String> images,
                                                List<String> videos,
                                                boolean[] unresolvedVideo) {
        boolean hasVideo = collectXMedia(
                tweet.optJSONArray("mediaDetails"), images, videos,
                unresolvedVideo, false);
        hasVideo |= collectXMedia(tweet.optJSONArray("photos"), images, videos,
                unresolvedVideo, true);
        for (String key : new String[]{"extended_entities", "entities"}) {
            JSONObject entities = tweet.optJSONObject(key);
            if (entities != null) {
                hasVideo |= collectXMedia(
                        entities.optJSONArray("media"), images, videos,
                        unresolvedVideo, false);
            }
        }
        return hasVideo;
    }

    static XMedia parseXStatusHtmlMedia(String html) {
        String normalized = decodeHtml(html);
        Map<String, String> images = new LinkedHashMap<>();
        Matcher matcher = X_STATUS_HTML_IMAGE.matcher(normalized);
        while (matcher.find()) {
            String imageUrl = withOriginalImageSize(matcher.group());
            String identity = xMediaIdentity(imageUrl);
            String current = images.get(identity);
            if (current == null || xImagePreference(imageUrl) > xImagePreference(current)) {
                images.put(identity, imageUrl);
            }
        }
        boolean hasVideo = X_STATUS_HTML_VIDEO.matcher(normalized).find();
        List<String> videos = new ArrayList<>();
        Matcher videoMatcher = X_STATUS_HTML_VIDEO_URL.matcher(normalized);
        while (videoMatcher.find()) {
            videos.add(decodeHtml(videoMatcher.group()));
        }
        hasVideo |= !videos.isEmpty();
        boolean loginRequired = isXLoginRestrictedHtml(normalized) && videos.isEmpty();
        if (loginRequired) {
            images.clear();
            hasVideo = true;
        }
        boolean unresolvedVideo = hasVideo && videos.isEmpty();
        return new XMedia(new ArrayList<>(images.values()),
                new ArrayList<>(new LinkedHashSet<>(videos)), hasVideo, unresolvedVideo,
                loginRequired);
    }

    static boolean isXLoginRestrictedHtml(String html) {
        String lower = decodeHtml(html).toLowerCase(Locale.US);
        return lower.contains("age-restricted adult content")
                || lower.contains("to view this media, you’ll need to log in")
                || lower.contains("to view this media, you'll need to log in")
                || (lower.contains("name=\"rating\"")
                        && lower.contains("content=\"adult\""));
    }

    static String parseXStatusHtmlUsername(String html) {
        Matcher matcher = X_STATUS_HTML_URL.matcher(decodeHtml(html));
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String xMediaIdentity(String url) {
        try {
            return URI.create(url).getPath().toLowerCase(Locale.US);
        } catch (IllegalArgumentException ignored) {
            int query = url.indexOf('?');
            return (query < 0 ? url : url.substring(0, query)).toLowerCase(Locale.US);
        }
    }

    private static int xImagePreference(String url) {
        String lower = url.toLowerCase(Locale.US);
        if (lower.matches(".*(?:[?&]format=)(?:jpe?g|png|gif)(?:&|$).*")
                || lower.matches(".*\\.(?:jpe?g|png|gif)(?:\\?|$).*") ) {
            return 3;
        }
        if (lower.contains("format=webp") || lower.contains(".webp")) {
            return 1;
        }
        return 2;
    }

    private static boolean collectXMedia(JSONArray media, List<String> images,
                                         List<String> videos,
                                         boolean[] unresolvedVideo,
                                         boolean photosArray) {
        if (media == null) {
            return false;
        }
        boolean hasVideo = false;
        for (int i = 0; i < media.length(); i++) {
            Object raw = media.opt(i);
            if (raw instanceof String) {
                images.add(withOriginalImageSize((String) raw));
                continue;
            }
            JSONObject item = media.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String type = item.optString("type").toLowerCase(Locale.US);
            boolean isPhoto = photosArray || "photo".equals(type);
            if (isPhoto) {
                String imageUrl = firstNonEmpty(item, "media_url_https", "media_url", "url");
                if (!isEmpty(imageUrl)) {
                    images.add(withOriginalImageSize(decodeHtml(imageUrl)));
                }
            } else {
                // Keep the pre-photo-fix behavior for mediaDetails/extended_entities:
                // anything that is not explicitly a photo must fall through to yt-dlp.
                // X can omit or add media type values, so treating an unknown item as a
                // photo can download its poster image and incorrectly skip the video.
                hasVideo = true;
                String videoUrl = bestXVideoUrl(item);
                if (!isEmpty(videoUrl)) {
                    videos.add(decodeHtml(videoUrl));
                } else {
                    unresolvedVideo[0] = true;
                }
            }
        }
        return hasVideo;
    }

    private static String bestXVideoUrl(JSONObject item) {
        JSONObject videoInfo = item.optJSONObject("video_info");
        JSONArray variants = videoInfo == null ? null : videoInfo.optJSONArray("variants");
        String bestMp4 = null;
        long bestBitrate = Long.MIN_VALUE;
        if (variants != null) {
            for (int index = 0; index < variants.length(); index++) {
                JSONObject variant = variants.optJSONObject(index);
                if (variant == null) {
                    continue;
                }
                String url = variant.optString("url");
                String contentType = variant.optString("content_type");
                if (isEmpty(url) || !("video/mp4".equalsIgnoreCase(contentType)
                        || url.toLowerCase(Locale.US).contains(".mp4"))) {
                    continue;
                }
                long bitrate = variant.optLong("bitrate", 0);
                if (bestMp4 == null || bitrate > bestBitrate) {
                    bestMp4 = url;
                    bestBitrate = bitrate;
                }
            }
        }
        return bestMp4;
    }

    private static String firstNonEmpty(JSONObject value, String... keys) {
        for (String key : keys) {
            String candidate = value.optString(key);
            if (!isEmpty(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private static Result downloadInstagramImages(Context context, String shortcode,
                                                  File outputDirectory,
                                                  File cookieFile, boolean allowAmbiguousFallback,
                                                  boolean downloadDirectVideos,
                                                  boolean reelOrVideoRoute, Listener listener)
            throws Exception {
        listener.onLog("Instagram 사진 검사 시작");
        String embedUrl = "https://www.instagram.com/p/" + shortcode + "/embed/captioned/";
        String html = readText(embedUrl, cookieHeader(cookieFile),
                INSTAGRAM_EMBED_USER_AGENT);
        String normalized = normalizeInstagramData(html);
        listener.onLog("Instagram 임베드 분석: 길이 " + html.length()
                + ", 전체 미디어 구조 "
                + (normalized.contains("\"edge_sidecar_to_children\"")
                || normalized.contains("\"carousel_media\"")));
        String username = firstGroup(USERNAME, normalized);
        String title = TextUtils.isEmpty(username)
                ? "Instagram " + shortcode : "Instagram @" + username + " " + shortcode;

        XMedia media = parseInstagramEmbedMedia(
                normalized, allowAmbiguousFallback, reelOrVideoRoute);
        List<String> images = media.imageUrls;
        List<String> videos = media.videoUrls;
        boolean hasVideo = media.hasVideo;
        listener.onLog("Instagram 응답: 사진 " + images.size() + "개, 직접 영상 "
                + videos.size() + "개, 영상 표시=" + hasVideo);
        if (!images.isEmpty() || !videos.isEmpty()) {
            listener.onTitle(title);
        }
        if (!images.isEmpty()) {
            boolean rejectThumbnailSizedFallback = !hasVideo
                    && (allowAmbiguousFallback || "GraphImage".equals(
                    firstGroup(MEDIA_TYPE, normalized)));
            downloadImages(context, images, outputDirectory, title,
                    rejectThumbnailSizedFallback, listener);
        }
        if (downloadDirectVideos && !videos.isEmpty()) {
            downloadVideos(context, videos, outputDirectory, title,
                    cookieHeader(cookieFile), listener);
        }
        return new Result(images.size(), downloadDirectVideos ? videos.size() : 0,
                hasVideo, media.unresolvedVideo);
    }

    static XMedia parseInstagramEmbedMedia(String normalized,
                                            boolean allowAmbiguousFallback) {
        return parseInstagramEmbedMedia(normalized, allowAmbiguousFallback, false);
    }

    static XMedia parseInstagramEmbedMedia(String normalized,
                                            boolean allowAmbiguousFallback,
                                            boolean reelOrVideoRoute) {
        List<String> images = new ArrayList<>();
        List<String> videos = new ArrayList<>();
        String mediaType = firstGroup(MEDIA_TYPE, normalized);
        boolean confirmedSingleImage = "GraphImage".equals(mediaType);
        String topVideoUrl = firstGroup(INSTAGRAM_JSON_VIDEO, normalized);
        if (isEmpty(topVideoUrl)) {
            topVideoUrl = findInstagramMetaVideo(normalized);
        }
        if (isEmpty(topVideoUrl)) {
            topVideoUrl = firstGroup(INSTAGRAM_META_VIDEO, normalized);
        }
        if (isEmpty(topVideoUrl)) {
            JSONArray videoVersions = extractJsonArray(normalized, "\"video_versions\"");
            if (videoVersions != null) {
                JSONObject videoNode = new JSONObject();
                try {
                    videoNode.put("video_versions", videoVersions);
                    topVideoUrl = bestInstagramVideo(videoNode);
                } catch (Exception ignored) {
                    // Continue with the unresolved-video path.
                }
            }
        }
        boolean hasVideo = (reelOrVideoRoute && !confirmedSingleImage
                && !"GraphSidecar".equals(mediaType)) || "GraphVideo".equals(mediaType)
                || !isEmpty(topVideoUrl);
        boolean unresolvedVideo = hasVideo && isEmpty(topVideoUrl);
        if (!isEmpty(topVideoUrl)) {
            videos.add(decodeHtml(topVideoUrl));
            unresolvedVideo = false;
        }
        JSONArray carousel = extractJsonArray(normalized, "\"carousel_media\"");
        if (carousel != null) {
            for (int i = 0; i < carousel.length(); i++) {
                JSONObject item = carousel.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                boolean isVideo = item.optBoolean("is_video")
                        || item.optInt("media_type") == 2
                        || item.has("video_versions") || item.has("video_url");
                if (isVideo) {
                    hasVideo = true;
                    String videoUrl = bestInstagramVideo(item);
                    if (!isEmpty(videoUrl)) {
                        videos.add(videoUrl);
                    } else {
                        unresolvedVideo = true;
                    }
                } else {
                    String imageUrl = bestInstagramImage(item);
                    if (!isEmpty(imageUrl)) {
                        images.add(imageUrl);
                    }
                }
            }
        }

        JSONObject sidecar = extractJsonObject(normalized, "\"edge_sidecar_to_children\"");
        if (carousel == null && sidecar != null) {
            JSONArray edges = sidecar.optJSONArray("edges");
            if (edges != null) {
                for (int i = 0; i < edges.length(); i++) {
                    JSONObject edge = edges.optJSONObject(i);
                    JSONObject node = edge == null ? null : edge.optJSONObject("node");
                    if (node == null) {
                        continue;
                    }
                    if (node.optBoolean("is_video")) {
                        hasVideo = true;
                        String videoUrl = bestInstagramVideo(node);
                        if (!isEmpty(videoUrl)) {
                            videos.add(videoUrl);
                        } else {
                            unresolvedVideo = true;
                        }
                    } else {
                        String imageUrl = bestInstagramImage(node);
                        if (!isEmpty(imageUrl)) {
                            images.add(imageUrl);
                        }
                    }
                }
            }
        } else if (carousel == null && sidecar == null
                && !hasVideo && (allowAmbiguousFallback || confirmedSingleImage)) {
            String imageUrl = firstGroup(EMBEDDED_IMAGE, normalized);
            if (!isEmpty(imageUrl)) {
                images.add(decodeHtml(imageUrl));
            }
        }

        images = new ArrayList<>(new LinkedHashSet<>(images));
        videos = new ArrayList<>(new LinkedHashSet<>(videos));
        if (hasVideo && videos.isEmpty()) {
            unresolvedVideo = true;
        }
        return new XMedia(images, videos, hasVideo, unresolvedVideo);
    }

    private static String findInstagramMetaVideo(String html) {
        Matcher tags = META_TAG.matcher(html);
        while (tags.find()) {
            String property = null;
            String content = null;
            Matcher attributes = HTML_ATTRIBUTE.matcher(tags.group());
            while (attributes.find()) {
                String name = attributes.group(1);
                String value = attributes.group(3);
                if ("property".equalsIgnoreCase(name) || "name".equalsIgnoreCase(name)) {
                    property = value;
                } else if ("content".equalsIgnoreCase(name)) {
                    content = value;
                }
            }
            if (!isEmpty(property) && property.toLowerCase(Locale.US).startsWith("og:video")
                    && !isEmpty(content)) {
                return decodeHtml(content);
            }
        }
        return null;
    }

    private static String bestInstagramVideo(JSONObject node) {
        String direct = node.optString("video_url");
        if (!isEmpty(direct)) {
            return decodeHtml(direct);
        }
        JSONArray versions = node.optJSONArray("video_versions");
        if (versions != null) {
            long bestPixels = -1;
            String bestUrl = null;
            for (int index = 0; index < versions.length(); index++) {
                JSONObject version = versions.optJSONObject(index);
                if (version == null || isEmpty(version.optString("url"))) {
                    continue;
                }
                long pixels = (long) version.optInt("width") * version.optInt("height");
                if (bestUrl == null || pixels > bestPixels) {
                    bestUrl = version.optString("url");
                    bestPixels = pixels;
                }
            }
            return decodeHtml(bestUrl);
        }
        return null;
    }

    private static String bestInstagramImage(JSONObject node) {
        JSONArray resources = node.optJSONArray("display_resources");
        if (resources == null) {
            JSONObject versions = node.optJSONObject("image_versions2");
            resources = versions == null ? null : versions.optJSONArray("candidates");
        }
        String bestUrl = null;
        long bestPixels = -1;
        if (resources != null) {
            for (int i = 0; i < resources.length(); i++) {
                JSONObject resource = resources.optJSONObject(i);
                if (resource == null) {
                    continue;
                }
                int width = resource.optInt("config_width", resource.optInt("width"));
                int height = resource.optInt("config_height", resource.optInt("height"));
                String url = firstNonEmpty(resource, "src", "url");
                long pixels = (long) width * height;
                if (pixels > bestPixels && !isEmpty(url)) {
                    bestPixels = pixels;
                    bestUrl = url;
                }
            }
        }
        if (isEmpty(bestUrl)) {
            bestUrl = node.optString("display_url");
        }
        return decodeHtml(bestUrl);
    }

    private static void downloadImages(Context context, List<String> urls, File outputDirectory,
                                       String title, boolean rejectThumbnailSizedFallback,
                                       Listener listener) throws Exception {
        String baseName = sanitizeFileName(title);
        for (int index = 0; index < urls.size(); index++) {
            if (listener.isCancelled()) {
                throw new InterruptedException("다운로드가 취소되었습니다.");
            }
            String imageUrl = urls.get(index);
            HttpURLConnection connection = open(imageUrl, null);
            long length = connection.getContentLengthLong();
            String contentType = connection.getContentType();
            if (contentType != null && !contentType.toLowerCase(Locale.US).startsWith("image/")) {
                connection.disconnect();
                throw new IllegalStateException("이미지 대신 " + contentType + " 응답을 받았습니다.");
            }
            String extension = imageExtension(imageUrl, contentType);
            String suffix = urls.size() == 1 ? "" : String.format(Locale.US, "-%02d", index + 1);
            String fileName = baseName + suffix + extension;
            listener.onLog("사진 저장 시작: " + fileName + " ("
                    + (length > 0 ? length + " bytes" : "크기 미상") + ")");
            File destination = null;
            Uri destinationUri = null;
            OutputStream destinationStream;
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE,
                        TextUtils.isEmpty(contentType) ? "image/jpeg" : contentType);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/YTDown");
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                destinationUri = context.getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (destinationUri == null) {
                    connection.disconnect();
                    throw new IllegalStateException("다운로드 저장소를 만들지 못했습니다.");
                }
                destinationStream = context.getContentResolver().openOutputStream(
                        destinationUri, "w");
                if (destinationStream == null) {
                    context.getContentResolver().delete(destinationUri, null, null);
                    connection.disconnect();
                    throw new IllegalStateException("다운로드 파일을 열지 못했습니다.");
                }
            } else {
                destination = uniqueFile(outputDirectory, baseName + suffix, extension);
                destinationStream = new FileOutputStream(destination);
            }

            boolean complete = false;
            try {
                long received = 0;
                try (InputStream input = new BufferedInputStream(connection.getInputStream());
                     BufferedOutputStream output = new BufferedOutputStream(destinationStream)) {
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (listener.isCancelled()) {
                            throw new InterruptedException("다운로드가 취소되었습니다.");
                        }
                        output.write(buffer, 0, count);
                        received += count;
                        double itemProgress = length > 0
                                ? Math.min(1.0, (double) received / length) : 0;
                        int progress = (int) Math.round(
                                ((index + itemProgress) / urls.size()) * 100.0);
                        listener.onProgress(progress);
                    }
                }
                int[] bounds = readImageBounds(context, destinationUri, destination);
                listener.onLog("사진 검증: " + bounds[0] + "x" + bounds[1]
                        + ", " + received + " bytes");
                if (bounds[0] <= 0 || bounds[1] <= 0) {
                    throw new IllegalStateException("저장된 파일이 실제 이미지가 아닙니다.");
                }
                if (rejectThumbnailSizedFallback
                        && bounds[0] <= 320 && bounds[1] <= 320) {
                    throw new IllegalStateException("게시물 원본이 아닌 "
                            + bounds[0] + "x" + bounds[1] + " 미리보기 이미지를 차단했습니다.");
                }
                complete = true;
            } finally {
                connection.disconnect();
                if (Build.VERSION.SDK_INT >= 29 && destinationUri != null) {
                    if (complete) {
                        ContentValues ready = new ContentValues();
                        ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
                        context.getContentResolver().update(destinationUri, ready, null, null);
                    } else {
                        context.getContentResolver().delete(destinationUri, null, null);
                    }
                } else if (!complete && destination != null) {
                    destination.delete();
                }
            }
            listener.onLog("사진 저장 완료: " + fileName);
            listener.onProgress((int) Math.round(((index + 1.0) / urls.size()) * 100.0));
        }
    }

    private static int[] readImageBounds(Context context, Uri uri, File file)
            throws Exception {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        InputStream raw = uri != null
                ? context.getContentResolver().openInputStream(uri)
                : new FileInputStream(file);
        if (raw == null) {
            return new int[]{-1, -1};
        }
        try (InputStream input = new BufferedInputStream(raw)) {
            BitmapFactory.decodeStream(input, null, options);
        }
        return new int[]{options.outWidth, options.outHeight};
    }

    private static void downloadVideos(Context context, List<String> urls, File outputDirectory,
                                       String title, String cookies, Listener listener)
            throws Exception {
        String baseName = sanitizeFileName(title);
        for (int index = 0; index < urls.size(); index++) {
            if (listener.isCancelled()) {
                throw new InterruptedException("다운로드가 취소되었습니다.");
            }
            String videoUrl = urls.get(index);
            HttpURLConnection connection = open(videoUrl, cookies);
            long length = connection.getContentLengthLong();
            String contentType = connection.getContentType();
            if (contentType != null && contentType.toLowerCase(Locale.US).startsWith("image/")) {
                connection.disconnect();
                throw new IllegalStateException("동영상 대신 이미지 응답을 받았습니다.");
            }
            String suffix = urls.size() == 1 ? "-video"
                    : String.format(Locale.US, "-video-%02d", index + 1);
            String fileName = baseName + suffix + ".mp4";
            listener.onLog("직접 동영상 저장 시작: " + fileName + " ("
                    + (length > 0 ? length + " bytes" : "크기 미상") + ")");

            File destination = null;
            Uri destinationUri = null;
            OutputStream destinationStream;
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/YTDown");
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                destinationUri = context.getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (destinationUri == null) {
                    connection.disconnect();
                    throw new IllegalStateException("동영상 저장소를 만들지 못했습니다.");
                }
                destinationStream = context.getContentResolver().openOutputStream(
                        destinationUri, "w");
                if (destinationStream == null) {
                    context.getContentResolver().delete(destinationUri, null, null);
                    connection.disconnect();
                    throw new IllegalStateException("동영상 파일을 열지 못했습니다.");
                }
            } else {
                destination = uniqueFile(outputDirectory, baseName + suffix, ".mp4");
                destinationStream = new FileOutputStream(destination);
            }

            boolean complete = false;
            long received = 0;
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 BufferedOutputStream output = new BufferedOutputStream(destinationStream)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (listener.isCancelled()) {
                        throw new InterruptedException("다운로드가 취소되었습니다.");
                    }
                    output.write(buffer, 0, count);
                    received += count;
                    double itemProgress = length > 0
                            ? Math.min(1.0, (double) received / length) : 0;
                    int progress = (int) Math.round(
                            ((index + itemProgress) / urls.size()) * 100.0);
                    listener.onProgress(progress);
                }
                complete = true;
            } finally {
                connection.disconnect();
                if (Build.VERSION.SDK_INT >= 29 && destinationUri != null) {
                    if (complete) {
                        ContentValues ready = new ContentValues();
                        ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
                        context.getContentResolver().update(destinationUri, ready, null, null);
                    } else {
                        context.getContentResolver().delete(destinationUri, null, null);
                    }
                } else if (!complete && destination != null) {
                    destination.delete();
                }
            }
            if (received < 16 * 1024) {
                listener.onLog("경고: 직접 동영상 파일 크기가 매우 작음: " + received + " bytes");
            }
            listener.onLog("직접 동영상 저장 완료: " + fileName + " (" + received + " bytes)");
            listener.onProgress((int) Math.round(((index + 1.0) / urls.size()) * 100.0));
        }
    }

    private static String readText(String url, String cookies) throws Exception {
        HttpURLConnection connection = open(url, cookies);
        try {
            return readConnectionText(connection);
        } finally {
            connection.disconnect();
        }
    }

    private static String readText(String url, String cookies, String userAgent)
            throws Exception {
        HttpURLConnection connection = open(url, cookies, userAgent);
        try {
            return readConnectionText(connection);
        } finally {
            connection.disconnect();
        }
    }

    private static String readConnectionText(HttpURLConnection connection) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            char[] buffer = new char[16 * 1024];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                content.append(buffer, 0, count);
            }
            return content.toString();
        }
    }

    private static HttpURLConnection open(String url, String cookies) throws Exception {
        return open(url, cookies, USER_AGENT);
    }

    private static HttpURLConnection open(String url, String cookies, String userAgent)
            throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", userAgent);
        connection.setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8");
        if (!TextUtils.isEmpty(cookies)) {
            connection.setRequestProperty("Cookie", cookies);
        }
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            connection.disconnect();
            throw new IllegalStateException("HTTP " + status);
        }
        return connection;
    }

    private static String cookieHeader(File cookieFile) {
        if (cookieFile == null || !cookieFile.isFile()) {
            return null;
        }
        List<String> cookies = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(cookieFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split("\\t", 7);
                if (fields.length == 7) {
                    cookies.add(fields[5] + "=" + fields[6]);
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return TextUtils.join("; ", cookies);
    }

    private static String cookieValue(String cookies, String name) {
        if (isEmpty(cookies)) {
            return null;
        }
        for (String pair : cookies.split(";\\s*")) {
            int separator = pair.indexOf('=');
            if (separator > 0 && name.equals(pair.substring(0, separator).trim())) {
                return pair.substring(separator + 1).trim();
            }
        }
        return null;
    }

    private static JSONObject extractJsonObject(String text, String key) {
        int keyIndex = text.indexOf(key);
        if (keyIndex < 0) {
            return null;
        }
        int start = text.indexOf('{', keyIndex + key.length());
        if (start < 0) {
            return null;
        }
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char value = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (value == '\\') {
                    escaped = true;
                } else if (value == '"') {
                    inString = false;
                }
                continue;
            }
            if (value == '"') {
                inString = true;
            } else if (value == '{') {
                depth++;
            } else if (value == '}' && --depth == 0) {
                try {
                    return new JSONObject(text.substring(start, i + 1));
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static JSONArray extractJsonArray(String text, String key) {
        int keyIndex = text.indexOf(key);
        if (keyIndex < 0) {
            return null;
        }
        int start = text.indexOf('[', keyIndex + key.length());
        if (start < 0) {
            return null;
        }
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char value = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (value == '\\') {
                    escaped = true;
                } else if (value == '"') {
                    inString = false;
                }
                continue;
            }
            if (value == '"') {
                inString = true;
            } else if (value == '[') {
                depth++;
            } else if (value == ']' && --depth == 0) {
                try {
                    return new JSONArray(text.substring(start, i + 1));
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    static String normalizeInstagramData(String html) {
        String value = html;
        for (int i = 0; i < 3; i++) {
            value = value.replace("\\\\u00253D", "%3D")
                    .replace("\\\\u0026", "&")
                    .replace("\\\"", "\"")
                    .replace("\\/", "/")
                    .replace("\\u00253D", "%3D")
                    .replace("\\u0026", "&");
        }
        return decodeHtml(value);
    }

    private static String decodeHtml(String value) {
        return value == null ? null : value.replace("&amp;", "&")
                .replace("&#38;", "&").replace("\\u0026", "&")
                .replace("\\u002F", "/").replace("\\u002f", "/")
                .replace("\\u003A", ":").replace("\\u003a", ":")
                .replace("\\u003D", "=").replace("\\u003d", "=")
                .replace("\\u003F", "?").replace("\\u003f", "?")
                .replace("\\/", "/");
    }

    private static String firstGroup(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String withOriginalImageSize(String url) {
        if (!url.contains("pbs.twimg.com")) {
            return url;
        }
        if (url.matches(".*[?&]name=[^&]*.*")) {
            return url.replaceFirst("([?&])name=[^&]*", "$1name=orig");
        }
        return url + (url.contains("?") ? "&" : "?") + "name=orig";
    }

    private static String imageExtension(String url, String contentType) {
        String path = URI.create(url).getPath().toLowerCase(Locale.US);
        for (String extension : new String[]{".jpg", ".jpeg", ".png", ".webp", ".gif", ".avif"}) {
            if (path.endsWith(extension)) {
                return extension;
            }
        }
        if (contentType != null) {
            if (contentType.contains("png")) return ".png";
            if (contentType.contains("webp")) return ".webp";
            if (contentType.contains("gif")) return ".gif";
            if (contentType.contains("avif")) return ".avif";
        }
        return ".jpg";
    }

    private static String sanitizeFileName(String value) {
        String clean = value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return clean.length() <= 160 ? clean : clean.substring(0, 160).trim();
    }

    private static File uniqueFile(File directory, String baseName, String extension) {
        File candidate = new File(directory, baseName + extension);
        int copy = 2;
        while (candidate.exists()) {
            candidate = new File(directory, baseName + " (" + copy++ + ")" + extension);
        }
        return candidate;
    }
}
