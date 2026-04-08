@echo off
REM ============================================================
REM InputBlocker - Build Magisk Module
REM ============================================================
REM
REM Options:
REM   build.bat      - Build full module with APK
REM   build.bat lite - Build lite module without APK

set "PROJECT_ROOT=%~dp0.."
cd /d "%PROJECT_ROOT%"

set "OUT=%PROJECT_ROOT%\releases"
set "BUILD_MODE=%~1"
if "%BUILD_MODE%"=="" set "BUILD_MODE=full"

echo.
echo ============================================================
echo  Building Module
echo ============================================================
echo Mode: %BUILD_MODE%
echo.

if not exist "%PROJECT_ROOT%\magisk-module\common" mkdir "%PROJECT_ROOT%\magisk-module\common"

if "%BUILD_MODE%"=="full" (
    if exist "%OUT%\InputBlocker.apk" (
        copy /Y "%OUT%\InputBlocker.apk" "%PROJECT_ROOT%\magisk-module\common\InputBlocker.apk" >nul
        echo APK included
    ) else (
        echo WARNING: APK not found in releases
    )
) else (
    if exist "%PROJECT_ROOT%\magisk-module\common\InputBlocker.apk" (
        del "%PROJECT_ROOT%\magisk-module\common\InputBlocker.apk" 2>nul
    )
)

echo Creating module zip...

if "%BUILD_MODE%"=="full" (
    powershell -Command "Compress-Archive -Path '%PROJECT_ROOT%\magisk-module\*' -DestinationPath '%OUT%\InputBlocker.zip' -Force"
    if exist "%OUT%\InputBlocker.zip" echo Done: %OUT%\InputBlocker.zip
) else (
    powershell -Command "Compress-Archive -Path '%PROJECT_ROOT%\magisk-module\*' -DestinationPath '%OUT%\InputBlocker-lite.zip' -Force"
    if exist "%OUT%\InputBlocker-lite.zip" echo Done: %OUT%\InputBlocker-lite.zip
)

echo.
echo ============================================================
echo  Module Build Complete
echo ============================================================
echo.
pause
