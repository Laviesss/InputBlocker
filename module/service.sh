#!/system/bin/sh
#########################################################################################
# InputBlocker - Boot Service Script
# Fallback installation for the companion app
#########################################################################################

MODDIR="${0%/*}"
INSTALL_FLAG="/data/local/tmp/inputblocker/.apk_installed"
PKG_NAME="com.inputblocker.app"

sys_log() {
    /system/bin/log -t InputBlocker "$1"
}

sys_log "========== INPUTBLOCKER SERVICE STARTED =========="

# Force grant overlay permission via appops for Android Go compatibility
# This bypasses the restricted "Display over other apps" settings page
appops set com.inputblocker.app SYSTEM_ALERT_WINDOW allow
sys_log "Forced SYSTEM_ALERT_WINDOW permission via appops."

# Create directory for flag file
mkdir -p /data/local/tmp/inputblocker

# 1. Wait for boot to complete
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done

# 2. Wait an additional 5 minutes for Magisk system overlay to register the app
sys_log "Boot completed. Waiting 300s for system app overlay registration..."
sleep 300

# 3. Check if the app is now installed
if pm list packages | grep -q "$PKG_NAME"; then
    sys_log "Companion app found via system overlay. Skipping fallback install."
    echo "$(getprop ro.build.version.release)" > "$INSTALL_FLAG"
    exit 0
fi

# 4. Fallback: Manual installation attempt
sys_log "Companion app NOT found after 300s. Starting fallback installation..."
APK_PATH="$MODDIR/common/InputBlocker.apk"

if [ ! -f "$APK_PATH" ]; then
    sys_log "FATAL: Fallback APK not found at $APK_PATH. Exiting."
    exit 1
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
    sys_log "Companion app installed successfully via fallback."
    echo "$(getprop ro.build.version.release)" > "$INSTALL_FLAG"
else
    sys_log "FATAL: Fallback installation failed after 3 attempts."
fi

sys_log "========== INPUTBLOCKER SERVICE FINISHED =========="
