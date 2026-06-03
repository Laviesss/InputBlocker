<div align="center">
  <h1>🛡️ InputBlocker</h1>
  <p><strong>Intelligent ghost-tap filtering for failing Android touchscreens</strong></p>

  <p>
    <a href="https://github.com/Laviesss/InputBlocker/actions/workflows/ci.yml">
      <img src="https://github.com/Laviesss/InputBlocker/actions/workflows/ci.yml/badge.svg" alt="CI Build">
    </a>
    <a href="https://github.com/Laviesss/InputBlocker/actions/workflows/release.yml">
      <img src="https://github.com/Laviesss/InputBlocker/actions/workflows/release.yml/badge.svg" alt="Release Pipeline">
    </a>
    <a href="LICENSE">
      <img src="https://img.shields.io/github/license/Laviesss/InputBlocker" alt="MIT License">
    </a>
    <a href="https://github.com/Laviesss/InputBlocker/releases">
      <img src="https://img.shields.io/github/v/release/Laviesss/InputBlocker?include_prereleases" alt="GitHub Release">
    </a>
    <a href="https://github.com/Laviesss/InputBlocker/issues">
      <img src="https://img.shields.io/github/issues/Laviesss/InputBlocker" alt="Issues">
    </a>
    <img src="https://img.shields.io/badge/platform-Android%20%7C%20Windows%20%7C%20Linux%20%7C%20macOS-blue" alt="Platform">
    <img src="https://img.shields.io/badge/API-23+-brightgreen" alt="Min SDK">
    <img src="https://img.shields.io/badge/Root-Magisk%20%7C%20KernelSU%20%7C%20APatch-yellow" alt="Root Managers">
    <a href="https://github.com/Laviesss/InputBlocker/blob/main/CONTRIBUTING.md">
      <img src="https://img.shields.io/badge/PRs-welcome-brightgreen" alt="PRs Welcome">
    </a>
  </p>

  <p>
    <a href="#-quick-start"><b>Quick Start</b></a> •
    <a href="#-features"><b>Features</b></a> •
    <a href="#-how-it-works"><b>How It Works</b></a> •
    <a href="#-downloads"><b>Downloads</b></a> •
    <a href="#-documentation"><b>Docs</b></a> •
    <a href="#-faq"><b>FAQ</b></a>
  </p>
</div>

---

## 📋 Table of Contents

- [Why InputBlocker?](#-why-inputblocker)
- [Quick Start](#-quick-start)
- [Features](#-features)
- [How It Works](#-how-it-works)
- [Components](#-components)
- [Downloads](#-downloads)
- [Configuration](#-configuration)
- [Comparison](#-comparison)
- [Documentation](#-documentation)
- [FAQ](#-faq)
- [Testing & Reporting](#-testing--reporting)
- [Building from Source](#-building-from-source)
- [Contributing](#-contributing)
- [License](#-license)

---

## 🤔 Why InputBlocker?

**The problem:** Failing digitizers cause ghost taps — phantom touches from electrical noise. These random touches can:

- **Interrupt** your typing with random taps
- **Click** things you never intended to touch
- **Drain** battery by keeping the screen active
- **Make** your device nearly unusable

Existing solutions block entire chunks of the screen unconditionally, turning usable displays into wastelands.

InputBlocker filters touches at the OS input level based on physical properties (pressure, duration). Your screen stays usable. Ghosts get blocked. No blind screen blocks.

---

## ⚡ Quick Start

### Requirements

| Dependency | Details |
|---|---|
| **Root access** | Magisk (≥20400), KernelSU, or APatch |
| **Xposed framework** | LSPosed or Vector installed in your root manager |
| **Android** | 6.0+ (API 23) |
| **ADB** | For PC tool — USB Debugging enabled (optional) |

### Installation

```bash
# 1. Download the latest module from Releases
# 2. Flash in your root manager (Magisk / KernelSU / APatch)
# 3. Reboot
# 4. Enable InputBlocker in LSPosed Manager for "System Framework"
# 5. Reboot again
# 6. Open the InputBlocker companion app to verify
```

> **First time?** See [Getting Started Guide](docs/GETTING_STARTED.md) for a full walkthrough.

### Emergency Recovery

| Situation | Action |
|---|---|
| Overlay blocks everything | Press **Volume Down × 3 → Volume Up × 3** |
| Boot loop | Boot Safe Mode → disable module in LSPosed |
| Crash detected | `adb shell rm /data/adb/modules/inputblocker/config/crash_detected` |
| Hard disable | `echo "1" > /data/adb/modules/inputblocker/config/kill_switch` |

---

## ✨ Features

### Core Engine

| Feature | Description |
|---|---|
| **System-level filtering** | Hooks `InputDispatcher.dispatchMotionLocked` in `system_server` — touches blocked before apps see them |
| **Surgical blocking** | Filter by pressure (ghost taps have low pressure) + duration (stuck pixels long-press) |
| **Shape support** | Rectangle, Circle, and Ellipse regions — not just squares |
| **Exclude zones** | Create "holes" in blocked regions for buttons you need |
| **Per-app profiles** | Different blocking configs for different applications |
| **Kill switch** | Emergency file-based disable — no UI needed |

### Companion App

| Feature | Description |
|---|---|
| **Visual editor** | Draw regions directly on a screen preview |
| **Live overlay** | See active blocking regions rendered on screen |
| **4 themes** | System, Light, Dark, AMOLED (battery-saving pure black) |
| **Auto-detection** | Captures ghost tap samples, suggests optimized regions |
| **Preset system** | Export/import `.ibpreset` files for sharing configs |
| **Block log viewer** | Review every filtered touch with full metadata |
| **Community gallery** | Browse and download presets from other users |
| **Update checker** | In-app notification when new releases are available |
| **Quick Settings tile** | Toggle blocking from the notification shade |
| **Emergency gesture** | Configurable button combo to disable blocking instantly |

### PC Designer Tool

| Feature | Description |
|---|---|
| **Visual region editor** | Drag, resize shapes on a screen preview |
| **DBSCAN auto-tuning** | Clusters ghost tap data to suggest optimal regions |
| **ADB bridge** | Push configs and pull logs wirelessly |
| **Cross-platform** | Windows (EXE), Linux (DEB), macOS (DMG) |

### Safety & Reliability

| Feature | Description |
|---|---|
| **Crash-safe design** | Hot-path try/catch + boot-time detection prevents lockouts |
| **Safe mode** | Auto-disables blocking after abnormal shutdown |
| **Async logging** | Dedicated logger thread prevents I/O from blocking touch dispatch |
| **Resolution-independent** | Normalized coordinates (0.0–1.0) — one config works across screen sizes |
| **Low overhead** | Sub-millisecond filtering with two-tier caching |

---

## 🔧 How It Works

Ghost taps from failing digitizers have three telltale characteristics:

| Signal | Ghost Tap | Real Finger |
|---|---|---|
| **Pressure** | Very low (< 0.10) | Normal (0.15 – 1.00) |
| **Duration** | Instant spike OR stuck hold | Brief, natural tap |
| **Location** | Clusters in dead zones | Anywhere on screen |

### The Filter Formula

```
BLOCK if (Pressure < MinPressure) OR (Duration > MaxDuration)
```

```
            Touch Event
                │
                ▼
        ┌───────────────┐
        │ In exclude     │──YES──> PASS THROUGH
        │ zone?          │
        └───────┬───────┘
                │ NO
                ▼
        ┌───────────────┐
        │ In blocked     │──NO──> PASS THROUGH
        │ zone?          │
        └───────┬───────┘
                │ YES
                ▼
        ┌───────────────┐
        │ Pressure <     │
        │ threshold?     │──YES──> BLOCK (ghost)
        │ OR             │
        │ Duration >     │
        │ max?           │
        └───────┬───────┘
                │ NO (real touch)
                ▼
            PASS THROUGH
```

### Architecture

```
┌──────────────────────────────────────────────┐
│              PC Designer                      │
│        Visual config editor + DBSCAN          │
└──────┬───────────────────────────────┬───────┘
       │ ADB push config               │ ADB pull logs
       ▼                               ▼
┌──────────────────────────────────────────────┐
│         Companion App (Overlay + UI)          │
│    On-device management, visual editor        │
└──────┬───────────────────────────────┬───────┘
       │ Write config                   │ Read crash flag
       ▼                               ▼
┌──────────────────────────────────────────────┐
│      Module Config (on-device storage)        │
│  /data/adb/modules/inputblocker/config/       │
└──────┬───────────────────────────────┬───────┘
       │ Read                          │ Write flag
       ▼                               ▼
┌──────────────────────────────────────────────┐
│    Xposed Hook (InputDispatcher hook)         │
│  Intercepts all touch events in system_server │
│  → Blocks ghosts → Apps never see them       │
└──────────────────────────────────────────────┘
```

---

## 📦 Components

| Component | Description | Tech Stack |
|---|---|---|
| **Xposed Hook** | System-level touch filter engine | Legacy Xposed API, Kotlin |
| **Companion App** | On-device management UI + overlay | Jetpack, Material 3, Kotlin |
| **PC Designer** | Desktop visual region editor | Compose Desktop, Kotlin |
| **Shared Core** | Normalized coordinate math, Region model | Kotlin Multiplatform |
| **Module Scripts** | Root installation, service, health-check | Shell (POSIX) |

---

## 📥 Downloads

| Platform | Format | Get It |
|---|---|---|
| **Android module** | `.zip` | [Releases page](https://github.com/Laviesss/InputBlocker/releases) |
| **PC Designer (Windows)** | `.exe` | [Releases page](https://github.com/Laviesss/InputBlocker/releases) |
| **PC Designer (Linux)** | `.deb` | [Releases page](https://github.com/Laviesss/InputBlocker/releases) |
| **PC Designer (macOS)** | `.dmg` | [Releases page](https://github.com/Laviesss/InputBlocker/releases) |

> The companion APK is bundled inside the module ZIP and installs automatically on first boot.

---

## ⚙️ Configuration

**Config file** (`/data/adb/modules/inputblocker/config/profiles/default.conf`):
```ini
enabled=1
lsposed_mode=1

# Format: isExclude,type,x1,y1,x2,y2,minPressure,maxDuration
# isExclude: 0=block, 1=exclude; type: 0=rect, 1=circle, 2=ellipse
# x1,y1,x2,y2: normalized 0.0-1.0; minPressure: 0.0-1.0; maxDuration: ms

0,0,0.65,0.0,1.0,1.0,0.15,300
1,2,0.88,0.92,0.06,0.08,0.0,0
```

**Per-app profiles:** Place at `config/profiles/<package_name>.conf` — engine auto-switches when the app is in the foreground.

**Special flags:** See [Configuration Reference](DOCUMENTATION.md#configuration-reference).

---

## 📊 Comparison

| Feature | InputBlocker | TouchBlocker | Screen Block | Manual ADB hack |
|---|---|---|---|---|
| **Filtering level** | Input dispatcher | Overlay-only | Overlay | Overlay |
| **Pressure filtering** | ✅ | ❌ | ❌ | ❌ |
| **Duration filtering** | ✅ | ❌ | ❌ | ❌ |
| **Shape support** | Rect, Circle, Ellipse | Rect only | Rect only | Rect only |
| **Exclude zones** | ✅ | ❌ | ❌ | ❌ |
| **Per-app profiles** | ✅ | ❌ | ❌ | ❌ |
| **PC visual designer** | ✅ | ❌ | ❌ | ❌ |
| **Auto-detection** | ✅ (DBSCAN) | ❌ | ❌ | ❌ |
| **Crash safety** | ✅ | ❌ | ✅ | ❌ |
| **Emergency gesture** | ✅ | ❌ | ❌ | ❌ |
| **Resolution-independent** | ✅ (normalized) | ❌ | ❌ | ❌ |
| **Open source** | ✅ (MIT) | ❌ | ❌ | ❌ |

---

## 📚 Documentation

| Document | What's Covered |
|---|---|
| [📖 Getting Started](docs/GETTING_STARTED.md) | Full walkthrough with screenshots for new users |
| [📘 Technical Docs](DOCUMENTATION.md) | Architecture, engine internals, configuration reference |
| [🔧 Troubleshooting](docs/TROUBLESHOOTING.md) | Common issues, solutions, and recovery |
| [❓ FAQ](docs/FAQ.md) | Frequently asked questions |
| [🔬 Advanced](docs/ADVANCED.md) | DBSCAN tuning, custom scripts, power user tips |
| [🏗️ Build Guide](BUILD.md) | Building from source, CI/CD, outputs |
| [🤝 Contributing](CONTRIBUTING.md) | How to contribute code and report issues |
| [🔒 Security](SECURITY.md) | Vulnerability reporting and disclosure policy |
| [📋 Changelog](CHANGELOG.md) | Release history |

---

## ❓ FAQ

**Q: Does this work with any Android device?**  
A: Any device running Android 6.0+ with root access and LSPosed/Vector installed.

**Q: Will this affect my battery?**  
A: Minimal. The filtering overhead is sub-millisecond with aggressive caching. Async logging prevents I/O on the dispatch thread.

**Q: Can I use this without LSPosed/Vector?**  
A: Yes — use "Overlay Mode" in the companion app. Less precise than the Xposed hook, but works without a framework module.

**Q: I found ghost taps — how do I configure it?**  
A: Run **Auto-Detection** in the companion app to capture samples, then review and save the suggested regions. Or use the PC Designer for manual control.

**Q: Will an Android update break this?**  
A: The hook targets `InputDispatcher.dispatchMotionLocked`, a stable internal API. Major Android versions rarely change this, but test after OS updates.

**Q: Can I share configs?**  
A: Yes — export as `.ibpreset` files. Normalized coordinates make them cross-device compatible.

---

## 🧪 Testing & Reporting

InputBlocker is in active testing and needs real-world data.

**What to test:**
- Filter tuning (minPressure / maxDuration values)
- Region layering (exclude zones inside blocking zones)
- Emergency reset gesture
- Per-app profile switching
- Performance (input lag, battery drain)

**How to report:** Open a [GitHub Issue](https://github.com/Laviesss/InputBlocker/issues) with device model, Android version, logs (Share Log button), and your config file.

---

## 🏗️ Building from Source

```bash
git clone https://github.com/Laviesss/InputBlocker.git
cd InputBlocker
./gradlew buildAll -PVERSION_NAME="0.1.0" -PVERSION_CODE=1
```

Requirements: JDK 17, Android SDK (API 34). See [BUILD.md](BUILD.md) for details.

---

## 🤝 Contributing

Contributions welcome! See [CONTRIBUTING.md](CONTRIBUTING.md).

- 🐛 Bugs → Open an issue
- 💡 Ideas → Feature request
- 🔧 Fixes → PRs gladly accepted

---

## 📄 License

MIT License — see [LICENSE](LICENSE).

<hr>

<div align="center">
  <p>⭐ If InputBlocker saved your device, give it a star! ⭐</p>
  <p><a href="#-table-of-contents">Back to Top ↑</a></p>
</div>
