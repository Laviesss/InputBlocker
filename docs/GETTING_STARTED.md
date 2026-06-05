# Getting Started

Welcome to InputBlocker. This guide walks you through installation, first configuration, and daily use.

---

## Prerequisites

Before you begin, ensure your device meets these requirements:

| Requirement | Details |
|---|---|
| **Root access** | Magisk (≥20400), KernelSU, or APatch |
| **Xposed framework** | LSPosed or Vector (install from your root manager's download section) |
| **Android** | 6.0+ (API 23) |
| **ADB** | Recommended for PC Designer tool and emergency recovery |

---

## Installation

### Step 1: Download the Module

Go to the [Releases page](https://github.com/Laviesss/InputBlocker/releases) and download the latest `InputBlockerModule-*.zip`.

### Step 2: Flash via Root Manager

- **Magisk**: Open Magisk → Modules → Install from storage → select the ZIP → reboot
- **KernelSU**: Open KernelSU → Modules → Install → select the ZIP → reboot
- **APatch**: Open APatch → Modules → Install → select the ZIP → reboot

### Step 3: Enable in LSPosed

1. Open **LSPosed Manager**
2. Go to the **Modules** tab
3. Find **InputBlocker** in the list
4. Tap into it and check **System Framework**
5. Reboot again

### Step 4: Verify Installation

After reboot, check that the module is active:

```bash
adb shell logcat -s InputBlocker:XposedHook
# Expected: "Hook registered: dispatchMotionLocked"
```

Or open the InputBlocker companion app — it should show "Module active" on the main screen.

---

## First Configuration

### Method 1: Auto-Detection (Easiest)

1. Open the InputBlocker companion app
2. Tap **Auto-Detect Zones**
3. Place the device on a flat surface — **do not touch the screen**
4. The tool will put your device to sleep, wake it, and capture ghost tap samples for ~60 seconds
5. After sampling, it runs DBSCAN clustering to identify hotspot locations
6. Review the suggested regions and tap **Save**

### Method 2: PC Designer (Most Control)

1. Download the PC Designer for your platform from the [Releases page](https://github.com/Laviesss/InputBlocker/releases)
2. Connect your device via USB (USB Debugging must be enabled)
3. Launch the PC Designer
4. Click **Connect ADB** — your device screen appears in the preview
5. Draw block zones over areas where ghost taps occur
6. Tune contact area (minPressure) and duration thresholds per zone
7. Click **Push Config** to send the config to your device

### Method 3: Manual Config (For Advanced Users)

Edit the config file directly at `/data/adb/modules/inputblocker/config/profiles/default.conf`.

See [ADVANCED.md](ADVANCED.md) for the config file format reference.

> **Onboarding Wizard**: On first launch, the companion app shows a 3-screen onboarding wizard explaining key features, blocking modes, and configuration options. You can revisit this from Settings → Show Onboarding. It's a quick way to get oriented if you're new to the app.

---

## Testing Your Configuration

After setting up blocking regions, verify they work:

1. **Enable the overlay** — In the companion app, toggle **Show Overlay** to see your block regions rendered on screen
2. **Watch the block log** — Open **Block Log** in the app to see filtered touches with metadata
3. **Test real usage** — Use your device normally. Real touches should pass through; ghost taps should be blocked
4. **Watch the block counter** — The main screen, overlay (green text), and notification all show a live count of total blocked touches. Rate-limited to prevent UI spam

**If the overlay is too aggressive**, use the emergency gesture: **Volume Down × 3 → Volume Up × 3** — this disables the overlay immediately.

---

## Daily Use

InputBlocker runs silently in the background. Once configured:

- Blocking happens at the system level — no app needs to be open
- The foreground app is checked every 2 seconds; per-app profiles load automatically
- The adaptive optimizer tightens region bounds based on real data
- Blocked touches are recorded in `blocklog.txt` for review

**Quick Actions**: The companion app's Quick Actions tab gives you several controls:
- **Pause/Resume** — Tap **PAUSE** to temporarily stop blocking. Resume manually or wait for the timer to expire. Pause for 5 or 30 minutes via the notification buttons.
- **Crash Log viewer** — Review crash dumps with timestamps and stack traces. Logs are written automatically by the crash detection system.
- **Profiles manager** — Create per-app profiles by entering a package name. Profiles auto-load when that app is foreground. Use the Load button to activate a profile immediately.

**Block Counter**: The main screen displays a live counter of total blocked touches. Also shown in the overlay (green text) and in the notification. Rate-limited to prevent UI spam.

**Quick Settings**: The companion app adds a Quick Settings tile — toggle blocking on/off from the notification shade.

**Battery Optimization**: On first launch, the app prompts you to disable battery optimization for itself. This prevents the system from killing background services. It's recommended to allow this.

---

## Understanding Modes

InputBlocker has three blocking modes:

| Mode | How It Works | Best For |
|---|---|---|
| **LSPosed Hook** | Hooks `InputDispatcher.dispatchMotionLocked` — touches blocked BEFORE apps see them | Precision, minimal latency |
| **Overlay Mode** | WindowManager overlay catches touches — apps may briefly see them before the overlay does | Devices without LSPosed, testing |
| **Accessibility Mode** | Uses `AccessibilityService` with `TYPE_ACCESSIBILITY_OVERLAY` to intercept touch events | Android 12+ devices without LSPosed |

Toggle modes in Settings. LSPosed mode is recommended for daily use. Accessibility Mode works on Android 12+ without LSPosed installed.

---

## Recovery

| Issue | Action |
|---|---|
| Overlay blocks everything | **Volume Down × 3 → Volume Up × 3** (emergency gesture) |
| Boot loop after install | Boot Safe Mode → disable InputBlocker in LSPosed |
| Crash detected on boot | `adb shell rm /data/adb/modules/inputblocker/config/crash_detected` then reboot |
| Hard disable all blocking | `echo "1" > /data/adb/modules/inputblocker/config/kill_switch` |
| Module won't activate in LSPosed | Reflash the module ZIP and reboot |

---

## Next Steps

- [Troubleshooting Guide](TROUBLESHOOTING.md) — Solutions for common issues
- [FAQ](FAQ.md) — Frequently asked questions
- [Advanced Guide](ADVANCED.md) — Manual config editing, DBSCAN tuning, shell automation
- [Technical Documentation](../DOCUMENTATION.md) — Architecture and engine internals
