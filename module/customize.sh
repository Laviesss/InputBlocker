#!/system/bin/sh
###############################################################
# InputBlocker - Cross-Manager Customization Script
# Works with: KernelSU, APatch, Magisk
###############################################################

# Cross-manager detection
if [ "$KSU" = "true" ]; then
    ui_print "- KernelSU detected"
elif [ "$APATCH" = "true" ]; then
    ui_print "- APatch detected"
else
    ui_print "- Magisk detected"
fi

# Copy module.prop (required by all managers)
if [ -f "$MODDIR/module.prop" ]; then
    cp -f "$MODDIR/module.prop" "$MODPATH/"
    chmod 644 "$MODPATH/module.prop"
fi

# Copy system binaries
if [ -d "$MODDIR/system/bin" ]; then
    mkdir -p "$MODPATH/system/bin"
    cp -rf "$MODDIR/system/bin/"* "$MODPATH/system/bin/" 2>/dev/null || true
    chmod -R 755 "$MODPATH/system/bin/" 2>/dev/null || true
fi

# Copy common files (APK)
if [ -f "$MODDIR/common/InputBlocker.apk" ]; then
    mkdir -p "$MODPATH/common"
    cp -f "$MODDIR/common/InputBlocker.apk" "$MODPATH/common/"
fi

# Copy service.sh
if [ -f "$MODDIR/service.sh" ]; then
    cp -f "$MODDIR/service.sh" "$MODPATH/"
    chmod 755 "$MODPATH/service.sh"
fi

# Copy custom installation script if exists
if [ -f "$MODDIR/install.sh" ]; then
    cp -f "$MODDIR/install.sh" "$MODPATH/"
    chmod 755 "$MODPATH/install.sh"
fi

# Create default config if not exists
mkdir -p "$MODPATH/config"
if [ ! -f "$MODPATH/config/blocked_regions.conf" ]; then
    cat > "$MODPATH/config/blocked_regions.conf" << 'EOFCONFIG'
# InputBlocker Configuration
enabled=1
force_safe_mode=0

# Add blocked regions below:
# Format: x1,y1,x2,y2 (Normalized 0.0 to 1.0)
# Example: 0.1,0.1,0.2,0.3
EOFCONFIG
    chmod 644 "$MODPATH/config/blocked_regions.conf"
fi

ui_print "- Root module installed successfully!"
ui_print "- Companion app will be verified/installed on first boot."
ui_print "- Reboot to apply changes"
