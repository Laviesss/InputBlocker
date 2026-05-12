@echo off
setlocal enabledelayedexpansion

echo ============================================================
echo  InputBlocker Android APK Builder (Windows)
echo ============================================================

set "SCRIPT_DIR=%~dp0"
set "ANDROID_APP_DIR=%SCRIPT_DIR%..\android-app"
set "OUTPUT_DIR=%SCRIPT_DIR%..\releases"

if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

cd /d "%ANDROID_APP_DIR%"

if not exist "gradlew.bat" (
    echo ERROR: gradlew.bat not found!
    exit /b 1
)

set "BUILD_TYPE=debug"
if "%1"=="release" set "BUILD_TYPE=release"

echo Build type: %BUILD_TYPE%

if "%BUILD_TYPE%"=="release" (
    call gradlew assembleRelease
    set "APK_OUTPUT=app\build\outputs\apk\release\app-release.apk"
) else (
    call gradlew assembleDebug
    set "APK_OUTPUT=app\build\outputs\apk\debug\app-debug.apk"
)

if not exist "%APK_OUTPUT%" (
    echo ERROR: APK not found at %APK_OUTPUT%
    exit /b 1
)

echo APK built successfully!
copy /y "%APK_OUTPUT%" "%OUTPUT_DIR%\InputBlocker-%BUILD_TYPE%.apk"
echo Copied to: %OUTPUT_DIR%\InputBlocker-%BUILD_TYPE%.apk
pause
