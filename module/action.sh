#!/system/bin/sh
# InputBlocker - Root Manager Action Script
# Gateway for UI Actions and Update Fallbacks

PKG_NAME="com.inputblocker.app"
CONFIG_FILE="/data/adb/modules/inputblocker/config/profiles/default.conf"
UPDATE_URL="https://github.com/Laviesss/InputBlocker/releases/latest"

# Fix: Rename function to avoid infinite recursion
sys_log() {
    /system/bin/log -t InputBlocker-Action "$1"
}

# Helper to find the APK in multiple common locations
find_apk() {
    local moddir="/data/adb/modules/inputblocker"
    for path in \
        "$moddir/common/InputBlocker.apk" \
        "$moddir/InputBlocker.apk" \
        "$moddir/common/InputBlocker.apk" \
        "/data/local/tmp/inputblocker/InputBlocker.apk" \
        "/sdcard/Download/InputBlocker.apk" \
        "/storage/emulated/0/Download/InputBlocker.apk"; do
        if [ -f "$path" ]; then
            echo "$path"
            return 0
        fi
    done
    return 1
}

# Use echo for user-facing output in root manager action panels
# Use sys_log for system logs
echo "Action button pressed. Evaluating request..."

# --- Root Module Hardening: Health Check ---
HEALTH_SCRIPT="/data/adb/modules/inputblocker/health-check.sh"
if [ -f "$HEALTH_SCRIPT" ]; then
    if "$HEALTH_SCRIPT"; then
        echo "System is healthy. Proceeding to launch app..."
    else
        echo "System health check failed! Triggering self-repair..."
    fi
fi

# Check if companion app is installed
if pm list packages | grep -q "$PKG_NAME"; then
    echo "Companion app found. Attempting to launch Quick Menu..."
    # Launch Companion App Quick Menu (FLAG_ACTIVITY_NEW_TASK)
    am start -f 0x10000000 -a com.inputblocker.ACTION_QUICK_MENU -n $PKG_NAME/.MainActivity > /dev/null 2>&1
    
    if [ $? -eq 0 ]; then
        echo "Successfully triggered Quick Action menu in app."
        exit 0
    fi
else
    echo "Companion app NOT found. Attempting auto-installation..."
    APK_PATH=$(find_apk)
    if [ -n "$APK_PATH" ]; then
        echo "Found APK at $APK_PATH. Installing..."
        INSTALL_LOG="/data/local/tmp/inputblocker/action_install.log"
        pm install -r "$APK_PATH" > "$INSTALL_LOG" 2>&1
        
        if [ $? -eq 0 ]; then
            echo "App installed successfully. Launching Quick Menu..."
            am start -f 0x10000000 -a com.inputblocker.ACTION_QUICK_MENU -n $PKG_NAME/.MainActivity > /dev/null 2>&1
            if [ $? -eq 0 ]; then
                echo "Launched app after installation."
                exit 0
            fi
        else
            echo "Installation failed. Check $INSTALL_LOG for details."
        fi
    else
        echo "Installation failed: APK not found in any common path."
    fi
fi

# Priority 2: Fallback Update Check
echo "Unable to launch app. Opening release page for updates..."
am start -a android.intent.action.VIEW -d "$UPDATE_URL" > /dev/null 2>&1

if [ $? -eq 0 ]; then
    echo "Redirected user to release page."
    exit 0
fi

# Priority 3: Emergency Reset (Last Resort)
echo "Fallback failed. Performing emergency reset to ensure device accessibility..."
if [ -f "$CONFIG_FILE" ]; then
    sed -i "s/^enabled=.*/enabled=0/" "$CONFIG_FILE"
    am broadcast -a com.inputblocker.CHANGE_CONFIG
    echo "Emergency reset complete: Blocking disabled."
else
    echo "ERROR: Config file not found at $CONFIG_FILE. Cannot reset."
fi
