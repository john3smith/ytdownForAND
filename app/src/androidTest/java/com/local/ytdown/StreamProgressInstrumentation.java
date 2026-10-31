package com.local.ytdown;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.TextView;
import android.widget.ProgressBar;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import kotlin.Unit;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.json.JSONObject;

/** Test APK only: downloads an owned synthetic MP4 over loopback, never visits a site/session. */
public final class StreamProgressInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        MainActivity activity = null;
        FixtureServer server = null;
        Uri published = null;
        try {
            Context context = getTargetContext();
            // Instrumentation.start() can race Application.onCreate during payload extraction.
            // Launch/wait for the existing engine-ready UI before invoking native tools.
            Intent intent = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = (MainActivity) startActivitySync(intent);
            final MainActivity target = activity;
            long deadline = SystemClock.elapsedRealtime() + 120000;
            while (!Boolean.TRUE.equals(field(target, "engineReady")) && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(100);
            }
            if (!Boolean.TRUE.equals(field(target, "engineReady"))) throw new AssertionError("Engine not ready");
            if (YTDownApplication.getInitializationError() != null) {
                throw new AssertionError("Download engine initialization failed");
            }
            // A failed online check must not strand a working installed engine.
            // Inject only the updater failure; probe the real installed Python/yt-dlp.
            Method prepare = MainActivity.class.getDeclaredMethod("prepareDownloadEngine",
                    DownloadEngineStartup.Engine.class);
            prepare.setAccessible(true);
            DownloadEngineStartup.Engine offline = new DownloadEngineStartup.Engine() {
                public void update() throws IOException { throw new IOException("Controlled offline update check"); }
                public String installedVersion() throws Exception {
                    YoutubeDLRequest probe = new YoutubeDLRequest(Collections.emptyList());
                    probe.addOption("--version");
                    return YoutubeDL.getInstance().execute(probe).getOut();
                }
            };
            prepare.invoke(target, new DownloadEngineStartup.Engine() {
                public void update() throws IOException { throw new IOException("Controlled offline update check"); }
                public String installedVersion() throws IOException { throw new IOException("Controlled broken engine"); }
            });
            waitForIdleSync();
            if (Boolean.TRUE.equals(field(target, "engineReady"))) {
                throw new AssertionError("Broken engine marked ready after update failure");
            }
            result.putString("broken_engine_remains_unready", "PASS");
            prepare.invoke(target, offline);
            waitForIdleSync();
            final String installedVersion = offline.installedVersion().trim();
            runOnMainSync(() -> {
                try {
                    if (!Boolean.TRUE.equals(field(target, "engineReady"))
                            || !((android.view.View) field(target, "downloadButton")).isEnabled()
                            || !((android.view.View) field(target, "bulkDownloadButton")).isEnabled()
                            || !((TextView) field(target, "statusText")).getText().toString()
                                    .equals(target.getString(R.string.status_engine_ready_update_failed,
                                            installedVersion))) {
                        throw new AssertionError("Verified offline engine did not restore ready controls/status");
                    }
                } catch (Exception error) { throw new RuntimeException(error); }
            });
            result.putString("failed_update_verified_installed_engine_ready", "PASS");
            android.graphics.Bitmap startupScreen = getUiAutomation().takeScreenshot();
            if (startupScreen == null) throw new AssertionError("Cannot capture verified startup UI");
            try (OutputStream output = new FileOutputStream(new File(context.getCacheDir(), "startup-ready.png"))) {
                startupScreen.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
            } finally { startupScreen.recycle(); }
            File folder = new File(context.getCacheDir(), "progress-test-" + UUID.randomUUID());
            if (!folder.mkdir()) throw new IOException("Cannot create isolated test directory");
            File fixture = new File(folder, "synthetic-input.mp4");
            File ffmpeg = new File(context.getApplicationInfo().nativeLibraryDir, "libffmpeg.so");
            File packages = new File(context.getNoBackupFilesDir(), "youtubedl-android/packages");
            File ca = new File(folder, "fixture-ca.pem");
            try (InputStream input = getContext().getAssets().open("fixture-ca.pem");
                 OutputStream output = new FileOutputStream(ca)) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            ProcessBuilder generate = new ProcessBuilder(ffmpeg.getAbsolutePath(), "-hide_banner",
                    "-loglevel", "error", "-f", "lavfi", "-i", "color=c=blue:s=320x180:r=10",
                    "-t", "12", "-c:v", "mpeg4", "-q:v", "5", "-movflags", "+faststart",
                    fixture.getAbsolutePath());
            generate.environment().put("LD_LIBRARY_PATH", new File(packages, "python/usr/lib")
                    + ":" + new File(packages, "ffmpeg/usr/lib"));
            generate.redirectErrorStream(true);
            Process generated = generate.start();
            try (InputStream output = generated.getInputStream()) {
                byte[] buffer = new byte[4096]; while (output.read(buffer) != -1) { }
            }
            if (generated.waitFor() != 0 || fixture.length() <= 0) {
                throw new AssertionError("Cannot generate synthetic MP4");
            }
            MediaMetadataRetriever source = new MediaMetadataRetriever();
            long durationMs;
            try {
                source.setDataSource(fixture.getAbsolutePath());
                durationMs = Long.parseLong(source.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            } finally { source.release(); }
            server = new FixtureServer(fixture, getContext());
            server.start();
            final long generation = ((Number) field(target, "downloadGeneration")).longValue();
            runOnMainSync(() -> {
                try {
                    setField(target, "downloading", true);
                    setField(target, "currentVideoTitle", "진행률 검증용 테스트 영상");
                    ((TextView) field(target, "videoTitleText")).setText("진행률 검증용 테스트 영상");
                } catch (Exception error) { throw new RuntimeException(error); }
            });
            Method update = MainActivity.class.getDeclaredMethod("updateStreamDownloadProgress",
                    FfmpegProgressTracker.Snapshot.class, long.class);
            update.setAccessible(true);
            FfmpegProgressTracker tracker = new FfmpegProgressTracker();
            String fixtureUrl = "https://127.0.0.1:" + server.port() + "/fixture.mp4";
            JSONObject info = new JSONObject().put("id", "synthetic-progress-test")
                    .put("title", "synthetic-progress-test").put("url", fixtureUrl)
                    .put("webpage_url", fixtureUrl).put("ext", "mp4").put("protocol", "https")
                    .put("format_id", "fixture").put("vcodec", "mpeg4").put("acodec", "none")
                    .put("duration", durationMs / 1000d).put("is_live", false)
                    .put("extractor", "generic").put("extractor_key", "Generic");
            File infoFile = new File(folder, "fixture-info.json");
            try (OutputStream output = new FileOutputStream(infoFile)) {
                output.write(info.toString().getBytes(StandardCharsets.UTF_8));
            }
            // Controlled metadata avoids an extra extractor HEAD with an unrelated CA store.
            // All actual media transfer uses FFmpeg with TLS verification and the test's own CA.
            YoutubeDLRequest request = new YoutubeDLRequest(Collections.emptyList());
            request.addOption("--load-info-json", infoFile.getAbsolutePath());
            FfmpegStreamDownloadOptions.apply(true, false, ffmpeg, ca,
                    (key, value) -> request.addOption(key,
                            "--downloader-args".equals(key) ? value + " -re" : value));
            request.addOption("--no-simulate");
            request.addOption("--no-quiet");
            request.addOption("--print", FfmpegProgressTracker.DURATION_TEMPLATE);
            File downloaded = new File(folder, "synthetic-downloaded.mp4");
            request.addOption("-o", downloaded.getAbsolutePath());
            AtomicInteger samples = new AtomicInteger();
            AtomicInteger maxIntermediate = new AtomicInteger();
            List<Integer> percentages = new ArrayList<>();
            final boolean[] screenshotHold = {false};
            YoutubeDL.getInstance().execute(request, "stream-progress-test", (ignored, eta, line) -> {
                FfmpegProgressTracker.Snapshot snapshot = tracker.accept(line);
                if (snapshot != null) {
                    try { update.invoke(target, snapshot, generation); }
                    catch (Exception error) { throw new RuntimeException(error); }
                    if (snapshot.percent > 0 && snapshot.percent < 99) {
                        samples.incrementAndGet();
                        maxIntermediate.accumulateAndGet(snapshot.percent, Math::max);
                    }
                    percentages.add(snapshot.percent);
                    if (snapshot.percent >= 40 && snapshot.percent < 99 && !screenshotHold[0]) {
                        screenshotHold[0] = true;
                        waitForIdleSync();
                        NotificationManager notifications = context.getSystemService(NotificationManager.class);
                        boolean enabled = notifications.areNotificationsEnabled();
                        if (enabled && !Arrays.stream(notifications.getActiveNotifications()).anyMatch(
                                n -> n.getNotification().extras.getInt("android.progress", -1) == snapshot.percent)) {
                            throw new AssertionError("Notification progress differs from app progress");
                        }
                        result.putString("notification_progress", enabled ? "PASS" : "permission not granted");
                        Bundle checkpoint = new Bundle();
                        checkpoint.putInt("capture_progress_percent", snapshot.percent);
                        sendStatus(1, checkpoint);
                        SystemClock.sleep(12000); // Explicit test-only window for a real in-progress screenshot.
                    }
                }
                return Unit.INSTANCE;
            });
            waitForIdleSync();
            if (samples.get() < 2 || maxIntermediate.get() < 40 || downloaded.length() <= 0) {
                throw new AssertionError("No genuine intermediate download percentages");
            }
            final String[] displayed = {null};
            runOnMainSync(() -> {
                try { displayed[0] = ((TextView) field(target, "percentText")).getText().toString(); }
                catch (Exception error) { throw new RuntimeException(error); }
            });
            if (!displayed[0].equals("99%")) throw new AssertionError("Finalizing UI must stay below 100%");
            published = StreamVideoPublisher.publish(context, downloaded, () -> false);
            MediaMetadataRetriever saved = new MediaMetadataRetriever();
            try {
                saved.setDataSource(context, published);
                long savedDuration = Long.parseLong(saved.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                if (Math.abs(savedDuration - durationMs) > 1000) throw new AssertionError("Duration mismatch");
                if (!"320".equals(saved.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))) {
                    throw new AssertionError("No valid video track");
                }
                result.putLong("saved_duration_ms", savedDuration);
            } finally { saved.release(); }
            result.putLong("downloaded_bytes", downloaded.length());
            result.putInt("intermediate_samples", samples.get());
            result.putString("percentages", percentages.toString());
            result.putString("finalizing_ui", displayed[0]);
            runOnMainSync(() -> {
                try {
                    Method finish = MainActivity.class.getDeclaredMethod("finishDownload", long.class, boolean.class, String.class);
                    finish.setAccessible(true);
                    finish.invoke(target, generation, true, null);
                } catch (Exception error) { throw new RuntimeException(error); }
            });
            waitForIdleSync();
            runOnMainSync(() -> {
                try { displayed[0] = ((TextView) field(target, "percentText")).getText().toString(); }
                catch (Exception error) { throw new RuntimeException(error); }
            });
            if (!"100%".equals(displayed[0])) throw new AssertionError("Verified completion not shown");
            result.putString("completed_ui", displayed[0]);
            FfmpegProgressTracker unknown = new FfmpegProgressTracker();
            unknown.accept("out_time_us=3000000");
            unknown.accept("total_size=1024");
            setField(target, "downloading", true);
            update.invoke(target, unknown.accept("progress=continue"), generation);
            waitForIdleSync();
            runOnMainSync(() -> {
                try {
                    String text = ((TextView) field(target, "percentText")).getText().toString();
                    if (!"저장 중".equals(text) || !((ProgressBar) field(target, "progressBar")).isIndeterminate()) {
                        throw new AssertionError("Unknown duration must not show a fake percentage");
                    }
                    Method controls = MainActivity.class.getDeclaredMethod("setDownloadingUi", boolean.class);
                    controls.setAccessible(true);
                    controls.invoke(target, false);
                    if (((ProgressBar) field(target, "progressBar")).isIndeterminate()) {
                        throw new AssertionError("Stopped UI still spinning");
                    }
                    setField(target, "downloading", false);
                    ((TextView) field(target, "percentText")).setText("100%");
                    ((TextView) field(target, "statusText")).setText("진행률 검증 완료");
                    ((ProgressBar) field(target, "progressBar")).setProgress(100);
                } catch (Exception error) { throw new RuntimeException(error); }
            });
            result.putString("unknown_duration_ui", "PASS");
            result.putString("stopped_ui_not_spinning", "PASS");
            verifyDirectVideoCloseCleanup(context, downloaded, result);
            result.putString("result", "PASS");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("result", "FAIL");
            result.putString("error", error.toString());
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            if (server != null) server.close();
            // Only this test's freshly created synthetic export; never user media.
            if (published != null) getTargetContext().getContentResolver().delete(published, null, null);
            if (activity != null) {
                try { setField(activity, "downloading", false); } catch (Exception ignored) { }
            }
        }
    }
    private static Object field(Object object, String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void setField(Object object, String name, Object value) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }

    /** Controlled response faults with the real direct-video path and real MediaStore. */
    private static void verifyDirectVideoCloseCleanup(Context context, File fixture, Bundle result)
            throws Exception {
        byte[] media;
        try (InputStream input = new FileInputStream(fixture);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            media = output.toByteArray();
        }
        byte[] image;
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(640, 480,
                android.graphics.Bitmap.Config.ARGB_8888);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            bitmap.eraseColor(android.graphics.Color.BLUE);
            if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)) {
                throw new AssertionError("Cannot create owned PNG fixture");
            }
            image = output.toByteArray();
        } finally { bitmap.recycle(); }
        // This instrumentation process alone intercepts HTTP after the genuine HTTPS test.
        // No TLS/trust changes or external requests; all other protocols keep their handler.
        URL.setURLStreamHandlerFactory(protocol -> "http".equals(protocol) ? new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) throws IOException {
                if (!"direct-close.invalid".equals(url.getHost())) throw new IOException("Unexpected fixture host");
                byte[] response = url.getPath().startsWith("/image/") ? image : media;
                return new HttpURLConnection(url) {
                    public void connect() { }
                    public void disconnect() { }
                    public boolean usingProxy() { return false; }
                    public int getResponseCode() { return 200; }
                    public long getContentLengthLong() { return response.length; }
                    public String getContentType() {
                        return url.getPath().startsWith("/image/") ? "image/png" : "video/mp4";
                    }
                    public InputStream getInputStream() throws IOException {
                        if (url.getPath().equals("/open")) throw new IOException("Controlled response open failure");
                        return new ByteArrayInputStream(response) {
                            @Override public void close() throws IOException {
                                if (url.getPath().equals("/input-close")) {
                                    throw new IOException("Controlled response close failure");
                                }
                            }
                        };
                    }
                };
            }
        } : null);
        Method download = SocialImageDownloader.class.getDeclaredMethod("downloadVideos", Context.class,
                List.class, File.class, String.class, NetscapeCookieJar.class, SocialImageDownloader.Listener.class);
        download.setAccessible(true);
        for (String mode : Arrays.asList("success", "input-close", "open")) {
            String title = "cycle-close-" + UUID.randomUUID();
            String name = title + "-video.mp4";
            List<String> logs = new ArrayList<>();
            SocialImageDownloader.Listener listener = new SocialImageDownloader.Listener() {
                public void onTitle(String text) { }
                public void onProgress(int percent) { }
                public void onLog(String text) { logs.add(text); }
                public boolean isCancelled() { return false; }
            };
            Throwable failure = null;
            try {
                download.invoke(null, context, Collections.singletonList("http://direct-close.invalid/" + mode),
                        fixture.getParentFile(), title, null, listener);
            } catch (java.lang.reflect.InvocationTargetException error) { failure = error.getCause(); }
            android.net.Uri collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            List<Uri> owned = new ArrayList<>();
            try {
                try (android.database.Cursor rows = context.getContentResolver().query(collection,
                        new String[]{"_id", "is_pending", "_size", "mime_type"},
                        "_display_name = ?", new String[]{name}, null)) {
                    if (rows == null) throw new AssertionError("Cannot inspect owned direct-video item");
                    while (rows.moveToNext()) {
                        Uri uri = android.content.ContentUris.withAppendedId(collection, rows.getLong(0));
                        owned.add(uri);
                        if (rows.getInt(1) != 0 || rows.getLong(2) != media.length
                                || !"video/mp4".equals(rows.getString(3))) {
                            throw new AssertionError("Direct-video metadata/commit mismatch");
                        }
                    }
                }
                boolean success = mode.equals("success");
                if (success ? failure != null || owned.size() != 1 : !(failure instanceof IOException) || !owned.isEmpty()) {
                    throw new AssertionError("Direct-video " + mode + " failure=" + failure + " remaining=" + owned.size());
                }
                boolean loggedComplete = logs.stream().anyMatch(line -> line.startsWith("직접 동영상 저장 완료:"));
                if (loggedComplete != success) throw new AssertionError("Incorrect direct-video completion log");
                if (success) {
                    try (InputStream input = context.getContentResolver().openInputStream(owned.get(0));
                         InputStream expected = new ByteArrayInputStream(media)) {
                        if (input == null || !StreamFileIdentity.sha256(input, () -> false)
                                .equals(StreamFileIdentity.sha256(expected, () -> false))) {
                            throw new AssertionError("Direct-video byte integrity mismatch");
                        }
                    }
                    MediaMetadataRetriever saved = new MediaMetadataRetriever();
                    try {
                        saved.setDataSource(context, owned.get(0));
                        if (!"12000".equals(saved.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))
                                || !"320".equals(saved.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))) {
                            throw new AssertionError("Direct-video playback metadata mismatch");
                        }
                    } finally { saved.release(); }
                }
                result.putString("direct_video_" + mode.replace('-', '_'), "PASS");
            } finally {
                // Names are unique to this attempt; preserve all unrelated media.
                for (Uri uri : owned) context.getContentResolver().delete(uri, null, null);
            }
        }
        verifyMediaStoreCommit(context, fixture.getParentFile(), media, image, result);
    }

    /** Actual download methods and real MediaStore I/O; only commit outcome is injected. */
    private static void verifyMediaStoreCommit(Context context, File directory,
                                              byte[] video, byte[] image, Bundle result) throws Exception {
        for (String kind : Arrays.asList("image", "video")) {
            for (String mode : Arrays.asList("success", "zero", "throw")) {
                CommitProvider provider = new CommitProvider(context.getContentResolver(), mode);
                android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo();
                info.authority = "media";
                provider.attachInfo(context, info);
                android.content.ContentResolver resolver = android.content.ContentResolver.wrap(provider);
                Context isolated = new android.content.ContextWrapper(context) {
                    @Override public android.content.ContentResolver getContentResolver() { return resolver; }
                };
                boolean photo = kind.equals("image");
                byte[] expected = photo ? image : video;
                String title = "cycle-commit-" + UUID.randomUUID();
                List<String> logs = new ArrayList<>();
                SocialImageDownloader.Listener listener = new SocialImageDownloader.Listener() {
                    public void onTitle(String text) { }
                    public void onProgress(int percent) { }
                    public void onLog(String text) { logs.add(text); }
                    public boolean isCancelled() { return false; }
                };
                Throwable failure = null;
                try {
                    Method download = photo
                            ? SocialImageDownloader.class.getDeclaredMethod("downloadImages", Context.class,
                                    List.class, File.class, String.class, boolean.class, SocialImageDownloader.Listener.class)
                            : SocialImageDownloader.class.getDeclaredMethod("downloadVideos", Context.class,
                                    List.class, File.class, String.class, NetscapeCookieJar.class, SocialImageDownloader.Listener.class);
                    download.setAccessible(true);
                    try {
                        download.invoke(null, isolated, Collections.singletonList(
                                "http://direct-close.invalid/" + kind + "/success"), directory, title,
                                photo ? Boolean.FALSE : null, listener);
                    } catch (java.lang.reflect.InvocationTargetException error) { failure = error.getCause(); }
                    boolean success = mode.equals("success");
                    if (provider.created == null || provider.commits != 1
                            || provider.deleted != (success ? 0 : 1)) {
                        throw new AssertionError("Commit lifecycle mismatch: " + kind + "/" + mode);
                    }
                    if (success ? failure != null : mode.equals("zero")
                            ? !(failure instanceof IOException) : !(failure instanceof IllegalStateException)) {
                        throw new AssertionError("Wrong commit result: " + kind + "/" + mode + " " + failure);
                    }
                    String completed = photo ? "사진 저장 완료:" : "직접 동영상 저장 완료:";
                    if (logs.stream().anyMatch(line -> line.startsWith(completed)) != success) {
                        throw new AssertionError("Incorrect completion log: " + kind + "/" + mode);
                    }
                    try (android.database.Cursor rows = context.getContentResolver().query(provider.created,
                            new String[]{"is_pending", "_size", "mime_type", "_display_name"}, null, null, null)) {
                        if (rows == null || rows.getCount() != (success ? 1 : 0)) {
                            throw new AssertionError("Failed commit left a MediaStore item: " + kind + "/" + mode);
                        }
                        if (success && (!rows.moveToFirst() || rows.getInt(0) != 0
                                || rows.getLong(1) != expected.length
                                || !(photo ? "image/png" : "video/mp4").equals(rows.getString(2))
                                || !rows.getString(3).equals(title + (photo ? ".png" : "-video.mp4")))) {
                            throw new AssertionError("Committed media metadata mismatch");
                        }
                    }
                    if (success) {
                        try (InputStream input = context.getContentResolver().openInputStream(provider.created);
                             InputStream original = new ByteArrayInputStream(expected)) {
                            if (input == null || !StreamFileIdentity.sha256(input, () -> false)
                                    .equals(StreamFileIdentity.sha256(original, () -> false))) {
                                throw new AssertionError("Committed media bytes mismatch");
                            }
                        }
                        if (photo) {
                            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
                            bounds.inJustDecodeBounds = true;
                            try (InputStream input = context.getContentResolver().openInputStream(provider.created)) {
                                android.graphics.BitmapFactory.decodeStream(input, null, bounds);
                            }
                            if (bounds.outWidth != 640 || bounds.outHeight != 480) {
                                throw new AssertionError("Committed PNG dimensions mismatch");
                            }
                        }
                    }
                    result.putString("commit_" + kind + "_" + mode, "PASS");
                } finally {
                    // The provider remembers only this attempt's inserted URI.
                    if (provider.created != null) context.getContentResolver().delete(provider.created, null, null);
                }
            }
        }
    }

    private static final class CommitProvider extends android.content.ContentProvider {
        private final android.content.ContentResolver real;
        private final String mode;
        Uri created;
        int commits, deleted;
        CommitProvider(android.content.ContentResolver real, String mode) { this.real = real; this.mode = mode; }
        @Override public boolean onCreate() { return true; }
        @Override public String getType(Uri uri) { return real.getType(uri); }
        @Override public Uri insert(Uri uri, android.content.ContentValues values) {
            if (created != null || !android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI.equals(uri)) {
                throw new AssertionError("Unexpected fixture insertion");
            }
            return created = real.insert(uri, values);
        }
        private void checkOwned(Uri uri) {
            if (created == null || !created.equals(uri)) throw new AssertionError("Unowned fixture URI");
        }
        @Override public android.os.ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
            checkOwned(uri); return real.openFileDescriptor(uri, mode);
        }
        @Override public android.database.Cursor query(Uri uri, String[] projection, String selection,
                                                       String[] args, String order) {
            checkOwned(uri); return real.query(uri, projection, selection, args, order);
        }
        @Override public int update(Uri uri, android.content.ContentValues values, String selection, String[] args) {
            checkOwned(uri); commits++;
            if (!Integer.valueOf(0).equals(values.getAsInteger("is_pending"))) {
                throw new AssertionError("Unexpected fixture update");
            }
            if (mode.equals("zero")) return 0;
            if (mode.equals("throw")) throw new IllegalStateException("Controlled commit failure");
            return real.update(uri, values, selection, args);
        }
        @Override public int delete(Uri uri, String selection, String[] args) {
            checkOwned(uri); deleted++; return real.delete(uri, selection, args);
        }
    }
    private static final class FixtureServer extends Thread {
        private final File file;
        private final ServerSocket server;
        FixtureServer(File file, Context testContext) throws Exception {
            this.file = file;
            KeyStore store = KeyStore.getInstance("PKCS12");
            try (InputStream input = testContext.getAssets().open("fixture.p12")) {
                store.load(input, "synthetic-test-only".toCharArray());
            }
            KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(store, "synthetic-test-only".toCharArray());
            SSLContext tls = SSLContext.getInstance("TLS");
            tls.init(keys.getKeyManagers(), null, null);
            server = tls.getServerSocketFactory().createServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
            setDaemon(true);
        }
        int port() { return server.getLocalPort(); }
        public void run() {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(10000);
                    BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    String first = input.readLine();
                    long start = 0;
                    boolean ranged = false;
                    String line;
                    while ((line = input.readLine()) != null && !line.isEmpty()) {
                        if (line.toLowerCase(Locale.ROOT).startsWith("range: bytes=")) {
                            start = Long.parseLong(line.substring(13).split("-", 2)[0]); ranged = true;
                        }
                    }
                    if (start < 0 || start >= file.length()) throw new IOException("Invalid fixture range");
                    OutputStream output = socket.getOutputStream();
                    String headers = "HTTP/1.1 " + (ranged ? "206 Partial Content" : "200 OK")
                            + "\r\nContent-Type: video/mp4\r\nContent-Length: " + (file.length() - start)
                            + "\r\nAccept-Ranges: bytes\r\nConnection: close\r\n"
                            + (ranged ? "Content-Range: bytes " + start + "-" + (file.length()-1) + "/" + file.length() + "\r\n" : "") + "\r\n";
                    output.write(headers.getBytes(StandardCharsets.US_ASCII));
                    if (first != null && !first.startsWith("HEAD ")) {
                        try (RandomAccessFile content = new RandomAccessFile(file, "r")) {
                            content.seek(start); byte[] buffer = new byte[8192]; int count;
                            while ((count = content.read(buffer)) != -1) output.write(buffer, 0, count);
                        }
                    }
                    output.flush();
                } catch (Exception ignored) { /* connection probing may close early */ }
            }
        }
        void close() { try { server.close(); } catch (IOException ignored) { } }
    }
}
