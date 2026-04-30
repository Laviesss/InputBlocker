#!/system/bin/sh
#########################################################################################
# InputBlocker - Boot Service Script
# Auto-installs companion app on first boot after module installation
#########################################################################################

MODDIR="${0%/*}"
# Check both locations for APK (system/app for system app, common for regular app)
APK_DIRS="$MODDIR/system/app/InputBlocker $MODDIR/common"
INSTALL_FLAG_DIR="/data/local/tmp/inputblocker"
INSTALL_FLAG="$INSTALL_FLAG_DIR/.apk_installed"

log() {
    log -t InputBlocker "$1"
}

# Create directory for flag file
mkdir -p "$INSTALL_FLAG_DIR"

# Find APK in either location
APK_PATH=""
for dir in $APK_DIRS; do
    if [ -f "$dir/InputBlocker.apk" ]; then
        APK_PATH="$dir/InputBlocker.apk"
        break
    fi
done

# Check if APK is found
if [ -z "$APK_PATH" ]; then
    log "No APK found in module, skipping auto-install"
    exit 0
fi

# Check if already installed
if [ -f "$INSTALL_FLAG" ]; then
    INSTALLED_VERSION=$(cat "$INSTALL_FLAG" 2>/dev/null)
    CURRENT_VERSION=$(dumpsys package com.inputblocker.app 2>/dev/null | grep versionName | head -1 | cut -d= -f2)
    
    if [ "$INSTALLED_VERSION" = "$CURRENT_VERSION" ] && [ -n "$INSTALLED_VERSION" ]; then
        log "APK already installed with same version"
        exit 0
    fi
fi

# Install the APK
log "Installing InputBlocker companion app..."

if [ -f "$APK_PATH" ]; then
    # Install APK (replace existing)
    pm install -r "$APK_PATH" 2>/dev/null
    
    if [ $? -eq 0 ]; then
        log "APK installed successfully"
        # Get APK version and store installed flag
        APK_VERSION=$(dumpsys package com.inputblocker.app 2>/dev/null | grep versionName | head -1 | cut -d= -f2)
        if [ -n "$APK_VERSION" ]; then
            echo "$APK_VERSION" > "$INSTALL_FLAG"
            log "Stored version: $APK_VERSION"
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
