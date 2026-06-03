# InputBlocker Technical Documentation

InputBlocker is a system-level utility that mitigates **ghost taps** (phantom touches) caused by hardware digitizer failure on Android devices. Unlike traditional overlay-based blockers, InputBlocker operates at the **input dispatcher level** within `system_server`, enabling intelligent touch filtering based on physical properties rather than blind coordinate blocking.

---

## Table of Contents

1. [Core Philosophy](#core-philosophy)
2. [System Architecture](#system-architecture)
3. [The Android Engine (Hook Module)](#the-android-engine-hook-module)
4. [The Android Companion App (Overlay)](#the-android-companion-app-overlay)
5. [The PC Designer](#the-pc-designer)
6. [Configuration Reference](#configuration-reference)
7. [Auto-Tuning Logic](#auto-tuning-logic)
8. [Installation & Recovery](#installation--recovery)
9. [Developer Reference](#developer-reference)

---

## Core Philosophy

### The Problem: Digitizer Noise

Hardware failure in touch panels often manifests as "ghost taps" — electrical noise that the system interprets as touch events. These events typically share three characteristics:

1. **Small Contact Area** — Ghost taps from electrical noise produce a tiny capacitive contact patch, reported as low "pressure" by `MotionEvent.getPressure()`.
2. **Abnormal Duration** — They are often near-instantaneous spikes or unnaturally long static holds.
3. **Localization** — They tend to cluster in specific "dead zones" on the panel.

### The Solution: Conditional Touch Filtering

Rather than blocking an entire screen region unconditionally, InputBlocker applies a **conditional filter**:

- **Standard Blocking** — All input in the region is dropped unconditionally.
- **Filtered Blocking** — Input is dropped **only if** it matches hardware noise criteria (contact area below threshold OR duration exceeds threshold). This preserves screen usability in the affected area.

### The Formula

```
Block = (ContactArea < MinPressure) OR (Duration > MaxDuration)

> **Note:** On capacitive screens `MotionEvent.getPressure()` reflects **touch contact area** (patch size), not physical force. The `minPressure` config parameter name follows Android's API naming.

- **`MinPressure`**: Filters out ghost taps with very small capacitive contact area.
- **`MaxDuration`**: Filters out "stuck" pixels simulating a long-press.

---

## System Architecture

InputBlocker is composed of four primary components:

```
┌─────────────────────────────────────────────────────────────┐
│                      PC Designer                            │
│              (Kotlin/Compose Desktop)                       │
└──────────┬──────────────────────────────────────┬───────────┘
           │ ADB Push (config)                    │ ADB Pull (logs)
           ▼                                      ▼
┌─────────────────────────────────────────────────────────────┐
│                 Android Companion App                        │
│              Overlay Service + UI                            │
└──────────┬──────────────────────────────────────┬───────────┘
           │ read config                          │ start/stop
           ▼                                      ▼
┌─────────────────────────────────────────────────────────────┐
│              Module Root Files                               │
│          (/data/adb/modules/inputblocker/)                   │
└──────────┬──────────────────────────────────────┬───────────┘
           │ read config                          │ crash detection
           ▼                                      ▼
┌─────────────────────────────────────────────────────────────┐
│  InputDispatcher Hook (LSPosed/Vector)                       │
│  com.android.server.input.InputDispatcher                   │
│  .dispatchMotionLocked                                       │
└─────────────────────────────────────────────────────────────┘
```

### 1. The Shared Core (KMP)

A Kotlin Multiplatform module that ensures the mathematical definition of a `Region` — its coordinate system, shape types, and serialization — is identical across both the PC and Android platforms. This prevents desyncs between the designer and the enforcement engine.

### 2. The Engine (LSPosed/Vector Hook Module)

The enforcement layer. It hooks `com.android.server.input.InputDispatcher.dispatchMotionLocked` in `system_server` to intercept touch events **before** they are dispatched to any application. Built against the legacy Xposed API (`de.robv.android.xposed`), which is fully compatible with the Vector framework (formerly LSPosed) at runtime.

### 3. The Companion App (Android)

Provides the **overlay service** that gives visual feedback on blocking regions, plus a UI for managing profiles, viewing logs, and triggering emergency resets. It also handles crash detection and safe mode logic.

### 4. The Designer (PC Tool)

A visual interface for defining blocking regions and tuning filter thresholds. It interacts with the device via **ADB** to push configurations and pull diagnostic logs.

**Data Flow:**

```
PC Tool → ADB Push → /config/profiles/default.conf
                    → Vector Hook Engine
                    → Touch Filter
                    → Android OS
```

---

## The Android Engine (Hook Module)

### The Hook Point

The engine hooks `com.android.server.input.InputDispatcher.dispatchMotionLocked`. This is the central point of entry for all motion events in Android's input pipeline. By calling `setResult(null)`, the hook effectively drops the event, preventing it from reaching the window manager and any application.

### Cache Architecture

To minimize overhead on the input dispatch hot path, the module uses a two-tier cache:

| Cache | TTL | Purpose |
|---|---|---|
| **Config cache** | 10 seconds | Region definitions and enabled state |
| **Display metrics** | 60 seconds | Screen dimensions for coordinate normalization |

Both caches are lazily invalidated — checked only when a touch event arrives. This keeps the hot path lightweight.

### Filtering Logic

Every touch event is evaluated against configured regions:

```kotlin
// Priority 1: Exclude zones (whitelist) — always allowed
for (region in regions.filter(Region::isExclude)) {
    if (isInsideRegion(normalizedX, normalizedY, region))
        return  // let touch pass
}

// Priority 2: Blocking zones — conditionally blocked
for (region in regions.filterNot(Region::isExclude)) {
    if (isInsideRegion(normalizedX, normalizedY, region)) {
        if (shouldBlockSurgically(motionEvent, region)) {
            setResult(null)  // drop the event
            return
        }
    }
}
// Default: touch passes
```

### Surgical Blocking

The `shouldBlockSurgically` function evaluates physical touch properties:

```kotlin
fun shouldBlockSurgically(event: MotionEvent, region: Region): Boolean {
    return event.pressure < region.minPressure
        || (event.eventTime - event.downTime) > region.maxDuration
}
```

### Shape Types

Three geometric shapes are supported, all using normalized coordinates (0.0–1.0):

| Type ID | Shape | Parameters |
|---|---|---|
| `0` | Rectangle | `(x1, y1)` top-left, `(x2, y2)` bottom-right |
| `1` | Circle | `(x1, y1)` center, `x2` = radius |
| `2` | Ellipse | `(x1, y1)` center, `x2` = radius X, `y2` = radius Y |

---

## The Android Companion App (Overlay)

The companion app provides:

- **OverlayService**: A foreground service that draws blocking region indicators on screen. Uses `WindowManager.LayoutParams` with `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL` — it never captures focus and passes through touches outside configured blocked regions. When no regions are configured, it dynamically adds `FLAG_NOT_TOUCHABLE` so the overlay does not interfere with normal device use.
- **VolumeButtonListenerService**: Listens for emergency reset gesture sequences (configurable button combos).
- **Configuration UI**: Edit profiles, toggle blocking, view block logs and latency data.
- **Crash Detection**: On next launch after an abnormal shutdown, the app enters safe mode (blocking force-disabled).

### Safe Mode Flow

```
Boot → InputBlockerServiceManager.startServices()
     → Check crash_detected flag
     → Check normal_shutdown flag
     → If abnormal shutdown without crash flag:
         → Write enabled=0, force_safe_mode=1 to config
         → Start overlay in safe mode (no blocking)
```

---

## The PC Designer

### Visual Canvas

The designer uses a **normalized coordinate system** (0.0 to 1.0), making configurations resolution-independent. One config works across different screen sizes and orientations.

### ADB Bridge

The tool automatically detects the root manager path (Magisk, KernelSU, APatch, SuperSU) and pushes configurations to the correct directory:

```
/data/adb/modules/inputblocker/config/profiles/
```

It can also pull `blocklog.txt` and `latency.log` for analysis.

### Hotspot Analysis

The engine logs every blocked touch to `blocklog.txt`. The PC tool uses the **DBSCAN** (Density-Based Spatial Clustering of Applications with Noise) algorithm to:

1. Identify clusters of ghost taps from log data.
2. Calculate the tightest bounding box around each cluster.
3. Suggest an optimized region size to maximize usable screen area.

---

## Configuration Reference

### File Format

Configurations are stored in plain-text CSV files with one region per line:

```
isExclude,type,x1,y1,x2,y2,minPressure,maxDuration
```

| Column | Name | Type | Range | Description |
|---|---|---|---|---|
| 1 | `isExclude` | Bool | `0` or `1` | `1` = Exclude zone (touch always passes), `0` = Blocking zone |
| 2 | `type` | Int | `0`, `1`, `2` | Shape: `0`=Rect, `1`=Circle, `2`=Ellipse |
| 3 | `x1` | Float | 0.0–1.0 | Left (Rect) or Center-X (Circle/Ellipse) |
| 4 | `y1` | Float | 0.0–1.0 | Top (Rect) or Center-Y (Circle/Ellipse) |
| 5 | `x2` | Float | 0.0–1.0 | Right (Rect) or Radius-X (Circle/Ellipse) |
| 6 | `y2` | Float | 0.0–1.0 | Bottom (Rect) or Radius-Y (Ellipse only) |
| 7 | `minPressure` | Float | 0.0–1.0 | Minimum pressure for a "real" touch |
| 8 | `maxDuration` | Long | ms | Maximum duration before flagged as ghost |

### Example: default.conf

```
# Blocking region covering right third of screen
0,0,0.65,0.0,1.0,1.0,0.15,300
# Exclude zone for the back button area
1,0,0.75,0.85,1.0,1.0,0.0,0
```

### Profile System

The engine supports **per-app profiles**. When a config exists at:

```
/data/adb/modules/inputblocker/config/profiles/<package_name>.conf
```

it takes priority over `default.conf` when that app is in the foreground. This allows different blocking strategies for different applications.

### Special Config Flags

| File | Effect |
|---|---|
| `config/kill_switch` | If present, all blocking is disabled immediately. |
| `config/test_mode` | If present, the hook drops **all** touch events for performance testing. |
| `config/crash_detected` | Written by the hook on critical failure. Triggers safe mode on next boot. |

---

## Auto-Tuning Logic

InputBlocker operates as a closed-loop feedback system:

1. **Log** — The Engine blocks a ghost tap and logs coordinates + pressure + duration to `blocklog.txt`.
2. **Analyze** — The PC Tool reads the log and identifies clusters using DBSCAN.
3. **Shrink** — The Designer calculates the minimum bounding shape that still covers the noise cluster.
4. **Deploy** — The optimized config is pushed to the device via ADB.
5. **Repeat** — Over time, the blocking regions converge to the minimum effective area.

---

## Installation & Recovery

### Initial Setup

1. Flash `InputBlockerModule.zip` via your root manager (Magisk / KernelSU / APatch).
2. Enable the module in LSPosed for the **System Framework** scope.
3. Reboot.
4. Open the InputBlocker companion app to verify the overlay is active.
5. Use the PC Designer to map dead zones and push the config.

### Emergency Recovery

| Method | How-To |
|---|---|
| **Emergency gesture** | Default: Volume Down ×3 → Volume Up ×3. Disables blocking instantly. |
| **Kill switch** | Create file `/data/adb/modules/inputblocker/config/kill_switch` containing `1`. Module enters dormant state. |
| **Safe Mode** | If the module causes a boot loop, boot into Android Safe Mode and disable the module in LSPosed. |
| **ADB** | `adb shell rm /data/adb/modules/inputblocker/config/crash_detected` to clear crash flag. |

---

## Developer Reference

### Coordinate System

All coordinates are stored as normalized floats (0.0 to 1.0):

```
NormalizedX = PixelX / ScreenWidth
NormalizedY = PixelY / ScreenHeight
```

Conversion is handled by the KMP shared core on both PC and Android sides.

### Hooking Point

```
Class:  com.android.server.input.InputDispatcher
Method: dispatchMotionLocked(IBinder, MotionEvent, ...args)
```

Hooked via `XposedHelpers.findAndHookMethod`. Returning `null` via `param.setResult(null)` drops the event at the highest possible level in the input pipeline — before the window manager, before any application.

### Async Logging

To prevent I/O on the input dispatch thread, all logging is performed via an asynchronous `LinkedBlockingQueue` and a dedicated logger thread. Log files are rotated at 200 KB.

### File Paths (Module Root)

| Path | Purpose |
|---|---|
| `/data/adb/modules/inputblocker/config/profiles/default.conf` | Default config |
| `/data/adb/modules/inputblocker/config/profiles/<pkg>.conf` | Per-app profile |
| `/data/adb/modules/inputblocker/config/latency.log` | Nano-time latency per touch |
| `/data/adb/modules/inputblocker/config/blocklog.txt` | Blocked touch records |
| `/data/adb/modules/inputblocker/config/kill_switch` | Emergency disable |
| `/data/adb/modules/inputblocker/config/test_mode` | Performance test mode |
| `/data/adb/modules/inputblocker/config/crash_detected` | Crash flag |

### Crash Protection

The hook module has two layers of crash protection:

1. **Hot-path safety**: Every `dispatchMotionLocked` invocation is wrapped in `try/catch(Throwable)`. On failure, a crash flag is written.
2. **Boot-time detection**: On app startup, the companion app checks for the crash flag and — if present — writes `enabled=0` to the config, forcing safe mode.
