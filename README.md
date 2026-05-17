# InputBlocker

A professional-grade Android system utility designed to eliminate "ghost taps"—erratic, phantom touch inputs caused by hardware failure or screen degradation. InputBlocker operates at the system level to provide zero-latency interception and absolute reliability.

[![Build](https://github.com/Laviesss/InputBlocker/actions/workflows/release.yml/badge.svg)](https://github.com/Laviesss/InputBlocker/actions)
[![Release](https://img.shields.io/github/v/release/Laviesss/InputBlocker?style=flat-square)](https://github.com/Laviesss/InputBlocker/releases)
[![License](https://img.shields.io/github/license/Laviesss/InputBlocker?style=flat-square)](LICENSE)

## 🚀 Core Features

### Precision Blocking
- **Advanced Shape Support**: Define blocking zones as **Rectangles, Circles, or Ellipses** for pinpoint accuracy.
- **Surgical Filtering**: Block only "ghost-like" touches using **Pressure and Duration thresholds**. Intentional long-presses and firm touches are allowed through.
- **Region Layering**: Support for **Exclude Zones**—mark specific areas as "always allowed," which override any overlapping blocking regions.
- **Coordinate Independence**: Uses a normalized coordinate system (0.0 to 1.0) to ensure regions remain consistent across different resolutions and densities.

### Intelligence & Automation
- **Adaptive Blocking**: The system analyzes the block log to identify actual "hotspots" and can automatically shrink regions to the minimum required size.
- **App-Specific Profiles**: Define different blocking configurations for different apps (e.g., strict blocking for a game, none for the home screen).
- **Auto-Detection**: Integrated sensing mode using DBSCAN clustering to automatically identify and suggest blocking regions.

### Reliability & Management
- **Dual-Mode Engine**: 
  - **LSPosed/Xposed**: High-performance system-level interception via `InputDispatcher`.
  - **Overlay Mode**: Standard root-level blocking using a system-wide transparent overlay.
- **Emergency Reset**: Secret gesture (long-press top-left corner) to instantly kill blocking and prevent lockouts.
- **Cloud-Ready Backups**: Timestamped local backups and Community Preset support (`.ibpreset`) for easy sharing and syncing.
- **Root Agnostic**: Compatible with Magisk, KernelSU, APatch, and SuperSU.

## 🛠️ Installation

1. **Flash the Module**: Install the `InputBlocker.zip` through your root manager.
2. **Reboot**: The companion app is automatically installed on the first boot.
3. **Configuration**:
   - Open the app and use **Auto-Detect** to find ghost tap hotspots.
   - Use the **PC Setup Tool** for precise manual mapping of complex shapes.

## 📁 Repository Structure

- `/android-app`: The companion management application.
- `/module`: The root module files (scripts, prop, and update JSON).
- `/pc-tool-csharp`: Cross-platform setup tool (.NET 8).
- `/pc-tool-java`: Lightweight setup tool (Java/Swing).
- `/build-scripts`: Automation scripts for the full build pipeline.

## 📚 Resources
- **Technical Deep-Dive**: [DOCUMENTATION.md](DOCUMENTATION.md)
- **Building from Source**: [BUILD.md](BUILD.md)
