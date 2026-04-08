#!/system/bin/sh
#########################################################################################
# InputBlocker - Universal Module Installer
# Works with Magisk, KernelSU, APatch, and other root managers
#########################################################################################

MODDIR="${0%/*}"
MODID="inputblocker"

# Detect root manager
detect_manager() {
    if [ -f "/sbin/magisk" ] || [ -f "/data/adb/magisk" ] || [ -n "$(which magisk)" ]; then
        echo "magisk"
    elif [ -f "/data/adb/ksu" ] || [ -n "$(which ksu)" ]; then
        echo "kernelsu"
    elif [ -f "/data/adb/apd" ] || [ -n "$(which apd)" ]; then
        echo "apatch"
    elif [ -f "/su/bin/su" ]; then
        echo "supersu"
    else
        echo "unknown"
    fi
}

# Get module path based on manager
get_modpath() {
    local manager="$1"
    case "$manager" in
        magisk)    echo "/data/adb/modules/$MODID" ;;
        kernelsu)   echo "/data/ksu/modules/$MODID" ;;
        apatch)    echo "/data/apatch/modules/$MODID" ;;
        supersu)   echo "/su/su.d/$MODID" ;;
        *)         echo "/data/adb/modules/$MODID" ;;
    esac
}

ui_print() {
    echo "$1"
}

print_modname() {
    ui_print "****************************************"
    ui_print "    InputBlocker - Ghost Tap Blocker    "
    ui_print "****************************************"
}

# Main installation
print_modname

MANAGER=$(detect_manager)
ui_print "- Detected root manager: $MANAGER"

MODPATH=$(get_modpath "$MANAGER")
ui_print "- Module path: $MODPATH"

# Create module directory
mkdir -p "$MODPATH"
mkdir -p "$MODPATH/system/bin"

# Copy files to module path
ui_print "- Installing files..."
cp -r "$MODDIR/system/"* "$MODPATH/system/" 2>/dev/null

# Copy common files (APK) if exists
if [ -f "$MODDIR/common/InputBlocker.apk" ]; then
    mkdir -p "$MODPATH/common"
    cp "$MODDIR/common/InputBlocker.apk" "$MODPATH/common/"
fi

# Create default config if not exists
mkdir -p "$MODPATH/config"
if [ ! -f "$MODPATH/config/blocked_regions.conf" ]; then
    cat > "$MODPATH/config/blocked_regions.conf" << 'EOF'
# InputBlocker Configuration
# Format: x1,y1,x2,y2 (top-left to bottom-right coordinates)
# Lines starting with # are comments
# enabled=1 (1=enable blocking, 0=disable)
# force_safe_mode=1 (1=enable crash protection)

enabled=1
force_safe_mode=0

# Add blocked regions below (one per line):
# Example: 0,0,100,200 blocks top-left 100x200 area
# Example: 980,1720,1080,1920 blocks bottom-right corner

EOF
fi

# Set permissions
ui_print "- Setting permissions..."
chmod -R 755 "$MODPATH/system/bin/" 2>/dev/null
chmod 644 "$MODPATH/config/blocked_regions.conf" 2>/dev/null
chmod 644 "$MODPATH/module.prop" 2>/dev/null
chmod 755 "$MODPATH/service.sh" 2>/dev/null

ui_print ""
ui_print "========================================"
ui_print " InputBlocker installed successfully!   "
ui_print "========================================"
ui_print ""
ui_print "Companion app will be installed automatically"
ui_print "on first boot after module installation."
ui_print ""
ui_print "Usage:"
ui_print "  1. Open the InputBlocker app on your device"
ui_print "  2. Use Visual Setup to draw blocked regions"
ui_print "  3. Or use terminal: inputblocker add x1,y1,x2,y2"
ui_print ""
ui_print "Kill Switch:"
ui_print "  Press Vol Down x3, then Vol Up x3 (within 5 seconds)"
ui_print ""
