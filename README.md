# 🛡️ InputBlocker v0.1.0

**Surgical-Grade Input Filtering for Android**

InputBlocker is a system-level utility designed to kill "ghost taps"—those annoying phantom touches caused by failing screens. Instead of just blocking a whole chunk of your screen, we use **Surgical Filtering** to block only the electrical noise, while letting your actual fingers through.

---

## ✨ What makes it special?

### 🎯 Surgical Precision
We don't just block areas; we block *behavior*.
- **Pressure Sensing**: Block only the low-pressure "spikes" typical of hardware failure.
- **Duration Control**: Filter out those weird 3-second long "stuck" pixels.
- **Shape Support**: Use **Rectangles, Circles, or Ellipses** to match the exact shape of your screen's dead zone.

### 📈 Adaptive Intelligence
The tool learns where your screen is failing:
- **Hotspot Detection**: The engine logs ghost taps and the PC tool analyzes them to find exactly where the noise is coming from.
- **Auto-Shrinking**: Based on the logs, the tool suggests the smallest possible region to block, so you get your screen space back.

### 📱 Pro-Grade Features
- **Exclude Zones**: Need to block a huge area but keep one button usable? Just draw an "Exclude Zone" over the button, and it'll always work.
- **App-Specific Profiles**: Different apps = different ghost tap behaviors. Set a "Gaming" profile for your favorite game and a "Daily" profile for everything else.
- **Emergency Reset**: accidentally blocked your whole screen? Just hold the top-left corner for 3 seconds to kill everything.

---

## 🛠️ The Toolkit

### 1. The Android Engine (LSPosed)
The "muscle." It hooks into the Android system's input dispatcher to kill ghost taps before they even reach your apps.

### 2. The PC Setup Tool (Kotlin/Compose)
The "brain." A professional visual designer where you can draw regions, tune thresholds, and analyze logs.
> **Note**: We've moved to a modern Kotlin-based tool for better cross-platform support. The old C# version is now in maintenance mode and is being phased out.

---

## 🚀 Quick Start

### Prerequisites
- **Root**: Magisk, KernelSU, or APatch.
- **Framework**: LSPosed (Recommended).
- **PC**: ADB installed and USB Debugging enabled.

### Setup
1. **Install**: Flash the module zip via your root manager.
2. **Enable**: Turn on the module in LSPosed and reboot.
3. **Configure**: Open the **PC Setup Tool**, connect your phone, and start mapping your dead zones.

---

## 📐 Technical Bit (for the nerds)
We use **Coordinate Normalization (0.0 to 1.0)**. This means if you share a config with a friend who has a different resolution, it still works perfectly.

**The Surgical Formula**:
$\text{Block} = (\text{Pressure} < \text{MinPressure}) \lor (\text{Duration} > \text{MaxDuration})$

---

## 📖 More Info
Check out the [DOCUMENTATION.md](./DOCUMENTATION.md) for the full technical deep-dive.

## ⚠️ Disclaimer
This tool modifies system-level input. Use it at your own risk. Always keep the Emergency Reset gesture in mind!
