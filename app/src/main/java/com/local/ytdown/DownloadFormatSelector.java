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
        return new File(outputDirectory,
                "%(title).165B [%(id)s]-%(autonumber)03d.%(ext)s").getAbsolutePath();
    }
}
