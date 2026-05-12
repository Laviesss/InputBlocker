# InputBlocker

**Professional-grade touch blocking for Android. Eliminate ghost taps and unwanted inputs with surgical precision.**

[![Build](https://github.com/Laviesss/InputBlocker/actions/workflows/release.yml/badge.svg)](https://github.com/Laviesss/InputBlocker/actions)
[![Release](https://img.shields.io/github/v/release/Laviesss/InputBlocker?style=flat-square)](https://github.com/Laviesss/InputBlocker/releases)
[![License](https://img.shields.io/github/license/Laviesss/InputBlocker?style=flat-square)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-green?style=flat-square)](https://www.android.com/)
[![Root](https://img.shields.io/badge/root-Magisk%20%7C%20KernelSU%20%7C%20APatch%20%7C%20SuperSU-blue?style=flat-square)](#root-manager-support)

InputBlocker is a system-level utility that allows users to define non-interactive rectangular regions on their screen. Once configured, the system intercepts and consumes all touch events within these regions, preventing "ghost taps" from affecting the OS or apps.

## 🚀 Key Features

### 🎯 Precision Blocking
- **Hybrid Setup**: Use the professional PC tool for manual layout or the on-device **Auto-Detection** flow.
- **Smart Detection**: Built-in **DBSCAN clustering algorithm** that analyzes raw touch data to automatically suggest blocking regions.
- **Visual Feedback**: A semi-transparent overlay allows you to verify blocked areas in real-time.

### 🛠️ Enterprise-Grade Architecture
- **Root Agnostic**: Seamlessly compatible with **Magisk**, **KernelSU**, **APatch**, and **SuperSU**.
- **Resolution Independent**: Uses a **Normalized Coordinate System (0.0 to 1.0)**, ensuring your configuration works perfectly regardless of screen resolution or density.
- **High Performance**: Core blocking is implemented via an Xposed hook on `InputDispatcher.dispatchMotionLocked` for near-zero latency.

### 🛡️ Safety & Accessibility
- **Hardware Kill Switch**: Immediate emergency disable via physical buttons: `Volume Down × 3` $\rightarrow$ `Volume Up × 3`.
- **Safe Mode**: Automatic fallback mechanism to prevent boot-loops or permanent lockouts.
- **Theming**: Full support for **Light**, **Dark**, and **AMOLED** themes, synchronized across all components.

## 📦 Installation

1. **Flash the Module**: Install `InputBlocker.zip` via your root manager (Magisk, KernelSU, etc.).
2. **Reboot**: The companion management app is automatically installed on the first boot.
3. **Configure**: 
   - **On-Device**: Open the app and use "Auto-Detect" to find ghost taps.
   - **Via PC**: Use the cross-platform Setup Tool for manual precision mapping.

## 💻 Component Overview

| Component | Tech Stack | Purpose |
|-----------|------------|----------|
| **Android App** | Kotlin | Management UI, Overlay Service, & Sensing |
| **Root Module** | Shell/Bin | System integration & boot-time setup |
| **Xposed Hook** | Java/Kotlin | High-performance touch interception |
| **PC Tool** | .NET 8 / Java | Visual region mapping via ADB |

## 🛠️ Root Manager Support
InputBlocker is designed to be manager-agnostic. It automatically detects its installation path whether you are using:
- **Magisk**
- **KernelSU**
- **APatch**
- **SuperSU**

## 📜 License
Distributed under the MIT License. See `LICENSE` for more information.
