@echo off
setlocal enabledelayedexpansion

echo ============================================================
echo  InputBlocker PC Tools Builder (Windows)
echo ============================================================

set "SCRIPT_DIR=%~dp0"
set "OUTPUT_DIR=%SCRIPT_DIR%..\releases"

if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

echo Building C# Version...
cd /d "%SCRIPT_DIR%..\"
for %%r in (win-x64 win-arm64 linux-x64 linux-arm64 osx-x64 osx-arm64) do (
    echo Building %%r...
    dotnet publish -c Release -r %%r -o "..\releases\csharp\%%r" --self-contained true -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true
)

echo Building Java Version...
cd /d "%SCRIPT_DIR%..\"
call gradlew assemble
copy /y build\libs\*.jar "..\releases\java\InputBlockerSetup.jar"

echo All PC tools built successfully!
pause
