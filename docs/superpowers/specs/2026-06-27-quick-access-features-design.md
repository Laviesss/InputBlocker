# Quick Access Features — InputBlocker Design Spec

**Date**: 2026-06-27
**Status**: Approved for implementation
**Package**: `com.inputblocker.app`

---

## 1. Overview

InputBlocker already has functional core blocking, but toggling it requires opening the app or using the Quick Settings tile. This spec adds three friction-reducing features that make blocking instant and invisible to operate.

**Goal**: A user can toggle blocking, switch profiles, and see status — without ever opening the app.

---

## 2. Feature 1: Rich Persistent Notification

### What

The `OverlayService` runs as a foreground service (required for Android 14+). Currently its notification is bare. Convert it into an interactive control panel.

### Notification Layout

```
┌──────────────────────────────────────┐
│  🔒  InputBlocker — Blocking Active  │
│  Profile: Default                    │
│                                      │
│  [⏸ Pause]  [⇄ Profile]  [🛡 Safe]  │
└──────────────────────────────────────┘
```

- **Icon + Title**: Dynamic — "Blocking Active" (green) or "Blocking Paused" (grey)
- **Subtitle**: Current profile name
- **Action buttons** (3 max on Android):
  1. **Toggle** — pause/resume blocking
  2. **Profile** — switch to next profile (cycle through presets)
  3. **Safe Mode** — emergency disable
- **Priority**: `IMPORTANCE_LOW` — visual only, no sound/vibration

### Actions

All actions route through the existing `NotificationReceiver` BroadcastReceiver via PendingIntents:

| Action | Intent Extra | Handler |
|---|---|---|
| Toggle | `ACTION_TOGGLE_BLOCKING` | Flip `enabled` in prefs + config, send RELOAD |
| Profile | `ACTION_SWITCH_PROFILE` + `profile_name` | Write new profile to config, send RELOAD |
| Safe Mode | `ACTION_SAFE_MODE` | Write `force_safe_mode=1`, send RELOAD |

### Implementation

- File: `OverlayService.kt` — build notification with `NotificationCompat.Builder`
- Add `NotificationCompat.Action` for each button
- `PendingIntent` targets `NotificationReceiver` with the action string
- Notification channel: `"inputblocker_status"` (already likely exists)
- Update notification dynamically when state changes (not just onCreate)

### Edge Cases

- **No profiles exist**: Profile button does nothing or shows toast "No other profiles"
- **Already in safe mode**: Hide safe mode button, show "Exit safe mode" instead
- **Permission denied**: If POST_NOTIFICATIONS denied (Android 13+), degrade silently — service still runs

---

## 3. Feature 2: Shake Gesture Toggle

### What

Shake the phone to toggle blocking on/off. Useful when the phone is in a pocket or mount and you can't look at the screen.

### Sensor Strategy

- **Sensor**: `TYPE_ACCELEROMETER` (already used by the app? check)
- **Algorithm**: Simple high-pass filter on the magnitude of acceleration
  - Sample at `SENSOR_DELAY_UI` (60ms interval)
  - Compute `magnitude = sqrt(x² + y² + z²)`
  - Compare to gravity baseline (~9.8 m/s²)
  - If deviation > threshold for 3+ consecutive samples → shake
- **Sensitivity presets** (configurable in Settings):
  - Low: threshold = 15 m/s² (hard shake)
  - Medium: threshold = 12 m/s² (default)
  - High: threshold = 9 m/s² (gentle shake ≈ any movement)
- **Debounce**: Ignore further shakes for 2 seconds after a toggle

### Battery Considerations

- Register only when the OverlayService is running
- Unregister when screen is off (use `PowerManager.isInteractive()`) — no point detecting shakes in a pocket
- Re-register on screen-on via `Intent.ACTION_SCREEN_ON`
- This keeps the sensor off during deep sleep

### Feedback

- Short vibration on toggle
- Toast: "Blocking enabled" / "Blocking disabled" (only if screen is on)
- Update notification icon immediately

### Implementation

- New file: `ShakeDetector.kt` — lightweight class wrapping `SensorEventListener`
- Parameters: sensitivity level (int), callback lambda
- Lifecycle tied to `OverlayService.onCreate()` / `.onDestroy()`

### Edge Cases

- **No accelerometer**: Silently skip registration, no crash
- **Rapid shaking**: Debounce prevents double-fire
- **Screen off + shake**: Registered but sensor events still come at reduced rate; toggle still works but no Toast
- **Conflict with volume kill-switch**: Both can coexist — shake is faster, volume sequence is emergency-only

---

## 4. Feature 3: Quick Settings Tile Enhancements

### What

The existing `QuickSettingsTileService` already toggles blocking. Make it more informative and faster to use.

### Changes

1. **Subtitle** — show current profile name below the label
2. **Long-click** — `onStartListening` → `isToggleable=false`, handle long-click to open `ProfileListActivity`
3. **Dynamic icon** — use a different tint/overlay for active vs inactive state

### Implementation

- File: `QuickSettingsTileService.kt`
- Add `tile.subtitle = profileName` in `updateTileState()`
- Override `onStartListening()` to read current profile from prefs
- Long-click is automatic on Android 12+ if tile is not locked — add `TileActivity` Intent

### Edge Cases

- **Android < 12**: Long-click not available, subtitle still works
- **Tile not added**: User must add it manually — nothing to do here

---

## 5. Architecture & Data Flow

```
User Interaction
      │
      ├── Notification buttons ──→ NotificationReceiver ──→ prefs + config file + RELOAD broadcast
      ├── Shake gesture ──→ ShakeDetector ──→ OverlayService.toggleBlocking() ──→ same as above
      └── QS Tile tap ──→ QuickSettingsTileService.onClick() ──→ same as above

State changes flow:
      Toggle/Profile/Safe
           │
           ▼
    SharedPreferences (enabled, currentProfile)
           │
           ▼
    Config file write (root)  ←── InputBlockerServiceManager
           │
           ▼
    RELOAD broadcast ──→ Xposed module picks up new config
           │
           ▼
    Notification updated by OverlayService
    QS Tile state updated by QuickSettingsTileService
```

---

## 6. Files Changed / Created

| File | Action | Notes |
|---|---|---|
| `OverlayService.kt` | **Modify** | Build rich notification with action buttons; integrate ShakeDetector lifecycle |
| `NotificationReceiver.kt` | **Modify** | Add `ACTION_SWITCH_PROFILE` handler |
| `QuickSettingsTileService.kt` | **Modify** | Add subtitle + long-click handler |
| `ShakeDetector.kt` | **Create** | Accelerometer shake detection |
| `ProfileManager.kt` | **Read** (no change) | Need `getNextProfile()` or equivalent cycling logic |

---

## 7. Testing

- **Notification**: Verify all 3 buttons fire correct intents and state persists
- **Shake**: Physical device test — shake at each sensitivity level; confirm debounce
- **QS Tile**: Verify subtitle updates; long-click opens profile list
- **Regression**: QS Tile still toggles; volume kill-switch still works; safe mode engages

---

## 8. Future (Not in Scope)

- Context-aware auto-blocking (scheduling, app triggers, car Bluetooth)
- Usage analytics dashboard
- Screen region blocking
- Tasker/Locale plugin integration
