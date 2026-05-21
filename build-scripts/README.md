# Build Scripts

This directory contains automation scripts to build the InputBlocker ecosystem.

## Scripts Overview

- **`build_all.sh` / `build_all.bat`**: The primary entry point. Builds the Android APK, Root Module, and all PC tools.
- **`build_android.sh`**: Compiles the Android companion app APK.
- **`build_module.sh`**: Packages the final root module ZIP. Requires a successful Android build first.
- **`build_pc_tools.sh`**: Compiles the Kotlin Compose Designer tool.

## Usage

### Bash (Linux/macOS/WSL)
```bash
chmod +x *.sh
./build_all.sh
```

### Batch (Windows)
```cmd
.\build_all.bat
```

## Output
All successful builds are placed in the `releases/` folder at the project root.
