@echo off
setlocal enabledelayedexpansion

echo ============================================================
echo  InputBlocker PC Tools Builder (Windows)
echo ============================================================

set "SCRIPT_DIR=%~dp0"

echo Building Kotlin Compose Designer...
cd /d "%SCRIPT_DIR%..\"
call gradlew :pc-tool-kotlin:packageDistributionForCurrentOS

if %ERRORLEVEL% equ 0 (
    echo PC Designer built successfully!
) else (
    echo Error occurred during build.
    exit /b %ERRORLEVEL%
)

pause
