# 🛡️ InputBlocker

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

## 🧪 We're looking for Testers!
InputBlocker is in active development. If you have a device with ghost taps and want to help us refine the filtering logic:
- **How to help**: Install the module, try out different threshold settings, and let us know what works (or what crashes).
- **Feedback**: Open an issue on GitHub with your device model and the results of your tests.

---

## 📐 Technical Bits
- **Resolution Independent**: We use Normalized Coordinates (0.0 to 1.0), so one config works across different screen sizes.
- **The Formula**: $\text{Block} = (\text{Pressure} < \text{MinPressure}) \lor (\text{Duration} > \text{MaxDuration})$

## ⚠️ Disclaimer
This tool modifies system input. Use it at your own risk.
