# Contributing to InputBlocker

Thank you for your interest in improving InputBlocker. This project tackles a real hardware problem — ghost taps from failing digitizers — and every contribution helps make it more reliable.

---

## Code of Conduct

Be respectful, constructive, and assume good faith. This is a small project maintained by volunteers.

---

## Branch Naming

Use clear, prefixed branch names:

| Prefix | Purpose | Example |
|---|---|---|
| `feature/` | New features | `feature/auto-tune-improvement` |
| `fix/` | Bug fixes | `fix/overlay-leak` |
| `docs/` | Documentation | `docs/update-build-guide` |
| `chore/` | Maintenance | `chore/update-dependencies` |
| `refactor/` | Code restructuring | `refactor/hook-cleanup` |

---

## Development Setup

Before contributing code, ensure you can build the project locally:

- **JDK 17** — Required for Gradle and Kotlin compilation
- **Android SDK** — API 34 (compile target), API 23 (minimum)
- **IDE** — IntelliJ IDEA or Android Studio recommended

See [BUILD.md](BUILD.md) for detailed setup instructions.

---

## Pull Request Process

### Checklist

Before submitting a PR:

- [ ] Changes tested on a **physical Android device** (emulators cannot reproduce ghost taps).
- [ ] All relevant documentation updated (README, DOCUMENTATION, BUILD, etc.).
- [ ] No unsigned APKs or debug builds committed.
- [ ] Version bump noted in `CHANGELOG.md` if applicable.
- [ ] Code follows established project patterns — no "AI slop," redundant abstractions, or dead code.
- [ ] No `as Any` casts, `@Suppress` annotations, or `catch (e: Exception) {}` blocks added to production code.

### Review Guidelines

- Keep PRs focused — one logical change per PR.
- Provide a clear description of what the change does and why.
- Reference related issues where applicable.
- Expect at least one review before merge.

---

## Testing

Since InputBlocker operates at the system input level, testing requires:

1. A **rooted device** with LSPosed/Vector installed.
2. Flashing the module ZIP via your root manager.
3. Enabling the module in LSPosed for the `System Framework` scope.
4. Rebooting and verifying the hook via `logcat | grep InputBlocker`.

### Test Areas

| Area | What to Check |
|---|---|
| **Filter tuning** | Does `minPressure` / `maxDuration` block ghost taps without affecting real touches? |
| **Emergency reset** | Trigger the gesture combo — does blocking disable immediately? |
| **Profile switching** | Do per-app configs load correctly when switching apps? |
| **Performance** | Is there any perceptible input lag or unusual battery drain? |
| **Safe mode** | Does crash detection trigger safe mode correctly after a forced failure? |

---

## Reporting Issues

- **Bug reports**: Use the [bug report template](.github/ISSUE_TEMPLATE/bug_report.md) and include device info, logs, and steps to reproduce.
- **Feature requests**: Use the [feature request template](.github/ISSUE_TEMPLATE/feature_request.md) and describe the use case clearly.
- **Security vulnerabilities**: Do **not** open a public issue. Report via [GitHub Security Advisories](https://github.com/Laviesss/InputBlocker/security/advisories/new).

---

## Code Style

- Kotlin: Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Android: Prefer stable APIs over reflection where possible. The Xposed hook module is the exception.
- Logging: Use `android.util.Log` for the app, `XposedBridge.log` for the hook module.
- No `@Suppress` annotations in production code without an inline comment explaining why.
