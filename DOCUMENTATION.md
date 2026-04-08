# InputBlocker Documentation

## Table of Contents

1. [Overview](#overview)
2. [Features](#features)
3. [Installation](#installation)
4. [Usage](#usage)
5. [Configuration](#configuration)
6. [Building from Source](#building-from-source)
7. [How It Works](#how-it-works)
8. [Troubleshooting](#troubleshooting)
9. [FAQ](#faq)

---

## Overview

**InputBlocker** is a root module for Android that blocks ghost taps and unwanted touch inputs by defining rectangular regions on your screen.

**Created by:** Laviesss

### Use Cases

- Blocking ghost tap areas on damaged screens
- Preventing accidental touches in specific screen regions
- Gaming overlays that need touch protection
- Edge touches on curved screen devices

---

## Features

### Core Features

| Feature | Description |
|---------|-------------|
| **Visual Setup** | Draw regions directly on screen preview via PC tool |
| **Config File** | Manual editing of blocked regions |
| **Terminal Commands** | Full CLI tool for management |
| **Semi-Transparent Overlay** | Green overlay shows blocked areas |
| **Root Manager Support** | Works with Magisk, KernelSU, APatch, SuperSU |

### Customization Features

| Feature | Description |
|---------|-------------|
| **Theme Support** | Light, Dark, and AMOLED themes |
| **System Theme** | Follow device system theme setting |
| **Visual Setup Theme Sync** | Setup screen matches selected theme |

### Safety Features

| Feature | Description |
|---------|-------------|
| **Kill Switch** | Vol↓×3 → Vol↑×3 disables blocking immediately |
| **Crash Protection** | Auto-disables on unexpected shutdown/reboot |
| **ADB Fallback** | `adb shell inputblocker disable` |
| **Safe Mode** | Module starts disabled after power loss |

### Multi-Platform Support

| Component | Platforms |
|-----------|-----------|
| **PC Setup Tool** | Windows, Linux, macOS (x64 & ARM) |
| **Android Module** | Any rooted Android device |
| **Companion App** | Android 5.0+ |
| **Auto APK Install** | App auto-installed on module boot |

---

## Installation

### Prerequisites

1. **Root access** (Magisk, KernelSU, APatch, or SuperSU) installed on your device
2. **ADB** installed on your computer (for PC tool)
3. **USB cable** to connect device

### Step 1: Install the Root Module

**Option A - Via Root Manager:**
1. Download `inputblocker.zip`
2. Flash via your root manager's module installer

**Option B - Manual (Magisk/KernelSU/APatch):**
```bash
adb push inputblocker.zip /data/adb/modules/
adb reboot
```

**Option C - Manual (SuperSU):**
```bash
adb push inputblocker.zip /data/su.d/
adb reboot
```

### Step 2: Companion App (Auto-Installed)

The companion app is **automatically installed** the first time your device boots after the module is installed. No manual APK installation needed!

The app provides:
- Visual setup directly on device
- Toggle blocking on/off
- View current regions
- Reset crash protection
- Theme customization

If the app doesn't auto-install:
```bash
adb install /data/adb/modules/inputblocker/common/InputBlocker.apk
```

### Step 3: Install the PC Setup Tool

Choose one:

**C# Version (Recommended):**
```bash
# Download pre-built release or build from source:
cd pc-tool-csharp
dotnet publish -c Release -r win-x64 -o ./release --self-contained true -p:PublishSingleFile=true
```

**Java Version:**
```bash
cd pc-tool-java
./gradlew jar
java -jar build/libs/InputBlockerSetup-1.0.0.jar
```

---

## Usage

### Theme Customization

The app supports three theme modes:

| Theme | Description |
|-------|-------------|
| **System Default** | Follows your device's system theme setting |
| **Light** | Clean light theme with white backgrounds |
| **Dark** | Dark theme with grey backgrounds |
| **AMOLED** | Pure black theme optimized for AMOLED screens |

To change the theme:
1. Open the InputBlocker app
2. Tap the **Theme** button in the top-right corner
3. Select your preferred theme
4. The app will restart with the new theme applied

### Quick Start Guide

1. **Connect** your device via USB
2. **Enable** USB debugging on your device
3. **Launch** the InputBlocker Setup tool
4. **Draw** rectangles on the screen preview where ghost taps occur
5. **Save** the configuration
6. **Test** the blocking

### PC Setup Tool Interface

```
┌─────────────────────────────────────┐
│  Status: Connected: ABC123XYZ       │
│  Regions: 2  [✓] Blocking Enabled   │
├─────────────────────────────────────┤
│                                     │
│     [Device Screen Preview]          │
│     ┌─────────────────┐           │
│     │ (1) Blocked     │ ← Blue box │
│     │     Region 1    │           │
│     └─────────────────┘           │
│                                     │
│              ┌─────────┐            │
│              │ (2)     │ ← Blue box │
│              └─────────┘            │
│                                     │
├─────────────────────────────────────┤
│  [Refresh] [Undo] [Clear] [Save]   │
│  R=Undo  C=Clear  S=Save  Space=Refresh│
└─────────────────────────────────────┘
```

### Mouse Controls

| Action | Control |
|--------|---------|
| Draw region | Left-click + drag |
| Delete region | Right-click on region |
| Undo last | R key or Undo button |
| Clear all | C key or Clear button |
| Refresh screen | Space key or Refresh button |
| Save & push | S key or Save button |

### Terminal Commands

On your device, run `inputblocker` with these commands:

```bash
# Show status
inputblocker status

# Add a region
inputblocker add 0,0,100,200

# List regions
inputblocker list

# Remove region by ID
inputblocker remove 1

# Enable blocking
inputblocker enable

# Disable blocking
inputblocker disable

# Reset crash protection flag
inputblocker reset-crash

# Show help
inputblocker help
```

---

## Configuration

### Config File Location

```
/data/adb/modules/inputblocker/config/blocked_regions.conf
```

### Config File Format

```conf
# InputBlocker Configuration
# Created by Laviesss

enabled=1
force_safe_mode=0

# Blocked regions (one per line)
# Format: x1,y1,x2,y2
# x1,y1 = top-left corner
# x2,y2 = bottom-right corner

# Example regions:
980,1720,1080,1920    # Bottom-right corner (common ghost tap area)
0,0,200,300           # Top-left area
500,1000,600,1100     # Center region
```

### Config Options

| Option | Values | Description |
|--------|--------|-------------|
| `enabled` | 0, 1 | Enable/disable all blocking |
| `force_safe_mode` | 0, 1 | Crash protection active |
| Region lines | x1,y1,x2,y2 | Blocked region coordinates |

---

## Building from Source

### Requirements

| Component | Requirement |
|-----------|-------------|
| **C# Version** | .NET 8.0 SDK |
| **Java Version** | Java 17+ JDK |
| **Android App** | Android SDK |
| **Root Module** | Just zip the folder and install via your root manager |

### Build All Platforms (C#)

```bash
# Windows
build-all.bat csharp

# Linux/Mac
./build-all.sh csharp
```

Output: `releases/csharp/`

### Individual Platform Builds

```bash
# Windows x64
dotnet publish -c Release -r win-x64 -o ./release/win-x64 --self-contained true -p:PublishSingleFile=true

# Linux x64
dotnet publish -c Release -r linux-x64 -o ./release/linux-x64 --self-contained true -p:PublishSingleFile=true

# macOS Apple Silicon
dotnet publish -c Release -r osx-arm64 -o ./release/macos --self-contained true -p:PublishSingleFile=true
```

### Build Java Version

```bash
cd pc-tool-java
./gradlew jar
```

Output: `build/libs/InputBlockerSetup-1.0.0.jar`

### Build Android App

```bash
cd android-app
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

### GitHub Actions (Automatic Releases)

Push a tag to trigger automatic builds:
```bash
git tag v1.0.0
git push origin v1.0.0
```

This builds:
- All 6 C# platforms
- Java JAR
- Android APK

---

## How It Works

### Architecture Overview

```
┌─────────────────┐     ┌──────────────────┐
│   PC Setup Tool │────▶│   Android Device │
│  (Visual GUI)   │ ADB │                  │
└─────────────────┘     └────────┬─────────┘
                                  │
                    ┌─────────────┼─────────────┐
                    │             │             │
              ┌─────▼─────┐ ┌────▼────┐ ┌─────▼─────┐
              │   Magisk  │ │  App    │ │  Native   │
              │  Module   │ │  Suite  │ │  Service  │
              └───────────┘ └─────────┘ └───────────┘
```

### Component Breakdown

#### 1. Overlay Service
- Runs as a foreground service
- Displays semi-transparent overlay on screen
- Captures touch events
- Compares touch coordinates against configured regions
- Blocks touches in blocked regions

#### 2. Volume Button Listener Service
- System-wide listener for volume button events
- Detects kill switch sequence: **Vol↓×3 → Vol↑×3** (within 5 seconds)
- Immediately disables blocking when triggered
- Works even when screen is unresponsive

#### 3. Boot Receiver
- Listens for boot completed events
- Checks crash protection flag
- Auto-disables blocking if unexpected shutdown detected
- Starts overlay and volume services

#### 4. PC Setup Tool
- Connects to device via ADB
- Captures screen in real-time
- Allows visual region drawing
- Pushes configuration to device

### Touch Blocking Flow

```
Touch Event Occurs
        │
        ▼
┌───────────────┐
│  Overlay View │
│  Intercepts   │
└───────┬───────┘
        │
        ▼
┌───────────────┐     ┌──────────────┐
│ Check if in   │────▶│  Not in     │──▶ Pass through
│ blocked region│     │  region      │
└───────┬───────┘     └──────────────┘
        │
        │ Yes
        ▼
┌───────────────┐
│   Blocked!   │
│ Return true  │
└───────────────┘
```

---

## Troubleshooting

### Common Issues

#### Regions Not Blocking Touches

1. Check if blocking is enabled:
   ```bash
   adb shell inputblocker status
   ```

2. Verify overlay permission is granted (app shows prompt on first launch)

3. Check log for errors:
   ```bash
   adb logcat | grep InputBlocker
   ```

4. Ensure config file exists:
   ```bash
   adb shell cat /data/adb/modules/inputblocker/config/blocked_regions.conf
   ```

#### Can't Interact with Screen

**Use the Kill Switch:**
Press **Vol↓ × 3** then **Vol↑ × 3** within 5 seconds.

Phone will vibrate and blocking will be disabled.

**Via ADB:**
```bash
adb shell inputblocker disable
```

#### Device Won't Boot Properly

Crash protection should auto-disable blocking. If not:

```bash
adb shell inputblocker reset-crash
adb shell reboot
```

#### Config File Not Found

Create it manually:
```bash
adb shell "mkdir -p /data/adb/modules/inputblocker/config"
adb shell "echo 'enabled=1' > /data/adb/modules/inputblocker/config/blocked_regions.conf"
```

#### PC Tool Can't Connect

1. Enable USB debugging: Settings → Developer Options → USB Debugging

2. Authorize computer: Allow USB debugging prompt on device

3. Verify connection:
   ```bash
   adb devices
   ```

4. Restart ADB:
   ```bash
   adb kill-server
   adb start-server
   ```

---

## FAQ

### Q: Does this work on non-rooted devices?

**A:** The root module requires root. For non-rooted devices, you would need to use ADB commands directly or the companion app's accessibility service (limited functionality).

### Q: Will this drain my battery?

**A:** Minimal impact. The overlay service uses very little resources - it's just a transparent layer that checks touch coordinates.

### Q: Can I block multiple regions?

**A:** Yes! There's no limit. You can block as many regions as needed.

### Q: Does blocking work during calls?

**A:** The overlay should remain active. If you experience issues, the kill switch is always available.

### Q: How do I update the config without rebooting?

**A:** Use the companion app or run:
```bash
adb shell am broadcast -a com.inputblocker.RELOAD
```

### Q: Can I temporarily disable without losing regions?

**A:** Yes. Use the app toggle or:
```bash
adb shell inputblocker disable
# Later:
adb shell inputblocker enable
```

### Q: What happens if I uninstall the root module?

**A:** All blocking stops immediately. Regions are stored in the module folder, so uninstalling removes them. Export your config first if needed.

### Q: Does this work with screen protectors?

**A:** Yes. Touch blocking is software-based and works regardless of screen protectors.

### Q: Can I use this for gaming?

**A:** Yes. Some users block L2/R2 trigger areas or dead zones on screens.

### Q: Is this safe to use?

**A:** Yes. The crash protection and kill switch ensure you can always disable blocking if something goes wrong.

---

## License

MIT License

**Author:** Laviesss

---

## Contributing

Pull requests welcome! Please:
1. Test changes on multiple devices
2. Update documentation
3. Follow existing code style
