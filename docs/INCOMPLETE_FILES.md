# 미완료 파일 관리 · 1.9.27

## 구조

- `IncompleteFiles`: 명시적인 임시 파일 이름 판정, 경로/파일 상태 재검증. 영상 확장자·파일 크기·오래된 시각만으로 실패를 추정하지 않음.
- `IncompleteFileStore`: Download/YTDown, 앱 cache/stream-downloads 및 Android 10+ 자체 소유 MediaStore pending 항목 조회. 미디어 전체 디코딩·재귀 검색 없음.
- `IncompleteFilesDialog`: 작업 스레드에서 조회/삭제, 단일 팝업, 항목 삭제/현재 목록 전체 삭제의 확인창, 결과·접근 실패 표시.
- `MainActivity`: 관리 버튼 및 다운로드 작업과 파일 잠금 공유. 실행/검색/대기열 중 관리 접근 거부. 취소 후 다운로드 스레드가 실제 종료하기 전 파일 삭제 경쟁 방지.

삭제는 영구적입니다. 사용자가 확인한 목록 스냅샷에만 적용하며, 이후 새로 생긴 파일은 전체 삭제에 추가하지 않습니다. 원본 쿠키·설정·완료 파일은 대상이 아닙니다. Android의 앱 데이터 경로 별칭은 정규화하지만 파일 자체의 우회 경로와 하위 디렉터리는 제외합니다.

## 검증 · 2026-10-14

- JVM 149개 통과(미완료 파일 신규 7개 포함). 완료/빈 미디어 제외, partial 포함, 변경·이동·없는 파일 건너뜀, 디렉터리 밖 대상 보호.
- x86_64 및 ARM64 debug APK 빌드 성공. Android lint 오류 0, 경고 26(새 안내문 문자열 결합 경고 1개 포함).
- API35 emulator-5554에 정상 패키지 데이터 보존 설치, 버전 66/1.9.27 확인.
- 기존 미완료 파일을 삭제하지 않기 위해 별도 테스트 applicationId로 동일 코드를 설치. 합성 partial 3개와 완료 이름 sentinel 1개만 만들고, UI 조회 3개/138B → 개별 삭제 1개 → 전체 삭제 2개 → 목록 0개 및 전체 삭제 비활성 확인. 완료 이름 sentinel은 남음. 취소 후 파일 보존도 확인.
- 기존 `StreamProgressInstrumentation`으로 자체 생성 12초 영상의 실제 HTTPS/FFmpeg 다운로드 14,452 bytes, 중간 진행률 22개, 99% 후 100%, 알림 진행률, 최종 영상 트랙/12,000ms 확인. 사이트별 인증/접근 제한을 재시험한 것은 아님. 이 시험의 출력만 기존 runner가 정리함.
- 런타임 패키징 입력 스크립트 해시가 기존 검증 빌드와 일치함을 확인하고 ABI별 생성 AAR 재사용. 원본 외부 빌드 폴더는 수정하지 않음. 테스트 전용 패키지/인증서/fixture는 배포 APK에 포함하지 않음.

## 남은 검증 범위

실제 폰, 구형 Android 저장소 권한 및 제조사별 MediaStore pending 삭제는 미시험. pending 항목은 공식 API의 소유 앱·경로·상태·메타데이터 조건으로 제한하며 오류는 건너뜀/조회 실패로 표시합니다. 정상 미디어 확장자로 남은 불확실한 캐시와 OS가 접근을 막은 파일은 자동 삭제하지 않습니다.

참고: [Android 공유 미디어 접근](https://developer.android.com/training/data-storage/shared/media), [MediaStore API](https://developer.android.com/reference/android/provider/MediaStore).
