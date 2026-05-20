# Contributing to InputBlocker

Thank you for your interest in improving InputBlocker!

## Branch Naming Conventions
Please use the following prefixes for your branches:
- `feature/` : New features (e.g., `feature/auto-tune-improvement`)
- `fix/` : Bug fixes (e.g., `fix/overlay-leak`)
- `docs/` : Documentation updates (e.g., `docs/update-build-guide`)
- `chore/` : Maintenance tasks (e.g., `chore/update-dependencies`)

## Build Requirements
To contribute code, you must be able to build the project locally:
- **JDK**: JDK 17
- **Android SDK**: API 34
- **PC Tool**: IntelliJ IDEA or any Kotlin-compatible IDE.

## Pull Request Checklist
Before submitting a PR, please ensure:
- [ ] The code has been tested on a physical Android device.
- [ ] All documentation changes are reflected in `README.md` or `DOCUMENTATION.md`.
- [ ] No unsigned APKs or debug builds are committed.
- [ ] SemVer version bump is noted if a new feature/fix is added.
- [ ] Code follows the established project patterns (no AI slop).

## Testing Changes
Since this is a system-level tool, testing requires:
1. A rooted device with LSPosed.
2. Flashing the module via your root manager.
3. Verifying the hook in `system_server` via `logcat`.
