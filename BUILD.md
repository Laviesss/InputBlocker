# Build Instructions

This project is a monorepo consisting of the Android Engine, the PC Designer, and a shared Kotlin Multiplatform (KMP) core.

## 🛠️ Requirements

### Java Development Kit (JDK)
- **Version**: JDK 17 (Required).
- Ensure your `JAVA_HOME` is pointed to a JDK 17 distribution (e.g., Eclipse Temurin).

### Android SDK
- **Compile/Target SDK**: 34
- **Min SDK**: 23

---

## 📦 Building the Project

The project uses the Gradle Wrapper. No manual Gradle installation is required.

### 1. Build All Artifacts (Recommended)
This task builds the Android APK, the PC Tool EXE, and the Root Module ZIP in a single pass.

**Windows (PowerShell):**
```powershell
.\gradlew buildAll -PVERSION_NAME="<version_name>" -PVERSION_CODE=<version_code>
```

**Linux/macOS:**
```bash
./gradlew buildAll -PVERSION_NAME="<version_name>" -PVERSION_CODE=<version_code>
```

### 2. Individual Component Builds
If you only need a specific part, use these tasks:
- **Android APK**: `.\gradlew buildAndroid -PVERSION_NAME="<version_name>" -PVERSION_CODE=<version_code>`
- **PC Tool EXE**: `.\gradlew buildPC -PVERSION_NAME="<version_name>" -PVERSION_CODE=<version_code>`
- **Root Module ZIP**: `.\gradlew buildModule -PVERSION_NAME="<version_name>" -PVERSION_CODE=<version_code>`

### 3. Versioning Requirements
The build will **fail** if version flags are missing.
- `-PVERSION_NAME`: The user-visible version string (e.g., `1.0.0`).
- `-PVERSION_CODE`: The internal integer version used for update tracking (e.g., `10`).

---

## 📂 Output Locations

After a successful `buildAll`, artifacts are located here:
- **Android APK**: `android-app/app/build/outputs/apk/release/app-release.apk`
- **PC Tool EXE**: `pc-tool-kotlin/build/compose/binaries/main/exe/InputBlockerSetup-<version>.exe`
- **PC Tool DEB**: `pc-tool-kotlin/build/compose/binaries/main/deb/inputblockersetup_<version>_amd64.deb`
- **PC Tool DMG**: `pc-tool-kotlin/build/compose/binaries/main/dmg/InputBlockerSetup-<version>.dmg`
- **Module ZIP**: `build/distributions/inputblocker.zip`

---

## ⚠️ Troubleshooting

### Java Version Mismatch
If you encounter an `Unsupported class file major version` error, your environment is not using JDK 17. Check your `JAVA_HOME` and IDE settings.

### SDK Path Issues
If Gradle cannot locate the Android SDK, create a `local.properties` file in the root directory:
`sdk.dir=C\:\\Users\\YourUser\\AppData\\Local\\Android\\Sdk`

---

## 📂 Project Structure
- `/android-app`: Android companion app and Xposed module.
- `/shared`: KMP core (Region logic, coordinate normalization).
- `/pc-tool-kotlin`: Compose for Desktop visual designer.
- `/module`: Root module files and scripts.
