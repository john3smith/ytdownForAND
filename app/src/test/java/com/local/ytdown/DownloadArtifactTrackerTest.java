package com.local.ytdown;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DownloadArtifactTrackerTest {
    @Test
    public void acceptsVideoContainersAndRejectsThumbnailImages() {
        assertTrue(DownloadArtifactTracker.isVideoFileName("clip.MP4"));
        assertTrue(DownloadArtifactTracker.isVideoFileName("clip.webm"));
        assertFalse(DownloadArtifactTracker.isVideoFileName("thumb.jpg"));
        assertFalse(DownloadArtifactTracker.isVideoFileName("thumb.webp"));
    }
}
