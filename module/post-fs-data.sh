#!/system/bin/sh
#########################################################################################
# InputBlocker - post-fs-data Script
# Runs after late_init and sets up the module
#########################################################################################

APK_PATH="/data/adb/modules/inputblocker/common/InputBlocker.apk"
INSTALL_FLAG="/data/local/tmp/inputblocker/.apk_installed"

log() {
    log -t InputBlocker-PostFS "POST-FS: $1"
}

log "Starting post-fs-data.sh"

# Clear install flag so service.sh will run fresh
rm -f "$INSTALL_FLAG"

# Verify APK exists
if [ -f "$APK_PATH" ]; then
    log "APK found at $APK_PATH"
    log "APK size: $(ls -l "$APK_PATH" | awk '{print $5}') bytes"
else
    log "WARNING: APK not found at $APK_PATH"
fi

log "post-fs-data.sh complete, service.sh will handle installation"
exit 0