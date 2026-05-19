# 🛠️ Build Guide (v0.1.0)

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

