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

# 🚨 CRITICAL CHECK: Ensure APK is present in the ZIP source
# Note: In customize.sh, $MODDIR is the temporary installation folder (ZIP root)
if [ ! -f "$MODPATH/common/InputBlocker.apk" ]; then
    ui_print "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
    ui_print "FATAL ERROR: Companion APK not found in ZIP!"
    ui_print "The installation cannot proceed without the app."
    ui_print "Please redownload the module ZIP."
    ui_print "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
    exit 1
fi

# Set permissions for scripts in the final destination ($MODPATH)
# These files are automatically copied from $MODDIR to $MODPATH by the manager
chmod 755 "$MODPATH/service.sh"
chmod 755 "$MODPATH/action.sh"
chmod 755 "$MODPATH/health-check.sh"
chmod 755 "$MODPATH/post-fs-data.sh"

# Set permissions for system binaries
if [ -d "$MODPATH/system/bin" ]; then
    chmod -R 755 "$MODPATH/system/bin/"
fi

# Create default config (Xposed hook + companion app read from config/profiles/default.conf)
mkdir -p "$MODPATH/config/profiles"
if [ ! -f "$MODPATH/config/profiles/default.conf" ]; then
    cat > "$MODPATH/config/profiles/default.conf" << 'EOFCONFIG'
# InputBlocker Configuration
enabled=1
force_safe_mode=0

# Add blocked regions below:
# Format: isExclude,type,x1,y1,x2,y2,minPressure,maxDuration
#   isExclude: 0=blocking zone, 1=exclude zone (touch always passes)
#   type: 0=rectangle, 1=circle, 2=ellipse
#   x1,y1,x2,y2: normalized coordinates (0.0-1.0)
#   minPressure: minimum pressure for a real touch (0.0-1.0)
#   maxDuration: max duration in ms before flagged as ghost
# Example (rectangle blocking bottom-right corner):
# 0,0,0.65,0.65,1.0,1.0,0.15,300
EOFCONFIG
    chmod 644 "$MODPATH/config/profiles/default.conf"
fi

# Remove legacy config location if present
rm -f "$MODPATH/config/blocked_regions.conf"

ui_print "- Root module installed successfully!"
ui_print "- Reboot to activate. Companion app installs automatically on first boot."
