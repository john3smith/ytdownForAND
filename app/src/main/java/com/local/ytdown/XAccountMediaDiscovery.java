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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class XAccountMediaDiscovery {
    private static final long POLL_MILLIS = 2_000;
    private static final long MAX_SCAN_MILLIS = 10 * 60_000;
    private static final int EMPTY_STABLE_ROUNDS_TO_FAIL = 15;
    private static final int FOUND_STABLE_ROUNDS_TO_FINISH = 8;
    private static final int MAX_DIAGNOSTIC_EVENTS = 80;
    private static final Pattern STATUS_URL = Pattern.compile(
            "https?://(?:www\\.)?(?:x|twitter)\\.com/([^/]+)/status/(\\d+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STATUS_ID = Pattern.compile("\\d+");

    interface Listener {
        void onComplete(String username, List<String> statusUrls);

        void onError(String detail, String diagnosticLog);
    }

    private final Activity activity;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> statusUrls = new LinkedHashSet<>();
    private final Set<String> inspectedTimelineRequests = new LinkedHashSet<>();
    private final Set<String> observedGraphQlOperations = new LinkedHashSet<>();
    private final List<String> diagnosticEvents = new ArrayList<>();
    private WebView webView;
    private String username;
    private String appVersion = "확인 불가";
    private String cookieNames = "없음";
    private String webViewVersion = "확인 불가";
    private String lastSnapshot = "아직 없음";
    private long startedAt;
    private long lastHeight = -1;
    private int stableRounds;
    private int offMediaRounds;
    private int sensitiveWarningAttempts;
    private int reloadAttempts;
    private int timelineRequestCount;
    private int timelineSuccessCount;
    private int timelineFailureCount;
    private boolean evaluating;
    private boolean documentStartHookEnabled;
    private volatile boolean finished;

    XAccountMediaDiscovery(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    void start(String sourceUrl) {
        username = SocialImageDownloader.xUsernameFromUrl(sourceUrl);
        startedAt = System.currentTimeMillis();
        try {
            PackageInfo appInfo = activity.getPackageManager().getPackageInfo(
                    activity.getPackageName(), 0);
            appVersion = appInfo.versionName;
        } catch (Throwable ignored) {
            // The diagnostic log can continue without package version metadata.
        }
        String rawCookies = CookieManager.getInstance().getCookie("https://x.com/");
        cookieNames = cookieNameSummary(rawCookies);
        if (Build.VERSION.SDK_INT >= 26) {
            PackageInfo currentWebView = WebView.getCurrentWebViewPackage();
            if (currentWebView != null) {
                webViewVersion = currentWebView.packageName + " " + currentWebView.versionName;
            }
        }
        addDiagnostic("검색 시작: @" + username + ", 앱 " + appVersion);
        addDiagnostic("WebView: " + webViewVersion);
        addDiagnostic("X 쿠키 이름: " + cookieNames);

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
        webView.addJavascriptInterface(new TimelineBridge(), "YTDownBridge");
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                    webView, responseCaptureScript(), java.util.Collections.singleton("https://x.com"));
            documentStartHookEnabled = true;
        }
        addDiagnostic("원본 응답 캡처: "
                + (documentStartHookEnabled ? "활성" : "미지원, 네트워크 대체 방식 사용"));
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
            public WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                String operation = graphQlOperationName(request.getUrl());
                if (operation == null) {
                    return super.shouldInterceptRequest(view, request);
                }
                recordGraphQlOperation(operation);
                if (!isProfileTimelineOperation(operation)
                        || !"GET".equalsIgnoreCase(request.getMethod())) {
                    return super.shouldInterceptRequest(view, request);
                }
                recordTimelineRequest(request, operation);
                if (documentStartHookEnabled) {
                    return super.shouldInterceptRequest(view, request);
                }
                WebResourceResponse response = inspectTimelineResponse(request, operation);
                return response == null
                        ? super.shouldInterceptRequest(view, request) : response;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!finished) {
                    addDiagnostic("페이지 완료: " + safePageUrl(url));
                    handler.removeCallbacks(scanRunnable);
                    handler.postDelayed(scanRunnable, 5_000);
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
                String operation = graphQlOperationName(request.getUrl());
                if (request.isForMainFrame() || operation != null) {
                    addDiagnostic("HTTP 오류 " + errorResponse.getStatusCode() + ": "
                            + (operation == null ? safePageUrl(request.getUrl().toString())
                            : operation));
                }
                super.onReceivedHttpError(view, request, errorResponse);
            }
        });
        webView.loadUrl("https://x.com/" + username + "/media");
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
                completeOrFail("X 계정 미디어 검색 시간이 초과되었습니다.");
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
        if (pageUrl.contains("/i/flow/login") || pageUrl.contains("/login")) {
            fail("X 로그인 세션이 만료되었습니다. 앱에서 X에 다시 로그인해 주세요.");
            return;
        }
        if (!snapshot.optBoolean("onMediaPage")) {
            stableRounds = 0;
            offMediaRounds++;
            if (offMediaRounds >= EMPTY_STABLE_ROUNDS_TO_FAIL) {
                fail(snapshot.optBoolean("hasMediaTab")
                        ? "X 미디어 탭으로 이동하지 못했습니다."
                        : "X 프로필에서 미디어 탭을 찾지 못했습니다.");
                return;
            }
            handler.postDelayed(scanRunnable, POLL_MILLIS);
            return;
        }
        offMediaRounds = 0;

        if (snapshot.optBoolean("sensitiveWarning")) {
            stableRounds = 0;
            sensitiveWarningAttempts++;
            if (snapshot.optBoolean("sensitiveButtonFound")) {
                addDiagnostic("민감한 콘텐츠 경고 승인 시도 "
                        + sensitiveWarningAttempts + ": "
                        + abbreviate(snapshot.optString("sensitiveButtonText"), 120));
                handler.postDelayed(scanRunnable, POLL_MILLIS);
                return;
            }
            if (sensitiveWarningAttempts >= 3) {
                fail("X의 민감한 콘텐츠 경고를 자동으로 승인하지 못했습니다.");
                return;
            }
            addDiagnostic("민감한 콘텐츠 경고는 감지했지만 승인 버튼을 찾지 못했습니다.");
            handler.postDelayed(scanRunnable, POLL_MILLIS);
            return;
        }
        sensitiveWarningAttempts = 0;

        JSONArray links = snapshot.optJSONArray("links");
        int before = statusUrls.size();
        if (links != null) {
            for (int index = 0; index < links.length(); index++) {
                addStatusUrl(links.optString(index));
            }
        }

        long height = snapshot.optLong("height", -1);
        boolean changed = statusUrls.size() != before || (height > 0 && height != lastHeight);
        stableRounds = changed ? 0 : stableRounds + 1;
        lastHeight = height;

        String body = snapshot.optString("body");
        String lowerBody = body.toLowerCase(Locale.US);
        boolean loginWall = (body.contains("최신 소식을 놓치지 마세요")
                && body.contains("로그인") && body.contains("가입하기"))
                || ((lowerBody.contains("don't miss what's happening")
                || lowerBody.contains("don’t miss what’s happening"))
                && lowerBody.contains("log in") && lowerBody.contains("sign up"));
        if (loginWall && statusUrls.isEmpty()) {
            fail("저장된 X 로그인 정보가 만료되었습니다. 앱에서 X에 다시 로그인해 주세요.");
            return;
        }
        boolean protectedAccount = body.contains("게시물이 비공개")
                || lowerBody.contains("these posts are protected");
        if (protectedAccount && statusUrls.isEmpty()) {
            fail("현재 로그인한 X 계정에는 이 비공개 계정의 미디어 접근 권한이 없습니다.");
            return;
        }
        boolean explicitlyEmpty = body.contains("아직 미디어를 게시하지 않았")
                || lowerBody.contains("hasn't posted media")
                || lowerBody.contains("hasn’t posted media")
                || lowerBody.contains("doesn't have any media");
        if (explicitlyEmpty && statusUrls.isEmpty()) {
            fail("X에서 이 계정에 게시된 미디어가 없다고 표시했습니다.");
            return;
        }
        boolean retryableError = body.contains("문제가 발생했습니다")
                || lowerBody.contains("something went wrong");
        if (retryableError && statusUrls.isEmpty() && reloadAttempts < 2) {
            reloadAttempts++;
            stableRounds = 0;
            addDiagnostic("X 오류 화면 감지, 새로고침 " + reloadAttempts + "/2");
            webView.reload();
            return;
        }
        if (retryableError && statusUrls.isEmpty()) {
            fail("X에서 미디어 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
            return;
        }

        int finishRounds = statusUrls.isEmpty()
                ? EMPTY_STABLE_ROUNDS_TO_FAIL : FOUND_STABLE_ROUNDS_TO_FINISH;
        if (stableRounds >= finishRounds) {
            completeOrFail("현재 X 미디어 탭에서 게시물을 찾지 못했습니다.");
            return;
        }
        handler.postDelayed(scanRunnable, POLL_MILLIS);
    }

    private WebResourceResponse inspectTimelineResponse(WebResourceRequest request,
                                                        String operation) {
        String requestUrl = request.getUrl().toString();
        synchronized (inspectedTimelineRequests) {
            if (!inspectedTimelineRequests.add(requestUrl)) {
                return null;
            }
        }
        Map<String, String> requestHeaders = request.getRequestHeaders();
        if (requestHeaders == null) {
            requestHeaders = new LinkedHashMap<>();
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(requestUrl).openConnection();
            connection.setConnectTimeout(20_000);
            connection.setReadTimeout(45_000);
            connection.setInstanceFollowRedirects(false);
            for (Map.Entry<String, String> header : requestHeaders.entrySet()) {
                String name = header.getKey();
                if (name == null || shouldReplaceRequestHeader(name)) {
                    continue;
                }
                connection.setRequestProperty(name, header.getValue());
            }
            String cookies = CookieManager.getInstance().getCookie(requestUrl);
            if (cookies != null && !cookies.isEmpty()) {
                connection.setRequestProperty("Cookie", cookies);
            }
            connection.setRequestProperty("Accept-Encoding", "identity");

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 400
                    ? connection.getInputStream() : connection.getErrorStream();
            byte[] body = readAll(stream);
            if (status < 200 || status >= 300) {
                synchronized (this) {
                    timelineFailureCount++;
                }
                addDiagnostic("타임라인 HTTP " + status + " (" + operation + "): "
                        + abbreviate(new String(body, StandardCharsets.UTF_8), 300));
                synchronized (inspectedTimelineRequests) {
                    inspectedTimelineRequests.remove(requestUrl);
                }
                return null;
            }

            collectTimelineResponse(operation,
                    new String(body, StandardCharsets.UTF_8), "대체");

            String contentType = connection.getContentType();
            String mimeType = contentType == null ? "application/json"
                    : contentType.split(";", 2)[0];
            String reason = connection.getResponseMessage();
            if (reason == null || reason.isEmpty()) {
                reason = "OK";
            }
            return new WebResourceResponse(mimeType, "UTF-8", status, reason,
                    responseHeaders(connection), new ByteArrayInputStream(body));
        } catch (Throwable error) {
            synchronized (this) {
                timelineFailureCount++;
            }
            addDiagnostic("타임라인 검사 실패 (" + operation + "): "
                    + error.getClass().getSimpleName() + " "
                    + abbreviate(error.getMessage(), 240));
            synchronized (inspectedTimelineRequests) {
                inspectedTimelineRequests.remove(requestUrl);
            }
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void recordTimelineRequest(WebResourceRequest request, String operation) {
        synchronized (this) {
            timelineRequestCount++;
        }
        Map<String, String> headers = request.getRequestHeaders();
        if (headers == null) {
            headers = new LinkedHashMap<>();
        }
        addDiagnostic("타임라인 요청: " + operation
                + ", authorization=" + hasHeader(headers, "authorization")
                + ", csrf=" + hasHeader(headers, "x-csrf-token")
                + ", transaction=" + hasHeader(headers, "x-client-transaction-id"));
    }

    private List<String> collectTimelineResponse(String operation, String responseBody,
                                                 String source) throws Exception {
        JSONObject root = new JSONObject(responseBody);
        List<String> statusIds = SocialImageDownloader.parseXTimelineMediaStatusIds(
                root, username);
        JSONArray errors = root.optJSONArray("errors");
        synchronized (this) {
            timelineSuccessCount++;
        }
        addDiagnostic(source + " 타임라인 응답 (" + operation + "), 미디어 "
                + statusIds.size() + "개"
                + (errors == null ? "" : ", API 오류 " + abbreviate(errors.toString(), 240)));
        if (!statusIds.isEmpty()) {
            handler.post(() -> {
                if (finished) {
                    return;
                }
                int previous = statusUrls.size();
                for (String statusId : statusIds) {
                    addStatusId(statusId);
                }
                if (statusUrls.size() > previous) {
                    stableRounds = 0;
                }
            });
        }
        return statusIds;
    }

    private void addStatusUrl(String value) {
        Matcher matcher = STATUS_URL.matcher(value);
        if (!matcher.find() || !username.equalsIgnoreCase(matcher.group(1))) {
            return;
        }
        addStatusId(matcher.group(2));
    }

    private void addStatusId(String statusId) {
        if (statusId == null || !STATUS_ID.matcher(statusId).matches()) {
            return;
        }
        statusUrls.add("https://x.com/" + username + "/status/" + statusId);
    }

    private void completeOrFail(String emptyDetail) {
        if (statusUrls.isEmpty()) {
            fail(emptyDetail);
            return;
        }
        finished = true;
        List<String> result = new ArrayList<>(statusUrls);
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
        webView.removeJavascriptInterface("YTDownBridge");
        webView.destroy();
        webView = null;
    }

    private void rememberSnapshot(JSONObject snapshot) {
        String body = snapshot.optString("body").replaceAll("\\s+", " ").trim();
        String value = "url=" + safePageUrl(snapshot.optString("url"))
                + ", ready=" + snapshot.optString("ready")
                + ", visibility=" + snapshot.optString("visibility")
                + ", viewport=" + snapshot.optInt("width") + "x" + snapshot.optInt("heightPx")
                + ", statusLinks=" + snapshot.optInt("statusLinkCount")
                + ", articles=" + snapshot.optInt("articleCount")
                + ", cells=" + snapshot.optInt("cellCount")
                + ", images=" + snapshot.optInt("imageCount")
                + ", videos=" + snapshot.optInt("videoCount")
                + ", bodyHeight=" + snapshot.optLong("height")
                + ", scrollY=" + snapshot.optLong("scrollY")
                + ", mediaSelected=" + snapshot.optString("mediaSelected")
                + ", pageCookieNames=" + snapshot.optString("cookieNames")
                + "\n화면 문구: " + abbreviate(body, 600);
        synchronized (this) {
            lastSnapshot = value;
        }
        if (stableRounds == 0 || stableRounds == 5 || stableRounds == 10) {
            addDiagnostic("화면 상태: 링크 " + snapshot.optInt("statusLinkCount")
                    + ", 글 " + snapshot.optInt("articleCount")
                    + ", 셀 " + snapshot.optInt("cellCount")
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

    private synchronized void recordGraphQlOperation(String operation) {
        if (observedGraphQlOperations.add(operation)) {
            addDiagnostic("GraphQL 작업: " + operation);
        }
    }

    private synchronized String diagnosticReport(String reason) {
        StringBuilder report = new StringBuilder();
        report.append("YTDown X 계정 진단 로그\n")
                .append("실패 원인: ").append(reason).append('\n')
                .append("앱 버전: ").append(appVersion).append('\n')
                .append("Android API: ").append(Build.VERSION.SDK_INT).append('\n')
                .append("WebView: ").append(webViewVersion).append('\n')
                .append("원본 응답 캡처: ")
                .append(documentStartHookEnabled ? "활성" : "미지원").append('\n')
                .append("계정: @").append(username).append('\n')
                .append("X 쿠키 이름: ").append(cookieNames).append('\n')
                .append("GraphQL 작업: ").append(observedGraphQlOperations).append('\n')
                .append("타임라인 요청/성공/실패: ")
                .append(timelineRequestCount).append('/')
                .append(timelineSuccessCount).append('/')
                .append(timelineFailureCount).append('\n')
                .append("수집한 게시물: ").append(statusUrls.size()).append("개\n\n")
                .append("마지막 화면\n").append(lastSnapshot).append("\n\n이벤트\n");
        for (String event : diagnosticEvents) {
            report.append(event).append('\n');
        }
        return report.toString().trim();
    }

    private String scanScript() {
        return "(() => {"
                + "const expected=" + JSONObject.quote("/" + username + "/media")
                + ".toLowerCase();"
                + "const path=location.pathname.replace(/\\/$/,'').toLowerCase();"
                + "const pageText=document.body.innerText||'';"
                + "const sensitiveWarning=/potentially sensitive content|민감한 콘텐츠|"
                + "敏感内容|敏感な内容/i.test(pageText);"
                + "const controls=[...document.querySelectorAll('button,[role=\\\"button\\\"],a')];"
                + "const sensitiveButton=controls.find(e=>{const t=(e.innerText||e.textContent||'')"
                + ".replace(/\\s+/g,' ').trim().toLowerCase();return "
                + "t.includes('yes, view profile')||t.includes('yes, view')||"
                + "t.includes('프로필 보기')||t.includes('프로필을 표시')||"
                + "t.includes('查看个人资料')||t.includes('查看個人資料')||"
                + "t.includes('プロフィールを表示');});"
                + "if(sensitiveWarning&&sensitiveButton)sensitiveButton.click();"
                + "const mediaTab=[...document.querySelectorAll('a[href]')].find(a=>{"
                + "try{return new URL(a.href).pathname.replace(/\\/$/,'').toLowerCase()===expected;}"
                + "catch(e){return false;}});"
                + "if(path!==expected&&mediaTab)mediaTab.click();"
                + "const statusAnchors=[...document.querySelectorAll('a[href*=\\\"/status/\\\"]')];"
                + "const links=path===expected?statusAnchors.map(a=>a.href):[];"
                + "const retry=[...document.querySelectorAll('button')].find(b=>"
                + "/다시 시도|retry/i.test(b.innerText||''));"
                + "if(retry)retry.click();"
                + "const cells=[...document.querySelectorAll('[data-testid=\\\"cellInnerDiv\\\"]')];"
                + "if(path===expected&&cells.length)cells[cells.length-1].scrollIntoView({block:'end'});"
                + "else if(path===expected)window.scrollTo(0,document.body.scrollHeight);"
                + "const cookieNames=document.cookie.split(';').map(v=>v.split('=')[0].trim())"
                + ".filter(Boolean).join(',');"
                + "return JSON.stringify({links:[...new Set(links)],"
                + "height:document.body.scrollHeight,heightPx:innerHeight,width:innerWidth,"
                + "scrollY:window.scrollY,body:pageText.slice(0,2500),"
                + "url:location.href,ready:document.readyState,visibility:document.visibilityState,"
                + "onMediaPage:path===expected,hasMediaTab:!!mediaTab,"
                + "sensitiveWarning,sensitiveButtonFound:!!sensitiveButton,"
                + "sensitiveButtonText:sensitiveButton?"
                + "(sensitiveButton.innerText||sensitiveButton.textContent||'').trim():'',"
                + "mediaSelected:mediaTab?mediaTab.getAttribute('aria-selected'):'',"
                + "statusLinkCount:statusAnchors.length,articleCount:document.querySelectorAll('article').length,"
                + "cellCount:cells.length,imageCount:document.images.length,"
                + "videoCount:document.querySelectorAll('video').length,cookieNames});"
                + "})()";
    }

    private String responseCaptureScript() {
        return "(() => {"
                + "if(window.__ytdownResponseCapture)return;"
                + "window.__ytdownResponseCapture=true;"
                + "const wanted=u=>{try{const p=new URL(String(u),location.href).pathname.toLowerCase();"
                + "const n=p.slice(p.lastIndexOf('/')+1);"
                + "return p.includes('/i/api/graphql/')&&n.startsWith('user')&&"
                + "(/media|tweet|timeline|original/.test(n));}catch(e){return false;}};"
                + "const send=(u,b)=>{try{if(wanted(u)&&typeof b==='string')"
                + "YTDownBridge.onGraphQlResponse(String(u),b);}catch(e){}};"
                + "const originalFetch=window.fetch;"
                + "if(originalFetch)window.fetch=function(...args){"
                + "const result=originalFetch.apply(this,args);"
                + "result.then(r=>{try{const input=args[0];"
                + "const u=typeof input==='string'?input:(input&&input.url)||r.url;"
                + "if(wanted(u))r.clone().text().then(t=>send(u,t)).catch(()=>{});"
                + "}catch(e){}}).catch(()=>{});return result;};"
                + "const originalOpen=XMLHttpRequest.prototype.open;"
                + "const originalSend=XMLHttpRequest.prototype.send;"
                + "XMLHttpRequest.prototype.open=function(m,u,...rest){"
                + "this.__ytdownUrl=String(u);return originalOpen.call(this,m,u,...rest);};"
                + "XMLHttpRequest.prototype.send=function(...args){"
                + "if(wanted(this.__ytdownUrl))this.addEventListener('load',()=>{"
                + "try{if(!this.responseType||this.responseType==='text')"
                + "send(this.__ytdownUrl,this.responseText);}catch(e){}});"
                + "return originalSend.apply(this,args);};"
                + "})()";
    }

    private final class TimelineBridge {
        @JavascriptInterface
        public void onGraphQlResponse(String url, String responseBody) {
            if (finished || url == null || responseBody == null) {
                return;
            }
            String operation = graphQlOperationName(Uri.parse(url));
            if (!isProfileTimelineOperation(operation)) {
                return;
            }
            try {
                collectTimelineResponse(operation, responseBody, "원본");
            } catch (Throwable error) {
                synchronized (XAccountMediaDiscovery.this) {
                    timelineFailureCount++;
                }
                addDiagnostic("원본 응답 분석 실패 (" + operation + "): "
                        + error.getClass().getSimpleName() + " "
                        + abbreviate(error.getMessage(), 240));
            }
        }
    }

    private static String graphQlOperationName(Uri uri) {
        if (uri == null) {
            return null;
        }
        String path = uri.getPath();
        if (path == null || !path.contains("/i/api/graphql/")) {
            return null;
        }
        int separator = path.lastIndexOf('/');
        return separator < 0 || separator == path.length() - 1
                ? null : path.substring(separator + 1);
    }

    private static boolean isProfileTimelineOperation(String operation) {
        if (operation == null) {
            return false;
        }
        String lower = operation.toLowerCase(Locale.US);
        return lower.startsWith("user") && (lower.contains("media")
                || lower.contains("tweet") || lower.contains("timeline")
                || lower.contains("original"));
    }

    private static boolean shouldReplaceRequestHeader(String name) {
        String lower = name.toLowerCase(Locale.US);
        return lower.equals("host") || lower.equals("connection")
                || lower.equals("content-length") || lower.equals("accept-encoding")
                || lower.equals("cookie");
    }

    private static boolean hasHeader(Map<String, String> headers, String expectedName) {
        for (String name : headers.keySet()) {
            if (expectedName.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] readAll(InputStream input) throws Exception {
        if (input == null) {
            return new byte[0];
        }
        try (InputStream stream = input;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static Map<String, String> responseHeaders(HttpURLConnection connection) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry
                : connection.getHeaderFields().entrySet()) {
            String name = entry.getKey();
            List<String> values = entry.getValue();
            if (name == null || values == null || values.isEmpty()) {
                continue;
            }
            String lower = name.toLowerCase(Locale.US);
            if (lower.equals("content-length") || lower.equals("content-encoding")
                    || lower.equals("transfer-encoding") || lower.equals("connection")) {
                continue;
            }
            StringBuilder joined = new StringBuilder();
            for (String value : values) {
                if (joined.length() > 0) {
                    joined.append(", ");
                }
                joined.append(value);
            }
            result.put(name, joined.toString());
        }
        return result;
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
