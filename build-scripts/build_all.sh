#!/bin/bash
# ============================================================
# InputBlocker Setup - Build All Platforms Script (Bash)
# ============================================================
#
# This script builds InputBlocker components.
#
# Requirements:
# - Java 17+ JDK (for Java version)
# - .NET 8.0 SDK (for C# version)
# - Android SDK (for module build)
#
# Usage:
#   ./build-all.sh              - Build everything (C# + Java + Module)
#   ./build-all.sh java         - Build Java version only
#   ./build-all.sh csharp       - Build C# version only
#   ./build-all.sh module       - Build module with APK only
#   ./build-all.sh tools        - Build PC tools only (C# + Java)
#
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="${SCRIPT_DIR}/releases"

echo ""
echo "============================================================"
echo " InputBlocker Setup - Build All Platforms"
echo "============================================================"
echo ""

# Create output directory
mkdir -p "${OUTPUT_DIR}"

BUILD_JAVA=1
BUILD_CSHARP=1
BUILD_MODULE=0

if [ "$1" == "java" ]; then
    BUILD_CSHARP=0
    BUILD_MODULE=0
    echo "Building Java version only..."
elif [ "$1" == "csharp" ]; then
    BUILD_JAVA=0
    BUILD_MODULE=0
    echo "Building C# version only..."
elif [ "$1" == "module" ]; then
    BUILD_CSHARP=0
    BUILD_JAVA=0
    BUILD_MODULE=1
    echo "Building module only..."
elif [ "$1" == "tools" ]; then
    BUILD_MODULE=0
    echo "Building PC tools only..."
else
    BUILD_MODULE=1
    echo "Building C#, Java, and Module..."
fi

echo ""

# ============================================================
# BUILD C# VERSION
# ============================================================
if [ "$BUILD_CSHARP" == "1" ]; then
    echo -e "\033[93mBuilding C# Version...\033[0m"

    cd "${SCRIPT_DIR}/pc-tool-csharp"

    CSHARP_OUTPUT="${OUTPUT_DIR}/csharp"
    mkdir -p "${CSHARP_OUTPUT}"

    # Windows x64
    echo "  Building Windows x64..."
    dotnet publish -c Release -r win-x64 -o "${CSHARP_OUTPUT}/windows-x64" --self-contained true -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true
    mv "${CSHARP_OUTPUT}/windows-x64/InputBlockerSetup.exe" "${CSHARP_OUTPUT}/windows-x64/InputBlockerSetup_windows-x64.exe" 2>/dev/null || true
    echo -e "\033[92mBuilt: ${CSHARP_OUTPUT}/windows-x64/\033[0m"

    # Windows ARM64
    echo "  Building Windows ARM64..."
    dotnet publish -c Release -r win-arm64 -o "${CSHARP_OUTPUT}/windows-arm64" --self-contained true -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true
    mv "${CSHARP_OUTPUT}/windows-arm64/InputBlockerSetup.exe" "${CSHARP_OUTPUT}/windows-arm64/InputBlockerSetup_windows-arm64.exe" 2>/dev/null || true
    echo -e "\033[92mBuilt: ${CSHARP_OUTPUT}/windows-arm64/\033[0m"

    # Linux x64
    echo "  Building Linux x64..."
    dotnet publish -c Release -r linux-x64 -o "${CSHARP_OUTPUT}/linux-x64" --self-contained true -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true
    mv "${CSHARP_OUTPUT}/linux-x64/InputBlockerSetup" "${CSHARP_OUTPUT}/linux-x64/InputBlockerSetup_linux-x64" 2>/dev/null || true
    echo -e "\033[92mBuilt: ${CSHARP_OUTPUT}/linux-x64/\033[0m"

    # Linux ARM64
    echo "  Building Linux ARM64..."
    dotnet publish -c Release -r linux-arm64 -o "${CSHARP_OUTPUT}/linux-arm64" --self-contained true -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true
    mv "${CSHARP_OUTPUT}/linux-arm64/InputBlockerSetup" "${CSHARP_OUTPUT}/linux-arm64/InputBlockerSetup_linux-arm64" 2>/dev/null || true
    echo -e "\033[92mBuilt: ${CSHARP_OUTPUT}/linux-arm64/\033[0m"

    # macOS x64 (Intel)
    echo "  Building macOS x64 (Intel)..."
    dotnet publish -c Release -r osx-x64 -o "${CSHARP_OUTPUT}/macos-intel" --self-contained true -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true
    mv "${CSHARP_OUTPUT}/macos-intel/InputBlockerSetup" "${CSHARP_OUTPUT}/macos-intel/InputBlockerSetup_macos-intel" 2>/dev/null || true
    echo -e "\033[92mBuilt: ${CSHARP_OUTPUT}/macos-intel/\033[0m"

    # macOS ARM64 (Apple Silicon)
    echo "  Building macOS ARM64 (Apple Silicon)..."
    dotnet publish -c Release -r osx-arm64 -o "${CSHARP_OUTPUT}/macos-apple-silicon" --self-contained true -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true
    mv "${CSHARP_OUTPUT}/macos-apple-silicon/InputBlockerSetup" "${CSHARP_OUTPUT}/macos-apple-silicon/InputBlockerSetup_macos-apple-silicon" 2>/dev/null || true
    echo -e "\033[92mBuilt: ${CSHARP_OUTPUT}/macos-apple-silicon/\033[0m"

    echo ""
    echo -e "\033[92mC# builds complete! Output: ${CSHARP_OUTPUT}\033[0m"
    echo ""
fi

# ============================================================
# BUILD JAVA VERSION
# ============================================================
if [ "$BUILD_JAVA" == "1" ]; then
    echo -e "\033[93mBuilding Java Version...\033[0m"

    cd "${SCRIPT_DIR}/pc-tool-java"

    JAVA_OUTPUT="${OUTPUT_DIR}/java"
    mkdir -p "${JAVA_OUTPUT}"

    # Check for Java
    if ! command -v java &> /dev/null; then
        echo -e "\033[91mJava not found. Please install JDK 17+\033[0m"
    else
        # Build JAR first
        echo "  Building JAR..."
        ./gradlew jar

        echo ""
        echo -e "\033[92mJAR built at: pc-tool-java/build/libs/\033[0m"
    fi
    echo ""
fi

# ============================================================
# BUILD MODULE (FULL AND LITE)
# ============================================================
if [ "$BUILD_MODULE" == "1" ]; then
    echo -e "\033[93mBuilding InputBlocker Modules...\033[0m"
    echo ""

    ANDROID_APP_DIR="${SCRIPT_DIR}/android-app"
    MODULE_DIR="${SCRIPT_DIR}/magisk-module"

    cd "${ANDROID_APP_DIR}"

    echo "Building Android APK..."
    ./gradlew assembleDebug --no-daemon
    if [ $? -ne 0 ]; then
        echo -e "\033[91mGradle failed. Trying release...\033[0m"
        ./gradlew assembleRelease --no-daemon
        if [ $? -ne 0 ]; then
            echo -e "\033[91mFailed to build APK.\033[0m"
            exit 1
        fi
        APK_PATH="${ANDROID_APP_DIR}/app/build/outputs/apk/release/app-release.apk"
    else
        APK_PATH="${ANDROID_APP_DIR}/app/build/outputs/apk/debug/app-debug.apk"
    fi

    if [ ! -f "$APK_PATH" ]; then
        echo -e "\033[91mERROR: APK not found!\033[0m"
        exit 1
    fi

    echo ""
    echo -e "\033[92mAPK built successfully!\033[0m"
    echo ""

    cd "${SCRIPT_DIR}"

    # ========================================
    # CREATE FULL MODULE (with APK)
    # ========================================
    echo "Creating full module (with APK)..."

    mkdir -p "${MODULE_DIR}/common"
    cp -f "$APK_PATH" "${MODULE_DIR}/common/InputBlocker.apk"

    rm -f "${OUTPUT_DIR}/InputBlocker.zip"
    zip -r "${OUTPUT_DIR}/InputBlocker.zip" "magisk-module/" -x "*.DS_Store" 2>/dev/null

    if [ -f "${OUTPUT_DIR}/InputBlocker.zip" ]; then
        echo -e "\033[92mFull module created: ${OUTPUT_DIR}/InputBlocker.zip\033[0m"
    else
        echo -e "\033[91mFailed to create full module zip.\033[0m"
    fi

    # ========================================
    # CREATE LITE MODULE (without APK)
    # ========================================
    echo "Creating lite module (without APK)..."

    rm -f "${MODULE_DIR}/common/InputBlocker.apk"

    rm -f "${OUTPUT_DIR}/InputBlocker-lite.zip"
    zip -r "${OUTPUT_DIR}/InputBlocker-lite.zip" "magisk-module/" -x "*.DS_Store" 2>/dev/null

    if [ -f "${OUTPUT_DIR}/InputBlocker-lite.zip" ]; then
        echo -e "\033[92mLite module created: ${OUTPUT_DIR}/InputBlocker-lite.zip\033[0m"
    else
        echo -e "\033[91mFailed to create lite module zip.\033[0m"
    fi

    # Restore APK for future builds
    cp -f "$APK_PATH" "${MODULE_DIR}/common/InputBlocker.apk"

    echo ""
fi

echo "============================================================"
echo " Build Complete!"
echo "============================================================"
echo ""
echo "Output directory: ${OUTPUT_DIR}"
echo ""
echo "Contents:"
ls -la "${OUTPUT_DIR}" 2>/dev/null || echo "(empty)"
echo ""
