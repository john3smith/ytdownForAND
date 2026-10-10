# Python 3.14.8 runtime (v1.9.22-beta.1)

This records the Python-only v1.9.22 change. v1.9.23 subsequently adds the
[browser transport runtime](CURL_TRANSPORT.md); its current dependencies and
test limitations are documented there.

This beta upgrades the app-private CPython runtime from 3.12 to **3.14.8**.
It does not install a system Python on Android. The Windows development Python
and Android app runtime are independent installations.

## Trusted inputs

- Official CPython 3.14.8 Android archives for ARM64 and x86_64:
  https://www.python.org/downloads/release/python-3148/
- Both SHA-256 values are pinned in `tools/prepare_python_runtime.py`.
- Base Java API: `io.github.junkfood02.youtubedl-android:library:0.18.1`.
  Its AAR SHA-256 is pinned; only its Python executable/payload are replaced.
- FFmpeg and the upstream Java API remain at 0.18.1. App login, queues and
  download settings are preserved by a normal APK update.
- No unmerged PR binaries, TLS verification exceptions, or remote proxies are added.

## Building

Use JDK 17, Android SDK 36, NDK **27.2.12479018**, Python 3.12+ and Gradle 8.13.
Set `ANDROID_NDK_HOME` if the NDK is installed outside this project's local SDK.
Set `-PruntimePython=<python executable>` if `python` is not available on PATH.

```powershell
gradle -PruntimePython="C:\path\python.exe" -PtargetAbi=arm64-v8a assembleDebug
gradle -PruntimePython="C:\path\python.exe" -PtargetAbi=x86_64 testDebugUnitTest assembleDebug
python -m unittest discover -s tools -p test_python_runtime.py -v
```

The packaging task downloads and verifies the official archive, builds the small
PIE launcher from `tools/python-launcher.c`, and creates a Python-only replacement
AAR in `build/python-runtime`. Headers, the CPython test suite, bytecode caches,
and generated AARs are not committed or packaged as source.

The launcher uses the official initialization API and leaves stdout/stderr on the
Java process pipes so download progress/error handling remains functional.
It reuses the upstream CA bundle path and does not disable HTTPS verification.
Upstream native support libraries needed by FFmpeg (including versioned expat,
zlib and lzma) are retained; official CPython libraries are never overwritten.
Old CPython 3.12 shared libraries and its version-specific extensions are excluded.

## Scope and limitations

This is a **Python upgrade**, not a claim that a previously failing site works.
`curl_cffi` is not yet included: official ARM64 wheels alone are not a complete
Android dependency set, and compatible CFFI/native dependencies still need their
own build and tests. No missing impersonation target is silently reported as working.

yt-dlp is separately updated using the existing stable-channel mechanism.
Device-installed yt-dlp versions can therefore differ from the bundled version.
Windows runtimes used by existing services and project virtual environments are
not switched or deleted as part of the development Python upgrade.

## Verification (2026-10-10)

- ARM64 APK and x86_64 emulator APK built; 88 app unit tests and 6 packaging tests passed.
- Android lint: zero errors (22 warnings remain).
- Installed the x86_64 update without clearing app data and ran the actual app-private
  interpreter: Python 3.14.8, OpenSSL 3.5.9, SQLite 3.53.4, subprocess and Cryptodome AES passed.
- The updated engine downloaded the public MediaElement Big Buck Bunny MP4 sample
  over HTTPS through completion (5,510,872 bytes, H.264/AAC, 60.095 seconds).
- FFprobe metadata, decoding the first three seconds, and AAC/M4A audio extraction
  passed (495,197-byte audio file). Tests used only app cache, not user media.
- This is an engine-level test, not a claim that every app UI path or authenticated
  website works. Site authentication/HTTP 403/impersonation issues remain outside
  the Python upgrade's verified scope.
