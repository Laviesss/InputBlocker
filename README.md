# 🛡️ InputBlocker

![Build Status](https://github.com/Laviesss/InputBlocker/actions/workflows/ci.yml/badge.svg)
![License](https://img.shields.io/github/license/Laviesss/InputBlocker)
![Platform](https://img.shields.io/badge/platform-Android%20%7C%20Windows%20%7C%20Linux%20%7C%20macOS-blue)

If your Android screen is failing and you're dealing with "ghost taps" (random phantom touches), InputBlocker is for you. 

Most solutions just block off chunks of your screen, making it unusable. InputBlocker is different. It works at the system level to filter touches based on their physical properties—meaning you can often keep using your screen while the ghost taps are killed.

---

## 🎯 How it works
Instead of a "dumb" block, we use **Touch Filtering**. The engine analyzes every touch in a designated area:
- **Pressure**: If a touch is too light (typical of electrical noise), it's blocked.
- **Duration**: If a touch is held for an unnatural amount of time (stuck pixel), it's blocked.
- **Real Touches**: Actual finger presses usually have enough pressure and the right timing to pass through the filter.

## 🛠️ The Toolkit

### 📱 The Android Engine (Xposed/LSPosed)
The core module that hooks into the system's input dispatcher. It intercepts touches before they ever reach your apps.

### 💻 The PC Designer (Kotlin/Compose)
A visual editor for your PC. Use it to:
- Draw your blocking regions (Rectangles, Circles, Ellipses).
- Tune pressure and duration thresholds.
- Analyze logs to find exactly where your ghost taps are happening.
- Push configs to your device via ADB.

---

## 🚀 Quick Start

### Prerequisites
- **Root**: Magisk, KernelSU, or APatch.
- **Framework**: LSPosed.
- **PC**: ADB installed and USB Debugging enabled on your phone.

### Setup
1. **Install**: Flash the module zip.
2. **Enable**: Enable the module in LSPosed and reboot.
3. **Configure**: Run the PC Designer, map your dead zones, and push the config.

---

## 📥 Download
You can find the latest releases on the [Releases Page](https://github.com/Laviesss/InputBlocker/releases).
- **Android users**: Download the `.zip` file and flash it via your root manager.
- **PC users**: Download the `.exe` (Windows), `.AppImage` (Linux), or `.dmg` (macOS) to use the Designer.

---

## 🧪 Testers Guide

InputBlocker is in active development. We need real-world data from devices with ghost taps to refine the filtering logic and ensure system stability.

### 🎯 What to Test
1. **Filter Tuning**: Use the PC Designer to find the "sweet spot" for `MinPressure` and `MaxDuration`. Does it kill the ghost taps without blocking your actual fingers?
2. **Region Layering**: Create a large blocking zone and a smaller "Exclude Zone" inside it. Verify that touches in the exclude zone always pass through.
3. **Emergency Reset**: Configure a custom button combo (e.g., Vol Down x3 $\rightarrow$ Vol Up x3). Trigger it to ensure the module disables itself instantly.
4. **Profile Switching**: Set up different configs for different apps. Switch between them and verify the blocking regions change as expected.
5. **Performance**: Note any perceptible input lag or unusual battery drain.

### 📋 How to Report Issues
When reporting a bug or sharing your findings, please include:
- **Device Model & Android Version**.
- **The Issue**: What happened? (e.g., "Filter too aggressive," "Module crashed on reboot").
- **Logs**: Use the **Share Log** button in the app's log activity to export `blocklog.txt` and `latency.log`.
- **Config**: Attach the `.conf` file you were using.

### 🆘 Recovery
If the screen becomes unusable or the device behaves unexpectedly:
- **Emergency Gesture**: Use your configured button combo to disable the engine.
- **Safe Mode**: If the module causes a boot loop or system instability, boot into Android Safe Mode to disable it via LSPosed.
- **ADB**: Use `adb shell rm /data/local/tmp/inputblocker/crash_detected` if Safe Mode was triggered.

---

## 📐 Technical Bits
- **Resolution Independent**: We use Normalized Coordinates (0.0 to 1.0), so one config works across different screen sizes.
- **The Formula**: $\text{Block} = (\text{Pressure} < \text{MinPressure}) \lor (\text{Duration} > \text{MaxDuration})$

## ⚠️ Disclaimer
This tool modifies system input. Use it at your own risk.
