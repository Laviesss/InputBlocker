#!/bin/bash
# ============================================================
# InputBlocker Setup - Build All Platforms Script (Bash)
# ============================================================

echo ""
echo "============================================================"
echo " InputBlocker Setup - Build All Platforms"
echo "============================================================"
echo ""

echo "Building entire ecosystem via Gradle..."
./gradlew buildAll

if [ $? -eq 0 ]; then
    echo ""
    echo "============================================================"
    echo "  ALL COMPONENTS BUILT SUCCESSFULLY"
    echo "============================================================"
else
    echo ""
    echo "============================================================"
    echo "  BUILD FAILED"
    echo "============================================================"
    exit 1
fi

echo ""
