#!/system/bin/sh
# InputBlocker - Root Manager Action Script
# Gateway for UI Actions and Update Fallbacks

MODDIR="${0%/*}"
PKG_NAME="com.inputblocker.app"
UPDATE_URL="https://github.com/Laviesss/InputBlocker/releases/latest"

sys_log() {
    /system/bin/log -t InputBlocker-Action "$1"
}

# Use echo for ALL user-facing messages (shown in root manager)
echo "Evaluating InputBlocker request..."

# Check if companion app is installed
if pm list packages | grep -q "$PKG_NAME"; then
    echo "Companion app found. Launching Quick Menu..."
    # Launch with FLAG_ACTIVITY_NEW_TASK (0x10000000)
    if am start -f 0x10000000 -a com.inputblocker.ACTION_QUICK_MENU -n $PKG_NAME/.MainActivity > /dev/null 2>&1; then
        echo "Success: Quick Action menu opened."
        sys_log "Successfully launched Quick Menu."
    else
        echo "Error: Failed to launch the app."
        sys_log "Failed to launch Quick Menu."
    fi
else
    echo "Companion app not found."
    sys_log "App not installed. Attempting fallback installation..."
    
    APK_PATH="$MODDIR/common/InputBlocker.apk"
    if [ -f "$APK_PATH" ]; then
        echo "Attempting to install companion app..."
        if pm install -r "$APK_PATH" > /dev/null 2>&1; then
            echo "Installation successful! Launching app..."
            am start -f 0x10000000 -a com.inputblocker.ACTION_QUICK_MENU -n $PKG_NAME/.MainActivity > /dev/null 2>&1
            sys_log "Installed and launched app via action button."
        else
            echo "Installation failed."
            echo "Please download the latest release:"
            echo "$UPDATE_URL"
            sys_log "Fallback installation failed."
        fi
    else
        echo "Installation APK missing from module."
        echo "Please update the module or visit:"
        echo "$UPDATE_URL"
        sys_log "APK missing for fallback install."
    fi
fi
