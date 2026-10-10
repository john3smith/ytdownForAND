package com.local.ytdown;

import java.io.File;
import java.io.IOException;
import java.util.function.BiConsumer;

/** Selects the bundled downloader without disabling HTTPS verification. */
final class FfmpegStreamDownloadOptions {
    private FfmpegStreamDownloadOptions() { }

    static boolean apply(boolean singlePass, boolean audioOnly, File executable,
                         File caBundle, BiConsumer<String, String> option) throws IOException {
        if (!singlePass || audioOnly) return false;
        requireFile(executable);
        requireFile(caBundle);
        String arguments = "ffmpeg_i:-tls_verify 1 -ca_file "
                + quoteArgument(caBundle.getAbsolutePath()) + " -rw_timeout 30000000";
        option.accept("--downloader", "ffmpeg");
        option.accept("--ffmpeg-location", executable.getAbsolutePath());
        option.accept("--downloader-args", arguments);
        return true;
    }

    private static void requireFile(File file) throws IOException {
        if (file == null || !file.isFile() || file.length() == 0) {
            throw new IOException("FFmpeg streaming dependency unavailable");
        }
        quoteArgument(file.getAbsolutePath());
    }

    // yt-dlp parses downloader-args with shlex, not a shell.
    static String quoteArgument(String value) throws IOException {
        if (value == null || value.indexOf('\0') >= 0 || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0) throw new IOException("Invalid FFmpeg argument");
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
