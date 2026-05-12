@echo off
setlocal enabledelayedexpansion

echo ============================================================
echo  InputBlocker Module Builder (Windows)
echo ============================================================

set "SCRIPT_DIR=%~dp0"
set "ANDROID_APP_DIR=%SCRIPT_DIR%..\android-app"
set "MODULE_DIR=%SCRIPT_DIR%..\module"
set "OUTPUT_DIR=%SCRIPT_DIR%..\releases"

if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

set "BUILD_MODE=full"
if "%1"=="lite" set "BUILD_MODE=lite"

echo Mode: %BUILD_MODE%

if "%BUILD_MODE%"=="full" (
    echo Building Android APK...
    cd /d "%ANDROID_APP_DIR%"
    if not exist "gradlew.bat" (
        echo ERROR: gradlew.bat not found!
        exit /b 1
    )
    call gradlew assembleRelease
    if errorlevel 1 (
        echo Failed release build. Trying debug...
        call gradlew assembleDebug
        if errorlevel 1 (
            echo ERROR: Failed to build APK.
            exit /b 1
        )
        set "APK_PATH=app\build\outputs\apk\debug\app-debug.apk"
    ) else (
        set "APK_PATH=app\build\outputs\apk\release\app-release.apk"
    )
    
    if not exist "%APK_PATH%" (
        echo ERROR: APK not found!
        exit /b 1
    )
    
    echo APK built successfully!
    mkdir "%MODULE_DIR%\common" 2>nul
    copy /y "%APK_PATH%" "%MODULE_DIR%\common\InputBlocker.apk"
)

echo Packaging module...
cd /d "%SCRIPT_DIR%.."
powershell -Command "Compress-Archive -Path 'module\*' -DestinationPath 'build-scripts\releases\InputBlocker.zip' -Force"
echo Module ZIP created in releases folder.
pause
