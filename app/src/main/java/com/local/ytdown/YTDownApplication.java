package com.local.ytdown;

import android.app.Application;
import android.util.Log;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;

public final class YTDownApplication extends Application {
    private static final String TAG = "YTDownApplication";
    private static volatile Throwable initializationError;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            YoutubeDL.getInstance().init(this);
            FFmpeg.getInstance().init(this);
        } catch (Throwable error) {
            initializationError = error;
            Log.e(TAG, "Failed to initialize download engine", error);
        }
    }

    public static Throwable getInitializationError() {
        return initializationError;
    }
}
