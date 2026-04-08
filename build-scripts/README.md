# InputBlocker Build Scripts

## Structure

```
build-scripts/
├── build.bat           # Build all (calls sub-scripts)
├── csharp/
│   └── build.bat       # Build C# Avalonia app (all platforms)
├── java/
│   └── build.bat       # Build Java Swing app
├── apk/
│   └── build.bat       # Build Android APK
└── module/
    └── build.bat       # Build Magisk module zip
```

## Usage

### Build Everything
```batch
build-scripts\build.bat
```

### Build Individual Components
```batch
build-scripts\build.bat csharp    # C# only (Windows, Linux, macOS*)
build-scripts\build.bat java      # Java only
build-scripts\build.bat apk       # APK only
build-scripts\build.bat module    # Module only (full + lite)
```

### Build Specific Tool
```batch
build-scripts\csharp\build.bat    # C# Avalonia
build-scripts\java\build.bat      # Java Swing
build-scripts\apk\build.bat       # Android APK
build-scripts\module\build.bat    # Magisk module
```

## Requirements

| Component | Required | Optional |
|-----------|----------|----------|
| C#        | .NET 8.0 SDK | |
| Java      | Java 17+ JDK | |
| APK       | Android SDK | |
| Module    | | APK (for full build) |

## Output

All builds go to `releases/`:
```
releases/
├── csharp/
│   ├── windows-x64/
│   ├── windows-arm64/
│   ├── linux-x64/
│   ├── linux-arm64/
│   ├── macos-intel/     # Run on macOS to build
│   └── macos-apple-silicon/
├── java/
│   └── InputBlockerSetup-1.0.0.jar
├── InputBlocker.apk
├── InputBlocker.zip       # Full module (with APK)
└── InputBlocker-lite.zip  # Lite module (no APK)
```

## Notes

- macOS builds must be done on macOS (cross-compilation not supported)
- Module builds will create both full (with APK) and lite (without APK) versions
- Use `build.bat module lite` to skip APK and just build the module structure
