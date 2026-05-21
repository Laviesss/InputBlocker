@echo off
setlocal enabledelayedexpansion

echo ============================================================
echo  InputBlocker - Build All Platforms (Windows)
echo ============================================================

echo Building entire ecosystem via Gradle...
call gradlew buildAll

if %ERRORLEVEL% equ 0 (
    echo.
    echo ============================================================
    echo  ALL COMPONENTS BUILT SUCCESSFULLY
    echo ============================================================
) else (
    echo.
    echo ============================================================
    echo  BUILD FAILED
    echo ============================================================
)

pause
