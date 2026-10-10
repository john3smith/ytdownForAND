package com.local.ytdown;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.RenderProcessGoneDetail;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public final class LoginActivity extends Activity {
    static final String EXTRA_PLATFORM = "platform";

    private String platform;
    private WebView webView;
    private TextView statusText;
    private Button doneButton;
    private boolean saving;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        platform = getIntent().getStringExtra(EXTRA_PLATFORM);
        if (TextUtils.isEmpty(platform)
                || !(AuthCookieStore.YOUTUBE.equals(platform) || AuthCookieStore.X.equals(platform)
                || AuthCookieStore.INSTAGRAM.equals(platform) || AuthCookieStore.PORNHUB.equals(platform))) {
            finish();
            return;
        }
        setTitle(AuthCookieStore.displayName(platform) + " 로그인");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(createContentView());
        webView.loadUrl(AuthCookieStore.loginUrl(platform));
    }

    private View createContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.background));

        statusText = new TextView(this);
        statusText.setText(getString(AuthCookieStore.PORNHUB.equals(platform)
                        ? R.string.login_manual_verification_instruction : R.string.login_instruction,
                AuthCookieStore.displayName(platform)));
        statusText.setTextColor(getColor(R.color.text_primary));
        statusText.setTextSize(15);
        statusText.setPadding(dp(16), dp(12), dp(16), dp(12));
        root.addView(statusText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(AuthCookieStore.PORNHUB.equals(platform));
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        CookieManager manager = CookieManager.getInstance();
        manager.setAcceptCookie(true);
        manager.setAcceptThirdPartyCookies(webView, true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) {
                    statusText.setText(R.string.login_connection_failed);
                }
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                if (view.getParent() instanceof ViewGroup) {
                    ((ViewGroup) view.getParent()).removeView(view);
                }
                view.destroy();
                if (webView == view) webView = null;
                saving = false;
                doneButton.setEnabled(false);
                statusText.setText(R.string.login_connection_failed);
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        root.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(12), dp(8), dp(12), dp(12));

        Button backButton = new Button(this);
        backButton.setText(R.string.back);
        backButton.setAllCaps(false);
        backButton.setOnClickListener(view -> {
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
            } else {
                finish();
            }
        });
        actions.addView(backButton, new LinearLayout.LayoutParams(0, dp(52), 1f));

        doneButton = new Button(this);
        doneButton.setText(R.string.login_done);
        doneButton.setAllCaps(false);
        doneButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        doneButton.setTextColor(Color.WHITE);
        doneButton.setBackgroundColor(getColor(R.color.accent));
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(0, dp(52), 1f);
        doneParams.setMarginStart(dp(8));
        actions.addView(doneButton, doneParams);
        doneButton.setOnClickListener(view -> saveAndFinish());

        root.addView(actions);
        return root;
    }

    private void saveAndFinish() {
        if (saving || webView == null) return;
        if (AuthCookieStore.PORNHUB.equals(platform)) {
            if (!AuthCookieStore.isTrustedLoginPage(webView.getUrl(), platform)) {
                Toast.makeText(this, R.string.login_cookie_missing, Toast.LENGTH_SHORT).show();
                return;
            }
            saving = true;
            doneButton.setEnabled(false);
            // Inspect only the login-state marker, never credentials or form values.
            try {
                webView.evaluateJavascript(
                    "Boolean(document.querySelector('#profileMenuDropdown,.ph-icon-logout'))",
                    result -> {
                        if (isFinishing() || isDestroyed() || webView == null) return;
                        saving = false;
                        doneButton.setEnabled(true);
                        if ("true".equals(result)
                                && AuthCookieStore.isTrustedLoginPage(webView.getUrl(), platform)) {
                            saveVerifiedSession(true);
                        } else {
                            Toast.makeText(this, R.string.login_cookie_missing, Toast.LENGTH_SHORT).show();
                        }
                    });
            } catch (RuntimeException error) {
                saving = false;
                doneButton.setEnabled(true);
                statusText.setText(R.string.login_connection_failed);
            }
            return;
        }
        saveVerifiedSession(false);
    }

    private void saveVerifiedSession(boolean browserLoginConfirmed) {
        try {
            CookieManager.getInstance().flush();
            File file = AuthCookieStore.exportCookies(this, platform, browserLoginConfirmed);
            if (file == null || !file.isFile()) {
                Toast.makeText(this, R.string.login_cookie_missing, Toast.LENGTH_SHORT).show();
                return;
            }
            Intent result = new Intent();
            result.putExtra(EXTRA_PLATFORM, platform);
            setResult(RESULT_OK, result);
            finish();
        } catch (Exception error) {
            statusText.setText(getString(R.string.status_with_detail,
                    getString(R.string.login_save_failed), error.getMessage()));
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
