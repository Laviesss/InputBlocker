@echo off
setlocal enabledelayedexpansion

echo ============================================================
echo  InputBlocker - Build All Platforms (Windows)
echo ============================================================

set "SCRIPT_DIR=%~dp0"

echo Building Android APK...
call "%SCRIPT_DIR%apk\build_android.bat"

echo Building PC Tools...
call "%SCRIPT_DIR%pc_tools\build_pc_tools.bat"

echo Packaging Root Module...
call "%SCRIPT_DIR%module\build_module.bat"

echo.
echo ============================================================
echo  ALL COMPONENTS BUILT SUCCESSFULLY
echo ============================================================
pause

