# Technical Documentation

## Architecture

InputBlocker is designed to be resolution-independent and root-manager agnostic. It consists of a management app, a root module, and a set of PC-based configuration tools.

### Coordinate System

To ensure that blocking regions are portable across different devices, InputBlocker avoids absolute pixel values. Instead, it uses a **Normalized Coordinate System**.

- **Scale**: All coordinates are stored as floats between `0.0` and `1.0`.
- **Origin**: `(0.0, 0.0)` is the top-left corner; `(1.0, 1.0)` is the bottom-right corner.
- **Logic**: 
  - $\text{Normalized X} = \frac{\text{Pixel X}}{\text{ScreenWidth}}$
  - $\text{Normalized Y} = \frac{\text{Pixel Y}}{\text{ScreenHeight}}$

This approach ensures that a region defined at 10% from the left edge will always be at 10% regardless of whether the screen is 720p or 1440p.

### Blocking Implementation

The system supports two methods of touch interception:

#### 1. System Server Hook (LSPosed/Xposed)
The most efficient method. It hooks `com.android.server.input.InputDispatcher.dispatchMotionLocked` in the Android system server.
- **Process**: When a motion event is dispatched, the hook checks if the touch coordinates fall within any blocked region.
- **Result**: If a match is found, the event is discarded, effectively making the touch invisible to the rest of the system.

#### 2. Overlay Interception
A fallback for standard root installations.
- **Process**: The `OverlayService` creates a transparent window of type `TYPE_APPLICATION_OVERLAY`.
- **Logic**: The overlay intercepts touch events via `onTouchEvent`. If the touch is within a blocked region, the view returns `true` to consume the event, preventing it from reaching the underlying layers.

### Auto-Detection Pipeline

InputBlocker identifies ghost taps using a data-driven pipeline:

1. **Sensing**: The `SensingActivity` records all `ACTION_DOWN` events as normalized points over a specified period.
2. **Clustering**: The `DetectionUtils` class applies the **DBSCAN (Density-Based Spatial Clustering of Applications with Noise)** algorithm.
   - **$\epsilon$ (Epsilon)**: $0.03$ (3% of screen width).
   - **MinPts**: $3$ points.
3. **Region Generation**: Each identified cluster is converted into the smallest possible bounding box (`Region`) that encompasses all points in that cluster.
4. **Review**: These suggested regions are presented to the user for approval or manual adjustment.

### Safety Mechanisms

- **Physical Kill Switch**: To prevent permanent lockouts, the system monitors hardware button combinations. Pressing `Volume Down` three times followed by `Volume Up` three times immediately disables all blocking.
- **Root Agnosticism**: The module uses a generic structure compatible with Magisk, KernelSU, and APatch, avoiding manager-specific APIs in the core logic.
