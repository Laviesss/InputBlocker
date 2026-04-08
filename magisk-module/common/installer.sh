#!/system/bin/sh
#########################################################################################
# InputBlocker - Common Installation Script
# Handles module installation
#########################################################################################

MODDIR=${0%/*}
MODID="inputblocker"

ui_print() {
    if [ -n "$2" ]; then
        echo "ui_print $1 $2"
    else
        echo "ui_print $1"
    fi
}

print_modname() {
    ui_print "****************************************" "   "
    ui_print "    InputBlocker - Ghost Tap Blocker    " "   "
    ui_print "****************************************" "   "
}

set_perm() {
    chown $2:$3 $1
    chmod $4 $1
}

set_perm_recursive() {
    find $1 -type f -exec chown $2:$3 {} \;
    find $1 -type f -exec chmod $4 {} \;
    find $1 -type d -exec chown $2:$3 {} \;
    find $1 -type d -exec chmod $5 {} \;
}

# Main installation
print_modname

ui_print "- Extracting files..."
ui_print "- Module path: $MODDIR"

# Create necessary directories
ui_print "- Creating directories..."
mkdir -p "$MODDIR/system/bin"
mkdir -p "$MODDIR/system/lib"
mkdir -p "$MODDIR/config"
mkdir -p "$MODDIR/common"

# Ensure config directory exists
if [ ! -f "$MODDIR/config/blocked_regions.conf" ]; then
    ui_print "- Creating default config..."
    cat > "$MODDIR/config/blocked_regions.conf" << 'EOF'
# InputBlocker Configuration
# Format: x1,y1,x2,y2 (top-left to bottom-right coordinates)
# Lines starting with # are comments
# enabled=1 (1=enable blocking, 0=disable)

enabled=1

# Add blocked regions below:
# Example: 0,0,100,200 blocks top-left 100x200 area
# Example: 980,1720,1080,1920 blocks bottom-right corner

EOF
fi

# Set permissions
ui_print "- Setting permissions..."
set_perm_recursive "$MODDIR" 0 0 0755 0644 0755

# Make scripts executable
chmod 0755 "$MODDIR/system/bin/inputblocker" 2>/dev/null
chmod 0755 "$MODDIR/system/bin/inputblockerd" 2>/dev/null

# Module info
ui_print ""
ui_print "========================================" "   "
ui_print " InputBlocker installed successfully!" "   "
ui_print "========================================" "   "
ui_print ""
ui_print "Companion app will be installed automatically"
ui_print "on first boot after module installation."
ui_print ""
ui_print "Usage:"
ui_print "  1. Open the InputBlocker app on your device"
ui_print "  2. Use Visual Setup to draw blocked regions"
ui_print "  3. Or use terminal: inputblocker add x1,y1,x2,y2"
ui_print ""
ui_print "For visual setup with GUI from computer:"
ui_print "  1. Connect device via USB with ADB"
ui_print "  2. Run the InputBlocker PC Setup tool"
ui_print ""
