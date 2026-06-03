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
| **isExclude** | `0` or `1` | `0` = block zone, `1` = exclude zone (hole in a block) |
| **type** | `0`, `1`, `2` | `0` = rectangle, `1` = circle, `2` = ellipse |
| **x1, y1** | `0.0` – `1.0` | Top-left corner (normalized) |
| **x2, y2** | `0.0` – `1.0` | Bottom-right corner (normalized) |
| **minPressure** | `0.0` – `1.0` | Touch pressure threshold — ghosts below this are blocked |
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

The crash detection system operates across two layers:

1. **In-hook detection** — Every `beforeHookedMethod` is wrapped in try/catch. On exception:
   - Logs the error to logcat
   - Writes to `crash_detected` flag file
   - Re-throws so the system doesn't notice

2. **Boot-time detection** — The `ServiceManager` reads `crash_detected` on startup:
   - If flag exists: enters **safe mode** (all blocking disabled, overlay shows "SAFE MODE")
   - If flag absent: normal operation

**To exit safe mode:**
```bash
adb shell rm /data/adb/modules/inputblocker/config/crash_detected
adb reboot
```

Or just clear it from the companion app's settings screen.

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
