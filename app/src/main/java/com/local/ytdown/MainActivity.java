package com.local.ytdown;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;
import com.yausername.youtubedl_android.mapper.VideoInfo;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

public final class MainActivity extends Activity {
    private static final String TAG = "YTDown";
    private static final int STORAGE_PERMISSION_REQUEST = 1001;
    private static final int LOGIN_REQUEST = 1002;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1003;
    private static final int DOWNLOAD_NOTIFICATION_ID =
            DownloadKeepAliveService.FOREGROUND_NOTIFICATION_ID;
    private static final int DOWNLOAD_RESULT_NOTIFICATION_ID =
            DownloadKeepAliveService.RESULT_NOTIFICATION_ID;
    private static final String DOWNLOAD_CHANNEL_ID = DownloadKeepAliveService.CHANNEL_ID;
    private static final String PROCESS_ID = "ytdown-download";
    private static final String UI_PREFERENCES = "ui_preferences";
    private static final String PREF_IMMEDIATE_DOWNLOAD = "immediate_download";
    private static final String PREF_WIFI_ONLY = "wifi_only";
    private static final String PREF_AUDIO_ONLY = "audio_only";
    private static final long CANCEL_CONFIRM_WINDOW_MILLIS = 1_000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final DownloadQueue downloadQueue = new DownloadQueue();
    private final Deque<String> bulkMediaUrls = new ArrayDeque<>();
    private EditText urlInput;
    private Spinner qualitySpinner;
    private Spinner loginPlatformSpinner;
    private Switch immediateDownloadSwitch;
    private Switch wifiOnlySwitch;
    private Switch audioOnlySwitch;
    private ProgressBar progressBar;
    private TextView videoTitleText;
    private TextView percentText;
    private TextView statusText;
    private TextView queueStatusText;
    private Button queueListButton;
    private AlertDialog queueDialog;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private TextView loginStatusText;
    private Button downloadButton;
    private Button bulkDownloadButton;
    private Button cancelButton;
    private volatile boolean downloading;
    private volatile boolean engineReady;
    private volatile int currentProgress;
    private volatile int lastNotificationProgress = -1;
    private volatile String currentVideoTitle;
    private volatile String activeUrl;
    private volatile long downloadGeneration;
    private volatile Future<?> activeDownloadFuture;
    private XAccountMediaDiscovery xAccountDiscovery;
    private InstagramAccountMediaDiscovery instagramAccountDiscovery;
    private boolean notificationPermissionRequested;
    private boolean pendingBulkPermission;
    private boolean bulkMode;
    private boolean bulkScanning;
    private DownloadQueue.Task accountTask;
    private int bulkTotal;
    private int bulkAttempted;
    private int bulkSucceeded;
    private int bulkFailed;
    private String bulkUsername;
    private String lastAutoStartedUrl;
    private String pendingSharedUrl;
    private boolean sharedIntentActive;
    private boolean keepAliveStarted;
    private boolean serviceReceiverRegistered;
    private long lastCancelButtonPressAt;
    private volatile boolean activityDestroyed;
    private final BroadcastReceiver downloadServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!DownloadKeepAliveService.ACTION_TIMEOUT.equals(intent.getAction())
                    || (!downloading && !bulkMode)) {
                return;
            }
            cancelDownload();
            statusText.setText(R.string.status_background_timeout);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel();
        setContentView(createContentView());
        registerNetworkCallback();
        registerDownloadServiceReceiver();
        refreshLoginStatus();
        readSharedUrl(getIntent());
        showInitializationState();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readSharedUrl(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        maybeStartSharedDownload();
        if (!sharedIntentActive && TextUtils.isEmpty(pendingSharedUrl)) {
            maybeAutoStartFromClipboard();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            maybeStartSharedDownload();
            if (!sharedIntentActive && TextUtils.isEmpty(pendingSharedUrl)) {
                maybeAutoStartFromClipboard();
            }
        }
    }

    @Override
    protected void onPause() {
        sharedIntentActive = false;
        super.onPause();
    }

    private View createContentView() {
        int padding = dp(20);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, dp(24), padding, padding);
        root.setBackgroundColor(getColor(R.color.background));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(R.string.screen_title);
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button infoButton = new Button(this);
        infoButton.setText(R.string.info);
        infoButton.setTextSize(16);
        infoButton.setAllCaps(false);
        infoButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        infoButton.setTextColor(getColor(R.color.text_primary));
        infoButton.setContentDescription(getString(R.string.usage));
        infoButton.setMinWidth(0);
        infoButton.setMinimumWidth(0);
        infoButton.setMinHeight(0);
        infoButton.setMinimumHeight(0);
        infoButton.setPadding(0, 0, 0, 0);
        infoButton.setBackground(rounded(
                Color.WHITE, getColor(R.color.border), 1, 20));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            infoButton.setTooltipText(getString(R.string.usage));
        }
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                dp(40), dp(40));
        infoParams.setMarginStart(dp(8));
        titleRow.addView(infoButton, infoParams);
        root.addView(titleRow, matchWrap(dp(10)));

        TextView location = new TextView(this);
        location.setText(R.string.save_location);
        location.setTextColor(getColor(R.color.text_secondary));
        location.setTextSize(14);
        root.addView(location, matchWrap(dp(24)));

        TextView loginLabel = new TextView(this);
        loginLabel.setText(R.string.login_label);
        loginLabel.setTextColor(getColor(R.color.text_primary));
        loginLabel.setTextSize(14);
        loginLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(loginLabel, matchWrap(dp(7)));

        LinearLayout loginRow = new LinearLayout(this);
        loginRow.setOrientation(LinearLayout.HORIZONTAL);
        loginRow.setGravity(Gravity.CENTER_VERTICAL);

        loginPlatformSpinner = new Spinner(this);
        ArrayAdapter<CharSequence> loginAdapter = ArrayAdapter.createFromResource(
                this, R.array.login_platforms, android.R.layout.simple_spinner_item);
        loginAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        loginPlatformSpinner.setAdapter(loginAdapter);
        loginPlatformSpinner.setPadding(dp(10), 0, dp(10), 0);
        loginPlatformSpinner.setBackground(rounded(
                Color.WHITE, getColor(R.color.border), 1, 6));
        Button loginButton = commandButton(R.string.login, true);
        Button clearLoginButton = commandButton(R.string.clear_login, false);
        loginButton.setTextSize(13);
        loginButton.setPadding(dp(4), 0, dp(4), 0);
        clearLoginButton.setTextSize(11);
        clearLoginButton.setPadding(dp(4), 0, dp(4), 0);
        loginRow.addView(loginPlatformSpinner, new LinearLayout.LayoutParams(
                0, dp(48), 1.2f));
        LinearLayout.LayoutParams loginButtonParams = new LinearLayout.LayoutParams(
                0, dp(48), 1f);
        loginButtonParams.setMarginStart(dp(6));
        loginRow.addView(loginButton, loginButtonParams);
        LinearLayout.LayoutParams clearLoginParams = new LinearLayout.LayoutParams(
                0, dp(48), 1f);
        clearLoginParams.setMarginStart(dp(6));
        loginRow.addView(clearLoginButton, clearLoginParams);
        root.addView(loginRow, matchWrap(dp(4)));

        loginStatusText = new TextView(this);
        loginStatusText.setTextColor(getColor(R.color.text_secondary));
        loginStatusText.setTextSize(13);
        root.addView(loginStatusText, matchWrap(dp(20)));
        loginButton.setOnClickListener(view -> openLogin());
        clearLoginButton.setOnClickListener(view -> clearSelectedLogin());

        LinearLayout urlHeaderRow = new LinearLayout(this);
        urlHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        urlHeaderRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView urlLabel = new TextView(this);
        urlLabel.setText(R.string.link_label);
        urlLabel.setTextColor(getColor(R.color.text_primary));
        urlLabel.setTextSize(14);
        urlLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        urlHeaderRow.addView(urlLabel, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        immediateDownloadSwitch = new Switch(this);
        immediateDownloadSwitch.setText(R.string.immediate_download);
        immediateDownloadSwitch.setTextColor(getColor(R.color.text_primary));
        immediateDownloadSwitch.setTextSize(13);
        immediateDownloadSwitch.setChecked(getSharedPreferences(
                UI_PREFERENCES, MODE_PRIVATE).getBoolean(PREF_IMMEDIATE_DOWNLOAD, true));
        immediateDownloadSwitch.setOnCheckedChangeListener((button, checked) -> {
            getSharedPreferences(UI_PREFERENCES, MODE_PRIVATE)
                    .edit()
                    .putBoolean(PREF_IMMEDIATE_DOWNLOAD, checked)
                    .apply();
            if (!checked) {
                pendingSharedUrl = null;
            }
        });
        urlHeaderRow.addView(immediateDownloadSwitch, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(urlHeaderRow, matchWrap(dp(7)));

        urlInput = new EditText(this);
        urlInput.setHint(R.string.url_hint);
        urlInput.setSingleLine(true);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setTextSize(15);
        urlInput.setPadding(dp(14), 0, dp(14), 0);
        urlInput.setBackground(rounded(Color.WHITE, getColor(R.color.border), 1, 6));
        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        urlParams.bottomMargin = dp(20);
        root.addView(urlInput, urlParams);

        wifiOnlySwitch = new Switch(this);
        wifiOnlySwitch.setText(R.string.wifi_only);
        wifiOnlySwitch.setTextColor(getColor(R.color.text_primary));
        wifiOnlySwitch.setChecked(getSharedPreferences(UI_PREFERENCES, MODE_PRIVATE)
                .getBoolean(PREF_WIFI_ONLY, false));
        wifiOnlySwitch.setOnCheckedChangeListener((button, checked) -> {
            getSharedPreferences(UI_PREFERENCES, MODE_PRIVATE).edit()
                    .putBoolean(PREF_WIFI_ONLY, checked).apply();
            updateQueueUi();
            if (!checked || isWifiConnected()) {
                maybeStartWaitingDownload();
            }
        });
        root.addView(wifiOnlySwitch, matchWrap(dp(12)));

        audioOnlySwitch = new Switch(this);
        audioOnlySwitch.setText(R.string.audio_only);
        audioOnlySwitch.setTextColor(getColor(R.color.text_primary));
        audioOnlySwitch.setChecked(getSharedPreferences(UI_PREFERENCES, MODE_PRIVATE)
                .getBoolean(PREF_AUDIO_ONLY, false));
        audioOnlySwitch.setOnCheckedChangeListener((button, checked) -> {
            getSharedPreferences(UI_PREFERENCES, MODE_PRIVATE).edit()
                    .putBoolean(PREF_AUDIO_ONLY, checked).apply();
            if (qualitySpinner != null) qualitySpinner.setEnabled(!checked);
        });
        root.addView(audioOnlySwitch, matchWrap(dp(12)));

        TextView qualityLabel = new TextView(this);
        qualityLabel.setText(R.string.quality_label);
        qualityLabel.setTextColor(getColor(R.color.text_primary));
        qualityLabel.setTextSize(14);
        qualityLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(qualityLabel, matchWrap(dp(7)));

        qualitySpinner = new Spinner(this);
        ArrayAdapter<CharSequence> qualityAdapter = ArrayAdapter.createFromResource(
                this, R.array.quality_options, android.R.layout.simple_spinner_item);
        qualityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        qualitySpinner.setAdapter(qualityAdapter);
        qualitySpinner.setEnabled(!audioOnlySwitch.isChecked());
        qualitySpinner.setPadding(dp(10), 0, dp(10), 0);
        qualitySpinner.setBackground(rounded(Color.WHITE, getColor(R.color.border), 1, 6));
        root.addView(qualitySpinner, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        TextView videoTitleLabel = new TextView(this);
        videoTitleLabel.setText(R.string.video_title_label);
        videoTitleLabel.setTextColor(getColor(R.color.text_primary));
        videoTitleLabel.setTextSize(14);
        videoTitleLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleLabelParams = matchWrap(dp(7));
        titleLabelParams.topMargin = dp(24);
        root.addView(videoTitleLabel, titleLabelParams);

        videoTitleText = new TextView(this);
        videoTitleText.setText(R.string.video_title_empty);
        videoTitleText.setTextColor(getColor(R.color.text_secondary));
        videoTitleText.setTextSize(15);
        videoTitleText.setMaxLines(2);
        videoTitleText.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(videoTitleText, matchWrap(dp(4)));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(ColorStateList.valueOf(getColor(R.color.progress)));
        progressBar.setProgressBackgroundTintList(
                ColorStateList.valueOf(getColor(R.color.progress_track)));
        LinearLayout.LayoutParams progressParams = matchWrap(dp(8));
        progressParams.topMargin = dp(16);
        root.addView(progressBar, progressParams);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusText = new TextView(this);
        statusText.setText(R.string.status_ready);
        statusText.setTextColor(getColor(R.color.text_secondary));
        statusText.setTextSize(14);
        statusRow.addView(statusText, new LinearLayout.LayoutParams(0, dp(48), 1f));
        percentText = new TextView(this);
        percentText.setText(getString(R.string.percent_format, 0));
        percentText.setTextColor(getColor(R.color.text_primary));
        percentText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        percentText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        statusRow.addView(percentText, new LinearLayout.LayoutParams(dp(64), dp(48)));
        root.addView(statusRow);

        queueStatusText = new TextView(this);
        queueStatusText.setText(getString(R.string.queue_status, 0));
        queueStatusText.setTextColor(getColor(R.color.text_secondary));
        queueStatusText.setTextSize(13);
        LinearLayout queueRow = new LinearLayout(this);
        queueRow.setOrientation(LinearLayout.HORIZONTAL);
        queueRow.setGravity(Gravity.CENTER_VERTICAL);
        queueRow.addView(queueStatusText, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        queueListButton = commandButton(R.string.queue_list, false);
        queueListButton.setTextSize(12);
        queueListButton.setEnabled(false);
        queueRow.addView(queueListButton, new LinearLayout.LayoutParams(dp(116), dp(42)));
        root.addView(queueRow, matchWrap(dp(10)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button pasteButton = commandButton(R.string.paste, false);
        downloadButton = commandButton(R.string.download, true);
        bulkDownloadButton = commandButton(R.string.download_account_media, false);
        cancelButton = commandButton(R.string.cancel, false);
        Button logButton = commandButton(R.string.download_log, false);
        cancelButton.setEnabled(false);
        actions.addView(pasteButton, new LinearLayout.LayoutParams(0, dp(52), 1f));
        LinearLayout.LayoutParams downloadParams = new LinearLayout.LayoutParams(
                0, dp(52), 1f);
        downloadParams.setMarginStart(dp(8));
        actions.addView(downloadButton, downloadParams);
        root.addView(actions);

        LinearLayout.LayoutParams bulkParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        bulkParams.topMargin = dp(8);
        root.addView(bulkDownloadButton, bulkParams);

        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        cancelParams.topMargin = dp(8);
        root.addView(cancelButton, cancelParams);

        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        logParams.topMargin = dp(16);
        root.addView(logButton, logParams);

        TextView versionText = new TextView(this);
        versionText.setText(getString(R.string.version_format, getAppVersionName()));
        versionText.setTextColor(getColor(R.color.text_secondary));
        versionText.setTextSize(12);
        versionText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        versionParams.topMargin = dp(12);
        root.addView(versionText, versionParams);

        pasteButton.setOnClickListener(view -> pasteUrl());
        downloadButton.setOnClickListener(view -> beginDownload());
        bulkDownloadButton.setOnClickListener(view -> beginAccountDownload());
        cancelButton.setOnClickListener(view -> requestCancelDownload());
        infoButton.setOnClickListener(view -> showUsage());
        logButton.setOnClickListener(view -> showDownloadLog());
        queueListButton.setOnClickListener(view -> showQueueDialog());
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        return scrollView;
    }

    private void showUsage() {
        String guide = getString(R.string.usage_guide)
                .replace("최대 5개", "최대 10개")
                + getString(R.string.usage_queue_addendum)
                + getString(R.string.usage_audio_addendum);
        new AlertDialog.Builder(this)
                .setTitle(R.string.usage)
                .setMessage(guide)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private void showDownloadLog() {
        String log = DownloadLogStore.read(this);
        if (TextUtils.isEmpty(log)) {
            log = getString(R.string.download_log_empty);
        }
        final String displayedLog = log;
        TextView logView = new TextView(this);
        logView.setText(displayedLog);
        logView.setTextColor(getColor(R.color.text_primary));
        logView.setTextSize(12);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setPadding(dp(16), dp(12), dp(16), dp(12));

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(logView, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle(R.string.download_log)
                .setView(scrollView)
                .setNeutralButton(R.string.copy_log, (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager)
                            getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText(
                            getString(R.string.download_log), displayedLog));
                    toast(R.string.log_copied);
                })
                .setNegativeButton(R.string.clear_log, (dialog, which) -> {
                    DownloadLogStore.clear(this);
                    toast(R.string.log_cleared);
                })
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private void openLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra(LoginActivity.EXTRA_PLATFORM, selectedLoginPlatform());
        startActivityForResult(intent, LOGIN_REQUEST);
    }

    private void clearSelectedLogin() {
        AuthCookieStore.clearCookies(this, selectedLoginPlatform());
        refreshLoginStatus();
        toast(R.string.login_cleared);
    }

    private String selectedLoginPlatform() {
        switch (loginPlatformSpinner.getSelectedItemPosition()) {
            case 1:
                return AuthCookieStore.X;
            case 2:
                return AuthCookieStore.INSTAGRAM;
            case 3:
                return AuthCookieStore.PORNHUB;
            default:
                return AuthCookieStore.YOUTUBE;
        }
    }

    private void refreshLoginStatus() {
        StringBuilder names = new StringBuilder();
        for (String platform : new String[]{
                AuthCookieStore.YOUTUBE, AuthCookieStore.X, AuthCookieStore.INSTAGRAM,
                AuthCookieStore.PORNHUB}) {
            if (!AuthCookieStore.hasSavedCookies(this, platform)) {
                continue;
            }
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(AuthCookieStore.displayName(platform));
        }
        loginStatusText.setText(names.length() == 0
                ? getString(R.string.login_status_none)
                : getString(R.string.login_status_saved, names.toString()));
    }

    private void showInitializationState() {
        Throwable error = YTDownApplication.getInitializationError();
        if (error != null) {
            downloadButton.setEnabled(false);
            statusText.setText(getString(R.string.status_with_detail,
                    getString(R.string.status_failed), safeMessage(error)));
            return;
        }
        downloadButton.setEnabled(false);
        statusText.setText(R.string.status_updating);
        executor.execute(this::updateDownloadEngine);
    }

    private void updateDownloadEngine() {
        try {
            logDownload("yt-dlp 업데이트 확인 시작");
            YoutubeDL.getInstance().updateYoutubeDL(
                    getApplicationContext(), YoutubeDL.UpdateChannel._STABLE);
            String version = YoutubeDL.getInstance().versionName(getApplicationContext());
            engineReady = true;
            logDownload("yt-dlp 준비 완료: " + version);
            runOnUiThread(() -> {
                statusText.setText(getString(R.string.status_engine_ready, version));
                downloadButton.setEnabled(true);
                bulkDownloadButton.setEnabled(true);
                maybeStartSharedDownload();
                if (!sharedIntentActive && TextUtils.isEmpty(pendingSharedUrl)) {
                    maybeAutoStartFromClipboard();
                }
            });
        } catch (Throwable error) {
            logDownload("yt-dlp 업데이트 실패: " + safeMessage(error));
            Log.e(TAG, "Failed to update yt-dlp", error);
            runOnUiThread(() -> {
                statusText.setText(getString(R.string.status_update_failed,
                        safeMessage(error)));
                downloadButton.setEnabled(true);
            });
        }
    }

    private void beginDownload() {
        if (!engineReady) {
            toast(R.string.engine_not_ready);
            showInitializationState();
            return;
        }
        String url = urlInput.getText().toString().trim();
        if (!isSupportedUrl(url)) {
            urlInput.setError(getString(R.string.invalid_url));
            return;
        }
        if (!hasStoragePermission()) {
            pendingBulkPermission = false;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_REQUEST);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()
                && !notificationPermissionRequested) {
            notificationPermissionRequested = true;
            pendingBulkPermission = false;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST);
            return;
        }

        enqueueOrStart(url, selectedFormat());
    }

    private void beginAccountDownload() {
        if (!engineReady) {
            toast(R.string.engine_not_ready);
            showInitializationState();
            return;
        }
        String url = urlInput.getText().toString().trim();
        String accountPlatform = SocialImageDownloader.accountPlatformForUrl(url);
        if (accountPlatform == null) {
            urlInput.setError(getString(R.string.invalid_account_url));
            return;
        }
        if (!hasStoragePermission()) {
            pendingBulkPermission = true;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_REQUEST);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()
                && !notificationPermissionRequested) {
            notificationPermissionRequested = true;
            pendingBulkPermission = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST);
            return;
        }

        enqueueTask(new DownloadQueue.Task(url, selectedFormat(), true, audioOnlySwitch.isChecked()));
    }

    private void startAccountDownload(DownloadQueue.Task task) {
        String url = task.url;
        String accountPlatform = SocialImageDownloader.accountPlatformForUrl(url);
        String platformName = AuthCookieStore.displayName(accountPlatform);
        File accountCookieFile;
        try {
            accountCookieFile = AuthCookieStore.exportCookies(this, accountPlatform);
        } catch (Exception error) {
            Log.w(TAG, "Failed to export " + platformName + " login cookies", error);
            Toast.makeText(this, getString(R.string.account_login_required, platformName),
                    Toast.LENGTH_SHORT).show();
            startNextQueuedDownload();
            return;
        }
        if (accountCookieFile == null || !accountCookieFile.isFile()) {
            Toast.makeText(this, getString(R.string.account_login_required, platformName),
                    Toast.LENGTH_SHORT).show();
            startNextQueuedDownload();
            return;
        }

        bulkMode = true;
        bulkScanning = true;
        accountTask = task;
        lastCancelButtonPressAt = 0;
        bulkTotal = 0;
        bulkAttempted = 0;
        bulkSucceeded = 0;
        bulkFailed = 0;
        bulkUsername = null;
        bulkMediaUrls.clear();
        String scanningStatus = getString(R.string.status_scanning_account, platformName);
        startKeepAlive(scanningStatus);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setDownloadingUi(false);
        cancelButton.setEnabled(true);
        updateQueueUi();
        statusText.setText(scanningStatus);
        videoTitleText.setText(R.string.video_title_loading);
        progressBar.setProgress(0);
        percentText.setText(getString(R.string.percent_format, 0));
        String format = task.format;

        if (AuthCookieStore.X.equals(accountPlatform)) {
            startXAccountDiscovery(url, platformName, format);
        } else {
            startInstagramAccountDiscovery(url, platformName, format, accountCookieFile);
        }
    }

    private void startXAccountDiscovery(String url, String platformName, String format) {
        DownloadQueue.Task owner = accountTask;
        xAccountDiscovery = new XAccountMediaDiscovery(this,
                new XAccountMediaDiscovery.Listener() {
                    @Override
                    public void onComplete(String username, List<String> mediaUrls) {
                        if (accountTask != owner || !bulkMode) return;
                        xAccountDiscovery = null;
                        onAccountMediaDiscovered(platformName, username, mediaUrls, format);
                    }

                    @Override
                    public void onError(String detail, String diagnosticLog) {
                        if (accountTask != owner || !bulkMode) return;
                        xAccountDiscovery = null;
                        finishBulkDiscovery(false, detail);
                        showAccountDiagnosticLog(platformName, diagnosticLog);
                    }
                });
        try {
            xAccountDiscovery.start(url);
        } catch (Throwable error) {
            xAccountDiscovery = null;
            finishBulkDiscovery(false, safeMessage(error));
        }
    }

    private void startInstagramAccountDiscovery(String url, String platformName,
                                                String format, File cookieFile) {
        DownloadQueue.Task owner = accountTask;
        instagramAccountDiscovery = new InstagramAccountMediaDiscovery(this,
                new InstagramAccountMediaDiscovery.Listener() {
                    @Override
                    public void onComplete(String username, List<String> mediaUrls) {
                        if (accountTask != owner || !bulkMode) return;
                        instagramAccountDiscovery = null;
                        onAccountMediaDiscovered(platformName, username, mediaUrls, format);
                    }

                    @Override
                    public void onError(String detail, String diagnosticLog) {
                        if (accountTask != owner || !bulkMode) return;
                        instagramAccountDiscovery = null;
                        finishBulkDiscovery(false, detail);
                        showAccountDiagnosticLog(platformName, diagnosticLog);
                    }
                });
        try {
            instagramAccountDiscovery.start(url, cookieFile);
        } catch (Throwable error) {
            instagramAccountDiscovery = null;
            finishBulkDiscovery(false, safeMessage(error));
        }
    }

    private void onAccountMediaDiscovered(String platformName, String username,
                                          List<String> urls, String format) {
        if (!bulkMode) {
            return;
        }
        bulkUsername = username;
        bulkScanning = false;
        if (urls.isEmpty()) {
            finishBulkDiscovery(false,
                    getString(R.string.no_account_media, platformName));
            return;
        }
        bulkMediaUrls.addAll(urls);
        bulkTotal = urls.size();
        videoTitleText.setText(getString(
                R.string.bulk_account_title, platformName, bulkUsername));
        updateQueueUi();
        statusText.setText(getString(R.string.bulk_found, bulkTotal));
        startNextBulkDownload(format);
    }

    private void showAccountDiagnosticLog(String platformName, String diagnosticLog) {
        String dialogTitle = getString(R.string.account_diagnostic_title, platformName);
        Log.e(TAG + "-" + platformName + "-Diagnostic", diagnosticLog);
        TextView logView = new TextView(this);
        logView.setText(diagnosticLog);
        logView.setTextColor(getColor(R.color.text_primary));
        logView.setTextSize(12);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setPadding(dp(16), dp(12), dp(16), dp(12));

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(logView, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle(dialogTitle)
                .setView(scrollView)
                .setNeutralButton(R.string.copy_log, (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager)
                            getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText(
                            dialogTitle, diagnosticLog));
                    toast(R.string.log_copied);
                })
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private void startNextBulkDownload() {
        startNextBulkDownload(accountTask == null ? selectedFormat() : accountTask.format);
    }

    private void startNextBulkDownload(String format) {
        if (!bulkMode || bulkScanning || downloading) {
            return;
        }
        if (wifiOnlySwitch.isChecked() && !isWifiConnected()) {
            statusText.setText(getString(R.string.wifi_waiting, bulkMediaUrls.size()));
            return;
        }
        String url = bulkMediaUrls.pollFirst();
        if (url == null) {
            finishBulkDiscovery(true, null);
            return;
        }
        statusText.setText(getString(R.string.bulk_attempting,
                bulkAttempted + 1, bulkTotal));
        startDownload(new DownloadQueue.Task(url, format, false,
                accountTask != null && accountTask.audioOnly));
    }

    private void finishBulkDiscovery(boolean success, String detail) {
        if (xAccountDiscovery != null) {
            xAccountDiscovery.cancel();
            xAccountDiscovery = null;
        }
        if (instagramAccountDiscovery != null) {
            instagramAccountDiscovery.cancel();
            instagramAccountDiscovery = null;
        }
        bulkMode = false;
        bulkScanning = false;
        accountTask = null;
        lastCancelButtonPressAt = 0;
        bulkMediaUrls.clear();
        stopKeepAlive();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setDownloadingUi(false);
        updateQueueUi();
        if (success) {
            progressBar.setProgress(100);
            percentText.setText(getString(R.string.percent_format, 100));
            String completionText = getString(
                    R.string.bulk_complete, bulkUsername, bulkAttempted,
                    bulkSucceeded, bulkFailed);
            if (bulkFailed > 0) {
                completionText += getString(R.string.bulk_failures_hint);
            }
            statusText.setText(completionText);
            logDownload(completionText);
            clearDownloadNotifications();
        } else {
            statusText.setText(getString(R.string.status_with_detail,
                    getString(R.string.status_failed), detail));
            clearDownloadNotifications();
        }
        shutdownExecutorIfDestroyed();
        if (!activityDestroyed) startNextQueuedDownload();
    }

    private void enqueueOrStart(String url, String format) {
        enqueueTask(new DownloadQueue.Task(DownloadUrlPolicy.normalize(url), format,
                false, audioOnlySwitch.isChecked()));
    }

    private void enqueueTask(DownloadQueue.Task task) {
        String url = task.url;
        if (bulkMode || downloading || downloadQueue.size() > 0
                || (wifiOnlySwitch.isChecked() && !isWifiConnected())) {
            if (url.equals(activeUrl) || (accountTask != null && url.equals(accountTask.url))
                    || downloadQueue.contains(url)) {
                toast(R.string.already_downloading);
                return;
            }
            if (downloadQueue.isFull()) {
                toast(R.string.queue_full);
                return;
            }
            downloadQueue.offer(task, activeUrl);
            updateQueueUi();
            Toast.makeText(this, getString(R.string.queue_added, downloadQueue.size()),
                    Toast.LENGTH_SHORT).show();
            if (downloading) {
                showProgressNotification(currentProgress, false);
            } else {
                maybeStartWaitingDownload();
            }
            return;
        }
        lastCancelButtonPressAt = 0;
        startTask(task);
    }

    private void startTask(DownloadQueue.Task task) {
        if (task.account) startAccountDownload(task);
        else startDownload(task);
    }

    private void startDownload(DownloadQueue.Task task) {
        File outputDirectory = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "YTDown");
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            statusText.setText(R.string.status_failed);
            if (bulkMode) {
                finishBulkDiscovery(false, getString(R.string.status_failed));
            } else {
                downloadQueue.clear();
                updateQueueUi();
                stopKeepAlive();
                clearDownloadNotifications();
            }
            return;
        }

        DownloadLogStore.start(this, task.url, task.format);
        logDownload("다운로드 작업 시작");
        logDownload(task.audioOnly ? "음원만 추출: M4A · 사진/동영상 저장 안 함"
                : "사진·동영상 다운로드 모드");
        downloading = true;
        long generation = ++downloadGeneration;
        activeUrl = task.url;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setDownloadingUi(true);
        statusText.setText(R.string.status_starting);
        videoTitleText.setText(R.string.video_title_loading);
        videoTitleText.setTextColor(getColor(R.color.text_secondary));
        progressBar.setProgress(0);
        percentText.setText(getString(R.string.percent_format, 0));
        currentProgress = 0;
        lastNotificationProgress = -1;
        currentVideoTitle = getString(R.string.video_title_loading);
        startKeepAlive(currentVideoTitle);
        showProgressNotification(0, true);

        File cookieFile = null;
        String platform = AuthCookieStore.platformForUrl(task.url);
        if (platform != null) {
            try {
                cookieFile = AuthCookieStore.exportCookies(this, platform);
            } catch (Exception error) {
                Log.w(TAG, "Failed to export login cookies", error);
            }
            logDownload("로그인 쿠키: " + AuthCookieStore.displayName(platform) + " "
                    + (cookieFile != null && cookieFile.isFile() ? "적용됨" : "없음"));
        }
        File downloadCookieFile = cookieFile;
        activeDownloadFuture = executor.submit(() -> executeDownload(
                task.url, outputDirectory, task.format, task.audioOnly, downloadCookieFile, generation));
    }

    private void startNextQueuedDownload() {
        if (activityDestroyed || downloading || bulkMode) return;
        if (wifiOnlySwitch.isChecked() && !isWifiConnected()) {
            updateQueueUi();
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            stopKeepAlive();
            clearDownloadNotifications();
            return;
        }
        DownloadQueue.Task next = downloadQueue.poll();
        updateQueueUi();
        if (next != null) {
            startTask(next);
        }
    }

    private void updateQueueUi() {
        if (queueStatusText != null) {
            queueStatusText.setText(!downloading && wifiOnlySwitch != null
                    && wifiOnlySwitch.isChecked() && !isWifiConnected()
                    && (downloadQueue.size() > 0 || bulkMode)
                    ? getString(R.string.wifi_waiting,
                            bulkMode ? bulkMediaUrls.size() : downloadQueue.size())
                    : bulkMode
                    ? (bulkScanning ? "계정 미디어 검색 중" : getString(R.string.bulk_progress, bulkAttempted, bulkTotal))
                            + " · 대기 " + downloadQueue.size() + "개"
                    : getString(R.string.queue_status, downloadQueue.size()));
        }
        if (queueListButton != null) {
            queueListButton.setEnabled(downloadQueue.size() > 0 || accountTask != null);
        }
        if (queueDialog != null && queueDialog.isShowing()) {
            queueDialog.dismiss();
            queueDialog = null;
            if (accountTask != null || downloadQueue.size() > 0) showQueueDialog();
        }
    }

    private void showQueueDialog() {
        List<DownloadQueue.Task> tasks = downloadQueue.snapshot();
        if (accountTask != null) tasks.add(0, accountTask);
        if (tasks.isEmpty()) {
            toast(R.string.queue_empty);
            return;
        }
        String[] labels = new String[tasks.size()];
        for (int index = 0; index < tasks.size(); index++) {
            DownloadQueue.Task task = tasks.get(index);
            String state = task == accountTask
                    ? (bulkScanning ? "미디어 검색 중" : "다운로드 " + bulkAttempted + "/" + bulkTotal)
                    : "대기";
            labels[index] = (index + 1) + ". [" + (task.account ? "계정 · " : "")
                    + (task.audioOnly ? "음원 · " : "")
                    + state + "] " + task.url;
        }
        queueDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.queue_list)
                .setItems(labels, (dialog, selected) -> {
                    DownloadQueue.Task task = tasks.get(selected);
                    new AlertDialog.Builder(this)
                            .setMessage(task.url)
                            .setNegativeButton(R.string.close, null)
                            .setPositiveButton(R.string.queue_cancel_item, (confirm, which) -> {
                                if (task == accountTask) {
                                    cancelDownload(false);
                                    startNextQueuedDownload();
                                } else if (downloadQueue.remove(task.url)) {
                                    updateQueueUi();
                                    toast(R.string.queue_item_cancelled);
                                }
                                if (downloadQueue.size() > 0) {
                                    showQueueDialog();
                                }
                            }).show();
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private boolean isWifiConnected() {
        if (connectivityManager == null) {
            connectivityManager = getSystemService(ConnectivityManager.class);
        }
        for (Network network : connectivityManager.getAllNetworks()) {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            if (capabilities != null
                    && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return true;
            }
        }
        return false;
    }

    private void maybeStartWaitingDownload() {
        if (downloading) {
            return;
        }
        if (bulkMode) {
            startNextBulkDownload();
        } else if (downloadQueue.size() > 0) {
            startNextQueuedDownload();
        }
    }

    private void registerNetworkCallback() {
        connectivityManager = getSystemService(ConnectivityManager.class);
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                runOnUiThread(() -> {
                    if (!activityDestroyed) {
                        updateQueueUi();
                        if (isWifiConnected()) {
                            maybeStartWaitingDownload();
                        }
                    }
                });
            }

            @Override
            public void onLost(Network network) {
                runOnUiThread(() -> {
                    if (!activityDestroyed) {
                        updateQueueUi();
                    }
                });
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                runOnUiThread(() -> {
                    if (!activityDestroyed) {
                        updateQueueUi();
                        if (isWifiConnected()) {
                            maybeStartWaitingDownload();
                        }
                    }
                });
            }
        };
        connectivityManager.registerNetworkCallback(new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), networkCallback);
    }

    private void executeDownload(String url, File outputDirectory, String format,
                                 boolean audioOnly, File cookieFile, long generation) {
        if (!isDownloadActive(generation)) {
            return;
        }
        // Audio-only must never take the image/direct-video shortcut or photo fallback.
        boolean socialUrl = !audioOnly && SocialImageDownloader.supports(url);
        logDownload(socialUrl
                ? "소셜 게시물 감지: 구조화된 전체 미디어를 먼저 실행"
                : "일반 동영상 주소 감지");

        int imageCount = 0;
        int directVideoCount = 0;
        boolean structuredHasVideo = false;
        boolean structuredUnresolvedVideo = false;
        Throwable photoError = null;
        if (socialUrl) {
            try {
                logDownload("구조화된 전체 사진·동영상 항목 우선 검사");
                SocialImageDownloader.Result result = downloadSocialMedia(
                        url, outputDirectory, cookieFile, false, true, generation);
                imageCount = result.imageCount;
                directVideoCount = result.videoCount;
                structuredHasVideo = result.hasVideo;
                structuredUnresolvedVideo = result.unresolvedVideo;
                logDownload("구조화된 미디어 결과: 사진 " + imageCount
                        + "개, 직접 영상 " + directVideoCount
                        + "개, 영상 표시=" + structuredHasVideo
                        + ", 미해결 영상=" + structuredUnresolvedVideo);
                if (!structuredUnresolvedVideo
                        && (directVideoCount > 0 || (imageCount > 0 && !structuredHasVideo))) {
                    MediaScannerConnection.scanFile(this,
                            new String[]{outputDirectory.getAbsolutePath()}, null, null);
                    logDownload("구조화된 전체 미디어 다운로드 완료");
                    runOnUiThread(() -> finishDownload(generation, true, null));
                    return;
                }
            } catch (InterruptedException cancelled) {
                return;
            } catch (Throwable error) {
                photoError = error;
                logDownload("구조화된 미디어 검사 실패: " + safeMessage(error));
            }
        }

        try {
            YoutubeDLRequest infoRequest = new YoutubeDLRequest(url);
            if (cookieFile != null && cookieFile.isFile()) {
                infoRequest.addOption("--cookies", cookieFile.getAbsolutePath());
            }
            VideoInfo info = YoutubeDL.getInstance().getInfo(infoRequest);
            String title = info == null ? null : info.getTitle();
            if (info != null) {
                logDownload("메타데이터 확인: extractor=" + info.getExtractor()
                        + ", duration=" + info.getDuration() + "초, ext=" + info.getExt());
            }
            if (!TextUtils.isEmpty(title)) {
                currentVideoTitle = title;
                runOnUiThread(() -> {
                    if (!isDownloadActive(generation)) {
                        return;
                    }
                    videoTitleText.setText(title);
                    videoTitleText.setTextColor(getColor(R.color.text_primary));
                    statusText.setText(R.string.status_starting);
                });
                if (isDownloadActive(generation)) {
                    showProgressNotification(currentProgress, false);
                }
            }
        } catch (Throwable error) {
            if (!isDownloadActive(generation)) {
                return;
            }
            Log.w(TAG, "Failed to read video title", error);
            logDownload("메타데이터 확인 실패: " + safeMessage(error));
            runOnUiThread(() -> {
                if (isDownloadActive(generation)) {
                    videoTitleText.setText(R.string.video_title_unavailable);
                }
            });
        }

        if (!isDownloadActive(generation)) {
            return;
        }
        YoutubeDLRequest request = new YoutubeDLRequest(url);
        request.addOption("--yes-playlist");
        request.addOption("--no-mtime");
        if (audioOnly) {
            request.addOption("--extract-audio");
            request.addOption("--audio-format", "m4a");
            request.addOption("--no-keep-video");
            request.addOption("--no-simulate");
            request.addOption("--progress");
            request.addOption("--print", "after_move:"
                    + DownloadArtifactTracker.AUDIO_PATH_MARKER + "%(filepath)j");
        } else {
            request.addOption("--merge-output-format", "mp4");
        }
        if (cookieFile != null && cookieFile.isFile()) {
            request.addOption("--cookies", cookieFile.getAbsolutePath());
        }
        request.addOption("-f", format);
        request.addOption("-o", DownloadFormatSelector.outputTemplate(outputDirectory, audioOnly));

        Map<String, String> filesBeforeVideoDownload =
                DownloadArtifactTracker.snapshot(outputDirectory);
        AtomicBoolean alreadyDownloaded = new AtomicBoolean(false);
        Function3<Float, Long, String, Unit> callback = (progress, eta, line) -> {
            if (!isDownloadActive(generation)) {
                try {
                    YoutubeDL.getInstance().destroyProcessById(PROCESS_ID);
                } catch (Throwable ignored) {
                    // Cancellation may have already removed the process.
                }
                return Unit.INSTANCE;
            }
            if (!TextUtils.isEmpty(line)
                    && line.toLowerCase(Locale.US).contains("already been downloaded")) {
                alreadyDownloaded.set(true);
            }
            int value = Math.max(0, Math.min(100, Math.round(progress)));
            updateDownloadProgress(value, line, generation);
            return Unit.INSTANCE;
        };

        boolean videoDownloaded = false;
        int ytDlpVideoCount = 0;
        Throwable videoError = null;
        try {
            logDownload(audioOnly ? "yt-dlp 음원 추출 실행 · M4A"
                    : "yt-dlp 전체 동영상 항목 다운로드 실행");
            YoutubeDLResponse response = YoutubeDL.getInstance().execute(request, PROCESS_ID, callback);
            if (!isDownloadActive(generation)) {
                return;
            }
            if (audioOnly) {
                logDownload("최종 음원 경로 보고: "
                        + (response.getOut().contains(DownloadArtifactTracker.AUDIO_PATH_MARKER) ? "있음" : "없음"));
                List<File> audioFiles = DownloadArtifactTracker.completedAudioFiles(
                        outputDirectory, response.getOut());
                if (audioFiles.isEmpty()) {
                    throw new IllegalStateException("실제 M4A 음원 파일이 없습니다. 사진 전용 게시물이나 음원 없는 영상은 추출할 수 없습니다.");
                }
                String[] paths = new String[audioFiles.size()];
                for (int index = 0; index < audioFiles.size(); index++) {
                    File audio = audioFiles.get(index);
                    paths[index] = audio.getAbsolutePath();
                    logDownload("실제 음원 확인: " + audio.getName() + " (" + audio.length() + " bytes)");
                }
                MediaScannerConnection.scanFile(this, paths, null, null);
                logDownload("음원 추출 완료: " + audioFiles.size() + "개 · M4A");
                runOnUiThread(() -> finishDownload(generation, true, null));
                return;
            }
            List<File> videoFiles = DownloadArtifactTracker.changedVideos(
                    outputDirectory, filesBeforeVideoDownload);
            if (videoFiles.isEmpty() && !alreadyDownloaded.get()) {
                throw new IllegalStateException(
                        "yt-dlp가 실제 동영상 파일을 만들지 않았습니다.");
            }
            videoDownloaded = true;
            ytDlpVideoCount = Math.max(videoFiles.size(), alreadyDownloaded.get() ? 1 : 0);
            for (File videoFile : videoFiles) {
                logDownload("실제 동영상 확인: " + videoFile.getName()
                        + " (" + videoFile.length() + " bytes)");
            }
            logDownload("yt-dlp 전체 동영상 항목 다운로드 완료: "
                    + ytDlpVideoCount + "개");
        } catch (Throwable error) {
            if (!isDownloadActive(generation)) {
                return;
            }
            videoError = error;
            logDownload((audioOnly ? "음원 추출 실패: " : "yt-dlp 동영상 다운로드 실패: ") + safeMessage(error));
        }

        if (socialUrl && !videoDownloaded && imageCount == 0
                && directVideoCount == 0 && isDownloadActive(generation)) {
            try {
                logDownload("동영상 실패 후 민감 게시물 사진 폴백 검사");
                SocialImageDownloader.Result result = downloadSocialMedia(
                        url, outputDirectory, cookieFile, true, true, generation);
                imageCount = result.imageCount;
                directVideoCount = result.videoCount;
                structuredHasVideo = result.hasVideo;
                structuredUnresolvedVideo = result.unresolvedVideo;
                logDownload("소셜 미디어 처리 결과: 사진 " + result.imageCount
                        + "개, 직접 영상 " + result.videoCount
                        + "개, 영상 표시=" + result.hasVideo);
            } catch (InterruptedException cancelled) {
                return;
            } catch (Throwable error) {
                photoError = error;
                logDownload("사진 다운로드 실패: " + safeMessage(error));
            }
        }

        if (!isDownloadActive(generation)) {
            return;
        }
        boolean unresolvedVideo = structuredUnresolvedVideo && !videoDownloaded;
        boolean success = !unresolvedVideo
                && (videoDownloaded || directVideoCount > 0 || imageCount > 0);
        String failureDetail = photoError instanceof SocialImageDownloader.LoginRequiredException
                ? safeMessage(photoError)
                : videoError != null ? safeMessage(videoError)
                : photoError != null ? safeMessage(photoError) : getString(R.string.status_failed);
        if (unresolvedVideo) {
            failureDetail = "게시물의 영상 항목을 실제 동영상 파일로 저장하지 못했습니다.";
        }
        if (success) {
            MediaScannerConnection.scanFile(this,
                    new String[]{outputDirectory.getAbsolutePath()}, null, null);
            logDownload("전체 미디어 완료: 영상="
                    + (ytDlpVideoCount + directVideoCount) + "개, 사진=" + imageCount + "개");
        }
        final String finalFailureDetail = failureDetail;
        runOnUiThread(() -> finishDownload(
                generation, success, success ? null : finalFailureDetail));
    }

    private SocialImageDownloader.Result downloadSocialMedia(
            String url, File outputDirectory, File cookieFile,
            boolean allowAmbiguousFallback, boolean downloadDirectVideos,
            long generation) throws Exception {
        return SocialImageDownloader.download(
                MainActivity.this, url, outputDirectory, cookieFile,
                allowAmbiguousFallback, downloadDirectVideos,
                new SocialImageDownloader.Listener() {
                    @Override
                    public void onTitle(String title) {
                        if (!isDownloadActive(generation)) {
                            return;
                        }
                        currentVideoTitle = title;
                        runOnUiThread(() -> {
                            if (isDownloadActive(generation)) {
                                videoTitleText.setText(title);
                                videoTitleText.setTextColor(getColor(R.color.text_primary));
                            }
                        });
                    }

                    @Override
                    public void onProgress(int progress) {
                        updateDownloadProgress(progress, null, generation);
                    }

                    @Override
                    public void onLog(String message) {
                        logDownload(message);
                    }

                    @Override
                    public boolean isCancelled() {
                        return !isDownloadActive(generation);
                    }
                });
    }

    private void logDownload(String message) {
        DownloadLogStore.append(getApplicationContext(), message);
    }

    private boolean isDownloadActive(long generation) {
        return downloading && downloadGeneration == generation
                && !Thread.currentThread().isInterrupted();
    }

    private void updateDownloadProgress(int value, String detail, long generation) {
        if (!isDownloadActive(generation)) {
            return;
        }
        currentProgress = value;
        if (value != lastNotificationProgress) {
            lastNotificationProgress = value;
            showProgressNotification(value, false);
        }
        runOnUiThread(() -> {
            if (!isDownloadActive(generation)) {
                return;
            }
            progressBar.setProgress(value);
            percentText.setText(getString(R.string.percent_format, value));
            if (!TextUtils.isEmpty(detail)) {
                statusText.setText(shortStatus(detail));
            }
        });
    }

    private void requestCancelDownload() {
        if (!downloading && !bulkMode) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (lastCancelButtonPressAt > 0
                && now - lastCancelButtonPressAt <= CANCEL_CONFIRM_WINDOW_MILLIS) {
            lastCancelButtonPressAt = 0;
            cancelDownload();
            return;
        }
        lastCancelButtonPressAt = now;
        toast(R.string.cancel_press_again);
    }

    private void cancelDownload() {
        cancelDownload(true);
    }

    private void cancelDownload(boolean clearPending) {
        if (!downloading && !bulkMode) {
            return;
        }
        logDownload("사용자가 다운로드 취소");
        lastCancelButtonPressAt = 0;
        downloadGeneration++;
        accountTask = null;
        bulkScanning = false;
        if (xAccountDiscovery != null) {
            xAccountDiscovery.cancel();
            xAccountDiscovery = null;
        }
        if (instagramAccountDiscovery != null) {
            instagramAccountDiscovery.cancel();
            instagramAccountDiscovery = null;
        }
        Future<?> future = activeDownloadFuture;
        activeDownloadFuture = null;
        if (future != null) {
            future.cancel(true);
        }
        try {
            YoutubeDL.getInstance().destroyProcessById(PROCESS_ID);
        } catch (Throwable ignored) {
            // The process may already have exited.
        }
        downloading = false;
        activeUrl = null;
        bulkMode = false;
        bulkMediaUrls.clear();
        if (clearPending) downloadQueue.clear();
        stopKeepAlive();
        updateQueueUi();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setDownloadingUi(false);
        statusText.setText(R.string.queue_cleared);
        clearDownloadNotifications();
        shutdownExecutorIfDestroyed();
    }

    private void finishDownload(long generation, boolean success, String detail) {
        if (!isDownloadActive(generation)) {
            return;
        }
        logDownload(success ? "작업 성공" : "작업 실패: " + detail);
        downloading = false;
        activeDownloadFuture = null;
        activeUrl = null;
        boolean sessionContinues = bulkMode || downloadQueue.size() > 0;
        if (!sessionContinues) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        setDownloadingUi(false);
        if (success) {
            progressBar.setProgress(100);
            percentText.setText(getString(R.string.percent_format, 100));
            statusText.setText(R.string.status_complete);
            if (!sessionContinues) {
                stopKeepAlive();
                clearDownloadNotifications();
                toast(R.string.status_complete);
            }
        } else {
            statusText.setText(getString(R.string.status_with_detail,
                    getString(R.string.status_failed), detail));
            if (!sessionContinues) {
                stopKeepAlive();
                clearDownloadNotifications();
                toast(R.string.status_failed);
            }
        }
        if (bulkMode) {
            bulkAttempted++;
            if (success) {
                bulkSucceeded++;
            } else {
                bulkFailed++;
            }
            updateQueueUi();
            if (bulkMediaUrls.isEmpty()) {
                finishBulkDiscovery(true, null);
            } else {
                statusText.setText(getString(R.string.bulk_waiting,
                        bulkAttempted, bulkTotal));
                setDownloadingUi(false);
                cancelButton.setEnabled(true);
                startNextBulkDownload();
            }
            return;
        }
        if (sessionContinues) {
            startNextQueuedDownload();
        } else {
            lastCancelButtonPressAt = 0;
            shutdownExecutorIfDestroyed();
        }
    }

    private void setDownloadingUi(boolean active) {
        downloadButton.setEnabled(engineReady);
        bulkDownloadButton.setEnabled(engineReady);
        cancelButton.setEnabled(active || bulkMode);
        urlInput.setEnabled(true);
        qualitySpinner.setEnabled(!audioOnlySwitch.isChecked());
    }

    private void pasteUrl() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            toast(R.string.clipboard_empty);
            return;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        if (TextUtils.isEmpty(text)) {
            toast(R.string.clipboard_empty);
            return;
        }
        urlInput.setText(text.toString().trim());
        urlInput.setSelection(urlInput.length());
    }

    private void maybeAutoStartFromClipboard() {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        if (TextUtils.isEmpty(text)) {
            return;
        }
        String url = extractFirstUrl(text.toString());
        if (!isSupportedUrl(url)) {
            return;
        }

        urlInput.setText(url);
        urlInput.setSelection(urlInput.length());
        if (!isImmediateDownloadEnabled()
                || !engineReady
                || url.equals(lastAutoStartedUrl)) {
            return;
        }
        lastAutoStartedUrl = url;
        beginDownload();
    }

    private void readSharedUrl(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
            return;
        }
        sharedIntentActive = true;
        String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (!TextUtils.isEmpty(shared)) {
            String url = extractFirstUrl(shared);
            if (!isSupportedUrl(url)) {
                urlInput.setText(url);
                urlInput.setError(getString(R.string.invalid_url));
                return;
            }
            urlInput.setText(url);
            urlInput.setSelection(urlInput.length());
            pendingSharedUrl = isImmediateDownloadEnabled() ? url : null;
            maybeStartSharedDownload();
        }
    }

    private void maybeStartSharedDownload() {
        if (!isImmediateDownloadEnabled()) {
            pendingSharedUrl = null;
            return;
        }
        if (!engineReady || TextUtils.isEmpty(pendingSharedUrl)) {
            return;
        }
        String url = pendingSharedUrl;
        pendingSharedUrl = null;
        lastAutoStartedUrl = url;
        urlInput.setText(url);
        urlInput.setSelection(urlInput.length());
        beginDownload();
    }

    private boolean isImmediateDownloadEnabled() {
        return immediateDownloadSwitch == null || immediateDownloadSwitch.isChecked();
    }

    private static String extractFirstUrl(String text) {
        for (String part : text.split("\\s+")) {
            if (part.startsWith("https://") || part.startsWith("http://")) {
                return part;
            }
        }
        return text.trim();
    }

    private boolean hasStoragePermission() {
        return Build.VERSION.SDK_INT >= 29
                || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasNotificationPermission() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void registerDownloadServiceReceiver() {
        IntentFilter filter = new IntentFilter(DownloadKeepAliveService.ACTION_TIMEOUT);
        ContextCompat.registerReceiver(this, downloadServiceReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        serviceReceiverRegistered = true;
    }

    private void startKeepAlive(String title) {
        if (keepAliveStarted) {
            return;
        }
        getSystemService(NotificationManager.class).cancel(DOWNLOAD_RESULT_NOTIFICATION_ID);
        keepAliveStarted = DownloadKeepAliveService.start(this, title);
        if (!keepAliveStarted) {
            Log.w(TAG, "Download foreground protection could not be started");
        }
    }

    private void stopKeepAlive() {
        DownloadKeepAliveService.stop(this);
        keepAliveStarted = false;
    }

    private void clearDownloadNotifications() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.cancel(DOWNLOAD_NOTIFICATION_ID);
        manager.cancel(DOWNLOAD_RESULT_NOTIFICATION_ID);
    }

    private void shutdownExecutorIfDestroyed() {
        if (activityDestroyed && !downloading && !bulkMode) {
            executor.shutdownNow();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.notification_channel_description));
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification.Builder notificationBuilder() {
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, DOWNLOAD_CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentIntent(contentIntent)
                .setColor(getColor(R.color.progress))
                .setOnlyAlertOnce(true);
    }

    private void showProgressNotification(int progress, boolean indeterminate) {
        if (!hasNotificationPermission()) {
            return;
        }
        Notification notification = notificationBuilder()
                .setContentTitle(downloadQueue.size() == 0
                        ? getString(R.string.notification_downloading)
                        : getString(R.string.notification_downloading_queued,
                                downloadQueue.size()))
                .setContentText(currentVideoTitle)
                .setProgress(100, progress, indeterminate)
                .setOngoing(true)
                .build();
        getSystemService(NotificationManager.class)
                .notify(DOWNLOAD_NOTIFICATION_ID, notification);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingBulkPermission) {
                    pendingBulkPermission = false;
                    beginAccountDownload();
                } else {
                    beginDownload();
                }
            } else {
                pendingBulkPermission = false;
                toast(R.string.permission_required);
            }
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            if (pendingBulkPermission) {
                pendingBulkPermission = false;
                beginAccountDownload();
            } else {
                beginDownload();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LOGIN_REQUEST) {
            refreshLoginStatus();
            if (resultCode == RESULT_OK) {
                toast(R.string.login_saved);
            }
        }
    }

    private static boolean isSupportedUrl(String value) {
        return DownloadUrlPolicy.isSupported(value);
    }

    private String selectedFormat() {
        return DownloadFormatSelector.forMode(
                qualitySpinner.getSelectedItemPosition(), audioOnlySwitch.isChecked());
    }

    private String getAppVersionName() {
        try {
            String versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0)
                    .versionName;
            return TextUtils.isEmpty(versionName) ? "-" : versionName;
        } catch (PackageManager.NameNotFoundException error) {
            Log.w(TAG, "Unable to read app version", error);
            return "-";
        }
    }

    private Button commandButton(int textResource, boolean primary) {
        Button button = new Button(this);
        button.setText(textResource);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? Color.WHITE : getColor(R.color.text_primary));
        button.setBackground(rounded(
                primary ? getColor(R.color.accent) : Color.WHITE,
                primary ? getColor(R.color.accent) : getColor(R.color.border), 1, 6));
        return button;
    }

    private GradientDrawable rounded(int fill, int stroke, int strokeDp, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(strokeDp), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap(int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = bottomMargin;
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(int resource) {
        Toast.makeText(this, resource, Toast.LENGTH_SHORT).show();
    }

    private static String shortStatus(String line) {
        String clean = line.replace('\r', ' ').replace('\n', ' ').trim();
        return clean.length() <= 100 ? clean : clean.substring(0, 97) + "...";
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return TextUtils.isEmpty(message) ? error.getClass().getSimpleName() : message;
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        if (serviceReceiverRegistered) {
            unregisterReceiver(downloadServiceReceiver);
            serviceReceiverRegistered = false;
        }
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            networkCallback = null;
        }
        if (isFinishing() && (downloading || bulkMode)) {
            cancelDownload();
        }
        if (!downloading && !bulkMode) {
            executor.shutdownNow();
        }
        super.onDestroy();
    }
}
