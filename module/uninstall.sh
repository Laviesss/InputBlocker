#!/system/bin/sh
#########################################################################################
# InputBlocker - Uninstall Script
# Ensures the companion app is removed when the module is uninstalled
#########################################################################################

PKG_NAME="com.inputblocker.app"

sys_log() {
    /system/bin/log -t InputBlocker-Uninstall "$1"
}

sys_log "Initiating InputBlocker cleanup..."

# Check if the companion app is installed
if pm list packages | grep -q "$PKG_NAME"; then
    sys_log "Companion app found. Attempting to uninstall..."
    
    # pm uninstall for system apps removes the app for the current user
    # which is sufficient as the actual APK is removed when the module is deleted.
    pm uninstall -k --user 0 "$PKG_NAME"
    
    if [ $? -eq 0 ]; then
        sys_log "Companion app successfully uninstalled from user 0."
    else
        sys_log "Warning: Companion app uninstall failed or not required."
    fi
else
    sys_log "Companion app not found. Skipping uninstall."
fi

sys_log "InputBlocker cleanup process complete."
