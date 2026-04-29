#!/system/bin/sh
###############################################################
# InputBlocker - Cross-Manager Customization Script
# Works with: KernelSU, APatch, Magisk
###############################################################

# Cross-manager compatibility: detect which manager is running
if [ "$KSU" = "true" ]; then
    ui_print "- KernelSU detected"
elif [ "$APATCH" = "true" ]; then
    ui_print "- APatch detected"
else
    ui_print "- Magisk detected"
fi

# Create module directories
mkdir -p "$MODPATH"
mkdir -p "$MODPATH/system/bin"
mkdir -p "$MODPATH/common"
mkdir -p "$MODPATH/config"

# Copy system binaries
cp -f "$MODDIR/system/bin/"* "$MODPATH/system/bin/" 2>/dev/null || true
chmod 755 "$MODPATH/system/bin/"* 2>/dev/null || true

# Copy APK if exists in ZIP
if [ -f "$MODDIR/common/InputBlocker.apk" ]; then
    cp -f "$MODDIR/common/InputBlocker.apk" "$MODPATH/common/"
fi

# Create default config if not exists
if [ ! -f "$MODPATH/config/blocked_regions.conf" ]; then
    cat > "$MODPATH/config/blocked_regions.conf" << 'EOFCONFIG'
# InputBlocker Configuration
enabled=1
force_safe_mode=0

# Add blocked regions below:
# Format: x1,y1,x2,y2
# Example: 0,0,100,200
EOFCONFIG
    chmod 644 "$MODPATH/config/blocked_regions.conf"
fi

# Set permissions
chmod 644 "$MODPATH/module.prop" 2>/dev/null || true

ui_print "- InputBlocker installed successfully!"
ui_print "- Reboot to apply changes"