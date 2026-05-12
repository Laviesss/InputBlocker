# Building InputBlocker

This guide provides detailed instructions for building all components of the InputBlocker ecosystem from source.

## 🛠️ Environment Requirements

### Core Dependencies
- **Java Development Kit (JDK) 17+**: Required for the Android APK and Java PC tool.
- **.NET 8.0 SDK**: Required for the C# cross-platform PC tools.
- **Android SDK & Platform Tools**: Required for building the APK and packaging the root module.
- **Git**: For version control and dependency management.

### OS Specifics
- **Windows**: PowerShell 5.1+ recommended.
- **Linux/macOS**: Bash shell.

---

## 🚀 Build Orchestration (Easy Way)

The project includes a suite of wrapper scripts that handle the complex build chain automatically.

| Script | Purpose | Output |
|--------|---------|--------|
| `build-all.bat/.sh` | Builds every single component | All binaries in `releases/` |
| `build-module.bat/.sh` | Builds the Root Module (with APK) | `releases/InputBlocker.zip` |
| `build-apk.bat/.sh` | Builds the Companion App only | `releases/InputBlocker.apk` |
| `build-pctools.bat/.sh` | Builds all PC setup tools | `releases/csharp/` and `releases/java/` |

### Example Usage
**Windows:**
```powershell
.\build-all.bat
```

**Linux/macOS:**
```bash
chmod +x *.sh
./build-all.sh
```

---

## 🔍 Component-Specific Build Guides

### 1. Android Companion App
The app is built using Gradle.
- **Debug Build**: `./gradlew :app:assembleDebug`
- **Release Build**: `./gradlew :app:assembleRelease`
- **Output**: `android-app/app/build/outputs/apk/`

### 2. Root Module (ZIP)
The root module is a specialized ZIP package.
- **Process**: The build script collects the compiled APK, the `service.sh` logic, `module.prop`, and default configuration files.
- **Structure**: 
    - `system/bin/inputblocker` (Binary)
    - `common/InputBlocker.apk` (Companion App)
    - `module.prop` (Module metadata)
    - `service.sh` (Boot-time logic)

### 3. C# Setup Tool (.NET 8)
Built using Avalonia UI for cross-platform support.
- **Command**: `dotnet publish -c Release -r [RID] -o [Output] --self-contained true`
- **Supported RIDs**: 
    - `win-x64`, `win-arm64`
    - `linux-x64`, `linux-arm64`
    - `osx-x64`, `osx-arm64`

### 4. Java Setup Tool
A lightweight alternative built with Java Swing.
- **Build**: Compiled via `javac` and packaged into a runnable JAR.

---

## ☁️ CI/CD Pipeline (GitHub Actions)

The project uses a sophisticated GitHub Actions pipeline for automated releases.

**Workflow: `release.yml`**
1. **Trigger**: Pushing a tag matching `v*` (e.g., `v1.0.0`).
2. **Parallel Build**:
   - 6 separate jobs build the C# tool for every supported architecture.
   - 1 job builds the Android APK.
   - 1 job packages the Root Module.
3. **Aggregation**: The `Create GitHub Release` job collects all artifacts from the previous jobs.
4. **Release**: Generates checksums and creates a formal GitHub Release with all binaries attached.

**Permissions Note**: The workflow requires `contents: write` permissions to create the release.
