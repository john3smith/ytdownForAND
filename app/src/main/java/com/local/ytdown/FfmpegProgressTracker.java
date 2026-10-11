package com.local.ytdown;

import org.json.JSONObject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** FFmpeg's machine-readable stdout, not stderr/URLs or guessed file sizes. */
final class FfmpegProgressTracker {
    static final String DURATION_MARKER = "YTDownDuration=";
    static final String DURATION_TEMPLATE = "before_dl:" + DURATION_MARKER
            + "{\"duration\":%(duration)j,\"is_live\":%(is_live)j}";
    private static final Pattern CLOCK = Pattern.compile("(\\d{1,8}):(\\d{2}):(\\d{2}(?:\\.\\d{1,6})?)");
    private double durationSeconds;
    private long elapsedMicros;
    private long bytes;
    private boolean blockHasMicros;

    static final class Snapshot {
        final int percent; // -1 means duration is not known; 100 is reserved for verified completion.
        final long elapsedSeconds;
        final long durationSeconds;
        final long bytes;
        Snapshot(int percent, long elapsedSeconds, long durationSeconds, long bytes) {
            this.percent = percent;
            this.elapsedSeconds = elapsedSeconds;
            this.durationSeconds = durationSeconds;
            this.bytes = bytes;
        }
    }

    static boolean isProtocolLine(String line) {
        if (line == null || line.length() > 8192) return false;
        if (line.startsWith(DURATION_MARKER)) return true;
        int separator = line.indexOf('=');
        if (separator < 0) return false;
        switch (line.substring(0, separator)) {
            case "frame": case "fps": case "bitrate": case "total_size":
            case "out_time_us": case "out_time_ms": case "out_time":
            case "dup_frames": case "drop_frames": case "speed": case "progress":
                return true;
            default: return line.substring(0, separator).matches("stream_\\d+_\\d+_q");
        }
    }

    Snapshot accept(String line) {
        if (!isProtocolLine(line)) return null;
        if (line.startsWith(DURATION_MARKER)) {
            // Every playlist item starts a fresh timeline, including missing/malformed metadata.
            durationSeconds = 0;
            elapsedMicros = bytes = 0;
            blockHasMicros = false;
            try {
                JSONObject metadata = new JSONObject(line.substring(DURATION_MARKER.length()));
                Object duration = metadata.opt("duration");
                if (duration instanceof Number && !metadata.optBoolean("is_live", false)) {
                    double value = ((Number) duration).doubleValue();
                    if (Double.isFinite(value) && value > 0 && value <= 31_536_000) {
                        durationSeconds = value;
                    }
                }
            } catch (Exception invalid) { /* no fabricated percentage */ }
            return snapshot();
        }
        int separator = line.indexOf('=');
        String key = line.substring(0, separator);
        String value = line.substring(separator + 1).trim();
        switch (key) {
            case "out_time_us":
            case "out_time_ms": // FFmpeg's legacy field is also microseconds, not milliseconds.
                Long micros = nonnegativeLong(value);
                if (micros != null) {
                    elapsedMicros = Math.max(elapsedMicros, micros);
                    blockHasMicros = true;
                }
                break;
            case "out_time":
                if (!blockHasMicros) {
                    Matcher clock = CLOCK.matcher(value);
                    if (clock.matches()) {
                        double seconds = Long.parseLong(clock.group(1)) * 3600d
                                + Integer.parseInt(clock.group(2)) * 60d
                                + Double.parseDouble(clock.group(3));
                        if (Integer.parseInt(clock.group(2)) < 60
                                && Double.parseDouble(clock.group(3)) < 60) {
                            elapsedMicros = Math.max(elapsedMicros, (long) (seconds * 1_000_000));
                        }
                    }
                }
                break;
            case "total_size":
                Long size = nonnegativeLong(value);
                if (size != null) bytes = Math.max(bytes, size);
                break;
            case "progress":
                if ("continue".equals(value) || "end".equals(value)) {
                    blockHasMicros = false;
                    return snapshot();
                }
                break;
            default: break;
        }
        return null;
    }

    private Snapshot snapshot() {
        int percent = durationSeconds > 0
                ? (int) Math.min(99, Math.floor(elapsedMicros / 10_000d / durationSeconds)) : -1;
        return new Snapshot(percent, elapsedMicros / 1_000_000,
                (long) Math.ceil(durationSeconds), bytes);
    }

    private static Long nonnegativeLong(String value) {
        try { long parsed = Long.parseLong(value); return parsed >= 0 ? parsed : null; }
        catch (NumberFormatException invalid) { return null; }
    }
}
