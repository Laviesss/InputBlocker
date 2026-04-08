# Building InputBlocker - Complete Guide

## Build Scripts Overview

| Script | Purpose | Output |
|--------|---------|--------|
| `build-module.bat/.sh` | Module with APK | `releases/InputBlocker.zip` |
| `build-apk.bat/.sh` | Android APK only | `releases/InputBlocker.apk` |
| `build-pctools.bat/.sh` | PC tools only | `releases/csharp/`, `releases/java/` |
| `build-all.bat/.sh` | Everything | All of the above |

---

## Building Module

Two versions available:
- **Full** - Includes APK, auto-installs companion app on boot
- **Lite** - Without APK, smaller file size

### Windows
```bash
build-module.bat full    # With APK (recommended)
build-module.bat lite    # Without APK
```

### Linux/Mac
```bash
./build-module.sh full   # With APK (recommended)
./build-module.sh lite   # Without APK
```

**Outputs:**
- `releases/InputBlocker.zip` - Full module with APK
- `releases/InputBlocker-lite.zip` - Lite module without APK

---

## Building APK Only

Build just the Android companion app.

### Windows
```bash
build-apk.bat           # Debug APK
build-apk.bat release   # Release APK
```

### Linux/Mac
```bash
./build-apk.sh           # Debug APK
./build-apk.sh release   # Release APK
```

**Output:** `releases/InputBlocker.apk`

---

## Building PC Tools Only

```bash
# Windows
build-pctools.bat

# Linux/Mac
./build-pctools.sh
```

**Output:**
- `releases/csharp/` - All 6 platforms
- `releases/java/` - Java JAR

---

## Building Everything

```bash
# Windows
build-all.bat

# Linux/Mac
./build-all.sh
```

**Output:**
- `releases/csharp/` - PC tool (6 platforms)
- `releases/InputBlocker.zip` - Module with APK
- `releases/InputBlocker.apk` - Standalone APK

---

## Building PC Tool Only

### C# Version (Recommended)

**Requirements:** [.NET 8.0 SDK](https://dotnet.microsoft.com/download/dotnet/8.0)

```bash
cd pc-tool-csharp

# Build for all platforms
dotnet publish -c Release -r win-x64 -o ../release/csharp/win-x64 --self-contained true -p:PublishSingleFile=true
dotnet publish -c Release -r win-arm64 -o ../release/csharp/win-arm64 --self-contained true -p:PublishSingleFile=true
dotnet publish -c Release -r linux-x64 -o ../release/csharp/linux-x64 --self-contained true -p:PublishSingleFile=true
dotnet publish -c Release -r linux-arm64 -o ../release/csharp/linux-arm64 --self-contained true -p:PublishSingleFile=true
dotnet publish -c Release -r osx-x64 -o ../release/csharp/osx-x64 --self-contained true -p:PublishSingleFile=true
dotnet publish -c Release -r osx-arm64 -o ../release/csharp/osx-arm64 --self-contained true -p:PublishSingleFile=true
```

Or use the script:
```bash
./build-all.sh csharp
```

### Java Version

**Requirements:** [Java 17+ JDK](https://adoptium.net/)

```bash
cd pc-tool-java
./gradlew jar
```

**Output:** `build/libs/InputBlockerSetup-1.0.0.jar`

---

## Building with GitHub Actions

Push a tag to trigger automatic builds:

```bash
git tag v1.0.0
git push origin v1.0.0
```

**Automated builds:**
- C# PC tool (6 platforms)
- Java JAR
- Android APK
- Module zip (APK + module)

---

## Manual Android Build

### Requirements
- Android SDK
- Java 17+ JDK

### Commands
```bash
cd android-app
./gradlew assembleRelease
```

**Output:** `app/build/outputs/apk/release/app-release.apk`

---

## Platform Support

| Platform | Architecture | C# | Java |
|----------|-------------|-----|------|
| Windows | x64 | ✅ | ✅ |
| Windows | ARM64 | ✅ | ✅ |
| Linux | x64 | ✅ | ✅ |
| Linux | ARM64 | ✅ | ✅ |
| macOS | Intel | ✅ | ✅ |
| macOS | Apple Silicon | ✅ | ✅ |

---

## Which Version to Use?

| Use Case | Recommended |
|----------|-------------|
| Most users | Module zip |
| Want standalone APK | APK only |
| PC visual setup | C# version |
| Already have JDK | Java version |

---

## Troubleshooting

### Gradle errors
- Update Gradle wrapper: `./gradlew wrapper --gradle-version=8.5`
- Clean: `./gradlew clean`

### jpackage not found
- Install JDK 14+ with jpackage
- Or use `JAVA_HOME` pointing to correct JDK

### .NET errors
- Update .NET SDK to 8.0+
- Check valid RIDs: https://docs.microsoft.com/en-us/dotnet/core/rid-catalog

### Access denied (Windows)
- Run terminal as Administrator
- Or disable antivirus temporarily

### macOS "App cannot be opened"
- `chmod +x InputBlockerSetup`
- System Settings → Privacy & Security → Allow

---

## Author

Laviesss
