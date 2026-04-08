@echo off
REM ============================================================
REM InputBlocker - Build Android APK
REM ============================================================

set "PROJECT_ROOT=%~dp0.."
cd /d "%PROJECT_ROOT%"

set "OUT=%PROJECT_ROOT%\releases"

echo.
echo ============================================================
echo  Building APK
echo ============================================================
echo.

cd /d "%PROJECT_ROOT%\android-app"

if not exist "gradlew.bat" (
    echo ERROR: gradlew.bat not found
    pause
    exit /b 1
)

set "BUILD_TYPE=debug"
if "%~1"=="release" set "BUILD_TYPE=release"

echo Build type: %BUILD_TYPE%
echo.

if "%BUILD_TYPE%"=="release" (
    call gradlew.bat assembleRelease --no-daemon
    if not errorlevel 1 (
        set "APK=app\build\outputs\apk\release\app-release.apk"
    ) else (
        echo Release failed, trying debug...
        call gradlew.bat assembleDebug --no-daemon
        set "APK=app\build\outputs\apk\debug\app-debug.apk"
    )
) else (
    call gradlew.bat assembleDebug --no-daemon
    set "APK=app\build\outputs\apk\debug\app-debug.apk"
)

if not exist "%APK%" (
    echo ERROR: APK not found
    pause
    exit /b 1
)

copy /Y "%APK%" "%OUT%\InputBlocker.apk" >nul
echo Done: %OUT%\InputBlocker.apk

echo.
echo ============================================================
echo  APK Build Complete
echo ============================================================
echo.
pause
