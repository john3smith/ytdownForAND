@echo off
setlocal
cd /d "%~dp0"
set "JAVA_HOME=%~dp0.tools\jdk-17.0.20.1+1"
set "ANDROID_HOME=%~dp0.tools\android-sdk"
set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%"

call ".tools\gradle-8.13\bin\gradle.bat" --no-daemon assembleRelease securityRuntimeInventory
if errorlevel 1 exit /b %errorlevel%

copy /y "app\build\outputs\apk\release\app-release.apk" "YTDown-v1.9.29-arm64-release-debugkey.apk" >nul
echo.
echo APK: %~dp0YTDown-v1.9.29-arm64-release-debugkey.apk
endlocal
