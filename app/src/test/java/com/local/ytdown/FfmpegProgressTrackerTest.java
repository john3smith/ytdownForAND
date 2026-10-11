package com.local.ytdown;

import org.junit.Test;
import static org.junit.Assert.*;

public class FfmpegProgressTrackerTest {
    private FfmpegProgressTracker tracker(double seconds) {
        FfmpegProgressTracker tracker = new FfmpegProgressTracker();
        tracker.accept(FfmpegProgressTracker.DURATION_MARKER
                + "{\"duration\":" + seconds + ",\"is_live\":false}");
        return tracker;
    }
    @Test public void usesMediaTimeForPercentageAndFileBytesForSizeOnly() {
        FfmpegProgressTracker tracker = tracker(120);
        assertNull(tracker.accept("total_size=999999999"));
        assertNull(tracker.accept("out_time_us=30000000"));
        FfmpegProgressTracker.Snapshot snapshot = tracker.accept("progress=continue");
        assertEquals(25, snapshot.percent);
        assertEquals(30, snapshot.elapsedSeconds);
        assertEquals(120, snapshot.durationSeconds);
        assertEquals(999999999, snapshot.bytes);
    }
    @Test public void legacyOutTimeMsIsMicroseconds() {
        FfmpegProgressTracker tracker = tracker(100);
        tracker.accept("out_time_ms=50000000");
        assertEquals(50, tracker.accept("progress=continue").percent);
    }
    @Test public void clockFallbackAndFractionalDurationsWork() {
        FfmpegProgressTracker tracker = tracker(120.5);
        tracker.accept("out_time=00:01:00.250000");
        assertEquals(50, tracker.accept("progress=continue").percent);
    }
    @Test public void unknownDurationStillReportsTimeAndSizeNotFakePercent() {
        FfmpegProgressTracker tracker = new FfmpegProgressTracker();
        tracker.accept("YTDownDuration={\"duration\":\"NA\",\"is_live\":\"NA\"}");
        tracker.accept("out_time_us=20000000");
        tracker.accept("total_size=1000");
        FfmpegProgressTracker.Snapshot snapshot = tracker.accept("progress=continue");
        assertEquals(-1, snapshot.percent);
        assertEquals(20, snapshot.elapsedSeconds);
        assertEquals(1000, snapshot.bytes);
    }
    @Test public void liveStreamDoesNotClaimCompletionEvenWithDuration() {
        FfmpegProgressTracker tracker = new FfmpegProgressTracker();
        tracker.accept("YTDownDuration={\"duration\":12,\"is_live\":true}");
        tracker.accept("out_time_us=12000000");
        assertEquals(-1, tracker.accept("progress=end").percent);
    }
    @Test public void completionIsReservedForVerifiedArtifact() {
        FfmpegProgressTracker tracker = tracker(10);
        tracker.accept("out_time_us=15000000");
        assertEquals(99, tracker.accept("progress=end").percent);
    }
    @Test public void unrelatedLinesAreNotProgressAndDoNotResetTimeline() {
        FfmpegProgressTracker tracker = tracker(100);
        tracker.accept("out_time_us=60000000");
        assertEquals(60, tracker.accept("progress=continue").percent);
        for (String line : new String[]{null, "", "[download] Destination: private.mp4",
                "https://example.org/?token=private", "YTDownTitle=\"title\""}) {
            assertFalse(FfmpegProgressTracker.isProtocolLine(line));
            assertNull(tracker.accept(line));
        }
        tracker.accept("out_time_us=59000000");
        assertEquals(60, tracker.accept("progress=continue").percent);
    }
    @Test public void resetsBetweenPlaylistItemsAndMalformedMetadata() {
        FfmpegProgressTracker tracker = tracker(100);
        tracker.accept("out_time_us=50000000");
        tracker.accept("total_size=12000");
        assertEquals(50, tracker.accept("progress=end").percent);
        FfmpegProgressTracker.Snapshot next = tracker.accept("YTDownDuration={\"duration\":200}");
        assertEquals(0, next.percent);
        assertEquals(0, next.bytes);
        tracker.accept("out_time_us=20000000");
        assertEquals(10, tracker.accept("progress=continue").percent);
        assertEquals(-1, tracker.accept("YTDownDuration=not json").percent);
    }
    @Test public void malformedFieldsNeverCrashOrProduceNegativeValues() {
        FfmpegProgressTracker tracker = tracker(10);
        for (String line : new String[]{"out_time_us=N/A", "out_time_ms=-1",
                "out_time_us=999999999999999999999", "out_time=99:99:99.9",
                "total_size=-100", "total_size=N/A", "speed=N/A", "progress=invalid"}) {
            assertNull(tracker.accept(line));
        }
        assertEquals(0, tracker.accept("progress=continue").percent);
        assertEquals(0, tracker.accept("progress=end").bytes);
    }
    @Test public void ignoresUnboundedOrNonNumericDuration() {
        for (String duration : new String[]{"null", "-3", "0", "1e99", "\"12\""}) {
            FfmpegProgressTracker tracker = new FfmpegProgressTracker();
            assertEquals(-1, tracker.accept("YTDownDuration={\"duration\":" + duration + "}").percent);
        }
    }
    @Test public void ignoresLongLinesAndUsesNumericTimeOverClockInSameBlock() {
        assertFalse(FfmpegProgressTracker.isProtocolLine("out_time_us=" + "1".repeat(8192)));
        FfmpegProgressTracker tracker = tracker(100);
        tracker.accept("out_time_us=40000000");
        tracker.accept("out_time=00:00:50.000000");
        assertEquals(40, tracker.accept("progress=continue").percent);
        tracker.accept("out_time=00:00:50.000000");
        assertEquals(50, tracker.accept("progress=continue").percent);
    }
}
