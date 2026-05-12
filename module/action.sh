#!/system/bin/sh
# InputBlocker - Root Manager Action Script
# Gateway for UI Actions and Update Fallbacks

PKG_NAME="com.inputblocker.app"
CONFIG_FILE="/data/adb/modules/inputblocker/config/profiles/default.conf"
UPDATE_URL="https://github.com/Laviesss/InputBlocker/releases/latest"

log() {
    log -t InputBlocker-Action "$1"
}

log "Action button pressed. Evaluating request..."

# --- STRATEGY: SMART GATEWAY ---
# Priority 1: Launch Companion App Quick Menu
# Added -f 0x10000000 (FLAG_ACTIVITY_NEW_TASK) to ensure it launches from shell
am start -f 0x10000000 -a com.inputblocker.ACTION_QUICK_MENU -n $PKG_NAME/.MainActivity > /dev/null 2>&1

if [ $? -eq 0 ]; then
    log "Successfully triggered Quick Action menu in app."
    exit 0
fi

# Priority 2: Fallback Update Check (For managers without native update UI)
# If the app isn't there, we provide a direct link to the releases page.
log "Companion app not found. Opening release page for updates..."
am start -a android.intent.action.VIEW -d "$UPDATE_URL" > /dev/null 2>&1

if [ $? -eq 0 ]; then
    log "Redirected user to release page."
    exit 0
fi

# Priority 3: Emergency Reset (Last Resort)
# If everything fails, ensure the device is accessible.
log "Fallback failed. Performing emergency reset to ensure device accessibility..."
if [ -f "$CONFIG_FILE" ]; then
    # Use sed to force enabled=0 in the config file
    sed -i 's/^enabled=.*/enabled=0/' "$CONFIG_FILE"
    
    # Notify the service to reload the config immediately
    am broadcast -a com.inputblocker.CHANGE_CONFIG
    
    log "Emergency reset complete: Blocking disabled."
else
    log "ERROR: Config file not found at $CONFIG_FILE. Cannot reset."
fi
