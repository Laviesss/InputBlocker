#!/system/bin/sh
#########################################################################################
# InputBlocker - Install Script
# Runs during module installation (first install and updates)
#########################################################################################

MODDIR="${MODPATH:-${0%/*}}"
APK_PATH="$MODDIR/common/InputBlocker.apk"
INSTALL_FLAG="/data/local/tmp/inputblocker/.apk_installed"

log() {
    log -t InputBlocker-Install "$1"
}

log "Install script running, MODPATH=$MODDIR"

# Create directory for flag file
mkdir -p /data/local/tmp/inputblocker

# Check if APK exists
if [ ! -f "$APK_PATH" ]; then
    log "No APK found at $APK_PATH"
    exit 0
fi

# Check if already installed
if [ -f "$INSTALL_FLAG" ]; then
    INSTALLED_VERSION=$(cat "$INSTALL_FLAG" 2>/dev/null)
    CURRENT_VERSION=$(dumpsys package com.inputblocker.app 2>/dev/null | grep versionName | head -1 | cut -d= -f2)

    if [ "$INSTALLED_VERSION" = "$CURRENT_VERSION" ] && [ -n "$INSTALLED_VERSION" ]; then
        log "APK already installed with same version ($INSTALLED_VERSION)"
        exit 0
    fi
fi

# Install the APK
log "Installing InputBlocker APK..."

if pm install -r "$APK_PATH" 2>/dev/null; then
    log "APK installed successfully"
    APK_VERSION=$(dumpsys package com.inputblocker.app 2>/dev/null | grep versionName | head -1 | cut -d= -f2)
    if [ -n "$APK_VERSION" ]; then
        echo "$APK_VERSION" > "$INSTALL_FLAG"
        log "Stored version: $APK_VERSION"
    else
        echo "installed" > "$INSTALL_FLAG"
    fi
else
    log "Failed to install APK via pm"
    # Try alternative method
    log "Trying alternative installation..."
fi

exit 0