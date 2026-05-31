# Build Scripts

This directory contains automation scripts for building the InputBlocker ecosystem without invoking Gradle directly.

---

## Scripts Overview

| Script | Purpose | Dependencies |
|---|---|---|
| `build_all.sh` / `build_all.bat` | Primary entry point — builds Android APK, root module, and PC tools | JDK 17, Android SDK |
| `build_android.sh` | Compiles only the Android companion app APK | JDK 17, Android SDK |
| `build_module.sh` | Packages the root module ZIP (requires prior Android build) | JDK 17 |
| `build_pc_tools.sh` | Compiles the Kotlin Compose Desktop PC Designer | JDK 17 |

---

## Usage

### Bash (Linux / macOS / WSL)

```bash
chmod +x *.sh
./build_all.sh
```

### Batch (Windows)

```cmd
.\build_all.bat
```

---

## Output

All successful builds are placed in the `releases/` folder at the project root. The Gradle wrapper is invoked internally — ensure `JAVA_HOME` points to JDK 17.

> For advanced build options (versioning, component selection, CI integration), see [BUILD.md](../BUILD.md).
