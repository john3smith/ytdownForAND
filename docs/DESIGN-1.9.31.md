# YTDown 1.9.31 design refresh and validation

2026-10-23. Version code 70. Android YTDown; the Windows YTDownPC project is unchanged.

## Result

The link field, selected media-mode summary and primary download action appear before account and management controls. Optional quality/audio/automatic/Wi-Fi/HTTPS controls expand in place. Progress, account connection and management use separate white cards on a soft background, with consistent blue buttons and a matching locally authored vector launcher icon. The scrolling viewport is inset below system bars, including after scrolling and with larger fonts.

Existing controls and their download, queue, cancellation, account, cookie deletion, privacy/notice and TLS handlers are retained. The audio-mode callback preserves its original stored-setting and quality-enabled behavior while refreshing the visible summary. Existing preference values and defaults are unchanged. No dependency, permission, external image, font or third-party design asset was added.

## Validation

- 155 JVM tests passed, 0 failures/errors/skips. Lint: 0 errors and 24 warnings. Existing runtime-build tools: 11 tests passed.
- Regular API35 emulator with an isolated `.designpreview` package: inspected default, expanded options, M4A summary, account/management areas, and 150% text. The original overlapping system-bar scroll case was corrected and visually rechecked. Font scale was restored to 1.0. Production credentials, cookies and downloaded user media were not used for these checks.
- Existing native fixture downloaded an owned synthetic MP4 over verified loopback HTTPS, published it through the real storage path and decoded its video metadata. Result: 14,452 bytes, 12,000ms duration, valid 320px video track, 22 intermediate progress samples. UI stayed at 99% during finalization and reached 100% after success; unknown-duration and stopped-spinner cases passed. The fixture removed only its own new published file. Preview notification permission was not granted, so notification parity was not proven.
- ARM64 production and x86_64 emulator release builds succeeded. Independent signature/alignment/manifest verification passed at API24/26/28/32/33/35/36. The production package updated the regular emulator to code70 without uninstall/data clearing. The emulator-only x86 APK is not a public delivery asset.
- Runtime inputs remain pinned to the same source/version/checksum manifest as the previous release configuration: CPython 3.14.8, youtubedl-android 0.18.1, CFFI 2.1.1, curl_cffi 0.16.3 and libcurl package 2.2.2. Native payloads were rebuilt from those inputs; byte-for-byte native reproducibility is not claimed. Existing Jackson/Commons pins are retained.
- Offline `store-notices.txt` and `store-privacy.txt` are byte-identical to the preceding local release. APK audit excludes signing material, fixture credentials, user databases and instrumentation classes. Existing third-party, service, store and commercial-review constraints remain unchanged; this UI update is not store submission or new commercial-rights approval.
- No physical phone, real signed-in site download, private-content access or live-site authentication test was performed. The native download evidence is an owned fixture, not proof that every supported external site works.

## Delivery

`YTDown-v1.9.31-arm64-v8a.apk`, 78,871,277 bytes, non-debuggable, existing production signing lineage.

SHA-256: `d93e9f745167e9f1a7c49d91715216bbb4d9c937b51355742201e1d1f112b3ec`.

Publish this file unchanged as a versioned GitHub Release after the required PR checks. Re-read the official relay queue before publication; unrelated queued AutoExpense work is not a YTDown modification. Raw Telegram attachment is blocked by this file exceeding the current configured cloud Bot API's 50MB `sendDocument` limit, verified against the [official documentation](https://core.telegram.org/bots/api#senddocument). Do not substitute a ZIP/split archive, change the live relay or replay historical APK delivery.
