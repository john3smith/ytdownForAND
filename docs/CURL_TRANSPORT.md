# Browser transport runtime — v1.9.23-beta.1

This beta addresses the missing browser transport dependency. Python 3.14.8
alone did not provide it. The existing Java/FFmpeg library API remains at 0.18.1.

## Implementation

- curl_cffi 0.16.3, CFFI backend 2.1.1, libcurl-impersonate 2.2.2.
- Both Android ARM64 and x86_64 are cross-compiled with NDK r27c against the
  official CPython 3.14.8 headers. No unmerged third-party build is used.
- Official upstream Android libcurl release archives and PyPI source/wheels are
  SHA-256 pinned in `tools/prepare_curl_transport.py`.
- CFFI uses the original upstream Android libffi runtime and compatible official
  3.4.8 headers; Python imports, native calls and native callbacks are tested.
- Package metadata is retained because curl_cffi uses importlib.metadata at import.
- certifi's CA bundle and dependencies are included. Certificate and hostname
  verification stay enabled; age/login/CAPTCHA/authorization checks are not bypassed.
- No external proxy or automatic HTTPS-assist setting changes are added.
- Site login sessions and queues are not reset. Existing site label `***` is retained.
- Upstream third-party license texts accompany the embedded transport libraries.

## Build

Install host code-generation prerequisites into an isolated Python build environment:

```powershell
python -m pip install -r requirements-build.txt
gradle --no-daemon -PruntimePython="C:\path\build-python.exe" -PtargetAbi=x86_64 testDebugUnitTest assembleDebug
gradle --no-daemon -PruntimePython="C:\path\build-python.exe" -PtargetAbi=arm64-v8a assembleDebug lintDebug
python -m unittest discover -s tools -p "test_*.py" -v
```

Other prerequisites: JDK 17, SDK 36, NDK 27.2.12479018, Gradle 8.13.
Generated source/dependency downloads/native libraries/AARs remain in ignored build
and `.tools` directories. Do not commit credentials, cookies or APKs to Git history.

## Verified and remaining limitations (2026-10-10)

- Regular x86_64 Android Emulator: Python/CFFI/curl_cffi imports, FFI calls and
  callbacks, verified HTTPS request (HTTP 200), yt-dlp available browser targets.
- A self-signed HTTPS certificate was rejected with certificate verification error
  code 60; the browser transport did not disable TLS verification.
- Explicit curl transport downloaded an authorized public MP4 to completion:
  5,510,872 bytes, H.264/AAC, 640x360, 60.095 seconds. First-three-seconds decode
  and M4A extraction passed (495,197 bytes). These are engine-level smoke tests,
  not every app UI path or authenticated-site download.
- The prior failing site's saved-session metadata request still encountered
  connection reset. The missing-module warning is gone, but this is NOT a
  claim that restricted-site downloads work. Normal PC HTTPS to the same host
  also encountered a connection reset; network/site origin cannot be conclusively
  distinguished without a comparison on the user's normal Chrome/network.
- ARM64 build success does not imply an actual ARM64 phone was tested.
- The app retains original errors and now distinguishes connection reset,
  missing runtime, certificate errors, HTTP access denial and timeout.
- The actual app share/download flow was exercised on the previous failing link:
  it displayed the new reset explanation and remained a failed download (not success).
- App unit tests: 93 passing. Host packaging tests: 11 passing. Android lint:
  zero errors and 22 warnings. Both target APKs build; real ARM64 device not tested.

References: https://github.com/lexiforest/curl_cffi/tree/v0.16.3
and https://github.com/lexiforest/curl-impersonate/releases/tag/v2.2.2
