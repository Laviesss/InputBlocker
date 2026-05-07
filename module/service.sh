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

log "InputBlocker service started, MODDIR=$MODDIR"

# Create directory for flag file
mkdir -p /data/local/tmp/inputblocker

# Wait for boot to complete
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done
sleep 5  # Extra wait for package manager

log "Boot completed, checking for APK..."

# Find APK - try multiple possible locations
APK_PATH=""
for path in \
    "$MODDIR/common/InputBlocker.apk" \
    "$MODDIR/InputBlocker.apk" \
    "/data/adb/modules/inputblocker/common/InputBlocker.apk" \
    "/data/local/tmp/inputblocker/InputBlocker.apk" \
    "/sdcard/Download/InputBlocker.apk" \
    "/storage/emulated/0/Download/InputBlocker.apk" \
    "/storage/emulated/0/Download/InputBlocker.zip"; do
    if [ -f "$path" ]; then
        APK_PATH="$path"
        log "Found APK at: $APK_PATH"
        log "File size: $(ls -l "$APK_PATH" 2>/dev/null | awk '{print $5}') bytes"
        break
    fi
done

if [ -z "$APK_PATH" ]; then
    log "ERROR: No APK found anywhere"
    log "Searched MODDIR=$MODDIR"
    exit 0
fi

# Check if already installed with same version
if [ -f "$INSTALL_FLAG" ]; then
    INSTALLED_VERSION=$(cat "$INSTALL_FLAG" 2>/dev/null)
    CURRENT_VERSION=$(dumpsys package com.inputblocker.app 2>/dev/null | grep versionName | head -1 | cut -d= -f2)
    
    if [ "$INSTALLED_VERSION" = "$CURRENT_VERSION" ] && [ -n "$INSTALLED_VERSION" ]; then
        log "APK already installed with same version ($INSTALLED_VERSION)"
        exit 0
    fi
fi

# Install APK
log "Installing APK from: $APK_PATH"

# Method 1: Try pm install -r (replace existing)
log "Trying pm install..."
pm install -r "$APK_PATH" 2>&1 | while read line; do
    log "pm: $line"
done

if [ $? -eq 0 ]; then
    log "pm install succeeded"
    APK_VERSION=$(dumpsys package com.inputblocker.app 2>/dev/null | grep versionName | head -1 | cut -d= -f2)
    if [ -n "$APK_VERSION" ]; then
        echo "$APK_VERSION" > "$INSTALL_FLAG"
        log "Stored version: $APK_VERSION"
    else
        echo "installed" > "$INSTALL_FLAG"
    fi
else
    log "pm install failed, trying alternative..."
    
    # Method 2: Copy to accessible location and try again
    cp "$APK_PATH" /sdcard/Download/InputBlocker.apk 2>/dev/null
    pm install -r /sdcard/Download/InputBlocker.apk 2>&1 | while read line; do
        log "pm2: $line"
    done
    
    if [ $? -eq 0 ]; then
        log "Install succeeded from /sdcard/Download/"
        echo "installed" > "$INSTALL_FLAG"
    else
        log "ERROR: All install methods failed"
        log "APK available at: $APK_PATH"
    fi
fi

log "Service finished"
exit 0
