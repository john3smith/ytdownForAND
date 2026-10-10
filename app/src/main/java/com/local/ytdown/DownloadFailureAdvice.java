package com.local.ytdown;

import java.util.Locale;

/** Explain transport failures without hiding the original log or bypassing access checks. */
final class DownloadFailureAdvice {
    private DownloadFailureAdvice() { }

    static String forError(String message) {
        if (message == null || message.isEmpty()) return null;
        String error = message.toLowerCase(Locale.ROOT);
        if (error.contains("certificate verify failed") || error.contains("certificate_verify_failed")
                || error.contains("ssl certificate problem")) {
            return "서버 인증서 검증에 실패했습니다. 기기 날짜·시간과 네트워크를 확인해 주세요. 인증서 검증은 끄지 않습니다.";
        }
        if (error.contains("no impersonate target is available")
                || error.contains("curl_cffi is not installed")) {
            return "브라우저 통신 모듈을 불러오지 못했습니다. 최신 APK를 설치해 주세요. PC에만 패키지를 설치해도 앱에는 적용되지 않습니다.";
        }
        if (error.contains("connection reset") || error.contains("reset by peer")
                || error.contains("winerror 10054") || error.contains("remote end closed connection")) {
            return "네트워크 또는 사이트가 연결을 강제로 종료했습니다. 같은 링크가 일반 Chrome에서 열리는지 확인해 주세요. 이 오류만으로 로그인 실패라고 판단하지 않습니다.";
        }
        if (error.contains("http error 403") || error.contains("http error 401")) {
            return "사이트가 요청을 거절했습니다. 일반 Chrome에서 접근 가능한지와 앱에 저장된 로그인 상태를 확인해 주세요. 접근 제한을 자동으로 우회하지 않습니다.";
        }
        if (error.contains("http error 404")) {
            return "해당 링크를 찾을 수 없습니다. 삭제되었거나 주소가 바뀌었는지 확인해 주세요.";
        }
        if (error.contains("timed out") || error.contains("timeout")) {
            return "서버 응답 시간이 초과됐습니다. 인터넷 연결 및 같은 링크의 일반 Chrome 접속 여부를 확인해 주세요.";
        }
        return null;
    }
}
