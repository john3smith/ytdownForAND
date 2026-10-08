package com.local.ytdown;

import java.io.File;

final class DownloadFormatSelector {
    private DownloadFormatSelector() {
    }

    static String forQualityPosition(int position) {
        switch (position) {
            case 1:
                return "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/"
                        + "best[height<=1080][ext=mp4][vcodec!=none]/"
                        + "best[height<=1080][vcodec!=none]";
            case 2:
                return "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/"
                        + "best[height<=720][ext=mp4][vcodec!=none]/"
                        + "best[height<=720][vcodec!=none]";
            default:
                return "bestvideo[ext=mp4]+bestaudio[ext=m4a]/"
                        + "best[ext=mp4][vcodec!=none]/best[vcodec!=none]";
        }
    }

    static String outputTemplate(File outputDirectory) {
        return outputTemplate(outputDirectory, false);
    }

    static String forMode(int position, boolean audioOnly) {
        return audioOnly ? "bestaudio[ext=m4a]/bestaudio/best[acodec!=none]"
                : forQualityPosition(position);
    }

    static String outputTemplate(File outputDirectory, boolean audioOnly) {
        return new File(outputDirectory,
                "%(title).165B [%(id)s]-%(autonumber)03d"
                        + (audioOnly ? "-audio" : "") + ".%(ext)s").getAbsolutePath();
    }
}
