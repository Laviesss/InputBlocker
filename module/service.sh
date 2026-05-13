#!/system/bin/sh
#########################################################################################
# InputBlocker - Boot Service Script
# Auto-installs companion app on first boot after module installation
#########################################################################################

MODDIR="${0%/*}"
INSTALL_FLAG="/data/local/tmp/inputblocker/.apk_installed"
PKG_NAME="com.inputblocker.app"

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
sleep 10  # Increased wait for package manager and system services to settle

# --- Root Module Hardening: Health Check ---
if [ -f "$MODDIR/health-check.sh" ]; then
    log "Performing system health check..."
    if "$MODDIR/health-check.sh"; then
        log "Health check passed. Skipping self-repair."
        exit 0
    else
        log "Health check failed! Initiating self-repair sequence..."
    fi
fi
# ------------------------------------------

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
        break
    fi
done

if [ -z "$APK_PATH" ]; then
    log "ERROR: No APK found in common paths, searching filesystem..."
    APK_PATH=$(find /data/adb/modules/inputblocker -name "InputBlocker.apk" 2>/dev/null | head -1)
fi

if [ -z "$APK_PATH" ]; then
    log "FATAL: No APK found anywhere. Exiting."
    exit 0
fi

# Verification function to ensure the app is actually registered
verify_installation() {
    local count=0
    while [ $count -lt 5 ]; do
        if pm list packages | grep -q "$PKG_NAME"; then
            return 0
        fi
        log "Waiting for package manager to register $PKG_NAME... ($((count+1))/5)"
        sleep 3
        count=$((count+1))
    done
    return 1
}

# Check if already installed with same version
if [ -f "$INSTALL_FLAG" ]; then
    INSTALLED_VERSION=$(cat "$INSTALL_FLAG" 2>/dev/null)
    CURRENT_VERSION=$(dumpsys package $PKG_NAME 2>/dev/null | grep versionName | head -1 | cut -d= -f2)
    
    if [ "$INSTALLED_VERSION" = "$CURRENT_VERSION" ] && [ -n "$INSTALLED_VERSION" ]; then
        log "APK already installed with same version ($INSTALLED_VERSION)"
        exit 0
    fi
fi

# Install APK
log "Installing APK from: $APK_PATH"
INSTALL_LOG="/data/local/tmp/inputblocker/pm_install.log"

# Attempt installation with retries (PM can be flaky at boot)
MAX_RETRIES=3
RETRY_COUNT=0
SUCCESS=false

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    log "Installation attempt $((RETRY_COUNT+1))/$MAX_RETRIES..."
    pm install -r "$APK_PATH" > "$INSTALL_LOG" 2>&1
    if [ $? -eq 0 ]; then
        log "APK installed successfully."
        SUCCESS=true
        break
    fi
    RETRY_COUNT=$((RETRY_COUNT+1))
    log "Installation failed. Retrying in 10s..."
    sleep 10
done

if [ "$SUCCESS" = false ]; then
    log "FATAL: APK installation failed after $MAX_RETRIES attempts. See $INSTALL_LOG for details."
    # Try one last fallback: copy to /sdcard/Download and try again
    log "Trying last-resort fallback to /sdcard/Download..."
    cp "$APK_PATH" /sdcard/Download/InputBlocker.apk 2>/dev/null
    pm install -r /sdcard/Download/InputBlocker.apk >> "$INSTALL_LOG" 2>&1
    if [ $? -eq 0 ]; then
        log "Fallback installation successful!"
        SUCCESS=true
    fi
fi

# Final verification check
if verify_installation; then
    log "VERIFIED: $PKG_NAME is now registered in the system"

    
    # FORCE VISIBILITY:
    # 1. Try am start to launch the app (this forces the launcher to index it)
    am start -n $PKG_NAME/.MainActivity > /dev/null 2>&1
    
    # 2. Poke with monkey as backup
    monkey -p $PKG_NAME -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
    
    APK_VERSION=$(dumpsys package $PKG_NAME 2>/dev/null | grep versionName | head -1 | cut -d= -f2)
    if [ -n "$APK_VERSION" ]; then
        echo "$APK_VERSION" > "$INSTALL_FLAG"
        log "Stored version: $APK_VERSION"
    else
        echo "installed" > "$INSTALL_FLAG"
    fi
else
    log "FATAL: Installation failed. App not found in package list after retries."
    log "Manual installation required from: $APK_PATH"
fi

log "========== INPUTBLOCKER SERVICE FINISHED =========="
