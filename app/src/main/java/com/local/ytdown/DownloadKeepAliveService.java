package com.local.ytdown;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;

public final class DownloadKeepAliveService extends Service {
    static final String CHANNEL_ID = "downloads";
    static final int FOREGROUND_NOTIFICATION_ID = 2001;
    static final int RESULT_NOTIFICATION_ID = 2002;
    static final String ACTION_TIMEOUT =
            "com.local.ytdown.action.DOWNLOAD_FOREGROUND_TIMEOUT";

    private static final String TAG = "YTDown-KeepAlive";
    private static final String EXTRA_TITLE = "title";
    private static final long WAKE_LOCK_TIMEOUT_MILLIS = 6L * 60L * 60L * 1000L;

    private PowerManager.WakeLock wakeLock;

    static boolean start(Context context, String title) {
        Intent intent = new Intent(context, DownloadKeepAliveService.class)
                .putExtra(EXTRA_TITLE, title);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            return true;
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to start download foreground service", error);
            return false;
        }
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, DownloadKeepAliveService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String title = intent == null ? null : intent.getStringExtra(EXTRA_TITLE);
        Notification notification = buildProgressNotification(title);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification);
        }
        acquireWakeLock();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        sendBroadcast(new Intent(ACTION_TIMEOUT).setPackage(getPackageName()));
        removeForegroundNotification();
        getSystemService(NotificationManager.class).cancel(RESULT_NOTIFICATION_ID);
        stopSelf(startId);
    }

    @Override
    public void onDestroy() {
        releaseWakeLock();
        removeForegroundNotification();
        super.onDestroy();
    }

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            return;
        }
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":download");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MILLIS);
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.notification_channel_description));
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildProgressNotification(String title) {
        Notification.Builder builder = notificationBuilder()
                .setContentTitle(getString(R.string.notification_downloading))
                .setContentText(TextUtils.isEmpty(title)
                        ? getString(R.string.status_starting) : title)
                .setProgress(0, 0, true)
                .setOngoing(true);
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(
                    Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return builder.build();
    }

    private Notification.Builder notificationBuilder() {
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentIntent(contentIntent)
                .setColor(getColor(R.color.progress))
                .setOnlyAlertOnce(true);
    }

    private void removeForegroundNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE);
    }
}
