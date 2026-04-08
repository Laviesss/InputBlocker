@echo off
REM ============================================================
REM InputBlocker - Build Java PC Tool
REM ============================================================

set "PROJECT_ROOT=%~dp0.."
cd /d "%PROJECT_ROOT%"

set "OUT=%PROJECT_ROOT%\releases\java"
if not exist "%OUT%" mkdir "%OUT%"

echo.
echo ============================================================
echo  Building Java
echo ============================================================
echo.

cd /d "%PROJECT_ROOT%\pc-tool-java"

java -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java not found
    echo Download: https://adoptium.net/
    pause
    exit /b 1
)

echo Building JAR...
call gradlew.bat jar --no-daemon

if not errorlevel 1 (
    copy /Y "build\libs\InputBlockerSetup-1.0.0.jar" "%OUT%\" >nul
    echo Done: %OUT%\InputBlockerSetup-1.0.0.jar
)

echo.
echo ============================================================
echo  Java Build Complete
echo ============================================================
echo.
pause
