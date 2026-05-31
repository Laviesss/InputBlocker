# Security Policy

## Supported Versions

| Version | Status |
|---|---|
| 0.x.x | ✅ Active development — security patches applied to the latest testing release. |

## Reporting a Vulnerability

**Do not open a public issue for security vulnerabilities.** Please report them privately.

### How to Report

1. Go to the [Security Advisories](https://github.com/Laviesss/InputBlocker/security/advisories/new) page.
2. Click **"Report a vulnerability"**.
3. Provide a detailed report including:
   - Vulnerability type and affected component.
   - Steps to reproduce.
   - Potential impact and exploitation vector.
   - Suggested fix (if known).

Reports will be acknowledged within 48 hours.

### Disclosure Process

1. **Report received** — Maintainer acknowledges and begins triage.
2. **Verification** — Issue is reproduced and impact assessed.
3. **Patch development** — Fix is developed, tested, and reviewed.
4. **Release** — Fixed version is published via the standard release process.
5. **Public disclosure** — After the fix is released, the vulnerability may be disclosed publicly.

### Scope

This policy covers:

- The Android hook engine (Xposed/LSPosed module)
- The companion Android app
- The PC Designer tool
- The KMP shared core
- The build and deployment pipeline

### Out of Scope

- Issues caused by rooted device configuration (custom ROMs, kernel modifications)
- Ghost tap behavior caused by physical hardware damage beyond the tool's mitigation scope
