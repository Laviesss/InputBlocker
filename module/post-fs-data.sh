#!/system/bin/sh
#########################################################################################
# InputBlocker - post-fs-data Script
# Runs after post-fs-data and sets up the module
#########################################################################################

MODDIR="${MODPATH:-${0%/*}}"
APK_PATHS="$MODDIR/common/InputBlocker.apk;
         $MODDIR/InputBlocker.apk;
         /data/adb/modules/inputblocker/common/InputBlocker.apk"

log() {
    log -t InputBlocker-PostFS "$1"
}

log "Starting post-fs-data.sh"

# Clear install flag so service.sh will run fresh
rm -f "$INSTALL_FLAG"

# Verify APK exists in module
APK_FOUND=""
for path in $APK_PATHS; do
    if [ -f "$path" ]; then
        log "APK found at: $path"
        log "APK size: $(ls -l "$path" 2>/dev/null | awk '{print $5}') bytes"
        APK_FOUND="yes"
        break
    fi
done

if [ -z "$APK_FOUND" ]; then
    log "WARNING: APK not found in module!"
    log "Searched: $APK_PATHS"
fi

log "post-fs-data.sh complete, service.sh will handle installation"
exit 0
