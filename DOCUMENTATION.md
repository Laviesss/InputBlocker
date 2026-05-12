# InputBlocker Technical Documentation

## 📐 Core Architecture

InputBlocker operates as a multi-layered system to ensure maximum compatibility, performance, and safety.

### 1. Coordinate System (The Lifeline)
To ensure configurations are portable across different devices and screen resolutions, InputBlocker uses a **Normalized Coordinate System**.

- **Scale**: All coordinates are stored as `Float` values ranging from `0.0` to `1.0`.
- **Origin**: `(0,0)` is the top-left corner; `(1,1)` is the bottom-right corner.
- **Calculation**: 
  - $\text{Normalized X} = \frac{\text{Pixel X}}{\text{ScreenWidth}}$
  - $\text{Normalized Y} = \frac{\text{Pixel Y}}{\text{ScreenHeight}}$
- **Benefit**: A region defined at `(0.1, 0.1)` to `(0.2, 0.2)` will block the same relative area on a 720p screen as on a 1440p screen.

### 2. The Blocking Pipeline
Touch events are intercepted at two possible levels depending on the installation:

#### A. High-Performance Hook (Xposed)
For devices with Xposed/LSPosed, InputBlocker hooks into the Android system server:
- **Target**: `com.android.server.input.InputDispatcher.dispatchMotionLocked`
- **Mechanism**: Intercepts the motion event before it reaches the window manager. If the normalized coordinates of the touch fall within a blocked region, the event is dropped (`setResult(null)`), making it invisible to the OS.

#### B. Overlay Interception (App Level)
For standard root installations:
- **Mechanism**: An `OverlayService` creates a transparent `TYPE_APPLICATION_OVERLAY` window.
- **Logic**: The overlay consumes touch events using `onTouchEvent`. If a touch falls within a blocked region, the view returns `true` to consume the event, preventing it from passing through to the apps underneath.

### 3. Smart Auto-Detection Flow
The application implements a hybrid sensing-to-review pipeline to identify ghost taps automatically.

1. **Sensing Phase (`SensingActivity`)**:
   - The screen is covered by a black overlay.
   - The app records every `ACTION_DOWN` event as a normalized point $(x, y)$.
   - This continues for a set duration (default 30s).

2. **Analysis Phase (`DetectionUtils`)**:
   - Uses a **DBSCAN (Density-Based Spatial Clustering of Applications with Noise)** algorithm.
   - **Epsilon ($\epsilon$)**: $0.03$ (points within 3% of screen width are neighbors).
   - **MinPts**: $3$ (minimum points required to form a "hotspot").
   - Clusters of points are converted into the smallest possible bounding box `Region`.

3. **Review Phase (`DetectionReviewActivity`)**:
   - Suggested regions are presented to the user.
   - Users can:
     - **Accept All**: Save all suggested regions.
     - **Refine**: Manually drag, resize, or delete suggested regions before saving.
     - **Discard**: Ignore all suggestions.

---

## ⚙️ Component Specification

### Root Module
- **Agnostic Installation**: Uses `customize.sh` to detect if the manager is Magisk, KernelSU, APatch, or SuperSU.
- **Boot Process**: `service.sh` manages the initial installation of the companion APK and starts the `OverlayService`.
- **Configuration**: Regions are stored in `/data/adb/modules/inputblocker/config/profiles/default.conf`.

### Companion App
- **Management**: Provides the UI for enabling/disabling blocking, theme switching, and manual region editing.
- **Overlay**: Handles the visual representation of blocked areas and the high-level touch consumption.
- **Theming**: Implements a custom theme engine supporting System, Light, Dark, and AMOLED (pure black) modes.

---

## 🛡️ Safety Mechanisms

### Hardware Kill Switch
To prevent permanent lockout (e.g., if a user accidentally blocks the entire screen), a physical trigger is implemented:
- **Sequence**: `Volume Down` $\times 3 \rightarrow$ `Volume Up` $\times 3$ (within 5 seconds).
- **Action**: Triggers a system broadcast that immediately disables blocking and updates the configuration file to `enabled=0`.

### Safe Mode
- If the system detects repeated crashes or if the `force_safe_mode` flag is set in the config, the module boots in a disabled state.
- This allows the user to enter the app and fix the configuration without being blocked by a faulty region.
