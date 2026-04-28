# AGENTS.md - Development Ledger & Intelligence Log

This file serves as the meta-documentation for **InputBlocker**, recording the architectural decisions, quality assurance audits, and the evolution of the project through AI-driven development. While `DOCUMENTATION.md` is for the user, `AGENTS.md` is for the maintainer.

## 🛠 AI Development Role
The project was developed and refined using a multi-agent approach powered by an advanced Large Language Model (LLM) acting through specialized personas:

- **Architecture & Implementation (Sisyphus/Build)**: Handled the core conversion of the Android app to Kotlin, C# tool to Avalonia, and the root module to a universal format.
- **Audit & QA (Momus)**: Performed a comprehensive codebase review, identifying critical memory leaks, security risks, and compatibility issues with Android 14.
- **System Integration (Oracle/Librarian)**: Engineered the hybrid blocking strategy (Overlay + LSPosed) to ensure 100% device coverage, including Android Go.

---

## 🔍 Full Code Audit Summary
A complete manual audit was performed on all three repositories. The following categories of issues were identified and resolved:

### 🔴 Critical / High Priority
- **Memory Leaks**: Fixed a major leak in `TouchBlockView` by replacing a hard reference to `OverlayService` with a `WeakReference`.
- **Android 14 Compliance**: Implemented `FOREGROUND_SERVICE_SPECIAL_USE` and added required metadata to prevent the OS from killing the blocking service.
- **ADB Stability**: Resolved "Device Disconnected" crashes by implementing `EnsureConnected()`/`ensureConnected()` logic in C# and Java tools.
- **Redundant Code**: Removed duplicated classes and methods in `OverlayService.kt` and consolidated the `Region` data model into a single shared file.

### 🟡 Medium / Low Priority
- **Universal Root Support**: Moved away from a Magisk-only structure to a universal format compatible with KernelSU and APatch.
- **CI/CD Optimization**: Transitioned from manual releases to an automated GitHub Actions pipeline that handles APK building and module packaging.
- **UX Polish**: Implemented a "Cyber-Dark" AMOLED theme to reduce battery drain and increase visual appeal.

---

## 🏗 Architectural Evolution

### 1. The Hybrid Blocking Strategy
To solve the problem of "Android Go" devices (which forbid overlays), the project evolved from a single method to a hybrid approach:
- **Standard Mode**: Uses a `TYPE_APPLICATION_OVERLAY` window. Best for most users.
- **Pro Mode (LSPosed)**: Hooks `com.android.server.input.InputDispatcher.dispatchMotionLocked`. This intercepts touches at the system level, making blocking invisible and compatible with all devices.

### 2. Universal Module Structure
The module was redesigned to be root-manager agnostic. It uses a dynamic detection script to locate the module path regardless of whether the user is on Magisk, KernelSU, or APatch, ensuring the configuration file is always found.

### 3. Cross-Platform Tooling
The PC tool was migrated from WinForms to **Avalonia UI**. This move was critical to ensure that the setup tool runs natively on Windows, macOS, and Linux without requiring separate codebases.

---

## 🛡 Safety & Reliability Engineering

### The "Anti-Lockout" Protocol
Because this tool can block the entire screen, two fail-safes were engineered:
1.  **The Hardware Kill Switch**: A physical button sequence (Vol Down $\times 3 \rightarrow$ Vol Up $\times 3$) that sends a broadcast to disable the service immediately.
2.  **Crash Protection (Safe Mode)**: If the service crashes repeatedly, a `force_safe_mode` flag is set in the config. When active, the service starts but disables all blocking, ensuring the user can always access the app to fix the regions.

---

## 🚀 CI/CD Logic
The release pipeline is automated via GitHub Actions:
- **Builds**: Compiles the Android APK, C# binaries for 4 platforms, and the Java JAR.
- **Packaging**: Automatically bundles the APK into the `InputBlocker.zip` module.
- **Release**: Creates a GitHub Release with tagged versions and uploads all artifacts automatically.

---

## 📅 Project Roadmap
- [x] Kotlin Conversion
- [x] Avalonia Migration
- [x] Universal Root Support
- [x] Android 14 Compliance
- [x] LSPosed Integration
- [x] Full Code Audit
- [ ] Integration of a local backup/restore system for regions.
- [ ] Advanced logging for debugging ghost tap patterns.
