# Build Instructions

This project is a monorepo containing the Android app, the PC Setup Tool, and a shared Kotlin Multiplatform (KMP) core.

## 🛠️ Environment Requirements

### Java Development Kit (JDK)
- **Version**: JDK 17 (Required)
- **Recommended**: Eclipse Temurin or OpenJDK 17.
- **Note**: If you have multiple Java versions installed, ensure your `JAVA_HOME` is set to JDK 17.

### Android SDK
- **Compile SDK**: 34
- **Min SDK**: 21
- **Target SDK**: 34

---

## 📦 Building the Project

The project uses the Gradle Wrapper. You do not need to install Gradle manually.

### 1. Build Everything (Recommended)
To build the Android APK, the PC Tool EXE, and the Root Module ZIP in one go:

**Windows (PowerShell):**
```powershell
.\gradlew buildAll -PVERSION_NAME="0.1.0" -PVERSION_CODE=1
```

**Linux/macOS:**
```bash
./gradlew buildAll -PVERSION_NAME="0.1.0" -PVERSION_CODE=1
```

### 2. Build Individual Parts
If you only need a specific component, use these tasks:

- **Android APK**: `.\gradlew buildAndroid -PVERSION_NAME="0.1.0" -PVERSION_CODE=1`
- **PC Tool EXE**: `.\gradlew buildPC -PVERSION_NAME="0.1.0" -PVERSION_CODE=1`
- **Root Module ZIP**: `.\gradlew buildModule -PVERSION_NAME="0.1.0" -PVERSION_CODE=1`

### 3. Understanding Version Flags
The build will fail if you don't provide the version flags.
- `-PVERSION_NAME`: The user-visible version (e.g., `0.1.0`).
- `-PVERSION_CODE`: The internal integer version (e.g., `1`).

---

## 📂 Output Locations

After a successful `buildAll`, you can find your files here:

- **Android APK**: `android-app/app/build/outputs/apk/release/app-release.apk`
- **PC Tool EXE**: `pc-tool-kotlin/build/compose/binaries/InputBlockerSetup.exe`
- **Module ZIP**: `build/distributions/inputblocker.zip`

---

## ⚠️ Troubleshooting

### Java Version Error
If you see an error about `Unsupported class file major version`, your system is likely using a Java version other than 17.
- **Fix**: Set your `JAVA_HOME` environment variable to point to JDK 17.

### SDK Not Found
If Gradle cannot find the Android SDK:
- **Fix**: Create a `local.properties` file in the root directory and add:
  `sdk.dir=C\:\\Users\\YourUser\\AppData\\Local\\Android\\Sdk`

This guide explains how to build the InputBlocker ecosystem from source.

---

## 📦 Project Structure
- `/android-app`: The Android companion app and Xposed module.
- `/shared`: The Kotlin Multiplatform (KMP) core containing shared `Region` logic.
- `/pc-tool-kotlin`: The modern Compose for Desktop designer.
- `/pc-tool-csharp`: The legacy Windows designer (Maintenance Mode).

---

## 📱 Building the Android App
The app is a standard Android project with an Xposed module integration.

### Prerequisites
- Android Studio (Hedgehog or newer)
- JDK 17

### Steps
1. Open the `/android-app` folder in Android Studio.
2. Sync Gradle.
3. Build the APK: `Build` $\rightarrow$ `Build Bundle(s) / APK(s)` $\rightarrow$ `Build APK(s)`.
4. The resulting APK should be flashed as a root module or installed as a standard app depending on your root manager's requirements.

---

## 💻 Building the Kotlin PC Tool
The new designer is built with **Compose for Desktop**.

### Prerequisites
- IntelliJ IDEA (Community or Ultimate)
- JDK 17

### Steps
1. Open the `/pc-tool-kotlin` folder in IntelliJ.
2. Sync Gradle.
3. Run the app: `./gradlew run`
4. Create a native executable: `./gradlew packageDistributionForCurrentOS`
   - The executable will be found in `build/compose/binaries`.

---

## 🪟 Building the C# PC Tool (Legacy)
The legacy tool is built using **Avalonia UI**.

### Prerequisites
- .NET 8 SDK
- Visual Studio 2022 or VS Code with C# Dev Kit

### Steps
1. Open the `/pc-tool-csharp` folder.
2. Run the project: `dotnet run`
3. Publish as a single-file executable:
   `dotnet publish -c Release -r win-x64 --self-contained true /p:PublishSingleFile=true`

---

## 🛠️ Troubleshooting Build Issues
- **Gradle Sync Failures**: Ensure you have the correct JDK 17 configured in your IDE.
- **ADB Connection**: If the PC tools can't see your device, check that USB Debugging is enabled and you have accepted the RSA fingerprint prompt on the phone.
- **LSPosed Errors**: Ensure the module is enabled for the `system_server` process in the LSPosed manager.

