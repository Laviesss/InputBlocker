# InputBlocker Technical Documentation

InputBlocker is a system-level utility designed to mitigate "ghost taps" (phantom touches) caused by hardware digitizer failure on Android devices.

Unlike traditional overlays, InputBlocker operates at the **input dispatcher level**, enabling the filtering of touches based on physical properties rather than just screen coordinates.

---

## ?? Table of Contents
1. [Core Philosophy](#core-philosophy)
2. [System Architecture](#system-architecture)
3. [The Android Engine](#the-android-engine)
4. [The PC Designer](#the-pc-designer)
5. [Configuration Reference](#configuration-reference)
6. [Auto-Tuning Logic](#auto-tuning-logic)
7. [Installation & Recovery](#installation--recovery)

---

## ?? Core Philosophy

### The Problem: Digitizer Noise
Hardware failure in screens often manifests as "ghost taps"—electrical noise that the system interprets as touch events. These events typically share three characteristics:
1. **Low Pressure**: They lack the physical force of a human finger.
2. **Abnormal Duration**: They are often near-instantaneous spikes or unnaturally long, static holds.
3. **Localization**: They typically cluster in specific "dead zones" on the panel.

### The Solution: Touch Filtering
Instead of blocking a region entirely, InputBlocker applies a conditional filter.
- **Standard Blocking**: All input in the region is dropped.
- **Filtered Blocking**: Input is dropped **only if** it meets the criteria for hardware noise (Pressure < Threshold OR Duration > Threshold). This preserves the usability of the screen.

---

## ??? System Architecture

InputBlocker is split into three primary components:

### 1. The Designer (PC Tool)
A visual interface used to define blocking regions and tune filter thresholds. It interacts with the device via **ADB (Android Debug Bridge)** to push configurations and pull diagnostic logs.

### 2. The Shared Core (KMP)
A Kotlin Multiplatform module that ensures the mathematical definition of a `Region` (center, radius, bounds) is identical across both the PC and Android platforms.

### 3. The Engine (Xposed Module)
The enforcement layer. It hooks the Android system server to intercept touch events before they are dispatched to any application.

**Data Flow**:
`PC Tool` $\rightarrow$ `ADB Push` $\rightarrow$ `/config/profiles/default.conf` $\rightarrow$ `Xposed Engine` $\rightarrow$ `Touch Filter` $\rightarrow$ `Android OS`

---

## ?? The Android Engine

### The Hook
The engine intercepts `com.android.server.input.InputDispatcher.dispatchMotionLocked`. By hooking this method, the tool can drop events at the highest possible level in the input pipeline.

### Filtering Logic
Every touch event within a defined region is evaluated:
$\text{Block} = (\text{Pressure} < \text{MinPressure}) \lor (\text{Duration} > \text{MaxDuration})$

- **MinPressure**: Filters out low-pressure electrical noise.
- **MaxDuration**: Filters out "stuck" pixels simulating a long-press.

### Layering & Priority
1. **Exclude Zones**: Highest priority. Any touch here is **always allowed**.
2. **Blocking Zones**: Filtered blocking is applied here.
3. **Default**: Any touch not in a zone is allowed.

---

## ?? The PC Designer

### Visual Canvas
The designer uses a normalized coordinate system (0.0 to 1.0), making configurations resolution-independent. 

### ADB Bridge
The tool automatically detects the root manager path (Magisk, KernelSU, APatch, SuperSU) to ensure configurations are pushed to the correct directory.

### Hotspot Analysis
The engine logs every blocked touch to `blocklog.txt`. The PC tool uses the **DBSCAN (Density-Based Spatial Clustering of Applications with Noise)** algorithm to:
1. Identify clusters of ghost taps.
2. Calculate the tightest possible bounding box around those clusters.
3. Suggest a reduced region size to the user to maximize usable screen area.

---

## ?? Configuration Reference

### CSV Format
Configurations are stored in plain-text CSV files.
`isExclude, type, x1, y1, x2, y2, minPressure, maxDuration`

| Column | Name | Type | Description |
| :--- | :--- | :--- | :--- |
| 1 | `isExclude` | Bool | `1` = Exclude (Allow), `0` = Blocking |
| 2 | `type` | Int | `0` = Rect, `1` = Circle, `2` = Ellipse |
| 3-4 | `x1, y1` | Float | Top-Left (Rect) or Center (Circle/Ellipse) |
| 5-6 | `x2, y2` | Float | Bottom-Right (Rect) or Radius (Circle/Ellipse) |
| 7 | `minPressure` | Float | Min pressure required for a "real" touch |
| 8 | `maxDuration` | Long | Max duration (ms) before touch is flagged as ghost |

### Profile System
The engine supports per-app profiles. If a config exists at `/config/profiles/[package_name].conf`, it takes priority over `default.conf` when that app is in the foreground.

---

## ?? Auto-Tuning Logic

InputBlocker operates as a closed-loop feedback system:
1. **Log**: The Engine blocks a ghost tap and logs its coordinates.
2. **Analyze**: The PC Tool identifies the cluster using DBSCAN.
3. **Shrink**: The Designer suggests a smaller region that still covers the noise.
4. **Deploy**: The updated config is pushed to the device.

---

## ??? Installation & Recovery

### Setup
1. Flash `InputBlocker.zip` via your root manager.
2. Enable the module in LSPosed and reboot.
3. Use the PC Designer to map your dead zones.

### Emergency Recovery
To prevent accidental screen lockouts:
- **Emergency Gesture**: Hold the top-left corner (5% area) for 3 seconds to disable all blocking.
- **Kill Switch**: Create a file at `/data/adb/modules/inputblocker/config/kill_switch` containing `1` to force the module into a dormant state.
