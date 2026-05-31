<p align="center">
  <img src="https://github.com/Laviesss/InputBlocker/actions/workflows/ci.yml/badge.svg" alt="Build Status">
  <img src="https://img.shields.io/github/license/Laviesss/InputBlocker" alt="License">
  <img src="https://img.shields.io/badge/platform-Android%20%7C%20Windows%20%7C%20Linux%20%7C%20macOS-blue" alt="Platform">
</p>

# 🛡️ InputBlocker

**Intelligent ghost tap filtering for failing Android touchscreens.**

If your device has a failing digitizer causing random phantom touches (ghost taps), InputBlocker is for you. Most solutions block off chunks of your screen entirely, making it unusable. InputBlocker works differently — it filters touches at the system level based on their physical properties, keeping your screen usable while killing the ghosts.

---

## How It Works

Instead of a blind screen block, InputBlocker uses **conditional touch filtering**:

- **Pressure check** — Ghost taps from electrical noise have very low pressure. Real finger presses pass through.
- **Duration check** — Stuck pixels produce unnaturally long touch holds. Real taps are brief.
- **Coordinate filtering** — Block only specific screen regions where ghost taps occur.

The engine hooks directly into Android's input dispatcher (`system_server`), intercepting touches **before** they reach your apps.

---

## Components

### 📱 Android Engine (LSPosed/Vector Hook)
The core module. Hooks `InputDispatcher.dispatchMotionLocked` to filter touches system-wide.

### 🖥️ PC Designer (Kotlin/Compose Desktop)
A visual editor for configuring blocking regions:

- Draw regions (Rectangles, Circles, Ellipses) on a screen preview.
- Tune pressure and duration thresholds per region.
- Analyze `blocklog.txt` with DBSCAN clustering to find ghost tap hotspots.
- Push configs to your device via ADB.

### 📲 Companion App (Android Overlay)
Provides on-device management:

- Visual overlay showing active blocking regions.
- Emergency reset gesture (Volume Down ×3 → Volume Up ×3).
- Crash detection with automatic safe mode.
- Log viewer and sharing.

---

## Quick Start

### Prerequisites

- **Root access** — Magisk, KernelSU, or APatch.
- **LSPosed / Vector** framework installed.
- **ADB** set up on your PC with USB Debugging enabled on the device.

### Setup

1. **Flash the module** — Download the latest `InputBlockerModule.zip` from [Releases](https://github.com/Laviesss/InputBlocker/releases) and flash via your root manager.
2. **Enable in LSPosed** — Activate the module for the **System Framework** scope and reboot.
3. **Configure** — Run the PC Designer (or the companion app), define your blocking regions, and save.
4. **Verify** — The overlay will show active regions. Ghost taps in filtered zones should be eliminated.

---

## Testing Phase

> ⚠️ **Version Note:** During the active testing phase, all releases use version `0.1.0` regardless of changes included. This ensures consistent distribution while we validate core functionality. Version `0.1.0` is treated as a rolling release — once testing concludes, strict semantic versioning will be applied.

We need real-world data from devices with ghost taps to refine the filtering logic. If you're testing InputBlocker, please report your findings.

### What to Test

1. **Filter tuning** — Adjust `minPressure` and `maxDuration`. Does it block ghosts without affecting your real touches?
2. **Region layering** — Create a blocking zone with an exclude zone inside it. Do exclude zones reliably pass touches through?
3. **Emergency reset** — Trigger the gesture combo. Does blocking disable immediately?
4. **Profile switching** — Set up different configs for different apps. Do they switch correctly?
5. **Performance** — Any perceptible input lag? Unusual battery drain?

### How to Report

When reporting issues on the [issue tracker](https://github.com/Laviesss/InputBlocker/issues), include:

- **Device model & Android version**
- **What happened** — e.g., "Filter too aggressive," "Module crashed on reboot"
- **Logs** — Use the **Share Log** button in the companion app to export `blocklog.txt` and `latency.log`
- **Config** — Attach the `.conf` file you were using

### Recovery

| Scenario | Action |
|---|---|
| Overlay blocks everything | Use emergency gesture (Vol Down ×3 → Vol Up ×3) |
| Boot loop after install | Boot into Safe Mode, disable module in LSPosed |
| Crash flag triggered | `adb shell rm /data/adb/modules/inputblocker/config/crash_detected` |
| Need hard disable | Create `/data/adb/modules/inputblocker/config/kill_switch` with content `1` |

---

## Downloads

| Platform | Format | Link |
|---|---|---|
| **Android module** | `.zip` | [Releases page](https://github.com/Laviesss/InputBlocker/releases) |
| **PC Designer (Windows)** | `.exe` | [Releases page](https://github.com/Laviesss/InputBlocker/releases) |
| **PC Designer (Linux)** | `.deb` | [Releases page](https://github.com/Laviesss/InputBlocker/releases) |
| **PC Designer (macOS)** | `.dmg` | [Releases page](https://github.com/Laviesss/InputBlocker/releases) |
| **Companion APK** | `.apk` | Packaged inside module ZIP |

---

## Technical Highlights

- **Resolution independent** — Normalized coordinates (0.0–1.0) mean one config works across screen sizes.
- **Sub-millisecond filtering** — The hook operates in the input dispatcher's hot path with minimal overhead.
- **Async logging** — Dedicated logger thread prevents I/O from blocking touch dispatch.
- **Crash-safe** — Two-layer crash protection (hot-path try/catch + boot-time detection) prevents lockouts.
- **DBSCAN analysis** — Clustering algorithm identifies ghost tap hotspots from log data.

---

## Building from Source

See [BUILD.md](BUILD.md) for complete build instructions.

```
./gradlew buildAll -PVERSION_NAME="0.1.0" -PVERSION_CODE=1
```

---

## Documentation

| Document | Description |
|---|---|
| [BUILD.md](BUILD.md) | Build instructions and CI/CD overview |
| [DOCUMENTATION.md](DOCUMENTATION.md) | In-depth technical documentation |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Contribution guidelines |
| [SECURITY.md](SECURITY.md) | Security vulnerability reporting |
| [CHANGELOG.md](CHANGELOG.md) | Release history |

---

## Disclaimer

This tool modifies system-level input handling. Use it at your own risk. The maintainers are not responsible for any damage to your device.
