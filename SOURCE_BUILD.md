# Source build prerequisites

The original build-apk.cmd expects local .tools installations, which are not
included in GitHub. Install JDK 17, Android SDK 36 and Gradle 8.13 on the target
machine. Set JAVA_HOME and ANDROID_HOME, or configure Android Studio's SDK path.
From the project root, run Gradle 8.13: `gradle --no-daemon assembleDebug`.
APK: app/build/outputs/apk/debug/app-debug.apk.

Unlike the other Android snapshots, the original project has no Gradle wrapper.
No wrapper, tool distribution, existing device cookies or downloaded media has
been invented or copied into this source backup.
