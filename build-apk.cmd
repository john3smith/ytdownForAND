@echo off
setlocal
cd /d "%~dp0"
set "JAVA_HOME=%~dp0.tools\jdk-17.0.20.1+1"
set "ANDROID_HOME=%~dp0.tools\android-sdk"
set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%"

call ".tools\gradle-8.13\bin\gradle.bat" --no-daemon assembleRelease securityRuntimeInventory
if errorlevel 1 exit /b %errorlevel%

echo.
echo UNSIGNED build input: %~dp0app\build\outputs\apk\release\app-release-unsigned.apk
echo Do not install or distribute. Follow docs\RELEASE_SECURITY.md for signing and verification.
endlocal
