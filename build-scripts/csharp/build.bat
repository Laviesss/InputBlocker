@echo off
REM ============================================================
REM InputBlocker - Build C# (All Platforms)
REM ============================================================

set "PROJECT_ROOT=%~dp0.."
cd /d "%PROJECT_ROOT%"

set "OUT=%PROJECT_ROOT%\releases\csharp"
if not exist "%OUT%" mkdir "%OUT%"

echo.
echo ============================================================
echo  Building C#
echo ============================================================
echo.

dotnet --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: .NET SDK not found
    echo Download: https://dotnet.microsoft.com/download/dotnet/8.0
    pause
    exit /b 1
)

echo Building Windows x64...
dotnet publish -c Release -r win-x64 -o "%OUT%\windows-x64" --self-contained true -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true
echo   Done

echo Building Windows ARM64...
dotnet publish -c Release -r win-arm64 -o "%OUT%\windows-arm64" --self-contained true -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true
echo   Done

echo Building Linux x64...
dotnet publish -c Release -r linux-x64 -o "%OUT%\linux-x64" --self-contained true -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true
echo   Done

echo Building Linux ARM64...
dotnet publish -c Release -r linux-arm64 -o "%OUT%\linux-arm64" --self-contained true -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true
echo   Done

echo.
echo macOS builds: Run on macOS
echo.
echo ============================================================
echo  C# Build Complete
echo ============================================================
echo Output: %OUT%
echo.
pause
