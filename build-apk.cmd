@echo off
setlocal
cd /d "%~dp0"
set "JAVA_HOME=%~dp0.tools\jdk-17.0.20.1+1"
set "ANDROID_HOME=%~dp0.tools\android-sdk"
set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%"

call ".tools\gradle-8.13\bin\gradle.bat" --no-daemon clean assembleDebug
if errorlevel 1 exit /b %errorlevel%

copy /y "app\build\outputs\apk\debug\app-debug.apk" "YTDown-v1.9.20-arm64-debug.apk" >nul
echo.
echo APK: %~dp0YTDown-v1.9.20-arm64-debug.apk
endlocal
