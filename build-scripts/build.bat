@echo off
REM ============================================================
REM InputBlocker - Build All Components
REM ============================================================

set "PROJECT_ROOT=%~dp0.."
cd /d "%PROJECT_ROOT%"

set "OUT_DIR=%PROJECT_ROOT%\releases"
if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"

set "BUILD_CSHARP=1"
set "BUILD_JAVA=1"
set "BUILD_APK=0"
set "BUILD_MODULE=0"

if "%~1"=="csharp" (
    set "BUILD_JAVA=0"
    set "BUILD_APK=0"
    set "BUILD_MODULE=0"
)
if "%~1"=="java" (
    set "BUILD_CSHARP=0"
    set "BUILD_APK=0"
    set "BUILD_MODULE=0"
)
if "%~1"=="apk" (
    set "BUILD_CSHARP=0"
    set "BUILD_JAVA=0"
    set "BUILD_MODULE=0"
)
if "%~1"=="module" (
    set "BUILD_CSHARP=0"
    set "BUILD_JAVA=0"
    set "BUILD_MODULE=1"
)
if "%~1"=="tools" (
    set "BUILD_APK=0"
    set "BUILD_MODULE=0"
)
if "%~1"=="full" (
    set "BUILD_APK=1"
    set "BUILD_MODULE=1"
)

echo.
echo ============================================================
echo  InputBlocker Build
echo ============================================================
echo.

REM ============================================================
REM BUILD C#
REM ============================================================
if "%BUILD_CSHARP%"=="1" (
    echo Building C#
    
    set "CSHARP_OUT=%OUT_DIR%\csharp"
    if not exist "%CSHARP_OUT%" mkdir "%CSHARP_OUT%"
    
    cd /d "%PROJECT_ROOT%\pc-tool-csharp"
    
    dotnet --version >nul 2>&1
    if errorlevel 1 (
        echo SKIPPED: .NET SDK not found
    ) else (
        dotnet publish -c Release -r win-x64 -o "%CSHARP_OUT%\windows-x64" --self-contained true -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true
        echo   win-x64 done
        
        dotnet publish -c Release -r win-arm64 -o "%CSHARP_OUT%\windows-arm64" --self-contained true -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true
        echo   win-arm64 done
        
        dotnet publish -c Release -r linux-x64 -o "%CSHARP_OUT%\linux-x64" --self-contained true -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true
        echo   linux-x64 done
        
        dotnet publish -c Release -r linux-arm64 -o "%CSHARP_OUT%\linux-arm64" --self-contained true -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true
        echo   linux-arm64 done
        
        echo   macOS: Run on macOS
    )
    echo.
)

REM ============================================================
REM BUILD JAVA
REM ============================================================
if "%BUILD_JAVA%"=="1" (
    echo Building Java
    
    set "JAVA_OUT=%OUT_DIR%\java"
    if not exist "%JAVA_OUT%" mkdir "%JAVA_OUT%"
    
    cd /d "%PROJECT_ROOT%\pc-tool-java"
    
    java -version >nul 2>&1
    if errorlevel 1 (
        echo SKIPPED: Java not found
    ) else (
        call gradlew.bat jar --no-daemon
        if not errorlevel 1 (
            copy /Y "build\libs\InputBlockerSetup-1.0.0.jar" "%JAVA_OUT%\" >nul
            echo   JAR done
        )
    )
    echo.
)

REM ============================================================
REM BUILD APK
REM ============================================================
if "%BUILD_APK%"=="1" (
    echo Building APK
    cd /d "%PROJECT_ROOT%\android-app"
    
    call gradlew.bat assembleRelease --no-daemon 2>nul
    if errorlevel 1 (
        call gradlew.bat assembleDebug --no-daemon
        if errorlevel 1 (
            echo SKIPPED: Gradle failed
        ) else (
            copy /Y "app\build\outputs\apk\debug\app-debug.apk" "%OUT_DIR%\InputBlocker.apk" >nul
            echo   Debug APK done
        )
    ) else (
        copy /Y "app\build\outputs\apk\release\app-release.apk" "%OUT_DIR%\InputBlocker.apk" >nul
        echo   Release APK done
    )
    echo.
)

REM ============================================================
REM BUILD MODULE
REM ============================================================
if "%BUILD_MODULE%"=="1" (
    echo Building module
    
    if not exist "%PROJECT_ROOT%\magisk-module\common" mkdir "%PROJECT_ROOT%\magisk-module\common"
    
    if exist "%OUT_DIR%\InputBlocker.apk" (
        copy /Y "%OUT_DIR%\InputBlocker.apk" "%PROJECT_ROOT%\magisk-module\common\InputBlocker.apk" >nul
        powershell -Command "Compress-Archive -Path '%PROJECT_ROOT%\magisk-module\*' -DestinationPath '%OUT_DIR%\InputBlocker.zip' -Force"
        del "%PROJECT_ROOT%\magisk-module\common\InputBlocker.apk" 2>nul
        powershell -Command "Compress-Archive -Path '%PROJECT_ROOT%\magisk-module\*' -DestinationPath '%OUT_DIR%\InputBlocker-lite.zip' -Force"
        echo   Full module done
        echo   Lite module done
    ) else (
        powershell -Command "Compress-Archive -Path '%PROJECT_ROOT%\magisk-module\*' -DestinationPath '%OUT_DIR%\InputBlocker-lite.zip' -Force"
        echo   Lite module done
    )
    echo.
)

cd /d "%PROJECT_ROOT%"

echo ============================================================
echo  Build Complete
echo ============================================================
echo Output: %OUT_DIR%
echo.
if exist "%OUT_DIR%" dir /b "%OUT_DIR%"
echo.
pause
