#!/system/bin/sh
# InputBlocker - Module Health Check Script
# Returns 0 if healthy, 1 if issues found

MODDIR="${0%/*}"
PKG_NAME="com.inputblocker.app"
APK_PATH="$MODDIR/common/InputBlocker.apk"

sys_log() {
    /system/bin/log -t InputBlocker-Health "$1"
}

sys_log "Starting module health check..."

# 1. Check module.prop
if [ ! -f "$MODDIR/module.prop" ]; then
    sys_log "ERROR: module.prop missing!"
    exit 1
fi

if ! grep -q "id=inputblocker" "$MODDIR/module.prop"; then
    sys_log "ERROR: module.prop is corrupted or not for InputBlocker!"
    exit 1
fi

# 2. Check APK existence
if [ ! -f "$APK_PATH" ]; then
    sys_log "ERROR: Companion APK missing at $APK_PATH"
    exit 1
fi

# 3. Check App installation
if ! pm list packages | grep -q "$PKG_NAME"; then
    sys_//log "ERROR: Companion app not installed"
    exit 1
fi

# 4. Check basic config directory
if [ ! -d "/data/adb/modules/inputblocker/config" ]; then
    sys_log "WARNING: Config directory missing. This will be recreated by the app."
fi

sys_log "Health check passed. Module is healthy."
exit 0
