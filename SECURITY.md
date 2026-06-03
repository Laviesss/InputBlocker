# Security Policy

## Supported Versions

| Version | Status |
|---|---|
| 0.x.x | Active development. Security patches are applied to the latest testing release. |

## Reporting a Vulnerability

Don't open a public issue for security vulnerabilities. Please report them privately.

### How to Report

1. Go to the [Security Advisories](https://github.com/Laviesss/InputBlocker/security/advisories/new) page.
2. Click "Report a vulnerability."
3. Provide a detailed report. Include the vulnerability type, affected component, and steps to reproduce.
4. Mention the potential impact and how it could be exploited.
5. Suggest a fix if you have one.

We'll acknowledge your report within 48 hours.

### GPG Signed Communications

For sensitive discussions, you can request signed communications. Our maintainers use GPG keys to verify their identity. You can find the public keys in the `SECURITY_KEYS.md` file (if available) or by requesting them through the security advisory channel.

## Coordinated Disclosure

We follow the principles of coordinated disclosure. This means we ask you to give us time to fix the issue before you share it publicly. In return, we'll keep you updated on our progress and give you credit for the discovery.

### Disclosure Timeline

We aim to release a patch for verified vulnerabilities within 30 to 60 days. This timeline depends on the severity of the issue and the complexity of the fix. If we need more time, we'll communicate that to you early in the process.

### Disclosure Process

1. **Report received**: A maintainer acknowledges the report and starts triage.
2. **Verification**: We reproduce the issue and assess the impact.
3. **Patch development**: We develop, test, and review a fix.
4. **Release**: We publish the fixed version through our standard release process.
5. **Public disclosure**: After the fix is out, we can discuss public disclosure.

## Scope

This policy covers:

- The Android hook engine (Xposed/LSPosed module).
- The companion Android app.
- The PC Designer tool.
- The KMP shared core.
- The build and deployment pipeline.

## Out of Scope

- Issues caused by rooted device configuration like custom ROMs or kernel modifications.
- Ghost tap behavior caused by physical hardware damage that the tool can't mitigate.
