package com.local.ytdown;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Keep actionable FFmpeg failures, never signed media URLs or authentication. */
final class FfmpegFailureDetails {
    private static final Pattern URL = Pattern.compile("(?i)(?:https?|socks[45]?|file)://[^\\s\\\"'<>]+");
    private static final Pattern PRIVATE_PATH = Pattern.compile(
            "(?:/(?:data|storage|sdcard|tmp)/[^\\s\\\"'<>]+|[A-Za-z]:\\\\[^\\r\\n\\\"'<>]+)");
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(?:cookie|authorization|password|passwd|access_token|refresh_token|api_key|token|secret)\\s*[:=]");
    private FfmpegFailureDetails() { }

    static String summarize(String raw) {
        if (raw == null || raw.isEmpty()) return "FFmpeg 상세 오류가 없습니다.";
        Set<String> lines = new LinkedHashSet<>();
        for (String line : raw.split("[\\r\\n]+")) {
            // Redact URL before checking keys: signed query parameters are not logged.
            String clean = URL.matcher(line).replaceAll("[주소 비공개]");
            if (SECRET.matcher(clean).find()) continue;
            clean = PRIVATE_PATH.matcher(clean).replaceAll("[파일경로 비공개]");
            clean = clean.replaceAll("\\u001B\\[[0-?]*[ -/]*[@-~]", "").trim();
            String lower = clean.toLowerCase(Locale.ROOT);
            if (!(lower.contains("error") || lower.contains("failed") || lower.contains("invalid")
                    || lower.contains("server returned") || lower.contains("permission denied")
                    || lower.contains("no space left") || lower.contains("unrecognized option")
                    || lower.contains("option not found") || lower.contains("connection reset")
                    || lower.contains("connection refused") || lower.contains("timed out")
                    || lower.contains("ffmpeg exited") || lower.contains("certificate"))) continue;
            if (clean.length() > 400) clean = clean.substring(0, 397) + "...";
            lines.add(clean);
            if (lines.size() == 12) break;
        }
        return lines.isEmpty() ? "FFmpeg 상세 오류를 분류하지 못했습니다."
                : String.join("\n", lines);
    }
}
