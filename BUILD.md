# Build Instructions

InputBlocker is a monorepo containing the Android Engine (Xposed/LSPosed hook), the PC Designer (Kotlin/Compose Desktop), and a shared Kotlin Multiplatform (KMP) core.

---

## Requirements

| Dependency | Version | Notes |
|---|---|---|
| **JDK** | 17 | Required. Use Eclipse Temurin or equivalent. |
| **Android SDK** | API 34 (compile/target), API 23 (min) | Set via `ANDROID_HOME` or `local.properties`. |
| **Gradle** | 8.x | Wrapper included — no manual install needed. |

### Environment Setup

**Linux / macOS:**
```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
```

**Windows (PowerShell):**
```powershell
$env:JAVA_HOME = "C:\path\to\jdk17"
$env:ANDROID_HOME = "C:\path\to\Android\Sdk"
```

If the Android SDK is not autodetected, create a `local.properties` at the project root:
```
sdk.dir=C:\\Users\\YourUser\\AppData\\Local\\Android\\Sdk
```

---

## Building

### 🔹 Build All Artifacts (Recommended)

Produces the APK, PC tool binaries, and module ZIP in a single pass:

**Windows (PowerShell):**
```powershell
.\gradlew buildAll -PVERSION_NAME="<version>" -PVERSION_CODE=<code>
```

**Linux / macOS / WSL:**
```bash
./gradlew buildAll -PVERSION_NAME="<version>" -PVERSION_CODE=<code>
```

### 🔹 Individual Builds

Build only the component you need:

| Command | Artifact |
|---|---|
| `buildAndroid` | Android APK |
| `buildPC` | PC Designer EXE / DEB / DMG |
| `buildModule` | Root module ZIP (requires prior Android build) |

```bash
./gradlew buildAndroid -PVERSION_NAME="1.0.0" -PVERSION_CODE=10
```

### Version Parameters

Both version flags are **required** — the build will fail without them:

| Flag | Type | Example | Purpose |
|---|---|---|---|
| `-PVERSION_NAME` | String | `"1.0.0"` | User-visible version string |
| `-PVERSION_CODE` | Int | `10` | Internal integer for update tracking |

> **Note on testing phase:** During the active testing phase, the `VERSION_NAME` will remain `0.1.0` regardless of changes. This ensures consistent distribution until core functionality is validated. See the release workflow for automatic version assignment via CI/CD.

---

## Output Locations

| Artifact | Path |
|---|---|
| **Android APK** | `android-app/app/build/outputs/apk/release/app-release.apk` |
| **PC Tool (EXE)** | `pc-tool-kotlin/build/compose/binaries/main/exe/InputBlockerSetup-<version>.exe` |
| **PC Tool (DEB)** | `pc-tool-kotlin/build/compose/binaries/main/deb/inputblockersetup_<version>_amd64.deb` |
| **PC Tool (DMG)** | `pc-tool-kotlin/build/compose/binaries/main/dmg/InputBlockerSetup-<version>.dmg` |
| **Module ZIP** | `build/distributions/inputblocker.zip` |

---

## CI/CD Pipeline

The project uses GitHub Actions for automated builds. The release workflow (`release.yml`) handles:

- Version validation (name format, code monotonicity)
- Android APK signing and SHA-256 checksumming
- Module ZIP packaging
- PC tool cross-platform compilation
- GitHub Release creation with auto-generated changelog
- `update.json` deployment to GitHub Pages for in-app updater

Manual workflow runs accept `force_version=true` to bypass the version-code-regression check during testing.

---

## Project Structure

```
├── android-app/          # Android companion app + LSPosed/Vector hook module
│   ├── app/
│   └── module/
├── shared/               # KMP shared core (Region data model, normalization)
├── pc-tool-kotlin/       # Compose Desktop PC Designer
├── module/               # Root module shell scripts and files
└── build-scripts/        # Shell/batch build helper scripts
```

---

## Troubleshooting

### Java Version Mismatch

```
Unsupported class file major version 67
```

Your environment is not using JDK 17. Verify:
```bash
java -version  # must show 17.x
echo $JAVA_HOME
```

### SDK Not Found

If Gradle cannot locate the Android SDK, ensure `local.properties` exists at the project root with the correct SDK path.

### Build Fails with "Version flags are required"

Always pass `-PVERSION_NAME` and `-PVERSION_CODE` to any Gradle build task.
