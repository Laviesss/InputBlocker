# InputBlocker Build Scripts

This directory contains the automation scripts used to compile and package the various components of the InputBlocker ecosystem.

## 📁 Directory Structure

```
build-scripts/
├── build.bat           # Main entry point: builds all components
├── csharp/
│   └── build.bat       # Builds cross-platform C# Avalonia tools
├── java/
│   └── build.bat       # Builds the Java Swing tool
├── apk/
│   └── build.bat       # Compiles the Android companion app
└── module/
    └── build.bat       # Packages the root module ZIP (Full & Lite)
```

## 🚀 Usage Guide

### 1. Full System Build
To build every component in the repository (Android APK, all C# binaries, Java JAR, and the Root Module), run the main wrapper:

**Windows:**
```batch
build-scripts\build.bat
```

### 2. Targeted Builds
You can build specific components by passing an argument to the main script:

| Command | Description |
|----------|-------------|
| `build-scripts\build.bat csharp` | Build C# tools for all 6 platforms |
| `build-scripts\build.bat java` | Build the Java JAR tool |
| `build-scripts\build.bat apk` | Compile the Android APK |
| `build-scripts\build.bat module` | Package the root module ZIP |

### 3. Direct Component Builds
Alternatively, you can run the sub-scripts directly:
- `build-scripts\csharp\build.bat`
- `build-scripts\java\build.bat`
- `build-scripts\apk\build.bat`
- `build-scripts\module\build.bat`

## 🛠️ Requirements
- **C# Tools**: .NET 8.0 SDK
- **Java Tool**: Java 17+ JDK
- **Android APK**: Android SDK & Platform Tools
- **Root Module**: Requires a previously built APK for the "Full" version.

## 📦 Output Destination
All finalized binaries are placed in the project root `releases/` folder:
- `releases/csharp/` $\rightarrow$ C# binaries for 6 platforms
- `releases/java/` $\rightarrow$ Java JAR
- `releases/InputBlocker.apk` $\rightarrow$ Android APK
- `releases/InputBlocker.zip` $\rightarrow$ Full Root Module
- `releases/InputBlocker-lite.zip` $\rightarrow$ Lite Root Module
