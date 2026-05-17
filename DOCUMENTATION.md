# InputBlocker: Comprehensive Technical Reference Manual

InputBlocker is a professional-grade Android system utility designed to eliminate "ghost taps"—erratic, phantom touch inputs caused by hardware failure, screen degradation, or moisture. Unlike simple overlay apps, InputBlocker operates at the system level to ensure zero-latency interception and absolute reliability.

---

## 🏛️ 1. System Architecture

InputBlocker employs a **Tri-Layer Architecture** to separate system-level execution from user-level management.

### 1.1 The Execution Layer (Xposed/LSPosed Hook)
The core "brain" of the system. It resides within the Android System Server process (`system_server`). Its sole responsibility is to intercept raw motion events and decide whether they should be allowed to reach the rest of the OS. By hooking the `InputDispatcher`, it can block touches before they are even processed by the window manager.

### 1.2 The Management Layer (Companion App)
A high-level UI that allows users to define and manage blocking regions. It handles the "intelligence" (adaptive detection), the visual configuration, and the communication with the execution layer via shared configuration files stored in the root directory.

### 1.3 The Foundation Layer (Root Module)
The glue that holds everything together. It ensures the companion app is always present and functional. It manages boot-time services and provides a gateway for root managers (Magisk, KernelSU, APatch) to interact with the system.

**Data Flow**:
`Physical Touch` $\rightarrow$ `InputDispatcher` $\rightarrow$ `InputBlocker Hook` $\rightarrow$ `Check Cached Config` $\rightarrow$ `(Block/Allow)` $\rightarrow$ `Window Manager` $\rightarrow$ `Target App`

---

## 📐 2. The Coordinate Lifeline (Normalization)

To achieve **Resolution Independence**, InputBlocker treats the entire screen as a unit square from `(0.0, 0.0)` to `(1.0, 1.0)`.

### 2.1 The Unit Square
The entire screen is treated as a 2D plane from `(0.0, 0.0)` (Top-Left) to `(1.0, 1.0)` (Bottom-Right).

### 2.2 Mathematical Conversion
When a touch event occurs, the system converts raw pixels to normalized coordinates:
$$\text{NormalizedX} = \frac{\text{RawPixelX}}{\text{ScreenWidth}}$$
$$\text{NormalizedY} = \frac{\text{RawPixelY}}{\text{ScreenHeight}}$$

### 2.3 Engineering Benefit
This approach ensures that a blocking region defined on a 1080p screen remains perfectly aligned on a 1440p screen without manual adjustment. It also allows for "Community Presets" to be shared across different device models with similar screen aspect ratios.

---

## ⚙️ 3. Blocking Engine Deep-Dive

### 3.1 Advanced Shape Support
InputBlocker supports three geometric primitives for blocking:
1. **Rectangles**: Standard boundary checks ($\text{x1} \le \text{nx} \le \text{x2}$ and $\text{y1} \le \text{ny} \le \text{y2}$).
2. **Circles**: Euclidean distance check from center $(cx, cy)$ with radius $r$: $(nx-cx)^2 + (ny-cy)^2 \le r^2$.
3. **Ellipses**: Normalized distance check: $\frac{(nx-cx)^2}{rx^2} + \frac{(ny-cy)^2}{ry^2} \le 1.0$.

### 3.2 Surgical Filtering
To prevent blocking intentional user interactions, the engine applies a a dual-threshold filter:
- **Pressure Threshold**: Blocks only if $\text{Pressure} < \text{minPressure}$.
- **Duration Threshold**: Blocks only if $\text{Duration} < \text{maxDuration}$.
- **Logic**: Low-pressure, short-duration events are flagged as ghost taps. Firm presses and long-holds are passed through to the system.

### 3.3 Region Layering (Exclude Zones)
The system implements a priority-based check:
1. **Exclude Check**: If a touch falls within a region marked as `isExclude`, it is immediately allowed.
2. **Block Check**: If not excluded, the touch is then checked against blocking regions.

---ine applies a a dual-threshold filter:
- **Pressure Threshold**: Blocks only if $\text{Pressure} < \text{minPressure}$.
- **Duration Threshold**: Blocks only if $\text{Duration} < \text{maxDuration}$.
- **Logic**: Low-pressure, short-duration events are flagged as ghost taps. Firm presses and long-holds are passed through to the system.

### Region Layering (Exclude Zones)
The system implements a priority-based check:
1. **Exclude Check**: If a touch falls within a region marked as `isExclude`, it is immediately allowed.
2. **Block Check**: If not excluded, the touch is then checked against blocking regions.

---

## 🧠 Intelligence & Automation

### Adaptive Blocking
The system uses a feedback loop to refine blocking regions:
1. **Log Collection**: Every blocked touch is logged to `blocklog.txt`.
2. **Hotspot Analysis**: The app analyzes the log to find the actual bounding box of all hits within a region.
3. **Dynamic Shrinking**: Regions are shrunk to fit the actual hit density, reducing the blocked area and increasing usable screen space.

### App-Specific Profiles
The module detects the foreground package name using `ActivityManager`. If a config file exists for that package (`/profiles/[package].conf`), it is loaded; otherwise, the system defaults to `default.conf`.

### Auto-Detection
The sensing mode captures raw touch data and applies **DBSCAN (Density-Based Spatial Clustering of Applications with Noise)** to automatically identify clusters of ghost taps and suggest the optimal blocking regions.

---

## 🛡️ System Hardening & Recovery

### Emergency Reset
To prevent "permanent lockout" due to misconfiguration, a secret gesture is implemented:
- **Trigger**: Long-press (3s) in the top-left corner (first 5% of screen).
- **Action**: Creates a `kill_switch` file in the config directory, which forces the blocking engine to disable itself immediately.

### Performance Optimization
To ensure zero UI lag, the Xposed hook employs:
- **Metrics Caching**: `DisplayMetrics` are cached with a 30s TTL.
- **Volatile Caching**: Configuration is cached in memory and updated every 5s.
- **Index Access**: Direct argument access in `dispatchMotionLocked` to avoid expensive reflection.

---

## 🧠 4. Intelligence & Automation

### 4.1 Adaptive Blocking (The Feedback Loop)
Rather than relying on a user's "best guess" for region size, InputBlocker implements an adaptive optimization loop.

1. **Log Collection**: Every blocked touch is logged to `blocklog.txt` with its exact normalized coordinates.
2. **Hotspot Analysis**: The app analyzes the logs to find the actual bounding box of all hits within a region.
3. **Dynamic Shrinking**: Regions are shrunk to fit the actual "hit cloud" plus a small safety margin. This minimizes the amount of usable screen that is blocked.

### 4.2 Auto-Detection (DBSCAN Clustering)
The "Sensing Mode" utilizes the **DBSCAN (Density-Based Spatial Clustering of Applications with Noise)** algorithm.

- **Why DBSCAN?**: Unlike K-Means, DBSCAN does not require the user to specify the number of clusters. It finds "dense" areas of touches and treats isolated touches as noise.
- **Process**: The app captures touches $\rightarrow$ clusters them by density $\rightarrow$ calculates the bounding box for each cluster $\rightarrow$ suggests these as blocking regions.

### 4.3 App-Specific Profiles
The module detects the foreground package name using `ActivityManager`. If a config file exists for that package (`/profiles/[package].conf`), it is loaded; otherwise, the system defaults to `default.conf`.

---

## 🛡️ 5. System Hardening & Recovery

### 5.1 Emergency Reset
To prevent a user from accidentally blocking the entire screen (and thus being unable to use the app to fix it), a secret gesture is implemented.
- **Trigger**: Long-press (3s) in the top-left corner (first 5% of screen).
- **Action**: Creates a `kill_switch` file in the config directory, which forces the blocking engine to disable itself immediately.

### 5.2 Performance Optimization
To ensure zero UI lag, the Xposed hook employs:
- **Metrics Caching**: `DisplayMetrics` (screen width/height) are cached with a 30s TTL.
- **Volatile Caching**: Configuration is cached in memory and updated every 5s.
- **Index Access**: Direct argument access in `dispatchMotionLocked` to avoid expensive reflection.

---

## 🛠️ 6. Installation & Maintenance

### 6.1 Installation
1. Flash the module via your root manager.
2. Reboot device.
3. Open the Companion App to configure regions.

### 6.2 Maintenance
- **Config Path**: `/data/adb/modules/inputblocker/config/`
- **Log Path**: `/data/adb/modules/inputblocker/config/blocklog.txt`
- **Backup Path**: `/storage/emulated/0/InputBlocker/backups/`

