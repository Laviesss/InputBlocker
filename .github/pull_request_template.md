---
name: Pull Request
about: Submit changes to InputBlocker
title: ""
labels: ""
assignees: ""
---

## Summary

<!-- One sentence describing what this PR does and why. -->

## Related Issues

<!-- Link any related issues using "Fixes #123", "Closes #456", or "Relates to #789". -->

- Fixes #

## Type of Change

<!-- Check all that apply. -->

- [ ] 🐛 Bug fix
- [ ] 🚀 New feature / Enhancement
- [ ] 📚 Documentation
- [ ] ♻️ Refactoring / code quality
- [ ] ⚙️ CI / build system
- [ ] 🧹 Chore (deps, tooling, config)

## What Changed

<!-- Describe what this PR changes in detail. Include before/after behavior if applicable. -->

### Before

<!-- What happened before this change? -->

### After

<!-- What happens after this change? -->

## Testing

<!-- PRs require testing on a physical Android device. Emulators cannot reproduce ghost taps. -->

**Device:** <!-- e.g., Pixel 6, Galaxy S22 -->
**Android version:** <!-- e.g., 13, 14 -->
**Root manager:** <!-- e.g., Magisk v27.0, KernelSU, APatch -->
**LSPosed / Vector version:** <!-- e.g., v1.9.2 -->

### Verification Steps

1. <!-- Step 1 -->
2. <!-- Step 2 -->
3. <!-- Step 3 -->

### Test Results

<!-- Describe what you observed. Did the fix work? Any regressions? -->

## Screenshots / Logs

<!-- If applicable, add screenshots or log output to help explain your changes. -->

## Checklist

<!-- PRs must pass all checks before merge. -->

- [ ] I have tested these changes on a **physical Android device**
- [ ] No unsigned APKs or debug builds are committed
- [ ] Documentation is updated (README, DOCUMENTATION, BUILD — as applicable)
- [ ] `CHANGELOG.md` is updated for user-facing changes
- [ ] No `as Any` casts, `@Suppress` annotations, or empty `catch` blocks added to production code
- [ ] Code follows established project patterns and [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- [ ] My changes generate no new warnings or errors
- [ ] I have performed a self-review of my own code

## Additional Context

<!-- Anything else reviewers should know? Design decisions, alternative approaches considered, performance implications, etc. -->
