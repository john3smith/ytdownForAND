package com.local.ytdown;

import org.junit.Test;
import static org.junit.Assert.*;

public class DownloadFailureAdviceTest {
    @Test public void distinguishesResetFromMissingTransport() {
        String reset = DownloadFailureAdvice.forError("Unable to download webpage: Connection reset by peer");
        assertTrue(reset.contains("연결을 강제로 종료"));
        assertTrue(reset.contains("로그인 실패라고 판단하지"));
        assertFalse(reset.contains("최신 APK"));
        assertTrue(DownloadFailureAdvice.forError("no impersonate target is available").contains("최신 APK"));
    }

    @Test public void certificateSafetyHasPriority() {
        assertTrue(DownloadFailureAdvice.forError("SSL certificate problem: certificate verify failed, timeout")
                .contains("검증은 끄지"));
    }

    @Test public void explainsAccessDenialWithoutAutomaticBypass() {
        assertTrue(DownloadFailureAdvice.forError("HTTP Error 403: Forbidden").contains("우회하지"));
        assertTrue(DownloadFailureAdvice.forError("HTTP Error 401: Unauthorized").contains("로그인"));
    }

    @Test public void explainsMissingLinkAndTimeout() {
        assertTrue(DownloadFailureAdvice.forError("HTTP Error 404: Not Found").contains("주소"));
        assertTrue(DownloadFailureAdvice.forError("Request timed out").contains("응답 시간"));
    }

    @Test public void unknownFailuresKeepExistingDetails() {
        assertNull(DownloadFailureAdvice.forError(null));
        assertNull(DownloadFailureAdvice.forError(""));
        assertNull(DownloadFailureAdvice.forError("No video formats found"));
    }
}
