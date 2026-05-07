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

log "========== INPUTBLOCKER SERVICE STARTED =========="
log "MODDIR=$MODDIR"

# Create directory for flag file
mkdir -p /data/local/tmp/inputblocker

# Wait for boot to complete
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done
sleep 5  # Extra wait for package manager

log "Boot completed, checking for APK..."

# List EVERYTHING in the module directory for debugging
log "=== Module directory contents ==="
ls -laR "$MODDIR/" 2>/dev/null | while read line; do
    log "MODDIR: $line"
done

# Check common dir specifically
log "=== Checking $MODDIR/common/ ==="
ls -la "$MODDIR/common/" 2>/dev/null | while read line; do
    log "COMMON: $line"
done

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
    log "Checking: $path"
    if [ -f "$path" ]; then
        APK_PATH="$path"
        log "FOUND APK at: $APK_PATH"
        ls -la "$APK_PATH" 2>/dev/null | while read line; do
            log "APK file: $line"
        done
        break
    fi
done

# If still not found, search entire filesystem (slow but thorough)
if [ -z "$APK_PATH" ]; then
    log "ERROR: No APK found in common paths, searching filesystem..."
    find /data/adb/modules/inputblocker -name "InputBlocker.apk" 2>/dev/null | while read line; do
        log "FOUND via find: $line"
        APK_PATH="$line"
        break
    done
fi

if [ -z "$APK_PATH" ]; then
    log "FATAL: No APK found anywhere. Exiting."
    exit 0
fi;

# Check if already installed with same version
if [ -f "$INSTALL_FLAG" ]; then
    INSTALLED_VERSION=$(cat "$INSTALL_FLAG" 2>/dev/null)
    CURRENT_VERSION=$(dumpsys package com.inputblocker.app 2>/dev/null | grep versionName | head -1 | cut -d= -f2)
    
    if [ "$INSTALLED_VERSION" = "$CURRENT_VERSION" ] && [ -n "$INSTALLED_VERSION" ]; then
        log "APK already installed with same version ($INSTALLED_VERSION)"
        exit 0
    fi;
fi;

# Install APK
log "Installing APK from: $APK_PATH"

# Method 1: Try pm install -r (replace existing)
log "Trying pm install..."
pm install -r "$APK_PATH" 2>&1 | while read line; do;
    log "pm: $line"
done;

if [ $? -eq 0 ]; then
    log "pm install succeeded"
    APK_VERSION=$(dumpsys package com.inputblocker.app 2>/dev/null | grep versionName | head -1 | cut -d= -f2)
    if [ -n "$APK_VERSION" ]; then;
        echo "$APK_VERSION" > "$INSTALL_FLAG"
        log "Stored version: $APK_VERSION"
    else
        echo "installed" > "$INSTALL_FLAG"
    fi;
else
    log "pm install failed, trying alternative..."
    
    # Method 2: Copy to accessible location and try again
    cp "$APK_PATH" /sdcard/Download/InputBlocker.apk 2>/dev/null
    pm install -r /sdcard/Download/InputBlocker.apk 2>&1 | while read line; do;
        log "pm2: $line"
    done;
    
    if [ $? -eq 0 ]; then
        log "Install succeeded from /sdcard/Download/"
        echo "installed" > "$INSTALL_FLAG"
    else
        log "ERROR: All install methods failed"
        log "APK copied to /sdcard/Download/InputBlocker.apk for manual install"
    fi;
fi;

log "========== INPUTBLOCKER SERVICE FINISHED =========="
exit 0;
