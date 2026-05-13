# ??? InputBlocker: Comprehensive Technical Documentation

InputBlocker is a professional-grade Android system utility designed to eliminate "ghost taps"—erratic, phantom touch inputs caused by hardware failure, screen degradation, or moisture. Unlike simple overlay apps that can be bypassed or cause lag, InputBlocker operates at the system level to ensure zero-latency interception and absolute reliability.

---

## ?? Table of Contents
1. [System Architecture](#-system-architecture)
2. [The Coordinate Lifeline (Normalization)](#-the-coordinate-lifeline)
3. [Blocking Engine Deep-Dive](#-blocking-engine-deep-dive)
4. [Ghost Tap Detection Pipeline](#-ghost-tap-detection-pipeline)
5. [Root Module & System Hardening](#-root-module--system-hardening)
6. [Companion App Logic](#-companion-app-logic)
7. [CI/CD & Release Engineering](#-cicd--release-engineering)
8. [Installation & Maintenance](#-installation--maintenance)
9. [Developer Reference](#-developer-reference)

---

## ??? System Architecture

InputBlocker employs a **Tri-Layer Architecture** to separate system-level execution from user-level management.

### 1. The Execution Layer (Xposed/LSPosed Hook)
The core "brain" of the system. It resides within the Android System Server process (`system_server`). Its sole responsibility is to intercept raw motion events and decide whether they should be allowed to reach the rest of the OS. By hooking the input dispatcher, it can block touches before they are even processed by the window manager.

### 2. The Management Layer (Companion App)
A high-level UI that allows users to define and manage blocking regions. It handles the "intelligence" (detection), the visual configuration, and the communication with the execution layer via a shared configuration file stored in the root directory.

### 3. The Foundation Layer (Root Module)
The glue that holds everything together. It ensures the companion app is always present and functional. It manages boot-time services and provides a gateway for root managers (Magisk, KernelSU, APatch) to interact with the system.

**Data Flow**:
`Physical Touch` $\rightarrow$ `InputDispatcher` $\rightarrow$ `InputBlocker Hook` $\rightarrow$ `Check Cached Config` $\rightarrow$ `(Block/Allow)` $\rightarrow$ `Window Manager` $\rightarrow$ `Target App`

---

## ?? The Coordinate Lifeline (Normalization)

The most critical engineering challenge in Android system utilities is **Resolution Independence**. A blocking region defined on a 1080p screen would be incorrectly positioned on a 1440p screen if absolute pixels were used.

### The Solution: Normalized Coordinates
InputBlocker treats the entire screen as a unit square from `(0.0, 0.0)` to `(1.0, 1.0)`.

#### Mathematical Model
For any touch event at pixel position $(P_x, P_y)$ on a screen with width $W$ and height $H$:

$$\text{Normalized } X = \frac{P_x}{W}$$
$$\text{Normalized } Y = \frac{P_y}{H}$$

#### Engineering Benefits:
- **Device Portability**: A configuration file created on one device can be shared with another device of a different resolution and work perfectly.
- **Computational Efficiency**: The hook does not need to query `DisplayMetrics` on every single touch event; it simply uses the normalized coordinates provided by the system or calculates them once per event.
- **Consistency**: Region definitions are relative to the screen percentage, not the pixel count.

---

## ? Blocking Engine Deep-Dive

### The Hook: `dispatchMotionLocked`
The system hooks `com.android.server.input.InputDispatcher.dispatchMotionLocked`. This is the primary bottleneck of the Android input system. By intercepting here, we can block touches before they are even dispatched to the active window.

### Optimization: The TTL Cache
Reading a file from `/data/adb/` on every single touch event (which can happen 120+ times per second on high-refresh screens) would cause massive system lag and CPU spikes.

**The Strategy**:
- **In-Memory Caching**: The hook loads the `default.conf` into a `mutableListOf<Region>` in memory.
- **TTL (Time-To-Live)**: The cache is only refreshed every 5 seconds (`CACHE_TTL = 5000L`).
- **Complexity**: Each touch event is checked against the list of regions in $O(N)$ time, where $N$ is the number of blocked regions (typically $<10$), ensuring negligible performance impact.

---

## ?? Ghost Tap Detection Pipeline

Instead of forcing users to manually guess the location of ghost taps, InputBlocker implements an automated detection pipeline.

### Phase 1: Raw Sensing
The `SensingActivity` creates a full-screen black overlay. It records every `ACTION_DOWN` event as a normalized coordinate pair. A **Real-time Heatmap** provides visual feedback, drawing a red circle at every detected point to show the user where the ghost taps are occurring.

### Phase 2: DBSCAN Clustering
The system uses a **Density-Based Spatial Clustering of Applications with Noise (DBSCAN)** algorithm to identify hotspots.

**Algorithm Logic**:
1. **Core Points**: A point is a "core point" if at least `MinPts` other points are within distance $\epsilon$.
2. **Expansion**: The algorithm recursively expands the cluster by adding all reachable neighbors.
3. **Noise**: Points that do not belong to any cluster are ignored as random noise.

**User-Tuning**:
Users can adjust $\epsilon$ (the radius of the search) and `MinPts` (the density required) via sliders in the `DetectionReviewActivity` to refine the clusters in real-time.

### Phase 3: Bounding Box Generation
Once clusters are identified, the system calculates the **Minimum Bounding Box** for each cluster:
- $X_{min} = \min(\text{all points in cluster})$
- $X_{max} = \max(\text{all points in cluster})$
- $Y_{min} = \min(\text{all points in cluster})$
- $Y_{max} = \max(\text{all points in cluster})$

---

## ??? Root Module & System Hardening

### Root Agnosticism
The module is designed to be compatible with all modern root managers:
- **Magisk**: Standard module structure and boot scripts.
- **KernelSU / APatch**: Compatible with the `/data/adb/modules` directory layout.
- **SuperSU**: Legacy support for basic APK installation.

### Hardening Scripts
1. **`service.sh` (The Guardian)**: 
   Runs at boot. It waits for the system to be fully ready (`sys.boot_completed=1`), then checks if the companion app is installed. If not, it silently installs it from the internal `common/` folder.
2. **`action.sh` (The Gateway)**: 
   Integrates with root manager "Action" buttons. It launches the app's Quick Actions menu via `am start` or performs an **Emergency Reset** (disabling all blocking) if the app is inaccessible.
3. **`health-check.sh` (The Auditor)**: 
   A standalone script that verifies the integrity of `module.prop` and the companion APK. If corruption is detected, it signals the system to initiate a self-repair sequence.

---

## ?? Companion App Logic

### Quick Actions FAB Menu
The `MainActivity` features a Floating Action Button (FAB) that provides immediate access to critical tools:
- **Safe Mode**: Temporarily disables all blocking to prevent lockouts.
- **Sync**: Forces the Root Module to reload the current configuration from the file.
- **Export**: Backs up the current blocking regions to a text file.
- **Test Hook**: Creates a temporary `test_mode` file. While this file exists, the Xposed hook blocks ALL touch input for 5 seconds, allowing the user to verify the hook is active.

### Theme Engine
Supports a high-contrast UI with four modes: **System**, **Light**, **Dark**, and **AMOLED** (pure black), ensuring accessibility in all lighting conditions and saving battery on OLED screens.

---

## ?? CI/CD & Release Engineering

The project uses a professional-grade GitHub Actions pipeline (`release.yml`) to ensure every release is consistent and signed.

### The Release Lifecycle
1. **Versioning**: The `versionCode` is automatically derived from the total git commit count, ensuring a strictly increasing integer for Android's package manager.
2. **Injection**: The pipeline uses `sed` to inject the version into `module.prop`, `update.json`, `build.gradle`, and `.csproj`.
3. **Hardened Signing**: Instead of relying on third-party actions, the pipeline uses the official Android `apksigner` tool from the SDK to sign the APK using a Base64-encoded keystore stored in GitHub Secrets.
4. **Update Authority**: The `update.json` is automatically deployed to **GitHub Pages**. This allows the app to check for updates without needing a dedicated backend server.

---

## ?? Installation & Maintenance

### Standard Installation
1. Install the Root Module (`.zip`) via your preferred manager (Magisk/KernelSU/APatch).
2. Reboot the device.
3. The companion app will be auto-installed. Open it and grant **Overlay Permission**.
4. Enable the **Xposed Module** in LSPosed/EdXposed and reboot.

### Troubleshooting
- **App not installing?** Check if the `service.sh` logs show "APK not found".
- **Blocking not working?** Verify that the Xposed hook is enabled and that the app is not in "Safe Mode".
- **Lockout?** Use the Root Manager "Action" button to trigger an Emergency Reset.

---

## ?? Project Specifications
- **Minimum API**: 23 (Android 6.0)
- **Target SDK**: 34 (Android 14)
- **JDK**: 17 (Temurin)
- **.NET**: 8.0 (Core)
- **Root Managers**: Magisk, KernelSU, APatch, SuperSU.
