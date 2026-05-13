#!/system/bin/sh
#########################################################################################
# InputBlocker - post-fs-data Script
# Runs after post-fs-data and copies APK to accessible locations
#########################################################################################

MODDIR="${MODPATH:-${0%/*}}"
APK_SOURCE="$MODDIR/common/InputBlocker.apk"

sys_log() {
    /system/bin/log -t InputBlocker-PostFS "$1"
}

sys_log "========== POST-FS-DATA STARTED =========="

# Copy APK to multiple accessible locations
if [ -f "$APK_SOURCE" ]; then
    sys_log "APK found at: $APK_SOURCE"
    sys_log "Copying APK to accessible locations..."
    
    # Copy to storage (works for all root managers)
    cp "$APK_SOURCE" /sdcard/Download/InputBlocker.apk 2>/dev/null
    cp "$APK_SOURCE" /storage/emulated/0/Download/InputBlocker.apk 2>/dev/null
    
    # Copy to module dir for service.sh
    cp "$APK_SOURCE" "$MODDIR/InputBlocker.apk" 2>/dev/null
    
    sys_log "APK distributed to:"
    sys_log " - $MODDIR/InputBlocker.apk"
    sys_log " - /sdcard/Download/InputBlocker.apk"
    sys_log " - /storage/emulated/0/Download/InputBlocker.apk"
else
    sys_log "ERROR: APK not found at $APK_SOURCE"
    ls -la "$MODDIR/" 2>/dev/null | while read line; do
        sys_log "MODDIR: $line"
    done
fi

# Clear install flag so service.sh will run fresh
rm -f /data/local/tmp/inputblocker/.apk_installed

sys_log "========== POST-FS-DATA FINISHED =========="
exit 0
