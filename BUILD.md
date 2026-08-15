# Build Instructions

InputBlocker is a monorepo. It contains the Android Engine (Xposed/LSPosed hook), the PC Designer (Kotlin/Compose Desktop), and a shared Kotlin Multiplatform (KMP) core.

## Requirements

| Dependency | Version | Notes |
|---|---|---|
| **JDK** | 17+ | Required (JDK 17, 21+ supported). |
| **Android SDK** | API 34+ (compile/target), API 23 (min) | Set this through `ANDROID_HOME` or `local.properties`. |
| **Gradle** | 9.x | The wrapper is included, so you don't need a manual install. |
| **Kotlin Compose Plugin** | 2.4.0 | Supported alongside the Kotlin plugin version matching your Gradle setup. |

### Environment Setup

**Linux / macOS:**
```bash
export JAVA_HOME=/path/to/jdk
export ANDROID_HOME=/path/to/android-sdk
```

**Windows (PowerShell):**
```powershell
$env:JAVA_HOME = "C:\path\to\jdk"
$env:ANDROID_HOME = "C:\path\to\Android\Sdk"
```

If the Android SDK isn't found automatically, create a `local.properties` file at the project root:
```
sdk.dir=C:\\Users\\YourUser\\AppData\\Local\\Android\\Sdk
```

## Building

### 🔹 Build All Artifacts (Recommended)

This produces the APK, PC tool binaries, and module ZIP in one go.

**Windows (PowerShell):**
```powershell
.\gradlew buildAll -PVERSION_NAME="<version>" -PVERSION_CODE=<code>
```

**Linux / macOS / WSL:**
```bash
./gradlew buildAll -PVERSION_NAME="<version>" -PVERSION_CODE=<code>
```

### 🔹 Individual Builds

Build only the part you need:

| Command | Artifact |
|---|---|
| `buildAndroid` | Android APK |
| `buildPC` | PC Designer EXE / DEB / DMG |
| `buildModule` | Root module ZIP (needs a prior Android build) |

```bash
./gradlew buildAndroid -PVERSION_NAME="0.1.0" -PVERSION_CODE=1
```

### 🔹 Docker Build (CI Reproducibility)

If you want a clean, reproducible environment, use the provided Dockerfile. This ensures your build matches the CI pipeline exactly.

```bash
docker build -t inputblocker-builder .
docker run --rm -v $(pwd):/home/gradle/project -w /home/gradle/project inputblocker-builder ./gradlew buildAll -PVERSION_NAME="0.1.0" -PVERSION_CODE=1
```

## Version Parameters

Version flags can be passed via `-PVERSION_NAME` and `-PVERSION_CODE`. Defaults (`0.1.0` / `1`) are configured in `gradle.properties` if omitted.

| Flag | Type | Default | Example | Purpose |
|---|---|---|---|---|
| `-PVERSION_NAME` | String | `"0.1.0"` | `"0.1.0"` | The version string users see. |
| `-PVERSION_CODE` | Int | `1` | `1` | Internal integer for tracking updates. |

Our CI/CD workflow handles automatic version assignment for official releases.

## Verifying Your Build

Once the build finishes, you should verify the artifacts before deployment.

1. **Check Signatures**: Ensure the APK is signed. You can use `apksigner verify --print-certs path/to/app.apk`.
2. **Checksums**: Compare the generated SHA-256 hashes with previous builds if you're doing a regression check.
3. **Device Test**: Install the APK on a rooted device. Verify the module loads correctly in LSPosed.

## Output Locations

| Artifact | Path |
|---|---|
| **Android APK** | `android-app/app/build/outputs/apk/release/app-release.apk` |
| **PC Tool (EXE)** | `pc-tool-kotlin/build/compose/binaries/main/exe/InputBlockerSetup-<version>.exe` |
| **PC Tool (DEB)** | `pc-tool-kotlin/build/compose/binaries/main/deb/inputblockersetup_<version>_amd64.deb` |
| **PC Tool (DMG)** | `pc-tool-kotlin/build/compose/binaries/main/dmg/InputBlockerSetup-<version>.dmg` |
| **Module ZIP** | `build/distributions/InputBlockerModule.zip` |

## Troubleshooting

### Java Version Mismatch

```
Unsupported class file major version
```
Make sure `JAVA_HOME` points to a compatible JDK 17+ distribution. Check your version with `java -version`.

### SDK Not Found

If Gradle can't find the Android SDK, double check your `local.properties` file. Ensure the path is correct and uses proper escaping for your OS.

### Gradle Daemon Issues

If the build hangs or behaves strangely, try stopping the daemon:
```bash
./gradlew --stop
```
Then run your build command again.

## CI/CD Pipeline

We use GitHub Actions for automated builds. The release workflow handles:

- Version validation.
- Android APK signing and SHA-256 checksumming.
- Module ZIP packaging.
- PC tool cross-platform compilation.
- GitHub Release creation.
- Deployment of `update.json` for the in-app updater.

## Project Structure

```
├── android-app/          # Android app and LSPosed/Vector hook module
├── shared/               # KMP shared core
├── pc-tool-kotlin/       # Compose Desktop PC Designer
├── module/               # Root module shell scripts
└── build-scripts/        # Build helper scripts
```
