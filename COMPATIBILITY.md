# 📱 Compatibility Matrix & Root Support

This document outlines the compatibility of InputBlocker across different Android versions and root management systems.

## 🛠️ Root Manager Support

InputBlocker is designed to be **Root Agnostic**. The core engine relies on system-level hooks (LSPosed) and file-system access via root, rather than manager-specific APIs.

| Manager | Support Level | Notes |
|---|---|---|
| **Magisk** | Full | Standard installation via ZIP module. |
| **KernelSU** | Full | Compatible with KSU module directory structure. |
| **APatch** | Full | Supports APatch's module implementation. |
| **SuperSU** | Legacy | Supported via `/su/su.d/` scripts, though not recommended for modern devices. |

## 🤖 Android Version Support

| Version | API Level | Status | Known Issues / Notes |
|---|---|---|---|
| **Android 11** | 30 | ✅ Stable | Full support for `InputDispatcher` hooks. |
| **Android 12** | 31/32 | ✅ Stable | Compatible with Android 12's updated input pipeline. |
| **Android 13** | 33 | ✅ Stable | Verified on most devices; requires latest LSPosed. |
| **Android 14** | 34 | ✅ Stable | Compatible with Android 14's security restrictions. |
| **Android 15** | 35 | ⚠️ Beta | Initial tests show compatibility; some devices may require specific LSPosed versions. |

## 🔍 Compatibility Testing Checklist

Before every major release, the following matrix must be verified:

### 1. Installation Flow
- [ ] Module flashes correctly in Magisk.
- [ ] Module flashes correctly in KernelSU.
- [ ] Module flashes correctly in APatch.
- [ ] Companion app installs automatically on first boot.

### 2. Core Engine Functionality
- [ ] `InputDispatcher` hook is active on all tested versions.
- [ ] Surgical filtering blocks ghost taps correctly.
- [ ] Exclude zones override blocking zones.
- [ ] Emergency Reset gesture works globally.

### 3. OS Integration
- [ ] App-specific profiles switch correctly on package change.
- [ ] Config files are read/written with correct permissions.
- [ ] No system-wide instability or boot-loops during installation/uninstallation.

## 🚀 Troubleshooting Compatibility

- **Module not loading**: Ensure LSPosed is installed and the module is enabled for the `system_server` process.
- **Config not applying**: Verify that the module path is correctly detected (e.g., `/data/adb/modules/inputblocker`).
- **UI Lag**: Check if the device has extreme resource constraints; the hook is designed for $<10\mu s$ overhead.
