# Changelog

All notable changes to this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

## [0.1.0] - 2026-06-04

### Added
- Initial release of the Android Engine with touch filtering.
- PC Designer for visual region mapping and threshold tuning.
- DBSCAN-based auto-tuning for ghost tap hotspot analysis.
- Root-agnostic support for Magisk, KernelSU, and APatch.
- Emergency reset gesture (Volume Down x3 followed by Volume Up x3).
- Async logging system to prevent input lag.
- Theme support for System, Light, Dark, and AMOLED modes.
- KMP shared core for coordinate normalization.
- Production-grade CI/CD pipeline with dynamic versioning.
- LSPosed mode toggle (`lsposed_mode` config flag) — hook can be disabled without uninstalling
- Auto-detection KDoc documenting the 500/2000/1000ms sleep tuning parameters
- Rolling-release version note in About dialog
- Export preset dialog with user-defined filename
- Import preset file picker listing all `.ibpreset` files
- `assembleRelease` CI build step with APK artifact upload
- GitHub FUNDING.yml and stale issue management config
- Full documentation suite: GETTING_STARTED.md, TROUBLESHOOTING.md, FAQ.md, ADVANCED.md
- User acquisition strategy guide (PROMOTION.md)
- "Sync with device" and "Run test mode" Quick Action buttons now functional
- Testing-phase banner in release notes with grouped changelog
- **Pause/Resume**: Pause/resume blocking across Xposed, Accessibility, and Overlay paths via `paused=1` config flag. Quick Actions tab with PAUSE/RESUME button. Notification provides Pause 5min, Pause 30min, and Resume buttons
- **InputBlockerAccessibilityService**: New `TYPE_ACCESSIBILITY_OVERLAY` service for trusted blocking on Android 12+. Includes emergency volume-key kill-switch, foreground detection for profile switching, block counter, config file watching, and rate-limited block logging
- **CrashLogActivity**: In-app crash log viewer at Quick Actions → Crash Log. Reads crash logs from `/data/local/tmp/inputblocker/crash_logs/` with timestamp and stack trace display. Features Refresh and Clear buttons
- **ProfileListActivity**: Per-app profile manager at Quick Actions → Profiles. Supports create/rename/delete operations. Auto-switches profile based on foreground app
- **Block counter**: Total blocked touches displayed in app UI, overlay (green text), and notification. Block entries rate-limited to 300ms minimum gap
- **Safe mode with crash counting**: Tracks consecutive crash count at `/data/local/tmp/inputblocker/crash_count`. 3 consecutive crashes triggers automatic safe mode. Counter resets on clean shutdown or manual reset
- **ConfigFileObserver**: Real-time config reload using Android FileObserver with 2s polling fallback for filesystems without inotify
- **Config validation**: `validateConfig()` checks config integrity before saving, returning descriptive error messages on failure
- **Onboarding wizard**: 3-slide first-run dialog explaining the app, how it works, and getting started. Shown once on initial launch
- **Haptic feedback**: All toggle switches vibrate on toggle via `performHapticFeedback(HapticFeedbackConstants.CONFIRM)`
- **Region preview colors**: Region list items display color-coded bars: green=rectangle, orange=circle, blue=ellipse, red=excluded zones
- **BlockLogActivity auto-prune**: Keeps only the last 1000 entries to prevent unbounded log growth
- **Battery optimization prompt**: On first launch, checks PowerManager whitelist status. Prompts user to disable battery optimization if not whitelisted
- **LSPosed pause sync**: `paused=1` config flag written by togglePause() via root sed. InputBlockerXposed reads the flag and skips blocking when set

### Changed
- README fully restructured with badges, ToC, feature table, architecture diagram, comparison table, FAQ
- BUILD.md: added Docker build section, common errors troubleshooting, verifying your build
- CONTRIBUTING.md: added PR lifecycle, good-first-issue onboarding, community section
- SECURITY.md: added GPG key info, coordinated disclosure process, clearer timeline
- CHANGELOG.md: added future release template sections
- Issue templates rewritten with checklist format and better log-collection instructions
- Release notes now auto-group commits by conventional-commit type (Features, Fixes, Docs, etc.)
- Notification icon changed from `ic_dialog_info` to `ic_lock_idle_lock`

### Fixed
- LSPosed hook now respects `lsposed_mode=0` — early return in `beforeHookedMethod`
- 8 empty catch blocks filled with proper Log calls across InputBlockerXposed, AdaptiveBlockingManager, MainActivity, OverlayService
- Dead `optimizeRegions()` method removed from MainActivity (superseded by AdaptiveBlockingManager)
- Backup restore filter corrected from `.txt` to `.tar.gz`
- Config path in customize.sh now writes to `config/profiles/default.conf`
- Legacy `blocked_regions.conf` removed from module setup
- module/action.sh: added `file` command APK integrity check before `pm install`
