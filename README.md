# InputBlocker

**Block ghost taps and unwanted touch inputs by defining rectangular regions on your screen.**

[![Build](https://github.com/Laviesss/InputBlocker/actions/workflows/release.yml/badge.svg)](https://github.com/Laviesss/InputBlocker/actions)
[![Release](https://img.shields.io/github/v/release/Laviesss/InputBlocker?style=flat-square)](https://github.com/Laviesss/InputBlocker/releases)
[![License](https://img.shields.io/github/license/Laviesss/InputBlocker?style=flat-square)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-gree?style=flat-square)](https://www.android.com/)
[![Root](https://img.shields.io/badge/root-Magisk%20%7C%20KernelSU%20%7C%20APatch%20%7C%20SuperSU-blue?style=flat-square)](#root-manager-support)

## Features

| Feature | Description |
|---------|-------------|
| **Visual Setup** | Draw regions on screen preview via PC tool |
| **Semi-Transparent Overlay** | Blue overlay shows blocked areas |
| **Kill Switch** | Vol↓×3 → Vol↑×3 disables blocking |
| **Crash Protection** | Auto-disables on unexpected shutdown |
| **Multi-Root Support** | Works with Magisk, KernelSU, APatch, SuperSU |
| **Multi-Platform** | Works on Windows, Linux, macOS |
| **Auto APK Install** | Companion app auto-installed when module boots |

## Quick Start

1. **Install** the root module (`InputBlocker.zip`)
2. **Reboot** - companion app auto-installs
3. **Connect** device via USB (for PC tool)
4. **Launch** PC Setup tool (or use on-device setup)
5. **Draw** rectangles where ghost taps occur
6. **Save** configuration

## Downloads

| File | Description |
|------|-------------|
| `InputBlocker.zip` | Root module with APK included |
| `InputBlocker-lite.zip` | Root module without APK (smaller) |
| `InputBlocker.apk` | Standalone companion app |
| `InputBlockerSetup` | PC setup tool (all platforms) |

Get releases from [GitHub Releases](https://github.com/Laviesss/InputBlocker/releases)

## Requirements

| Component | Requirement |
|-----------|-------------|
| Android | Root access (Magisk, KernelSU, APatch, or SuperSU) |
| PC Tool | Windows, Linux, or macOS |
| Building | .NET 8.0 SDK or Java 17+ JDK |
| Android SDK | Required for APK/module builds |

## Root Manager Support

This module automatically detects and works with:
- **Magisk** - Most common root manager
- **KernelSU** - Kernel-based root solution
- **APatch** - Alternative root solution
- **SuperSU** - Legacy root manager

The module path is automatically detected based on your installed root manager.

## Usage

### Kill Switch

Press **Volume Down × 3** then **Volume Up × 3** within 5 seconds to disable blocking.

### Terminal Commands

```bash
inputblocker status          # Show status
inputblocker add 0,0,100,200 # Add region
inputblocker enable          # Enable blocking
inputblocker disable         # Disable blocking
inputblocker reset-crash     # Reset crash protection
```

### Config File

Location is automatically detected based on your root manager:
- `/data/adb/modules/inputblocker/config/blocked_regions.conf`
- `/data/su.d/inputblocker/config/blocked_regions.conf`

```conf
enabled=1
force_safe_mode=0

# Format: x1,y1,x2,y2
980,1720,1080,1920    # Bottom-right corner
```

## Building from Source

### Build Scripts

| Script | What it builds |
|--------|----------------|
| `build-module.bat/.sh` | Module with APK |
| `build-module.bat/.sh lite` | Module without APK |
| `build-apk.bat/.sh` | APK only |
| `build-pctools.bat/.sh` | PC tools only (C# + Java) |
| `build-all.bat/.sh` | Everything |

### Module Builds

```bash
# Full (with APK) - Recommended
build-module.bat full
./build-module.sh full

# Lite (without APK)
build-module.bat lite
./build-module.sh lite
```

**Outputs:**
- `releases/InputBlocker.zip` - Module with APK (auto-installs app)
- `releases/InputBlocker-lite.zip` - Module without APK (smaller)

### Other Builds

```bash
# APK only
build-apk.bat release
./build-apk.sh release

# Everything
build-all.bat
./build-all.sh
```

## Project Structure

```
InputBlocker/
├── magisk-module/           # Root module folder (install this)
│   └── common/              # APK auto-installed on boot
├── android-app/             # Companion app source
├── pc-tool-csharp/          # C# PC tool
├── pc-tool-java/            # Java PC tool
├── build-module.bat/.sh     # Build module with/without APK
├── build-apk.bat/.sh       # Build APK only
├── build-pctools.bat/.sh   # Build PC tools only
├── build-all.bat/.sh       # Build everything
├── BUILD.md                 # Detailed build instructions
├── DOCUMENTATION.md         # Full documentation
└── README.md                # This file
```

## Documentation

- **[DOCUMENTATION.md](DOCUMENTATION.md)** - Complete guide
- **[BUILD.md](BUILD.md)** - Detailed build instructions

## Disclaimer

**USE AT YOUR OWN RISK.**

- This software is provided as-is without any warranties
- The author is not responsible for any damage, data loss, or other issues
- Always test in a safe environment first
- This module intercepts touch inputs - ensure you understand what this means before using
- Keep backup of your device data
- The kill switch exists as a safety measure - know how to use it before enabling blocking

## Testing Limitations

Due to hardware and software constraints, not all devices and configurations can be tested. If you encounter issues:
1. Check the [Troubleshooting section](DOCUMENTATION.md#troubleshooting)
2. Report issues with your device model and root manager details
3. Contributions and pull requests are welcome

## License

MIT License - See [LICENSE](LICENSE) for details. Created by Laviesss

## Contributing

Pull requests welcome! Please:
1. Test changes on multiple devices and root managers
2. Update documentation
3. Follow existing code style
