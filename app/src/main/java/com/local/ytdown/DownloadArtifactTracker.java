package com.local.ytdown;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DownloadArtifactTracker {
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
}
