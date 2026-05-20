# 🛡️ InputBlocker v0.1.0

InputBlocker is a system-level tool that stops "ghost taps" (phantom touches) caused by failing screens. Instead of blocking large areas of the screen, it filters out touches based on their physical properties.

---

## ✨ Features

### 🎯 Touch Filtering
The tool blocks touches based on behavior:
- **Pressure**: Blocks touches that are too light (typical of hardware noise).
- **Duration**: Blocks touches that last too long (stuck pixels).
- **Shapes**: Supports **Rectangles, Circles, and Ellipses** to match your screen's dead zones.

### 📈 Auto-Tuning
The tool can help you find the best settings:
- **Hotspot Detection**: It logs ghost taps so you can see exactly where the noise is.
- **Automatic Shrinking**: It suggests the smallest possible area to block based on those logs.

### 📱 Other Features
- **Exclude Zones**: Keep specific buttons usable even inside a blocked area.
- **App Profiles**: Use different blocking settings for different apps.
- **Emergency Reset**: Hold the top-left corner for 3 seconds to disable all blocking.

---

## 🛠️ The Toolkit

### 1. The Android Engine (LSPosed)
Hooks into the system's input dispatcher to stop ghost taps before they reach your apps.

### 2. The PC Setup Tool (Kotlin/Compose)
A visual editor to draw regions, change filter settings, and analyze logs via ADB.
> **Note**: The old C# version is in maintenance mode and is being phased out.

---

## 🚀 Quick Start

### Prerequisites
- **Root**: Magisk, KernelSU, or APatch.
- **Framework**: LSPosed (Recommended).
- **PC**: ADB installed and USB Debugging enabled.

### Setup
1. **Install**: Flash the module zip.
2. **Enable**: Turn on the module in LSPosed and reboot.
3. **Configure**: Open the **PC Setup Tool** and map your dead zones.

---

## 📐 Technical Details
We use **Normalized Coordinates (0.0 to 1.0)** so configs work across different screen resolutions.

**The Filtering Formula**:
$\text{Block} = (\text{Pressure} < \text{MinPressure}) \lor (\text{Duration} > \text{MaxDuration})$

---

## 📖 More Info
See [DOCUMENTATION.md](./DOCUMENTATION.md) for more details.

## ⚠️ Disclaimer
This tool modifies system input. Use it at your own risk.
