# InputBlocker

A system-level utility for Android that allows users to block specific screen regions to eliminate ghost taps and unwanted touch inputs.

[![Build](https://github.com/Laviesss/InputBlocker/actions/workflows/release.yml/badge.svg)](https://github.com/Laviesss/InputBlocker/actions)
[![Release](https://img.shields.io/github/v/release/Laviesss/InputBlocker?style=flat-square)](https://github.com/Laviesss/InputBlocker/releases)
[![License](https://img.shields.io/github/license/Laviesss/InputBlocker?style=flat-square)](LICENSE)

## Overview

InputBlocker allows you to define rectangular regions on your screen that become non-interactive. Once active, any touch events occurring within these regions are intercepted and discarded before they reach the operating system or other applications.

### Core Features
- **Coordinate Independence**: Uses a normalized coordinate system (0.0 to 1.0) to ensure blocked regions remain consistent across different screen resolutions and densities.
- **Root Agnostic**: Compatible with Magisk, KernelSU, APatch, and SuperSU.
- **Dual-Mode Blocking**: 
  - **LSPosed/Xposed**: Intercepts events at the system server level (`InputDispatcher`) for maximum performance.
  - **Overlay Mode**: Uses a transparent system overlay to consume events for standard root installations.
- **Auto-Detection**: Includes a sensing mode that uses DBSCAN clustering to identify ghost tap hotspots and suggest blocking regions automatically.
- **Safe Mode**: Built-in emergency disable sequence (`Volume Down x3` $\rightarrow$ `Volume Up x3`) to prevent accidental lockouts.

## Installation

1. **Flash the Module**: Install the `InputBlocker.zip` through your root manager.
2. **Reboot**: The companion app is automatically installed on the first boot.
3. **Configuration**:
   - Open the app to use the **Auto-Detect** feature.
   - Or use the **PC Setup Tool** for precise manual region mapping.

## Repository Structure

- `/android-app`: The companion management application.
- `/module`: The root module files (scripts, prop, and update JSON).
- `/pc-tool-csharp`: Cross-platform setup tool written in .NET 8.
- `/pc-tool-java`: Alternative setup tool written in Java.
- `/build-scripts`: Automation scripts for building the entire project.

## Development

Detailed instructions on building the project from source can be found in [BUILD.md](BUILD.md).
