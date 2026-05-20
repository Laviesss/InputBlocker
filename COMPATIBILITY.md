# 📱 Compatibility Matrix & Root Support

This document outlines the intended compatibility of InputBlocker across different Android versions and root management systems.

## 🛠️ Root Manager Support

InputBlocker is designed to be **Root Agnostic**. The core engine relies on system-level hooks (LSPosed) and file-system access via root, rather than manager-specific APIs.

| Manager | Support Level | Notes |
|---|---|---|
| **Magisk** | Targeted | Standard installation via ZIP module. |
| **KernelSU** | Targeted | Compatible with KSU module directory structure. |
| **APatch** | Targeted | Supports APatch's module implementation. |
| **SuperSU** | Legacy | Supported via `/su/su.d/` scripts, though not recommended for modern devices. |

## 🤖 Android Version Support

The following versions are targeted for support. **Status is based on API compatibility and intended design; actual device verification is ongoing.**

| Version | API Level | Status | Notes |
|---|---|---|---|
| **Android 11** | 30 | Targeted | Support for `InputDispatcher` hooks. |
| **Android 12** | 31/32 | Targeted | Support for updated input pipeline. |
| **Android 13** | 33 | Targeted | Requires latest LSPosed versions. |
| **Android 14** | 34 | Targeted | Target for current development. |
| **Android 15** | 35 | Experimental | Initial analysis only; verification pending. |

## 🔍 Compatibility Testing Checklist

Before any release is marked as "Stable," the following must be verified on physical hardware:

### 1. Installation Flow
- [ ] Module flashes correctly in Magisk.
- [ ] Module flashes correctly in KernelSU.
- [ ] Module flashes correctly in APatch.
- [ ] Companion app installs automatically on first boot.

### 2. Core Engine Functionality
- [ ] `InputDispatcher` hook is active on the specific OS version.
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
- **UI Lag**: Check if the device has extreme resource constraints; the hook is designed for minimal overhead.
