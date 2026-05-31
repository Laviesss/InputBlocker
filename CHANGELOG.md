# Changelog

All notable changes to this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.1.0] — 2026-05-20

### Added
- Initial release of the Android Engine with touch filtering.
- PC Designer for visual region mapping and threshold tuning.
- DBSCAN-based auto-tuning for ghost tap hotspot analysis.
- Root-agnostic support for Magisk, KernelSU, and APatch.
- Emergency reset gesture (Volume Down ×3 → Volume Up ×3).
- Async logging system to prevent input lag.
- Theme support (System, Light, Dark, AMOLED).
- KMP shared core for coordinate normalization.
- Production-grade CI/CD pipeline with dynamic versioning.

---

> ⚠️ **Testing Phase Notice**
> All testing releases use version `0.1.0` regardless of the changes included. Version `0.1.0` is treated as a rolling release during active development. Once the project exits the testing phase, semantic versioning will be applied strictly.
