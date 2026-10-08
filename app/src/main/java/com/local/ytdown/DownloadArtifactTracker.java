package com.local.ytdown;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONTokener;

final class DownloadArtifactTracker {
    static final String AUDIO_PATH_MARKER = "YTDownAudio=";
    private DownloadArtifactTracker() {
    }

    static Map<String, String> snapshot(File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            return Collections.emptyMap();
        }
        Map<String, String> snapshot = new HashMap<>();
        for (File file : files) {
            if (file.isFile()) {
                snapshot.put(file.getAbsolutePath(), file.length() + ":" + file.lastModified());
            }
        }
        return snapshot;
    }

    static List<File> changedVideos(File directory, Map<String, String> before) {
        File[] files = directory.listFiles();
        if (files == null) {
            return Collections.emptyList();
        }
        List<File> changed = new ArrayList<>();
        for (File file : files) {
            String oldStamp = before.get(file.getAbsolutePath());
            String newStamp = file.length() + ":" + file.lastModified();
            if (file.isFile() && isVideoFileName(file.getName())
                    && file.length() > 0 && !newStamp.equals(oldStamp)) {
                changed.add(file);
            }
        }
        return changed;
    }

    static boolean isVideoFileName(String name) {
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".mp4") || lower.endsWith(".mkv")
                || lower.endsWith(".webm") || lower.endsWith(".mov")
                || lower.endsWith(".m4v") || lower.endsWith(".avi")
                || lower.endsWith(".ts");
    }

    /** Validate final post-processed paths, including a repeated download.
     * A success message or a thumbnail/intermediate MP4 is not an audio artifact. */
    static List<File> completedAudioFiles(File directory, String output) {
        List<File> completed = new ArrayList<>();
        if (output == null) return completed;
        try {
            File parent = directory.getCanonicalFile();
            for (String line : output.split("[\\r\\n]+")) {
                line = line.trim();
                if (!line.startsWith(AUDIO_PATH_MARKER)) continue;
                try {
                    Object decoded = new JSONTokener(line.substring(AUDIO_PATH_MARKER.length())).nextValue();
                    if (!(decoded instanceof String)) continue;
                    File file = new File((String) decoded).getCanonicalFile();
                    if (parent.equals(file.getParentFile()) && file.isFile()
                            && file.length() > 0 && file.getName().endsWith("-audio.m4a")
                            && !completed.contains(file)) {
                        completed.add(file);
                    }
                } catch (Exception ignored) {
                    // Malformed stdout cannot manufacture a successful result.
                }
            }
        } catch (IOException ignored) {
            return Collections.emptyList();
        }
        return completed;
    }
}
