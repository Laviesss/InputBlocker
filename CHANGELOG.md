# Changelog

All notable changes to this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
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

---

## [0.1.0] - 2026-05-20

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

---

### ⚠️ Testing Phase Notice

All testing releases use version `0.1.0` regardless of the changes included. Version `0.1.0` is treated as a rolling release during active development — the version string stays the same even as features and fixes accumulate.

This ensures consistent distribution while core functionality is validated. Once the project exits the testing phase, strict semantic versioning will be applied.

> **For the actual changelog of what changed between pre-releases, check the GitHub Releases page or the commit log between tags.**
