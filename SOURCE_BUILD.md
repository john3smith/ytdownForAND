# Source build prerequisites

The original build-apk.cmd expects local .tools installations, which are not
included in GitHub. Install JDK 17, Android SDK 36, NDK 27.2.12479018,
Python 3.12+ and Gradle 8.13 on the target
machine. Set JAVA_HOME and ANDROID_HOME, or configure Android Studio's SDK path.
Set ANDROID_NDK_HOME if the NDK is outside `.tools/android-sdk/ndk/27.2.12479018`.
From the project root, run Gradle 8.13:

```powershell
gradle --no-daemon -PtargetAbi=arm64-v8a -PruntimePython="C:\path\python.exe" assembleDebug
gradle --no-daemon -PtargetAbi=x86_64 -PruntimePython="C:\path\python.exe" testDebugUnitTest assembleDebug
python -m unittest discover -s tools -p test_python_runtime.py -v
```

The build verifies official CPython 3.14.8 Android archives and compiles the native
launcher. See [runtime details](docs/PYTHON_RUNTIME.md). Do not commit generated AARs.
APK: app/build/outputs/apk/debug/app-debug.apk.

Unlike the other Android snapshots, the original project has no Gradle wrapper.
No wrapper, tool distribution, existing device cookies or downloaded media has
been invented or copied into this source backup.
