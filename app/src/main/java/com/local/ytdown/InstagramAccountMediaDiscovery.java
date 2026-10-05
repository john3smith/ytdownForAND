package com.local.ytdown;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class InstagramAccountMediaDiscovery {
    private static final long POLL_MILLIS = 2_000;
    private static final long MAX_SCAN_MILLIS = 10 * 60_000;
    private static final int EMPTY_STABLE_ROUNDS_TO_FAIL = 15;
    private static final int FOUND_STABLE_ROUNDS_TO_FINISH = 10;
    private static final int MAX_DIAGNOSTIC_EVENTS = 80;
    private static final Pattern MEDIA_URL = Pattern.compile(
            "https?://(?:www\\.)?instagram\\.com/(p|reel|tv)/([A-Za-z0-9_-]+)",
            Pattern.CASE_INSENSITIVE);

    interface Listener {
        void onComplete(String username, List<String> mediaUrls);

        void onError(String detail, String diagnosticLog);
    }

    private final Activity activity;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, String> mediaUrls = new LinkedHashMap<>();
    private final Set<String> observedApiRequests = new LinkedHashSet<>();
    private final List<String> diagnosticEvents = new ArrayList<>();
    private WebView webView;
    private String username;
    private String appVersion = "확인 불가";
    private String cookieNames = "없음";
    private String webViewVersion = "확인 불가";
    private String lastSnapshot = "아직 없음";
    private long startedAt;
    private long lastHeight = -1;
    private long lastScrollTop = -1;
    private int stableRounds;
    private int offProfileRounds;
    private int reloadAttempts;
    private int apiResponseCount;
    private boolean evaluating;
    private boolean documentStartHookEnabled;
    private boolean scanningReels;
    private boolean paginationPending;
    private volatile boolean finished;

    InstagramAccountMediaDiscovery(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    void start(String sourceUrl, File cookieFile) {
        try {
            username = SocialImageDownloader.instagramUsernameFromUrl(sourceUrl);
            startProfileScan();
            return;
        } catch (IllegalArgumentException ignored) {
            if (SocialImageDownloader.instagramShortcodeFromMediaUrl(sourceUrl) == null) {
                throw ignored;
            }
        }
        startedAt = System.currentTimeMillis();
        addDiagnostic("Instagram 게시물 링크에서 작성자 계정 확인 시작");
        Thread resolver = new Thread(() -> {
            try {
                String owner = SocialImageDownloader.resolveInstagramPostOwner(
                        sourceUrl, cookieFile);
                handler.post(() -> {
                    if (finished) {
                        return;
                    }
                    username = owner;
                    addDiagnostic("게시물 작성자 확인: @" + owner);
                    try {
                        startProfileScan();
                    } catch (Throwable error) {
                        fail("Instagram 계정 화면 시작 실패: " + error.getMessage());
                    }
                });
            } catch (Exception error) {
                handler.post(() -> {
                    if (!finished) {
                        fail("Instagram 게시물 작성자 확인 실패: " + error.getMessage());
                    }
                });
            }
        }, "instagram-post-owner");
        resolver.setDaemon(true);
        resolver.start();
    }

    private void startProfileScan() {
        startedAt = System.currentTimeMillis();
        try {
            PackageInfo appInfo = activity.getPackageManager().getPackageInfo(
                    activity.getPackageName(), 0);
            appVersion = appInfo.versionName;
        } catch (Throwable ignored) {
            // Diagnostics can continue without package version metadata.
        }
        String rawCookies = CookieManager.getInstance().getCookie(
                "https://www.instagram.com/");
        cookieNames = cookieNameSummary(rawCookies);
        PackageInfo currentWebView = WebViewCompat.getCurrentWebViewPackage(activity);
        if (currentWebView != null) {
            webViewVersion = currentWebView.packageName + " " + currentWebView.versionName;
        }
        addDiagnostic("검색 시작: @" + username + ", 앱 " + appVersion);
        addDiagnostic("WebView: " + webViewVersion);
        addDiagnostic("Instagram 쿠키 이름: " + cookieNames);

        webView = new WebView(activity);
        webView.setBackgroundColor(Color.TRANSPARENT);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgentString(settings.getUserAgentString()
                .replace("; wv", "")
                .replace(" Version/4.0", ""));
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);
        cookies.flush();

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.END | Gravity.BOTTOM);
        webView.setClickable(false);
        webView.setFocusable(false);
        FrameLayout content = activity.findViewById(android.R.id.content);
        content.addView(webView, 0, params);
        webView.addJavascriptInterface(new ApiBridge(), "YTDownInstagramBridge");
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, responseCaptureScript(),
                    Collections.singleton("https://www.instagram.com"));
            documentStartHookEnabled = true;
        }
        addDiagnostic("API 응답 캡처: "
                + (documentStartHookEnabled ? "활성" : "미지원, 화면 링크만 사용"));
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    addDiagnostic("웹 콘솔 오류: " + abbreviate(message.message(), 240));
                }
                return super.onConsoleMessage(message);
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (!finished) {
                    addDiagnostic("페이지 완료: " + safePageUrl(url));
                    handler.removeCallbacks(scanRunnable);
                    handler.postDelayed(scanRunnable, 4_000);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) {
                    addDiagnostic("페이지 오류 " + error.getErrorCode() + ": "
                            + abbreviate(String.valueOf(error.getDescription()), 200));
                }
                super.onReceivedError(view, request, error);
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                            WebResourceResponse errorResponse) {
                if (request.isForMainFrame()) {
                    addDiagnostic("HTTP 오류 " + errorResponse.getStatusCode() + ": "
                            + safePageUrl(request.getUrl().toString()));
                }
                super.onReceivedHttpError(view, request, errorResponse);
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                addDiagnostic("WebView 렌더러가 종료되었습니다.");
                fail("Instagram 페이지 처리기가 종료되었습니다. 다시 시도해 주세요.");
                return true;
            }
        });
        webView.loadUrl("https://www.instagram.com/" + username + "/");
    }

    void cancel() {
        if (finished) {
            return;
        }
        finished = true;
        addDiagnostic("사용자가 검색을 취소했습니다.");
        cleanup();
    }

    private final Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            if (finished || webView == null || evaluating) {
                return;
            }
            if (System.currentTimeMillis() - startedAt >= MAX_SCAN_MILLIS) {
                fail("Instagram 목록을 끝까지 확인하기 전에 검색 시간이 초과되었습니다. "
                        + "이미 찾은 " + mediaUrls.size() + "개는 전체 목록으로 처리하지 않았습니다.");
                return;
            }
            evaluating = true;
            webView.evaluateJavascript(scanScript(), value -> {
                evaluating = false;
                if (finished) {
                    return;
                }
                try {
                    handleSnapshot(value);
                } catch (Throwable error) {
                    addDiagnostic("화면 분석 예외: " + error.getClass().getSimpleName()
                            + " " + abbreviate(error.getMessage(), 200));
                    handler.postDelayed(scanRunnable, POLL_MILLIS);
                }
            });
        }
    };

    private void handleSnapshot(String encoded) throws Exception {
        if (encoded == null || "null".equals(encoded)) {
            addDiagnostic("화면 분석 결과가 비어 있습니다. 페이지 전환 중일 수 있습니다.");
            handler.postDelayed(scanRunnable, POLL_MILLIS);
            return;
        }
        Object decoded = new JSONTokener(encoded).nextValue();
        String json = decoded instanceof String ? (String) decoded : encoded;
        JSONObject snapshot = new JSONObject(json);
        rememberSnapshot(snapshot);

        String pageUrl = snapshot.optString("url");
        if (pageUrl.contains("/accounts/login")) {
            fail("Instagram 로그인 세션이 만료되었습니다. 앱에서 다시 로그인해 주세요.");
            return;
        }
        if (pageUrl.contains("/challenge/") || pageUrl.contains("/accounts/suspended")) {
            fail("Instagram에서 추가 계정 인증을 요구하고 있습니다. 로그인 화면에서 인증을 완료해 주세요.");
            return;
        }
        if (!snapshot.optBoolean("onProfilePage")) {
            stableRounds = 0;
            offProfileRounds++;
            if (offProfileRounds >= EMPTY_STABLE_ROUNDS_TO_FAIL) {
                fail("Instagram 계정 프로필로 이동하지 못했습니다.");
                return;
            }
            handler.postDelayed(scanRunnable, POLL_MILLIS);
            return;
        }
        offProfileRounds = 0;

        JSONArray links = snapshot.optJSONArray("links");
        int before = mediaUrls.size();
        if (links != null) {
            for (int index = 0; index < links.length(); index++) {
                addMediaUrl(links.optString(index));
            }
        }

        long height = snapshot.optLong("height", -1);
        long scrollTop = snapshot.optLong("scrollTop", -1);
        boolean atBottom = snapshot.optBoolean("atBottom");
        boolean changed = mediaUrls.size() != before || (height > 0 && height != lastHeight)
                || (scrollTop > 0 && scrollTop != lastScrollTop);
        stableRounds = changed ? 0 : stableRounds + 1;
        lastHeight = height;
        lastScrollTop = scrollTop;

        String body = snapshot.optString("body");
        String lowerBody = body.toLowerCase(Locale.US);
        boolean privateAccount = body.contains("비공개 계정")
                || lowerBody.contains("this account is private");
        if (privateAccount && mediaUrls.isEmpty()) {
            fail("현재 로그인한 Instagram 계정에는 이 비공개 계정의 게시물 접근 권한이 없습니다.");
            return;
        }
        boolean unavailable = body.contains("페이지를 사용할 수 없습니다")
                || lowerBody.contains("sorry, this page isn't available")
                || lowerBody.contains("page isn't available");
        if (unavailable && mediaUrls.isEmpty()) {
            fail("Instagram 계정을 찾을 수 없거나 접근할 수 없습니다.");
            return;
        }
        boolean explicitlyEmpty = body.contains("게시물 없음")
                || body.contains("아직 게시물")
                || lowerBody.contains("no posts yet");
        if (explicitlyEmpty && mediaUrls.isEmpty()) {
            fail("Instagram에서 이 계정에 게시된 미디어가 없다고 표시했습니다.");
            return;
        }
        boolean retryableError = body.contains("문제가 발생했습니다")
                || lowerBody.contains("something went wrong")
                || lowerBody.contains("please wait a few minutes")
                || lowerBody.contains("try again later");
        if (retryableError && mediaUrls.isEmpty() && reloadAttempts < 2) {
            reloadAttempts++;
            stableRounds = 0;
            addDiagnostic("Instagram 오류 화면 감지, 새로고침 " + reloadAttempts + "/2");
            webView.reload();
            return;
        }
        if (retryableError && mediaUrls.isEmpty()) {
            fail("Instagram에서 미디어 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
            return;
        }

        int finishRounds = mediaUrls.isEmpty()
                ? EMPTY_STABLE_ROUNDS_TO_FAIL : FOUND_STABLE_ROUNDS_TO_FINISH;
        if (stableRounds >= finishRounds && atBottom) {
            if (paginationPending) {
                fail("Instagram이 다음 목록이 있다고 응답했지만 스크롤로 불러오지 못했습니다. "
                        + "수집한 " + mediaUrls.size() + "개는 전체 목록으로 처리하지 않았습니다.");
                return;
            }
            if (!scanningReels) {
                scanningReels = true;
                paginationPending = false;
                stableRounds = 0;
                lastHeight = -1;
                lastScrollTop = -1;
                addDiagnostic("프로필 목록 끝 확인: " + mediaUrls.size() + "개, 릴스 탭 검사 시작");
                webView.loadUrl("https://www.instagram.com/" + username + "/reels/");
                return;
            }
            completeOrFail("현재 Instagram 프로필과 릴스 탭에서 미디어를 찾지 못했습니다.");
            return;
        }
        if (stableRounds >= EMPTY_STABLE_ROUNDS_TO_FAIL && !atBottom) {
            fail("Instagram 목록 스크롤이 멈춰 전체 게시물을 확인하지 못했습니다. "
                    + "수집한 " + mediaUrls.size() + "개는 전체 목록으로 처리하지 않았습니다.");
            return;
        }
        handler.postDelayed(scanRunnable, POLL_MILLIS);
    }

    private void collectApiResponse(String url, String responseBody) {
        try {
            JSONObject response = new JSONObject(responseBody);
            List<String> urls = SocialImageDownloader.parseInstagramProfileMediaUrls(
                    response, username);
            Boolean hasMore = SocialImageDownloader.instagramProfileHasMore(response);
            handler.post(() -> {
                if (finished) {
                    return;
                }
                apiResponseCount++;
                observedApiRequests.add(apiRequestName(url));
                int before = mediaUrls.size();
                for (String mediaUrl : urls) {
                    addMediaUrl(mediaUrl);
                }
                int added = mediaUrls.size() - before;
                if (added > 0) {
                    stableRounds = 0;
                }
                Boolean relevantPagination = SocialImageDownloader.relevantInstagramPagination(
                        hasMore, urls.size(), added);
                if (relevantPagination != null) {
                    paginationPending = relevantPagination;
                }
                addDiagnostic("API 응답: 미디어 " + urls.size()
                        + "개, 신규 " + added + "개, 다음 목록 "
                        + (hasMore == null ? "불명" : hasMore ? "있음" : "없음")
                        + " (" + apiRequestName(url) + ")");
            });
        } catch (Throwable error) {
            addDiagnostic("API 응답 분석 실패: " + error.getClass().getSimpleName()
                    + " " + abbreviate(error.getMessage(), 180));
        }
    }

    private void addMediaUrl(String value) {
        Matcher matcher = MEDIA_URL.matcher(value);
        if (!matcher.find()) {
            return;
        }
        String type = matcher.group(1).equalsIgnoreCase("p") ? "p" : "reel";
        String code = matcher.group(2);
        String normalized = "https://www.instagram.com/" + type + "/" + code + "/";
        String current = mediaUrls.get(code);
        if (current == null || (type.equals("reel") && current.contains("/p/"))) {
            mediaUrls.put(code, normalized);
        }
    }

    private void completeOrFail(String emptyDetail) {
        if (mediaUrls.isEmpty()) {
            fail(emptyDetail);
            return;
        }
        finished = true;
        List<String> result = new ArrayList<>(mediaUrls.values());
        addDiagnostic("검색 완료: 미디어 게시물 " + result.size() + "개");
        cleanup();
        listener.onComplete(username, result);
    }

    private void fail(String detail) {
        if (finished) {
            return;
        }
        finished = true;
        addDiagnostic("검색 실패: " + detail);
        String report = diagnosticReport(detail);
        cleanup();
        listener.onError(detail, report);
    }

    private void cleanup() {
        handler.removeCallbacks(scanRunnable);
        if (webView == null) {
            return;
        }
        ViewGroup parent = (ViewGroup) webView.getParent();
        if (parent != null) {
            parent.removeView(webView);
        }
        webView.stopLoading();
        webView.removeJavascriptInterface("YTDownInstagramBridge");
        webView.destroy();
        webView = null;
    }

    private void rememberSnapshot(JSONObject snapshot) {
        String body = snapshot.optString("body").replaceAll("\\s+", " ").trim();
        String value = "url=" + safePageUrl(snapshot.optString("url"))
                + ", ready=" + snapshot.optString("ready")
                + ", visibility=" + snapshot.optString("visibility")
                + ", viewport=" + snapshot.optInt("width") + "x"
                + snapshot.optInt("heightPx")
                + ", mediaLinks=" + snapshot.optInt("mediaLinkCount")
                + ", images=" + snapshot.optInt("imageCount")
                + ", bodyHeight=" + snapshot.optLong("height")
                + ", scrollY=" + snapshot.optLong("scrollY")
                + ", scrollTop=" + snapshot.optLong("scrollTop")
                + ", atBottom=" + snapshot.optBoolean("atBottom")
                + ", pageCookieNames=" + snapshot.optString("cookieNames")
                + "\n화면 문구: " + abbreviate(body, 600);
        synchronized (this) {
            lastSnapshot = value;
        }
        if (stableRounds == 0 || stableRounds == 5 || stableRounds == 10) {
            addDiagnostic("화면 상태: 링크 " + snapshot.optInt("mediaLinkCount")
                    + ", 이미지 " + snapshot.optInt("imageCount")
                    + ", 높이 " + snapshot.optLong("height"));
        }
    }

    private synchronized void addDiagnostic(String event) {
        if (diagnosticEvents.size() >= MAX_DIAGNOSTIC_EVENTS) {
            diagnosticEvents.remove(0);
        }
        long elapsed = startedAt == 0 ? 0 : System.currentTimeMillis() - startedAt;
        diagnosticEvents.add(String.format(Locale.US, "+%.1fs %s",
                elapsed / 1000.0, abbreviate(event, 700)));
    }

    private synchronized String diagnosticReport(String reason) {
        StringBuilder report = new StringBuilder();
        report.append("YTDown Instagram 계정 진단 로그\n")
                .append("실패 원인: ").append(reason).append('\n')
                .append("앱 버전: ").append(appVersion).append('\n')
                .append("Android API: ").append(Build.VERSION.SDK_INT).append('\n')
                .append("WebView: ").append(webViewVersion).append('\n')
                .append("API 응답 캡처: ")
                .append(documentStartHookEnabled ? "활성" : "미지원").append('\n')
                .append("계정: @").append(username).append('\n')
                .append("Instagram 쿠키 이름: ").append(cookieNames).append('\n')
                .append("API 요청: ").append(observedApiRequests).append('\n')
                .append("분석한 API 응답: ").append(apiResponseCount).append("개\n")
                .append("다음 목록 대기: ").append(paginationPending).append('\n')
                .append("수집한 게시물: ").append(mediaUrls.size()).append("개\n\n")
                .append("마지막 화면\n").append(lastSnapshot).append("\n\n이벤트\n");
        for (String event : diagnosticEvents) {
            report.append(event).append('\n');
        }
        return report.toString().trim();
    }

    private String scanScript() {
        return "(() => {"
                + "const expected=" + JSONObject.quote("/" + username.toLowerCase(Locale.US))
                + ";const path=location.pathname.replace(/\\/$/,'').toLowerCase();"
                + "const target=expected+" + JSONObject.quote(scanningReels ? "/reels" : "")
                + ";const onProfilePage=path===target||"
                + (scanningReels ? "path===expected;" : "false;")
                + "const anchors=[...document.querySelectorAll('a[href]')];"
                + "const links=onProfilePage?anchors.map(a=>a.href).filter(h=>{try{"
                + "const u=new URL(h,location.href);return /instagram\\.com$/i.test(u.hostname)&&"
                + "/^\\/(?:p|reel|tv)\\/[A-Za-z0-9_-]+\\/?$/i.test(u.pathname);"
                + "}catch(e){return false;}}):[];"
                + "const dismiss=[...document.querySelectorAll('[role=\"dialog\"] button')].find(b=>"
                + "/나중에 하기|not now/i.test((b.innerText||'').trim()));"
                + "if(dismiss)dismiss.click();"
                + "const scrollables=[document.scrollingElement,...document.querySelectorAll('main,*[style*=overflow]')]"
                + ".filter(e=>e&&e.scrollHeight>e.clientHeight+8);"
                + "const scroller=scrollables.sort((a,b)=>(b.scrollHeight-b.clientHeight)"
                + "-(a.scrollHeight-a.clientHeight))[0]||document.scrollingElement;"
                + "const atBottom=document.readyState==='complete'&&(!scroller||"
                + "scroller.scrollTop+scroller.clientHeight>=scroller.scrollHeight-12);"
                + "const scrollTop=scroller?scroller.scrollTop:0;"
                + "const scrollHeight=scroller?scroller.scrollHeight:0;"
                + "if(onProfilePage&&scroller)scroller.scrollTop=scroller.scrollHeight;"
                + "const pageText=document.body.innerText||'';"
                + "const cookieNames=document.cookie.split(';').map(v=>v.split('=')[0].trim())"
                + ".filter(Boolean).join(',');"
                + "return JSON.stringify({links:[...new Set(links)],"
                + "height:scrollHeight,heightPx:innerHeight,width:innerWidth,"
                + "scrollY:window.scrollY,scrollTop,atBottom,"
                + "body:pageText.slice(0,2500),url:location.href,ready:document.readyState,"
                + "visibility:document.visibilityState,onProfilePage,"
                + "mediaLinkCount:links.length,imageCount:document.images.length,cookieNames});"
                + "})()";
    }

    private String responseCaptureScript() {
        return "(() => {"
                + "if(window.__ytdownInstagramCapture)return;"
                + "window.__ytdownInstagramCapture=true;"
                + "const wanted=u=>{try{const x=new URL(String(u),location.href);"
                + "if(!/instagram\\.com$/i.test(x.hostname))return false;"
                + "const p=x.pathname.toLowerCase();return p.includes('/graphql/query')||"
                + "p.includes('/api/graphql')||"
                + "p.includes('/api/v1/feed/user')||p.includes('/api/v1/users/web_profile_info');"
                + "}catch(e){return false;}};"
                + "const send=(u,b)=>{try{if(wanted(u)&&typeof b==='string')"
                + "YTDownInstagramBridge.onApiResponse(String(u),b);}catch(e){}};"
                + "const originalFetch=window.fetch;if(originalFetch)window.fetch=function(...args){"
                + "const result=originalFetch.apply(this,args);result.then(r=>{try{const input=args[0];"
                + "const u=typeof input==='string'?input:(input&&input.url)||r.url;"
                + "if(wanted(u))r.clone().text().then(t=>send(u,t)).catch(()=>{});"
                + "}catch(e){}}).catch(()=>{});return result;};"
                + "const originalOpen=XMLHttpRequest.prototype.open;"
                + "const originalSend=XMLHttpRequest.prototype.send;"
                + "XMLHttpRequest.prototype.open=function(m,u,...rest){this.__ytdownUrl=String(u);"
                + "return originalOpen.call(this,m,u,...rest);};"
                + "XMLHttpRequest.prototype.send=function(...args){if(wanted(this.__ytdownUrl))"
                + "this.addEventListener('load',()=>{try{if(!this.responseType||"
                + "this.responseType==='text')send(this.__ytdownUrl,this.responseText);"
                + "}catch(e){}});return originalSend.apply(this,args);};"
                + "})()";
    }

    private final class ApiBridge {
        @JavascriptInterface
        public void onApiResponse(String url, String responseBody) {
            if (!finished && url != null && responseBody != null) {
                collectApiResponse(url, responseBody);
            }
        }
    }

    private static String apiRequestName(String value) {
        Uri uri = Uri.parse(value);
        String path = uri.getPath();
        if (path == null) {
            return "unknown";
        }
        if (path.contains("graphql/query")) {
            String docId = uri.getQueryParameter("doc_id");
            return docId == null ? "graphql" : "graphql:" + docId;
        }
        int end = path.endsWith("/") ? path.length() - 1 : path.length();
        int separator = path.lastIndexOf('/', Math.max(0, end - 1));
        return path.substring(Math.min(separator + 1, end), end);
    }

    private static String cookieNameSummary(String rawCookies) {
        if (rawCookies == null || rawCookies.trim().isEmpty()) {
            return "없음";
        }
        List<String> names = new ArrayList<>();
        for (String pair : rawCookies.split(";\\s*")) {
            int separator = pair.indexOf('=');
            String name = separator < 0 ? pair.trim() : pair.substring(0, separator).trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names.isEmpty() ? "없음" : names.toString();
    }

    private static String safePageUrl(String value) {
        if (value == null) {
            return "";
        }
        int query = value.indexOf('?');
        return query < 0 ? value : value.substring(0, query);
    }

    private static String abbreviate(String value, int maximum) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("[\\r\\n]+", " ").trim();
        return normalized.length() <= maximum
                ? normalized : normalized.substring(0, maximum) + "...";
    }
}
