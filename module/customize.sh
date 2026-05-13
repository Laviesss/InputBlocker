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

# 🚨 CRITICAL CHECK: Ensure APK is present
if [ ! -f "$MODPATH/common/InputBlocker.apk" ]; then
    ui_print "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
    ui_print "FATAL ERROR: Companion APK not found!"
    ui_print "The installation cannot proceed without the app."
    ui_print "Please redownload the module ZIP."
    ui_print "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
    exit 1
fi

# Set permissions for scripts
chmod 755 "$MODPATH/service.sh"
chmod 755 "$MODPATH/action.sh"
chmod 755 "$MODPATH/health-check.sh"
chmod 755 "$MODPATH/post-fs-data.sh"

# Set permissions for system binaries
if [ -d "$MODPATH/system/bin" ]; then
    chmod -R 755 "$MODPATH/system/bin/"
fi

# Set permissions for system app APK (Overlay)
if [ -f "$MODPATH/system/app/InputBlocker/InputBlocker.apk" ]; then
    chmod 644 "$MODPATH/system/app/InputBlocker/InputBlocker.apk"
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
ui_print "- Reboot to activate. Companion app installs automatically on first boot."
