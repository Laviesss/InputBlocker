#!/bin/bash
# ============================================================
# InputBlocker Module Builder
# ============================================================
#
# Builds the InputBlocker root module.
#
# Options:
#   ./build-module.sh         - Build module with APK
#   ./build-module.sh full    - Build module with APK (same)
#   ./build-module.sh lite   - Build module without APK
#
# Requirements:
# - Android SDK (for APK builds)
# - Gradle (or use included wrapper)
#
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_APP_DIR="$SCRIPT_DIR/android-app"
MODULE_DIR="$SCRIPT_DIR/magisk-module"
OUTPUT_DIR="$SCRIPT_DIR/releases"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

BUILD_MODE="${1:-full}"
if [ "$BUILD_MODE" != "lite" ]; then
    BUILD_MODE="full"
fi

echo ""
echo "============================================================"
echo " InputBlocker Module Builder"
echo "============================================================"
echo ""
echo -e "Mode: ${YELLOW}${BUILD_MODE}${NC}"
echo ""

# Create output directory
mkdir -p "$OUTPUT_DIR"

# ============================================================
# BUILD APK (if full mode)
# ============================================================
if [ "$BUILD_MODE" == "full" ]; then
    echo -e "${YELLOW}Building Android APK...${NC}"
    echo ""
    
    cd "$ANDROID_APP_DIR"
    
    if [ ! -f "./gradlew" ]; then
        echo -e "${RED}ERROR: gradlew not found!${NC}"
        exit 1
    fi
    
    chmod +x ./gradlew
    
    echo "Building release APK..."
    ./gradlew assembleRelease --no-daemon 2>/dev/null
    
    if [ $? -ne 0 ]; then
        echo -e "${YELLOW}Failed release build. Trying debug...${NC}"
        ./gradlew assembleDebug --no-daemon
        if [ $? -ne 0 ]; then
            echo -e "${RED}Failed to build APK.${NC}"
            exit 1
        fi
        APK_PATH="$ANDROID_APP_DIR/app/build/outputs/apk/debug/app-debug.apk"
    else
        APK_PATH="$ANDROID_APP_DIR/app/build/outputs/apk/release/app-release.apk"
    fi
    
    if [ ! -f "$APK_PATH" ]; then
        echo -e "${RED}ERROR: APK not found!${NC}"
        exit 1
    fi
    
    echo ""
    echo -e "${GREEN}APK built successfully!${NC}"
    echo ""
    
    # Create common directory and copy APK
    mkdir -p "$MODULE_DIR/common"
    cp -f "$APK_PATH" "$MODULE_DIR/common/InputBlocker.apk"
    echo -e "${GREEN}APK copied to module/common/InputBlocker.apk${NC}"
    echo ""
else
    echo -e "${YELLOW}Lite mode: Skipping APK build${NC}"
    echo ""
    echo -e "${YELLOW}Removing APK from module if present...${NC}"
    if [ -f "$MODULE_DIR/common/InputBlocker.apk" ]; then
        rm -f "$MODULE_DIR/common/InputBlocker.apk"
        echo -e "${GREEN}APK removed from module${NC}"
    fi
    echo ""
fi

# ============================================================
# CREATE MODULE ZIP
# ============================================================
echo -e "${YELLOW}Creating module zip...${NC}"
echo ""

cd "$SCRIPT_DIR"

if [ "$BUILD_MODE" == "full" ]; then
    ZIP_NAME="InputBlocker.zip"
else
    ZIP_NAME="InputBlocker-lite.zip"
fi

# Remove old zip
rm -f "$ZIP_NAME"

# Create zip
zip -r "$ZIP_NAME" "magisk-module/" -x "*.DS_Store" 2>/dev/null

if [ -f "$ZIP_NAME" ]; then
    mv -f "$ZIP_NAME" "$OUTPUT_DIR/"
    echo -e "${GREEN}Module created: $OUTPUT_DIR/$ZIP_NAME${NC}"
else
    echo -e "${RED}Failed to create module zip.${NC}"
    echo "Make sure zip utility is installed."
    exit 1
fi

echo ""

# ============================================================
# SUMMARY
# ============================================================
echo "============================================================"
echo " Build Complete!"
echo "============================================================"
echo ""
echo "Output: $OUTPUT_DIR/$ZIP_NAME"
echo ""

if [ "$BUILD_MODE" == "full" ]; then
    echo "This module includes the companion app."
    echo "It will auto-install on first boot."
else
    echo "This is a lite module without the companion app."
    echo "Install InputBlocker.apk separately if needed."
fi

echo ""
