#!/system/bin/sh
#########################################################################################
# InputBlocker - Boot Service Script
# Auto-installs companion app on first boot after module installation
#########################################################################################

MODDIR="${0%/*}"
INSTALL_FLAG="/data/local/tmp/inputblocker/.apk_installed"

log() {
    log -t InputBlocker "$1"
}

# Create directory for flag file
mkdir -p /data/local/tmp/inputblocker

log "Service started, MODDIR=$MODDIR"

# Find APK
APK_PATH=""
for dir in "$MODDIR/common" "$MODDIR/system/app"; do
    if [ -f "$dir/InputBlocker.apk" ]; then
        APK_PATH="$dir/InputBlocker.apk"
        log "Found APK at: $APK_PATH"
        break
    fi
done

# Check if APK is found
if [ -z "$APK_PATH" ]; then
    log "No APK found in module, skipping auto-install"
    # Debug: list what's in the module
    log "Module contents:"
    ls -la "$MODDIR/" 2>/dev/null || log "Cannot list MODDIR"
    ls -la "$MODDIR/common/" 2>/dev/null || log "No common dir"
    ls -la "$MODDIR/system/" 2>/dev/null || log "No system dir"
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
