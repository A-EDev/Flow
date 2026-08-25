# Security Policy

## Supported versions

Security fixes land on the latest release line only. Older versions do not
receive backported patches.

| Version | Supported |
| ------- | --------- |
| 2.2.x   | ✅ |
| < 2.2.0 | ❌ |

Always update to the newest release before reporting a security issue.

## Reporting a vulnerability

**Do not report security vulnerabilities through public GitHub issues.**

Report privately through
[GitHub Security Advisories](https://github.com/A-EDev/Flow/security/advisories/new).
If you cannot use that form, email <flow.aedev@gmail.com>.

Please include:

- Type of issue (for example: credential exposure, path traversal, injection).
- Full paths of the source files involved.
- The affected tag, branch, or commit.
- Any configuration required to reproduce the issue.
- Step-by-step reproduction instructions.
- Proof-of-concept or exploit code, if available.
- Impact, including how an attacker might exploit it.

You will receive an acknowledgement within 48 hours and a timeline for a fix.
Please do not disclose the issue publicly until a fix has shipped.

## Verifying release APKs

Official Flow builds are signed with a single release key. Any APK that does
not match the fingerprint below is not an official build, regardless of where
it was downloaded.

```
SHA-256: 43:22:29:4E:D4:CA:A2:D4:29:41:40:09:58:18:08:0F:FE:8A:CC:1F:BE:3C:DC:76:10:7D:F4:5C:52:86:BE:40
```

Verify a downloaded APK with the Android SDK build tools:

```bash
apksigner verify --print-certs flow-foss.apk
```

The reported `Signer #1 certificate SHA-256 digest` must equal the fingerprint
above (lower case, without colons).

Official distribution channels are the
[GitHub Releases page](https://github.com/A-EDev/Flow/releases) and
[IzzyOnDroid](https://apt.izzysoft.de/packages/io.github.aedev.flow). Builds
obtained anywhere else are unverified.
