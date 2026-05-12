#!/system/bin/sh
# InputBlocker - Root Manager Action Script
# Gateway for UI Actions and Update Fallbacks

PKG_NAME="com.inputblocker.app"
CONFIG_FILE="/data/adb/modules/inputblocker/config/profiles/default.conf"
UPDATE_URL="https://github.com/Laviesss/InputBlocker/releases/latest"
APK_PATH="/data/adb/modules/inputblocker/common/InputBlocker.apk"

log() {
    log -t InputBlocker-Action "$1"
}

log "Action button pressed. Evaluating request..."

# Check if companion app is installed
if pm list packages | grep -q "$PKG_NAME"; then
    log "Companion app found. Attempting to launch Quick Menu..."
    # Launch Companion App Quick Menu (FLAG_ACTIVITY_NEW_TASK)
    am start -f 0x10000000 -a com.inputblocker.ACTION_QUICK_MENU -n $PKG_NAME/.MainActivity > /dev/null 2>&1
    
    if [ $? -eq 0 ]; then
        log "Successfully triggered Quick Action menu in app."
        exit 0
    fi
else
    log "Companion app NOT found. Attempting auto-installation..."
    if [ -f "$APK_PATH" ]; then
        pm install -r "$APK_PATH" > /dev/null 2>&1
        if [ $? -eq 0 ]; then
            log "App installed successfully. Launching Quick Menu..."
            am start -f 0x10000000 -a com.inputblocker.ACTION_QUICK_MENU -n $PKG_NAME/.MainActivity > /dev/null 2>&1
            if [ $? -eq 0 ]; then
                log "Launched app after installation."
                exit 0
            fi
        else
            log "Installation failed. APK may be incompatible or corrupted."
        fi
    else
        log "Installation failed: APK not found at $APK_PATH"
    fi
fi

# Priority 2: Fallback Update Check
log "Unable to launch app. Opening release page for updates..."
am start -a android.intent.action.VIEW -d "$UPDATE_URL" > /dev/null 2>&1

if [ $? -eq 0 ]; then
    log "Redirected user to release page."
    exit 0
fi

# Priority 3: Emergency Reset (Last Resort)
log "Fallback failed. Performing emergency reset to ensure device accessibility..."
if [ -f "$CONFIG_FILE" ]; then
    sed -i 's/^enabled=.*/enabled=0/' "$CONFIG_FILE"
    am broadcast -a com.inputblocker.CHANGE_CONFIG
    log "Emergency reset complete: Blocking disabled."
else
    log "ERROR: Config file not found at $CONFIG_FILE. Cannot reset."
fi
