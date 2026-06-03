# Contributing to InputBlocker

Thanks for your interest in improving InputBlocker. This project fixes a real hardware problem, ghost taps from failing digitizers, and every contribution helps make it more reliable.

## Code of Conduct

Be respectful, constructive, and assume good faith. This is a small project maintained by volunteers.

## Getting Started

If you're new to the project, look for issues labeled `good first issue`. These are smaller tasks perfect for getting familiar with the codebase. You can also join our GitHub Discussions to share ideas or ask questions before you start writing code.

## Branch Naming

Use clear, prefixed branch names:

| Prefix | Purpose | Example |
|---|---|---|
| `feature/` | New features | `feature/auto-tune-improvement` |
| `fix/` | Bug fixes | `fix/overlay-leak` |
| `docs/` | Documentation | `docs/update-build-guide` |
| `chore/` | Maintenance | `chore/update-dependencies` |
| `refactor/` | Code restructuring | `refactor/hook-cleanup` |

## Development Setup

Before you contribute code, make sure you can build the project locally:

- **JDK 17** is required for Gradle and Kotlin compilation.
- **Android SDK** needs API 34 for the compile target and API 23 for the minimum.
- **IDE** choice is up to you, but IntelliJ IDEA or Android Studio are recommended.

Check [BUILD.md](BUILD.md) for detailed setup instructions.

## Pull Request Lifecycle

1. **Draft**: Open a Draft PR early if you want feedback on your approach.
2. **Review**: Once you're ready, mark it as "Ready for review." A maintainer will look at your code.
3. **Revision**: Address any comments or requested changes.
4. **Merge**: After approval, your PR will be merged into the main branch.
5. **Release**: Changes are bundled into the next testing or stable release.

We aim to review PRs within a week, but it might take longer depending on the complexity and maintainer availability.

## Pull Request Checklist

Before you submit a PR:

- [ ] Test changes on a **physical Android device**. Emulators can't reproduce ghost taps.
- [ ] Update all relevant documentation like the README or BUILD guide.
- [ ] Don't commit unsigned APKs or debug builds.
- [ ] Note any version bumps in `CHANGELOG.md`.
- [ ] Follow established project patterns. Avoid redundant abstractions or dead code.
- [ ] Don't add `as Any` casts, `@Suppress` annotations, or empty `catch` blocks to production code.

## Testing

InputBlocker works at the system input level, so testing requires:

1. A **rooted device** with LSPosed or Vector installed.
2. Flashing the module ZIP through your root manager.
3. Enabling the module in LSPosed for the `System Framework` scope.
4. Rebooting and checking the hook with `logcat | grep InputBlocker`.

### Test Areas

| Area | What to Check |
|---|---|
| **Filter tuning** | Does `minPressure` or `maxDuration` block ghost taps without affecting real touches? |
| **Emergency reset** | Trigger the gesture combo. Does blocking disable immediately? |
| **Profile switching** | Do per-app configs load correctly when you switch apps? |
| **Performance** | Is there any perceptible input lag or unusual battery drain? |
| **Safe mode** | Does crash detection trigger safe mode correctly after a forced failure? |

## Reporting Issues

- **Bug reports**: Use the bug report template. Include device info, logs, and steps to reproduce.
- **Feature requests**: Use the feature request template and describe your use case clearly.
- **Security vulnerabilities**: Don't open a public issue. Report them through [GitHub Security Advisories](https://github.com/Laviesss/InputBlocker/security/advisories/new).

## Community

We encourage sharing your `.ibpreset` files in the GitHub Discussions area. This helps other users with similar devices find working configurations quickly.

## Code Style

- Kotlin: Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Android: Use stable APIs instead of reflection where you can. The Xposed hook module is the only exception.
- Logging: Use `android.util.Log` for the app and `XposedBridge.log` for the hook module.
- Don't use `@Suppress` annotations in production code without an inline comment explaining why.
