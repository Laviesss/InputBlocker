#!/bin/bash
# ============================================================
# InputBlocker APK Builder
# ============================================================
# 
# This script builds only the Android APK.
#
# Requirements:
# - Android SDK (ANDROID_HOME env var or local SDK)
# - Gradle (or use included wrapper)
#
# Usage:
#   ./build-apk.sh          # Build debug APK
#   ./build-apk.sh release  # Build release APK
#
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_APP_DIR="$SCRIPT_DIR/android-app"
OUTPUT_DIR="$SCRIPT_DIR/releases"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo ""
echo "============================================================"
echo " InputBlocker APK Builder"
echo "============================================================"
echo ""

# Create output directory
mkdir -p "$OUTPUT_DIR"

cd "$ANDROID_APP_DIR"

# Check for Gradle wrapper
if [ ! -f "./gradlew" ]; then
    echo -e "${RED}ERROR: gradlew not found!${NC}"
    echo "Run this script from the InputBlocker root directory."
    exit 1
fi

chmod +x ./gradlew

# Choose build type
BUILD_TYPE="debug"
if [ "$1" == "release" ]; then
    BUILD_TYPE="release"
fi

echo -e "Build type: ${YELLOW}${BUILD_TYPE}${NC}"
echo ""

# ============================================================
# Build APK
# ============================================================
echo "Building ${BUILD_TYPE} APK..."

if [ "$BUILD_TYPE" == "release" ]; then
    ./gradlew assembleRelease --no-daemon
    if [ $? -ne 0 ]; then
        echo -e "${RED}Failed to build release APK.${NC}"
        exit 1
    fi
    APK_OUTPUT="$ANDROID_APP_DIR/app/build/outputs/apk/release/app-release.apk"
else
    ./gradlew assembleDebug --no-daemon
    if [ $? -ne 0 ]; then
        echo -e "${RED}Failed to build debug APK.${NC}"
        exit 1
    fi
    APK_OUTPUT="$ANDROID_APP_DIR/app/build/outputs/apk/debug/app-debug.apk"
fi

if [ ! -f "$APK_OUTPUT" ]; then
    echo -e "${RED}ERROR: APK not found at $APK_OUTPUT${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}APK built successfully!${NC}"
echo ""

# ============================================================
# Copy to releases folder
# ============================================================
echo "Copying to releases folder..."

if [ "$BUILD_TYPE" == "release" ]; then
    cp -f "$APK_OUTPUT" "$OUTPUT_DIR/InputBlocker-release.apk"
    echo -e "${GREEN}Copied to: $OUTPUT_DIR/InputBlocker-release.apk${NC}"
else
    cp -f "$APK_OUTPUT" "$OUTPUT_DIR/InputBlocker-debug.apk"
    echo -e "${GREEN}Copied to: $OUTPUT_DIR/InputBlocker-debug.apk${NC}"
fi

# Also copy as generic name
cp -f "$APK_OUTPUT" "$OUTPUT_DIR/InputBlocker.apk"
echo -e "${GREEN}Copied to: $OUTPUT_DIR/InputBlocker.apk${NC}"

echo ""
echo "Build complete!"
echo ""
