package com.local.ytdown;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

/** Private in-app viewer; does not queue downloads or export authentication cookies. */
public final class WebPageActivity extends Activity {
    static final String EXTRA_URL = "webpage_url";
    private WebView webView;
    private TextView status;
    private TextView address;
    private ProgressBar progress;
    private boolean loadFailed;
    private OnBackInvokedCallback backCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String url = getIntent().getStringExtra(EXTRA_URL);
        if (!isAllowedForBuild(url)) {
            finish();
            return;
        }
        // The viewer shares the app WebView cookie store and may show private sessions.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setTitle(R.string.view_current_webpage);
        setContentView(createContentView());
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = this::goBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(url.trim());
        }
    }

    private View createContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.background));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        address = new TextView(this);
        address.setTextSize(13);
        address.setTextColor(getColor(R.color.text_primary));
        address.setMaxLines(2);
        address.setPadding(dp(12), dp(12), dp(12), dp(4));
        root.addView(address);
        status = new TextView(this);
        status.setTextSize(12);
        status.setTextColor(getColor(R.color.text_secondary));
        status.setPadding(dp(12), 0, dp(12), dp(8));
        root.addView(status);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (isAllowedForBuild(request.getUrl().toString())) return false;
                status.setText(R.string.webpage_navigation_blocked);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                loadFailed = false;
                address.setText(url);
                status.setText(R.string.webpage_loading);
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                address.setText(url);
                if (!loadFailed) status.setText(R.string.webpage_loaded);
                progress.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) showFailure(R.string.webpage_load_failed);
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                            WebResourceResponse response) {
                if (request.isForMainFrame()) showFailure(R.string.webpage_load_failed);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                showFailure(R.string.webpage_certificate_failed);
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                disposeWebView();
                showFailure(R.string.webpage_load_failed);
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(8), dp(4), dp(8), dp(8));
        addButton(actions, R.string.back, this::goBack);
        addButton(actions, R.string.webpage_refresh, () -> {
            if (webView != null) webView.reload();
        });
        addButton(actions, R.string.close, this::finish);
        root.addView(actions);
        return root;
    }

    private void addButton(LinearLayout row, int text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(view -> action.run());
        row.addView(button, new LinearLayout.LayoutParams(0, dp(52), 1f));
    }

    private void showFailure(int message) {
        loadFailed = true;
        status.setText(message);
        progress.setVisibility(View.GONE);
    }

    private void goBack() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else finish();
    }

    // Legacy devices use this callback; API 33+ uses the registered platform callback.
    @SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { goBack(); }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (webView != null) webView.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    private void disposeWebView() {
        if (webView == null) return;
        WebView old = webView;
        webView = null;
        if (old.getParent() instanceof ViewGroup) ((ViewGroup) old.getParent()).removeView(old);
        old.stopLoading();
        old.destroy();
    }

    @Override protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        }
        CookieManager.getInstance().flush();
        disposeWebView();
        super.onDestroy();
    }

    private boolean isAllowedForBuild(String url) {
        return WebPageUrlPolicy.isAllowed(url)
                && !(getResources().getBoolean(R.bool.store_distribution)
                    && DownloadUrlPolicy.isRestrictedForStore(url));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
