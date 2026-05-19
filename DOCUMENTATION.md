# InputBlocker Technical Documentation (v0.1.0)

Welcome to the official technical documentation for **InputBlocker**. InputBlocker is a professional-grade, system-level input filtering ecosystem designed to combat hardware-level digitizer failures (commonly known as "ghost taps") on Android devices.

Unlike traditional blocking apps that simply overlay a transparent view, InputBlocker operates at the **input dispatcher level**, allowing for "Surgical Filtering" based on physical touch properties.

---

## 📖 Table of Contents
1. [Core Philosophy](#core-philosophy)
2. [System Architecture](#system-architecture)
3. [The Android Engine](#the-android-engine)
    - [The Xposed Hook](#the-xposed-hook)
    - [Surgical Filtering Logic](#surgical-filtering-logic)
    - [Region Layering](#region-layering)
    - [Emergency Recovery](#emergency-recovery)
4. [The PC Setup Tool](#the-pc-setup-tool)
    - [Visual Designer](#visual-designer)
    - [ADB Bridge](#adb-bridge)
    - [Log Analysis & Hotspots](#log-analysis--hotspots)
5. [Configuration Reference](#configuration-reference)
    - [The CSV Format](#the-csv-format)
    - [Profile System](#profile-system)
6. [Adaptive Optimization](#adaptive-optimization)
7. [Installation & Setup](#installation--setup)
8. [Troubleshooting](#troubleshooting)

---

## 🎯 Core Philosophy

### The Problem: Digitizer Failure
When a screen's digitizer fails, it often generates "ghost taps"—electrical noise that the system interprets as a user touch. These taps are usually:
1. **Low Pressure**: They lack the physical force of a real finger.
2. **Abnormal Duration**: They are either instantaneous spikes or unnaturally long holds.
3. **Localized**: They usually occur in specific "dead zones" on the panel.

### The Solution: Surgical Blocking
InputBlocker does not just block a region; it blocks **specific types of touches** within that region. 
- **Blunt Blocking**: "Block everything in this rectangle." (Prevents use of the area).
- **Surgical Blocking**: "Block touches in this rectangle **ONLY IF** they have pressure $< X$ or duration $> Y$." (Allows real touches to pass through while killing ghost taps).

---

## 🏗️ System Architecture

InputBlocker is composed of three primary modules that form a closed-loop feedback system:

### 1. The Designer (PC Tool)
The control center. It allows users to visually map out blocking regions and tune their surgical thresholds using a high-precision canvas. It communicates with the device via **ADB (Android Debug Bridge)**.

### 2. The Shared Core (KMP)
A Kotlin Multiplatform module that contains the mathematical definitions of a `Region` and the logic for coordinate normalization. This ensures that a "Circle" defined on the PC is interpreted exactly the same way by the Android engine.

### 3. The Engine (Android App/Xposed)
The enforcement layer. It hooks into the Android system server's `InputDispatcher` to intercept every touch event before it reaches any application.

**Data Flow**:
`PC Tool` $\xrightarrow{\text{ADB Push}}$ `/config/profiles/default.conf` $\xrightarrow{\text{Xposed Load}}$ `Surgical Filter` $\xrightarrow{\text{Block/Allow}}$ `Android OS`

---

## 📱 The Android Engine

### The Xposed Hook
The engine hooks `com.android.server.input.InputDispatcher.dispatchMotionLocked`. This is the "bottleneck" of all touch input on Android. By intercepting events here, InputBlocker can stop a ghost tap before it ever triggers a click in an app.

### Surgical Filtering Logic
Every touch event is passed through the `shouldBlockSurgically` filter:
$$\text{Block} = (\text{Pressure} < \text{MinPressure}) \lor (\text{Duration} > \text{MaxDuration})$$

- **MinPressure**: Filters out "light" electrical noise.
- **MaxDuration**: Filters out "stuck" pixels that simulate a long-press.

### Region Layering
InputBlocker supports two types of zones:
1. **Exclude Zones (White-lists)**: High-priority areas (e.g. the Home button or a specific app icon). If a touch falls here, it is **always allowed**, regardless of other rules.
2. **Blocking Zones (Black-lists)**: Areas where surgical filtering is applied.

**Priority Order**: `Exclude Zone` $\rightarrow$ `Blocking Zone` $\rightarrow$ `Allow`.

### Emergency Recovery
To prevent users from accidentally blocking their entire screen, two safety mechanisms are implemented:
1. **The Emergency Gesture**: Holding the top-left corner (top 5% of screen) for 3 seconds triggers an immediate override, disabling all blocking.
2. **The Kill Switch**: A file at `/data/adb/modules/inputblocker/config/kill_switch`. If this file contains `1`, the module enters a dormant state.

---

## 💻 The PC Setup Tool

### Visual Designer
The tool provides a professional canvas that mirrors the device screen.
- **Normalization**: All coordinates are stored as floats from `0.0` to `1.0`. This ensures that configs created on one device work on another device with a different resolution.
- **Interactive Handles**: Users can drag regions to move them or use corner handles to resize them.
- **Property Inspector**: A sidebar allowing real-time adjustment of the surgical thresholds.

### ADB Bridge
The tool uses a custom `ADBHelper` to:
- Auto-detect the module path (supporting Magisk, KernelSU, APatch, and SuperSU).
- Push configurations as `.conf` files to the device's internal storage.
- Pull `blocklog.txt` for diagnostic analysis.

### Log Analysis & Hotspots
When the Android engine blocks a touch, it logs the coordinates to `blocklog.txt`. The PC tool can analyze this file to find **Hotspots**.
- **Calculation**: The tool identifies clusters of blocked touches.
- **Suggestion**: It calculates a new, smaller bounding box that encompasses the ghost taps, allowing the user to shrink their blocking region and reclaim usable screen space.

---

## ⚙️ Configuration Reference

### The CSV Format
Configurations are stored in plain-text CSV files. Each region is defined on a single line:
`isExclude, type, x1, y1, x2, y2, minPressure, maxDuration`

| Column | Name | Type | Description |
| :--- | :--- | :--- | :--- |
| 1 | `isExclude` | Boolean (0/1) | `1` = Exclude Zone, `0` = Blocking Zone |
| 2 | `type` | Integer | `0` = Rectangle, `1` = Circle, `2` = Ellipse |
| 3-4 | `x1, y1` | Float (0-1) | Top-Left (Rect) or Center (Circle/Ellipse) |
| 5-6 | `x2, y2` | Float (0-1) | Bottom-Right (Rect) or Radius (Circle/Ellipse) |
| 7 | `minPressure` | Float | Minimum pressure threshold for a "real" touch |
| 8 | `maxDuration` | Long (ms) | Maximum duration before a touch is flagged as a ghost |

**Example Line**:
`0,0,0.1,0.1,0.2,0.2,0.05,2000`
*(A blocking rectangle from 10% to 20% of the screen, blocking touches with pressure < 0.05 or duration > 2s)*

### Profile System
The engine supports per-app profiles. If a file exists at `/config/profiles/[package_name].conf`, the engine will load that specific profile when that app is in the foreground. Otherwise, it falls back to `default.conf`.

---

## 📈 Adaptive Optimization

InputBlocker features a self-optimizing loop:
1. **Sensing**: The engine blocks a ghost tap and logs the exact $(x, y)$ coordinate.
2. **Analysis**: The `AdaptiveBlockingManager` (on-device) or the PC Tool analyzes these logs.
3. **Shrinking**: If ghost taps are found to be clustered in a smaller area than the current region, the boundaries are adjusted.
4. **Enforcement**: The updated `.conf` is saved, and the engine reloads it.

This process reduces the "collateral damage" of blocking, ensuring the smallest possible area of the screen is affected.

---

## 🛠️ Installation & Setup

### Prerequisites
- **Root Access**: Magisk, KernelSU, or APatch.
- **Xposed Framework**: LSPosed (Recommended).
- **ADB**: Android Debug Bridge installed on PC.

### Setup Steps
1. **Install Module**: Flash the `InputBlocker.zip` via your root manager.
2. **Enable Module**: Enable "InputBlocker" in the LSPosed manager and reboot.
3. **Connect PC**: Enable USB Debugging on the device and connect to the PC Setup Tool.
4. **Configure**: Use the Visual Designer to map your ghost tap zones and push the config.

---

## ❓ Troubleshooting

| Issue | Possible Cause | Solution |
| :--- | :--- | :--- |
| **Regions not blocking** | Module not enabled in LSPosed | Check LSPosed $\rightarrow$ Modules $\rightarrow$ InputBlocker |
| **Screen frozen/unusable** | Too many blocking regions | Use the **Emergency Reset Gesture** (Hold top-left corner for 3s) |
| **Config not updating** | Path mismatch | Use the PC Tool to "Push to Device" to ensure the correct path is used |
| **Surgical filter too aggressive** | `minPressure` set too high | Lower the `minPressure` in the Property Inspector |
