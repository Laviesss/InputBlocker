# Troubleshooting Guide

Common issues, diagnostic steps, and recovery procedures for InputBlocker.

---

## Quick Reference

| Symptom | Likely Cause | First Action |
|---|---|---|
| Module not filtering | LSPosed not enabled / wrong scope | Check LSPosed Manager → Modules |
| Boot loop after install | Bad config / framework conflict | Boot Safe Mode, disable module |
| Overlay blocking real touches | Regions too aggressive | Emergency gesture (Vol↓×3 → Vol↑×3) |
| Ghost taps still getting through | Config not applied / overlays | Verify config file exists and is valid |
| "Crash detected" on boot | Hook threw unhandled exception | Clear crash flag, check logcat |
| App crashes on launch | Corrupt config or data | Clear app data, reconfigure |
| PC Designer can't connect | ADB not authorized / wrong port | Check USB debugging, re-authorize |
| Pause/Resume not working | Broadcast not received by service | Restart app or toggle blocking off/on |
| Crash logs empty | No crashes detected yet | This is normal — crash logs only appear after a crash |
| Profile not switching | Profile file doesn't match foreground package | Verify profile name matches the exact package name |

---

## Module Not Active in LSPosed

**Issue:** InputBlocker doesn't appear in LSPosed Modules, or appears but is grayed out.

**Fix:**
1. Open LSPosed Manager
2. Go to **Modules** tab
3. Verify InputBlocker shows "Active" with version
4. Tap into it and ensure **System Framework** is checked
5. If missing entirely, the module didn't install — reflash the ZIP
6. If grayed out, your LSPosed version may be too old — update to v1.9.2+

**Verify:**
```bash
adb shell logcat -s InputBlocker:XposedHook
```
You should see: `Hook registered: dispatchMotionLocked`

---

## Boot Loop After Install

**Scenario:** Device won't boot past splash after flashing InputBlocker.

**Recovery:**
1. **Boot Safe Mode** — Press Volume Down repeatedly during boot
2. Open LSPosed → Modules → disable InputBlocker → reboot
3. If LSPosed safe mode needed:
   ```bash
   adb shell touch /data/adb/lsposed/disable
   adb reboot
   ```
4. Once booted, review `/data/adb/modules/inputblocker/config/profiles/default.conf` for invalid syntax
5. Re-enable and reboot

> **Prevention:** Always test new configs with the overlay on before rebooting. A bad overlay config won't boot-loop you, but a bad hook config can.

---

## Overlay Is Blocking Real Touches

**Issue:** The overlay mode is too aggressive and blocks touches you need.

**Fix:**
1. **Emergency gesture** — Press Volume Down × 3 → Volume Up × 3 (this kills the overlay immediately)
2. Open the companion app
3. Go to Region Editor → reduce block zone sizes or adjust press/duration thresholds
4. If using overlay mode (not LSPosed hook), switch to hook mode via Settings for more precise filtering

**Diagnostic:**
```bash
adb shell cat /data/adb/modules/inputblocker/config/profiles/default.conf
```
Check if `minPressure` is set too high (e.g., > 0.3 blocks many real taps — Android's "pressure" reflects contact patch size, so real fingers typically produce 0.15–1.0).

---

## Ghost Taps Not Being Filtered

**Issue:** Ghost taps are still visible and interacting with apps.

**Causes & fixes:**

| Cause | Check | Fix |
|---|---|---|
| LSPosed mode disabled | `lsposed_mode=1` in config? | Toggle "LSPosed Mode" in settings |
| No blocking zones defined | `cat default.conf` shows no regions? | Run auto-detection or add zones |
| Wrong package profile | Is foreground app profile loaded? | Check `logcat \| grep InputBlocker:Profile` |
| Regions too small | Ghost taps outside defined zones | Enlarge zones or add coverage |
| Contact area threshold too low | `minPressure < 0.05` | Set to 0.15 |
| Duration threshold too low | `maxDuration < 100ms` | Set to 300ms |

**Verify hook is live:**
```bash
adb shell logcat -s InputBlocker:XposedHook
# Should show: Blocked touch at (0.72, 0.34) pressure=0.03
```

---

## Crash Detected — Safe Mode Active

**Issue:** After a crash, InputBlocker enters safe mode and won't filter.

**What happened:** The Xposed hook caught an unexpected exception. The crash flag at `/data/adb/modules/inputblocker/config/crash_detected` was set.

**Resolution:**
```bash
# Clear the flag
adb shell rm /data/adb/modules/inputblocker/config/crash_detected

# If that doesn't work, check for the app-level flag
adb shell rm /data/data/com.inputblocker.app/files/crash_detected

# Then reboot or restart LSPosed scope
adb reboot
```

**Find the root cause:**
```bash
adb shell logcat -s InputBlocker:* '*:E' | grep -i crash
```

---

## Config Not Applying After Edit

**Issue:** You edited a `.conf` file but the changes aren't taking effect.

**Possible reasons:**
- **Config cache** — Regions are cached for 10 seconds. Wait or trigger a reload by toggling blocking off/on.
- **Wrong profile** — You edited `default.conf` but a per-app profile is overriding it. Check which profile is active:
  ```bash
  adb shell logcat -s InputBlocker:Profile
  ```
- **Syntax error** — An invalid line in the config causes the parser to reject the entire file. Validate with:
  ```bash
  adb shell cat /data/adb/modules/inputblocker/config/profiles/default.conf
  ```
- **Permissions** — After pushing via ADB, ensure file ownership is correct:
  ```bash
  adb shell chmod 644 /data/adb/modules/inputblocker/config/profiles/default.conf
  ```

---

## PC Designer Can't Connect

**Issue:** The PC Designer tool shows "No device found" or connection fails.

**Troubleshoot:**
1. Verify USB debugging is enabled on the device
2. Check `adb devices` shows your device as `device` (not `unauthorized`)
3. Re-authorize if needed:
   ```bash
   adb kill-server
   adb start-server
   adb devices  # check for prompt on device
   ```
4. Try wireless ADB:
   ```bash
   adb connect <device-ip>:5555
   ```
5. Restart the PC Designer tool
6. Check for firewall blocking ADB (port 5555)

---

## Companion App Crashes on Startup

**Issue:** The InputBlocker app crashes immediately when opened.

**Fix:**
```bash
# Clear app data (this preserves module config, but resets app settings)
adb shell pm clear com.inputblocker.app

# If that doesn't work, reinstall the APK from the module ZIP
adb shell /data/adb/modules/inputblocker/action.sh reinstall
```

---

## Profiles Not Switching

**Issue:** Per-app profile doesn't load when switching apps.

**Fix:**
1. Verify profile name matches exact package name (e.g., `com.example.app.conf`)
2. Check logcat: `adb shell logcat -s InputBlocker:Profile`
3. Ensure Accessibility Mode is enabled (foreground detection needs it)
4. Profile files are in `/data/adb/modules/inputblocker/config/profiles/`
5. Restart the companion app after creating a profile

---

## Pause/Resume Not Working

**Issue:** Tapping PAUSE doesn't stop blocking.

**Fix:**
1. Check overlay mode — visual overlay should show "PAUSED" with countdown
2. Notification should show Resume button during pause
3. For LSPosed mode: pause writes `paused=1` to config — verify with `adb shell cat /data/adb/modules/inputblocker/config/profiles/default.conf | grep paused`
4. If auto-resume doesn't trigger: check the service uptime (auto-resume timer runs in service poll loop)

---

## Crash Logs Show Nothing

**Issue:** CrashLogActivity displays "No crash logs found."

This is normal behavior — it only shows data after a crash has been detected and reported. To generate a test entry, the app writes crash logs on uncaught exceptions.

---

## Collecting Logs for Bug Reports

When opening a GitHub issue, include these logs:

```bash
# Block log (history of filtered touches)
adb shell cat /data/adb/modules/inputblocker/config/blocklog.txt

# Latency log (performance data)
adb shell cat /data/adb/modules/inputblocker/config/latency.log

# Current config
adb shell cat /data/adb/modules/inputblocker/config/profiles/default.conf

# Crash logs (if any)
adb shell ls -la /data/local/tmp/inputblocker/crash_logs/

# logcat filtered for InputBlocker
adb shell logcat -d -s InputBlocker:* '*:E'
```

Or use the **Share Log** button in the companion app to export everything at once.

---

## Factory Reset Module Config

To completely reset InputBlocker to a clean state:

```bash
adb shell rm -rf /data/adb/modules/inputblocker/config/*
adb shell mkdir -p /data/adb/modules/inputblocker/config/profiles
adb reboot
```

After reboot, the module will recreate default configs on first run.
