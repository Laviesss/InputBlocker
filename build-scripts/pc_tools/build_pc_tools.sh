#!/bin/bash
# ============================================================
# InputBlocker - Build PC Tools Only
# ============================================================

echo ""
echo "============================================================"
echo " InputBlocker - PC Tools Builder"
echo "============================================================"
echo ""

echo "Building Kotlin Compose Designer..."
cd "$(dirname "$0")/../.."
./gradlew :pc-tool-kotlin:packageDistributionForCurrentOS

if [ $? -eq 0 ]; then
    echo ""
    echo "============================================================"
    echo " PC Designer built successfully!"
    echo "============================================================"
else
    echo ""
    echo "============================================================"
    echo " BUILD FAILED"
    echo "============================================================"
    exit 1
fi

echo ""
