#!/system/bin/sh
#########################################################################################
# InputBlocker - Service Script
# Auto-installs companion app on first boot after module installation
#########################################################################################

MODDIR=${0%/*}
APK_DIR="$MODDIR/common"
INSTALL_FLAG="/data/local/tmp/.inputblocker_apk_installed"

log() {
    log -t InputBlocker "$1"
}

# Check if APK is in the module
if [ ! -f "$APK_DIR/InputBlocker.apk" ]; then
    log "No APK found in module, skipping auto-install"
    exit 0
fi

# Check if already installed
if [ -f "$INSTALL_FLAG" ]; then
    INSTALLED_VERSION=$(cat "$INSTALL_FLAG" 2>/dev/null)
    CURRENT_VERSION=$(pm dump com.inputblocker.app 2>/dev/null | grep "versionName" | head -1 | cut -d= -f2)
    
    if [ "$INSTALLED_VERSION" = "$CURRENT_VERSION" ]; then
        log "APK already installed with same version"
        exit 0
    fi
fi

# Install the APK
log "Installing InputBlocker companion app..."
APK_PATH="$APK_DIR/InputBlocker.apk"

if [ -f "$APK_PATH" ]; then
    # Get APK version for the flag
    APK_VERSION=$(dumpsys package com.inputblocker.app 2>/dev/null | grep versionName | head -1 | cut -d= -f2)
    
    # Install APK (replace existing)
    pm install -r "$APK_PATH" 2>/dev/null
    
    if [ $? -eq 0 ]; then
        log "APK installed successfully"
        # Store installed version
        if [ -n "$APK_VERSION" ]; then
            echo "$APK_VERSION" > "$INSTALL_FLAG"
        else
            echo "installed" > "$INSTALL_FLAG"
        fi
    else
        log "Failed to install APK"
    fi
else
    log "APK file not found at $APK_PATH"
fi

exit 0
