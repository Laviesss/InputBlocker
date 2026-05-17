# Build Guide

This document provides streamlined instructions for building InputBlocker components from source.

## 🛠️ Prerequisites

### Toolchain
- **JDK 17+**: Required for the Android app and Java PC tool.
- **.NET 8.0 SDK**: Required for the C# PC tool.
- **Android SDK & Platform Tools**: Required for APK compilation and module packaging.
- **Git**: For version control.

### OS-Specifics
- **Windows**: PowerShell 5.1+ recommended.
- **Linux/macOS**: Bash shell.

## 🚀 Build Orchestration

The project uses automation scripts in the `build-scripts/` directory to simplify the build process.

### Available Scripts
| Script | Purpose | Output |
|--------|---------|--------|
| `build_all.sh` / `build_all.bat` | Full pipeline build | All binaries in `releases/` |
| `build_android.sh` | Build Companion App only | `releases/InputBlocker.apk` |
| `build_module.sh` | Package Root Module (includes APK) | `releases/InputBlocker.zip` |
| `build_pc_tools.sh` | Build C# and Java tools | `releases/csharp/` and `releases/java/` |

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

## 📦 Component Details

### Android App
Built via Gradle.
- **Debug**: `./gradlew :app:assembleDebug`
- **Release**: `./gradlew :app:assembleRelease`

### Root Module
A ZIP package comprising:
- `module.prop`: Metadata and versioning.
- `service.sh`: Installation and boot-time orchestration.
- `common/InputBlocker.apk`: The companion management app.

### C# Setup Tool
Built with Avalonia UI.
- **Build**: `dotnet publish -c Release -r [RID] --self-contained true`

### Java Setup Tool
A lightweight Swing-based utility.
- **Build**: Compiled via Gradle/javac and packaged as a runnable JAR.

## ⚙️ CI/CD Pipeline
Automated releases are managed via GitHub Actions (`.github/workflows/release.yml`).
- **Trigger**: Push a tag matching `v*` (e.g., `git tag v0.1.0 && git push --tags`).
- **Pipeline**: Injects versions, builds all components in parallel, generates SHA-256 checksums, and publishes a GitHub Release.
