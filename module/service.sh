#!/system/bin/sh
#########################################################################################
# InputBlocker - Boot Service Script
# Installs the companion app as a regular user app (uninstallable)
#########################################################################################

MODDIR="${0%/*}"
INSTALL_FLAG="/data/local/tmp/inputblocker/.apk_installed"
PKG_NAME="com.inputblocker.app"

sys_log() {
    /system/bin/log -t InputBlocker "$1"
}

sys_log "========== INPUTBLOCKER SERVICE STARTED =========="

# Force grant overlay permission via appops for Android Go compatibility
appops set com.inputblocker.app SYSTEM_ALERT_WINDOW allow 2>/dev/null

# Create directory for flag file
mkdir -p /data/local/tmp/inputblocker

# 1. Wait for boot to complete
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done

# 2. Wait for system & package manager to settle (reduced from 300s — no system overlay)
sys_log "Boot completed. Waiting 30s for system to settle..."
sleep 30

# 3. Check if app is already installed (from a previous successful run)
if pm list packages | grep -q "$PKG_NAME"; then
    sys_log "Companion app already installed. Skipping install."
    echo "$(getprop ro.build.version.release)" > "$INSTALL_FLAG"
    exit 0
fi

# 4. Install as a regular user app via package manager
sys_log "Companion app not found. Starting user-app installation..."
APK_PATH="$MODDIR/common/InputBlocker.apk"

if [ ! -f "$APK_PATH" ]; then
    sys_log "FATAL: APK not found at $APK_PATH."
    sys_log "Trying /sdcard/Download/InputBlocker.apk as fallback..."
    APK_PATH="/sdcard/Download/InputBlocker.apk"
    if [ ! -f "$APK_PATH" ]; then
        sys_log "FATAL: APK not found anywhere. Exiting."
        exit 1
    fi
fi

# Try install up to 3 times
INSTALLED=false
for i in 1 2 3; do
    sys_log "Installation attempt $i/3..."
    pm install -r "$APK_PATH" > /dev/null 2>&1

    # Verify installation (up to 5 checks, 3s apart)
    for j in 1 2 3 4 5; do
        if pm list packages | grep -q "$PKG_NAME"; then
            sys_log "App successfully installed on attempt $i."
            INSTALLED=true
            break 2
        fi
        sleep 3
    done
    sys_log "Attempt $i failed. Retrying in 10s..."
    sleep 10
done

if [ "$INSTALLED" = "true" ]; then
    sys_log "Companion app installed successfully as user app (uninstallable)."
    echo "$(getprop ro.build.version.release)" > "$INSTALL_FLAG"
else
    # Final fallback: open the APK for manual install via system intent
    sys_log "FATAL: Silent install failed after 3 attempts. Launching manual install intent..."
    if [ -f "$APK_PATH" ]; then
        am start -a android.intent.action.VIEW \
            -d "file://$APK_PATH" \
            -t "application/vnd.android.package-archive" \
            -f 0x10000000 > /dev/null 2>&1
        sys_log "Manual install intent launched. User must confirm installation."
    fi
fi

sys_log "========== INPUTBLOCKER SERVICE FINISHED =========="
