package com.local.ytdown;

import static org.junit.Assert.*;
import org.junit.Test;

public class FfmpegFailureDetailsTest {
    @Test public void retainsRootCauseWithoutSignedAddress() {
        String result = FfmpegFailureDetails.summarize("[https @ 123] HTTP error 403 Forbidden\n"
                + "Error opening input https://cdn.example/video.mp4?token=private-secret\n"
                + "ERROR: ffmpeg exited with code 8");
        assertTrue(result.contains("403 Forbidden"));
        assertTrue(result.contains("code 8"));
        assertFalse(result.contains("cdn.example"));
        assertFalse(result.contains("private-secret"));
    }
    @Test public void suppressesCookiesAndAuthorization() {
        String result = FfmpegFailureDetails.summarize("ERROR Cookie: session=private-cookie\n"
                + "ERROR Authorization: Bearer private-bearer\n"
                + "ERROR password=private-password\nERROR access_token=private-token\n"
                + "Error opening output /data/user/0/app/cache/private-title.mp4: Permission denied");
        assertFalse(result.contains("private-"));
        assertFalse(result.contains("/data/"));
        assertTrue(result.contains("Permission denied"));
    }
    @Test public void ignoresBannerProgressAndTitleMetadata() {
        assertEquals("FFmpeg 상세 오류를 분류하지 못했습니다.",
                FfmpegFailureDetails.summarize("Metadata:\n title: private-title\nframe=20 time=00:00:02"));
        assertEquals("FFmpeg 상세 오류가 없습니다.", FfmpegFailureDetails.summarize(null));
    }
    @Test public void boundsAndDeduplicatesErrorLines() {
        StringBuilder lines = new StringBuilder("ERROR: repeated\nERROR: repeated\n");
        for (int i = 0; i < 100; i++) lines.append("Error number ").append(i).append('\n');
        assertEquals(12, FfmpegFailureDetails.summarize(lines.toString()).split("\n").length);
    }
    @Test public void preservesCertificateFailure() {
        assertTrue(FfmpegFailureDetails.summarize("[tls] certificate verify failed\nERROR: ffmpeg exited with code 8")
                .contains("certificate verify failed"));
    }
}
