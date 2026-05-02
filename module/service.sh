#!/system/bin/sh
#########################################################################################
# InputBlocker - Boot Service Script
# Auto-installs companion app on first boot after module installation
#########################################################################################

APK_PATH="/data/adb/modules/inputblocker/common/InputBlocker.apk"
INSTALL_FLAG="/data/local/tmp/inputblocker/.apk_installed"

log() {
    log -t InputBlocker "$1"
}

log "InputBlocker service started"

# Remove flag to force reinstall (in case previous attempt failed)
rm -f "$INSTALL_FLAG"

# Wait for boot to complete
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done

sleep 5  # Extra wait for package manager

log "Boot completed, checking for APK..."

# Check if APK exists
if [ ! -f "$APK_PATH" ]; then
    log "APK not found at $APK_PATH"
    # Try alternative paths
    for path in "/data/adb/modules/inputblocker/common/InputBlocker.apk" \
                "/data/local/tmp/inputblocker/InputBlocker.apk" \
                "/sdcard/InputBlocker.apk"; do
        if [ -f "$path" ]; then
            APK_PATH="$path"
            log "Found APK at alternative path: $APK_PATH"
            break
        fi
    done
fi

if [ ! -f "$APK_PATH" ]; then
    log "No APK found anywhere, exiting"
    exit 0
fi

# Install APK
log "Installing APK from: $APK_PATH"
log "File size: $(ls -l "$APK_PATH" 2>/dev/null | awk '{print $5}') bytes"

# Try installation with verbose output
cmd package install-existing com.inputblocker.app 2>/dev/null && {
    log "Installation via cmd succeeded"
    echo "installed" > "$INSTALL_FLAG"
} || {
    log "cmd install failed, trying pm install"
    pm install -r "$APK_PATH" 2>&1 | while read line; do
        log "pm: $line"
    done
    if [ $? -eq 0 ]; then
        log "pm install succeeded"
        echo "installed" > "$INSTALL_FLAG"
    else
        log "pm install failed"
        # Copy to accessible location and signal user
        cp "$APK_PATH" /sdcard/Download/InputBlocker.apk 2>/dev/null
        log "Copied APK to /sdcard/Download/ for manual install"
    fi
}

log "Service finished"
exit 0