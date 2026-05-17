# Build Guide

This document provides instructions for building InputBlocker components from source.

## Prerequisites

### Toolchain
- **JDK 17+**: Required for the Android app and Java PC tool.
- **.NET 8.0 SDK**: Required for the C# PC tool.
- **Android SDK & Platform Tools**: Required for APK compilation and module packaging.
- **Git**: For version control.

### OS-Specifics
- **Windows**: PowerShell 5.1+ recommended.
- **Linux/macOS**: Bash shell.

## Build Orchestration

The project includes automation scripts to simplify the build process. These are located in the `build-scripts/` directory.

### Available Scripts
| Script | Purpose | Output |
|--------|---------|--------|
| `build_all.sh` / `build_all.bat` | Builds all components | All binaries in `releases/` |
| `build_android.sh` | Builds the Companion App only | `releases/InputBlocker.apk` |
| `build_module.sh` | Packages the Root Module (includes APK) | `releases/InputBlocker.zip` |
| `build_pc_tools.sh` | Builds both C# and Java tools | `releases/csharp/` and `releases/java/` |

### Usage Examples
**Windows:**
```powershell
cd build-scripts
.\build_all.bat
```

**Linux/macOS:**
```bash
cd build-scripts
chmod +x *.sh
./build_all.sh
```

## Component Details

### Android App
Built using Gradle.
- **Debug Build**: `./gradlew :app:assembleDebug`
- **Release Build**: `./gradlew :app:assembleRelease`

### Root Module
The module is a ZIP package containing:
- `module.prop`: Module metadata.
- `service.sh`: Installation and boot-time logic.
- `common/InputBlocker.apk`: The companion management app.

### C# Setup Tool
Built with Avalonia UI.
- **Build Command**: `dotnet publish -c Release -r [RID] --self-contained true`
- **Supported Platforms**: Windows (x64, ARM64), Linux (x64, ARM64), macOS (x64, ARM64).

### Java Setup Tool
A lightweight Swing-based alternative.
- **Build**: Compiled via Gradle/javac and packaged as a runnable JAR.

## CI/CD Pipeline

Automated releases are handled via GitHub Actions (`.github/workflows/release.yml`).
- **Trigger**: Push a tag matching `v*` (e.g., `git tag v0.1.0 && git push --tags`).
- **Process**: The pipeline injects versions, builds all components in parallel, generates SHA-256 checksums, and publishes a GitHub Release.
