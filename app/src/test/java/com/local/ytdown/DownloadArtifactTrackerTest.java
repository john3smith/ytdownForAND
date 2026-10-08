package com.local.ytdown;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.json.JSONObject;
import java.io.File;
import java.nio.file.Files;

public class DownloadArtifactTrackerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test
    public void acceptsVideoContainersAndRejectsThumbnailImages() {
        assertTrue(DownloadArtifactTracker.isVideoFileName("clip.MP4"));
        assertTrue(DownloadArtifactTracker.isVideoFileName("clip.webm"));
        assertFalse(DownloadArtifactTracker.isVideoFileName("thumb.jpg"));
        assertFalse(DownloadArtifactTracker.isVideoFileName("thumb.webp"));
    }

    @Test
    public void acceptsFinalAudioIncludingUnchangedRepeatedDownloadOnlyOnce() throws Exception {
        File directory = temporary.newFolder("output");
        File audio = new File(directory, "한글 제목 [ID]-001-audio.m4a");
        Files.write(audio.toPath(), new byte[]{1, 2, 3});
        String line = DownloadArtifactTracker.AUDIO_PATH_MARKER + JSONObject.quote(audio.getAbsolutePath());
        assertEquals(1, DownloadArtifactTracker.completedAudioFiles(directory, line + "\n" + line).size());
        assertEquals(audio.getCanonicalFile(), DownloadArtifactTracker.completedAudioFiles(directory, line).get(0));
        assertEquals(1, DownloadArtifactTracker.completedAudioFiles(directory, line).size());
        assertEquals(1, DownloadArtifactTracker.completedAudioFiles(directory,
                "[download] 100%\r  " + line + "\r").size());
    }

    @Test
    public void rejectsThumbnailIntermediateEmptyAndOutsideDirectory() throws Exception {
        File directory = temporary.newFolder("output");
        for (String name : new String[]{"cover.jpg", "source-audio.mp4", "incomplete-audio.m4a.part", "normal.m4a"}) {
            File file = new File(directory, name);
            Files.write(file.toPath(), new byte[]{1});
            assertTrue(DownloadArtifactTracker.completedAudioFiles(directory,
                    DownloadArtifactTracker.AUDIO_PATH_MARKER + JSONObject.quote(file.getAbsolutePath())).isEmpty());
        }
        File empty = new File(directory, "empty-audio.m4a");
        assertTrue(empty.createNewFile());
        assertTrue(DownloadArtifactTracker.completedAudioFiles(directory,
                DownloadArtifactTracker.AUDIO_PATH_MARKER + JSONObject.quote(empty.getAbsolutePath())).isEmpty());
        File outside = temporary.newFile("outside-audio.m4a");
        Files.write(outside.toPath(), new byte[]{1});
        assertTrue(DownloadArtifactTracker.completedAudioFiles(directory,
                DownloadArtifactTracker.AUDIO_PATH_MARKER + JSONObject.quote(outside.getAbsolutePath())).isEmpty());
    }

    @Test
    public void rejectsMissingOrMalformedCompletionOutput() throws Exception {
        File directory = temporary.newFolder("output");
        assertTrue(DownloadArtifactTracker.completedAudioFiles(directory, null).isEmpty());
        assertTrue(DownloadArtifactTracker.completedAudioFiles(directory, "already been downloaded").isEmpty());
        assertTrue(DownloadArtifactTracker.completedAudioFiles(directory, "YTDownAudio={}").isEmpty());
        assertTrue(DownloadArtifactTracker.completedAudioFiles(directory, "YTDownAudio=\"broken").isEmpty());
    }
}
