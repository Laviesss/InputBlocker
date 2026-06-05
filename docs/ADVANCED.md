# Advanced Configuration & Power User Guide

---

## Manual Config File Editing

Config files are stored at:

```
/data/adb/modules/inputblocker/config/profiles/default.conf
/data/adb/modules/inputblocker/config/profiles/<package_name>.conf
```

### Format

```ini
enabled=1
lsposed_mode=1

# One region per line: isExclude,type,x1,y1,x2,y2,minPressure,maxDuration
0,0,0.65,0.0,1.0,1.0,0.15,300
1,2,0.88,0.92,0.06,0.08,0.0,0
```

### Field Reference

| Field | Values | Description |
|---|---|---|
| **enabled** | `0` or `1` | Master toggle for this profile |
| **lsposed_mode** | `0` or `1` | `1` = LSPosed hook mode (recommended), `0` = overlay mode |
| **paused** | `0` or `1` | `1` = blocking suspended until explicitly resumed. Synced across all blocking services via `com.inputblocker.PAUSE` / `com.inputblocker.RESUME` broadcasts. |
| **isExclude** | `0` or `1` | `0` = block zone, `1` = exclude zone (hole in a block) |
| **type** | `0`, `1`, `2` | `0` = rectangle, `1` = circle, `2` = ellipse |
| **x1, y1** | `0.0` – `1.0` | Top-left corner (normalized) |
| **x2, y2** | `0.0` – `1.0` | Bottom-right corner (normalized) |
| **minPressure** | `0.0` – `1.0` | Touch contact area threshold — ghosts with smaller contact patches (lower "pressure") are blocked. On capacitive screens Android's "pressure" is actually contact patch size, not physical force. |
| **maxDuration** | ms | Touch duration threshold — touches longer than this are blocked (`0` = disabled) |

### Example: Block a right-side strip, exclude a button in it

```ini
enabled=1
lsposed_mode=1

# Block the right 35%
0,0,0.65,0.0,1.0,1.0,0.15,300
# Exclude a small circle in the bottom-right (keep the power button usable)
1,1,0.90,0.92,0.05,0.05,0.0,0
```

---

## DBSCAN Parameter Tuning

The auto-detection feature uses a DBSCAN-inspired clustering algorithm to identify ghost tap hotspots.

### Parameters

| Parameter | Default | Effect |
|---|---|---|
| **eps** | `0.03` | Maximum distance (in normalized coordinates) between points in the same cluster. Smaller values = more, smaller clusters. |
| **minPts** | `3` | Minimum points needed to form a cluster. Higher values ignore sparse ghost taps. |

### When to Tune

- **eps too small**: Ghost taps that should be one region get split into many tiny regions
- **eps too large**: Separate ghost tap zones merge into one oversized block
- **minPts too high**: Sparse but real ghost taps get ignored
- **minPts too low**: Noise gets classified as ghost taps

**Tip:** Start with defaults (`eps=0.03, minPts=3`). If auto-detection produces too many small regions, increase `eps` to `0.05`. If it misses sparse ghost taps, decrease `minPts` to `2`.

---

## Adaptive Optimization Internals

The `AdaptiveBlockingManager` reads the block log (`blocklog.txt`) every 60 minutes and tightens region bounds when it detects ≥10 ghost taps at similar normalized coordinates.

**How it works:**
1. Scans `blocklog.txt` for clustered ghost tap locations
2. For each existing block zone, checks if there's a tighter sub-region containing ≥90% of the detected ghosts
3. If found, shrinks the zone to the tighter bounds
4. Logs the optimization to logcat (`InputBlocker:Adaptive`)

**To force a re-optimization:**
```bash
adb shell rm /data/adb/modules/inputblocker/config/blocklog.txt
adb shell pkill -f com.inputblocker.app  # triggers service restart
```

The optimization never enlarges zones — it only tightens them based on actual data.

---

## Shell Scripting

All module operations are accessible via shell for automation.

### Read Current Config
```bash
adb shell cat /data/adb/modules/inputblocker/config/profiles/default.conf
```

### Push a New Config
```bash
adb push my_config.conf /data/adb/modules/inputblocker/config/profiles/default.conf
```

### Toggle Blocking
```bash
# Enable blocking
adb shell sed -i 's/enabled=0/enabled=1/' /data/adb/modules/inputblocker/config/profiles/default.conf

# Disable blocking (kill switch)
adb sh -c 'echo "1" > /data/adb/modules/inputblocker/config/kill_switch'
```

### View Filter Stats
```bash
adb shell cat /data/adb/modules/inputblocker/config/blocklog.txt | tail -50
```

### Live Monitoring
```bash
adb shell logcat -s InputBlocker:XposedHook
# Output: "Blocked touch at (0.72, 0.34) pressure=0.03"
```

### Remote Management Script Example
```bash
#!/system/bin/sh
# Quick-check ghost tap count
BLOCKLOG="/data/adb/modules/inputblocker/config/blocklog.txt"
COUNT=$(wc -l < "$BLOCKLOG")
echo "Ghost taps blocked since last reset: $COUNT"
```

---

## Crash Detection Mechanism

The crash detection system operates across three layers:

1. **In-hook detection** — Every `beforeHookedMethod` is wrapped in try/catch. On exception:
   - Logs the error to logcat
   - Calls `InputBlockerServiceManager.reportCrash(Throwable)` which writes the full stack trace to `/data/local/tmp/inputblocker/crash_logs/<timestamp>.log`
   - Increments the crash counter at `/data/local/tmp/inputblocker/crash_count`
   - Re-throws so the system doesn't notice

2. **Boot-time detection** — The `ServiceManager` reads `crash_detected` on startup:
   - If flag exists: enters **safe mode** (all blocking disabled, overlay shows "SAFE MODE")
   - If flag absent: normal operation

3. **3-strike safe mode** — On each boot, the crash counter at `/data/local/tmp/inputblocker/crash_count` is checked:
   - If count >= 3: enters safe mode automatically, regardless of other flags
   - Counter resets on clean shutdown
   - Prevents repeated crash loops from locking the device

### Viewing Crash Logs In-App

The companion app includes `CrashLogActivity` for viewing crash reports directly on the device:

1. Open the InputBlocker companion app
2. Tap **Quick Actions** in the menu
3. Select **View Crash Logs**
4. Each entry shows timestamp, error message, and full stack trace

Crash logs are stored at `/data/local/tmp/inputblocker/crash_logs/` and persist across reboots.

### Manual Crash Log Access

```bash
# List available crash logs
adb shell ls -la /data/local/tmp/inputblocker/crash_logs/

# Read a specific crash log
adb shell cat /data/local/tmp/inputblocker/crash_logs/crash_20260101_120000.log

# Check crash count
adb shell cat /data/local/tmp/inputblocker/crash_count

# Reset crash counter (after investigating)
adb shell echo "0" > /data/local/tmp/inputblocker/crash_count
```

### Exiting Safe Mode

```bash
adb shell rm /data/adb/modules/inputblocker/config/crash_detected
adb shell echo "0" > /data/local/tmp/inputblocker/crash_count
adb reboot
```

Or just clear it from the companion app's settings screen.

---

## ConfigFileObserver (Real-Time Config Reload)

The companion app reloads configuration changes in real time without requiring a service restart. This is powered by `ConfigFileObserver`, a two-layer file monitoring system:

### How It Works

```
Config File Change → FileObserver (inotify)
                   → On failure/unsupported FS → 2s polling fallback
                   → Config reloaded in memory
                   → Services pick up new region definitions
```

- **Primary layer**: `FileObserver` uses Linux inotify to receive instant notifications when config files change, are created, or are deleted.
- **Fallback layer**: On filesystems that don't support inotify (certain FUSE mounts, some MTP or virtual storage), a polling loop checks file modification timestamps every 2 seconds.
- **Scope**: Monitors all `.conf` files in the profiles directory, plus `kill_switch`, `crash_detected`, and `paused` state changes.

### What Triggers a Reload

- Editing `default.conf` or any per-app profile via ADB or PC Designer
- Creating or deleting a profile file
- Writing `paused=1` or `paused=0` to the config
- Creating or removing the `kill_switch` file
- Crash flag changes

When a change is detected, the relevant service (OverlayService, AccessibilityService, or hook module) reloads its region definitions from disk. No service restart required.

### Verifying Config Changes

```bash
# Watch for reload events in logcat
adb shell logcat -s InputBlocker:ConfigFileObserver

# Expected output:
# "Config file modified: /data/adb/modules/inputblocker/config/profiles/default.conf"
# "Reloaded 3 regions from config"
```

---

## Pause/Resume Blocking

The companion app provides a pause/resume mechanism that temporarily suspends all blocking without disabling the profile. This is useful for scenarios where you need full touch access temporarily.

### How to Pause

| Method | Steps |
|---|---|
| **Quick Actions toggle** | Open companion app → Quick Actions → tap Pause |
| **Notification buttons** | Pull down notification → tap Pause 5min or Pause 30min |
| **Shell command** | `adb shell am broadcast -a com.inputblocker.PAUSE` |

### How to Resume

| Method | Steps |
|---|---|
| **Quick Actions toggle** | Open companion app → Quick Actions → tap Resume |
| **Notification buttons** | Pull down notification → tap Resume |
| **Auto-resume** | Timer expires and re-enables blocking automatically |
| **Shell command** | `adb shell am broadcast -a com.inputblocker.RESUME` |

### How It Works

1. The MainActivity toggle (or notification action) broadcasts `com.inputblocker.PAUSE` or `com.inputblocker.RESUME` as an Android intent.
2. All three blocking services listen for these intents:
   - **OverlayService** — stops blocking touches in the overlay
   - **AccessibilityService** — stops blocking via accessibility gesture interception
   - **LSPosed hook** — reads `paused=1` from the config file and stops filtering at the input dispatcher level
3. The `paused=1` flag is written to the config file, so pause state persists across service restarts and reboots.
4. Each service runs an auto-resume timer. When the selected pause duration (5 min, 30 min) expires, blocking is re-enabled automatically.

### Notification Action Buttons

The persistent notification includes three action buttons:

- **Pause 5min** — Suspends blocking for 5 minutes, then auto-resumes
- **Pause 30min** — Suspends blocking for 30 minutes, then auto-resumes
- **Resume** — Immediately re-enables blocking (only shown when paused)

These buttons work with all three services, not just the foreground service that posted the notification.

### Shell Script Automation

```bash
#!/system/bin/sh
# Pause blocking for a custom duration (in milliseconds)
am broadcast -a com.inputblocker.PAUSE
sleep 300   # 5 minutes
am broadcast -a com.inputblocker.RESUME

# Check current pause state
grep "^paused=" /data/adb/modules/inputblocker/config/profiles/default.conf
```

---

## Custom Emergency Gesture

The VolumeButtonListenerService handles emergency gesture detection. By default, it uses:

```
Volume Down × 3 → Volume Up × 3
```

The gesture sequence and timing are configurable at:
`/data/adb/modules/inputblocker/config/gesture.conf`

```ini
sequence=KEYCODE_VOLUME_DOWN,KEYCODE_VOLUME_DOWN,KEYCODE_VOLUME_DOWN,KEYCODE_VOLUME_UP,KEYCODE_VOLUME_UP,KEYCODE_VOLUME_UP
timeout_ms=3000
```

- `sequence`: Comma-separated list of keycodes
- `timeout_ms`: Max time to complete the sequence

---

## Building Custom Presets for Sharing

Presets (`.ibpreset` files) are JSON with metadata:

```json
{
  "version": 1,
  "device_model": "Pixel 6",
  "config_version": "0.1.0",
  "block_count": 12,
  "regions": [
    {"isExclude": false, "type": 0, "x1": 0.65, "y1": 0.0, "x2": 1.0, "y2": 1.0, "minPressure": 0.15, "maxDuration": 300}
  ]
}
```

To share a preset:
1. In companion app: **Export** → name your preset
2. File saved to `/storage/emulated/0/InputBlocker/presets/<name>.ibpreset`
3. Share the file — normalized coordinates make it cross-device

---

## Performance Profiling

To measure the hook's overhead:

```bash
# Enable performance logging
adb shell setprop persist.inputblocker.perf_log 1

# Check latency log
adb shell cat /data/adb/modules/inputblocker/config/latency.log

# Each entry shows: timestamp,dispatch_us,hook_us
# dispatch_us: total dispatch time
# hook_us: time spent in InputBlocker filter
```

Expected overhead: **< 50µs** per touch event. If you see > 200µs, check for excessive logging or many overlapping regions.
