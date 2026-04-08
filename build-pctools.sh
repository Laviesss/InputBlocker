#!/bin/bash
# ============================================================
# InputBlocker - Build PC Tools Only
# ============================================================
#
# Builds the C# and Java PC setup tools.
# Does NOT build APK or module.
#
# Requirements:
# - .NET 8.0 SDK (for C#)
# - Java 17+ JDK (for Java)
#
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/releases"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo ""
echo "============================================================"
echo " InputBlocker - PC Tools Builder"
echo "============================================================"
echo ""

# Create output directory
mkdir -p "$OUTPUT_DIR"

BUILD_CSHARP=0
BUILD_JAVA=0

# ============================================================
# BUILD C# VERSION
# ============================================================
echo -e "${YELLOW}Building C# Version (all platforms)...${NC}"
echo ""

cd "$SCRIPT_DIR/pc-tool-csharp"

if command -v dotnet &> /dev/null; then
    BUILD_CSHARP=1
    CSHARP_OUTPUT="$OUTPUT_DIR/csharp"
    mkdir -p "$CSHARP_OUTPUT"
    
    echo "Building Windows x64..."
    dotnet publish -c Release -r win-x64 -o "$CSHARP_OUTPUT/windows-x64" --self-contained true -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true
    echo -e "${GREEN}Done: $CSHARP_OUTPUT/windows-x64${NC}"
    
    echo "Building Windows ARM64..."
    dotnet publish -c Release -r win-arm64 -o "$CSHARP_OUTPUT/windows-arm64" --self-contained true -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true
    echo -e "${GREEN}Done: $CSHARP_OUTPUT/windows-arm64${NC}"
    
    echo "Building Linux x64..."
    dotnet publish -c Release -r linux-x64 -o "$CSHARP_OUTPUT/linux-x64" --self-contained true -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true
    echo -e "${GREEN}Done: $CSHARP_OUTPUT/linux-x64${NC}"
    
    echo "Building Linux ARM64..."
    dotnet publish -c Release -r linux-arm64 -o "$CSHARP_OUTPUT/linux-arm64" --self-contained true -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true
    echo -e "${GREEN}Done: $CSHARP_OUTPUT/linux-arm64${NC}"
    
    echo "Building macOS Intel..."
    dotnet publish -c Release -r osx-x64 -o "$CSHARP_OUTPUT/macos-intel" --self-contained true -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true
    echo -e "${GREEN}Done: $CSHARP_OUTPUT/macos-intel${NC}"
    
    echo "Building macOS Apple Silicon..."
    dotnet publish -c Release -r osx-arm64 -o "$CSHARP_OUTPUT/macos-apple-silicon" --self-contained true -p:PublishSingleFile=true -p:EnableCompressionInSingleFile=true
    echo -e "${GREEN}Done: $CSHARP_OUTPUT/macos-apple-silicon${NC}"
else
    echo -e "${RED}.NET SDK not found. Install .NET 8.0 to build C# version.${NC}"
    echo "Download: https://dotnet.microsoft.com/download/dotnet/8.0"
fi

echo ""

# ============================================================
# BUILD JAVA VERSION
# ============================================================
echo -e "${YELLOW}Building Java Version...${NC}"
echo ""

cd "$SCRIPT_DIR/pc-tool-java"

if command -v java &> /dev/null; then
    BUILD_JAVA=1
    JAVA_OUTPUT="$OUTPUT_DIR/java"
    mkdir -p "$JAVA_OUTPUT"
    
    echo "Building JAR..."
    ./gradlew jar 2>/dev/null
    if [ $? -eq 0 ]; then
        cp -f "build/libs/InputBlockerSetup-1.0.0.jar" "$JAVA_OUTPUT/"
        echo -e "${GREEN}Done: $JAVA_OUTPUT/InputBlockerSetup-1.0.0.jar${NC}"
    else
        echo -e "${RED}Failed to build Java JAR.${NC}"
    fi
else
    echo -e "${RED}Java not found. Install JDK 17+ to build Java version.${NC}"
    echo "Download: https://adoptium.net/"
fi

echo ""

# ============================================================
# SUMMARY
# ============================================================
echo "============================================================"
echo " Build Complete!"
echo "============================================================"
echo ""
echo "Output: $OUTPUT_DIR"
echo ""
echo "Contents:"
ls -la "$OUTPUT_DIR" 2>/dev/null || echo "(empty)"
echo ""
