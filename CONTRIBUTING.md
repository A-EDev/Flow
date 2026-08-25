# Contributing to Flow

Thank you for your interest in contributing to Flow! We welcome contributions from the community.

## 🐛 Reporting Bugs

Before creating bug reports, please check existing issues to avoid duplicates. When creating a bug report, include:

- **Clear description** of the issue
- **Steps to reproduce** the behavior
- **Expected behavior** vs actual behavior
- **Screenshots** if applicable
- **Device information** (Android version, device model)
- **App version** or commit hash

## 💡 Suggesting Features

Feature suggestions are welcome! Please:

- Check if the feature has already been suggested
- Provide a clear description of the feature
- Explain why this feature would be useful
- Include mockups or examples if possible

## 🔧 Pull Requests

### Before You Start

1. Fork the repository
2. Create a new branch from `main` or `develop`
3. Make sure you can build the project

### Development Setup

```bash
# Clone your fork
git clone https://github.com/YOUR_USERNAME/Flow.git
cd Flow

# Add upstream remote
git remote add upstream https://github.com/A-EDev/Flow.git

# Create a feature branch
git checkout -b feature/your-feature-name
```

### Code Guidelines

- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Keep functions small and focused
- Write self-documenting code

### Commit Messages

Use clear, descriptive commit messages:

```
✨ Add new theme selector
🐛 Fix music player crash on rotation
📝 Update README with new features
♻️ Refactor video player logic
🎨 Improve UI spacing in settings
```

**Commit prefixes:**
- ✨ `:sparkles:` - New feature
- 🐛 `:bug:` - Bug fix
- 📝 `:memo:` - Documentation
- ♻️ `:recycle:` - Refactoring
- 🎨 `:art:` - UI/styling
- ⚡ `:zap:` - Performance
- 🧪 `:test_tube:` - Tests
- 🔧 `:wrench:` - Configuration

### Testing

- Test your changes thoroughly
- Ensure the app builds without errors
- Test on different screen sizes if possible
- Check for memory leaks

### Submitting Your PR

1. **Update your fork:**
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Push your changes:**
   ```bash
   git push origin feature/your-feature-name
   ```

3. **Create Pull Request:**
   - Go to GitHub and create a PR
   - Fill out the PR template
   - Link any related issues
   - Wait for review

## 📋 Code Review Process

- Maintainers will review your PR
- Address any feedback or requested changes
- Once approved, your PR will be merged
- Your contribution will be credited in releases

## 🔐 Release and Signing Invariants

Flow is distributed through GitHub Releases and
[IzzyOnDroid](https://apt.izzysoft.de/packages/io.github.aedev.flow). Both pin
properties of the published artifacts, so the following are hard constraints.
Breaking one of them cannot be fixed by a follow-up release — it forces every
installed user to uninstall and reinstall, losing their local data.

**The signing key never changes.** Every release APK must be signed with the
official key, certificate SHA-256
`4322294ed4caa2d4294140095818080ffe8acc1fbe3cdc76107df45c5286be40`. Android
refuses to install an update signed by a different key. CI enforces this in the
`Verify release signing certificate` step, which fails the build on a mismatch.
Never regenerate `release.keystore`, and never rotate the
`RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS`, or `KEY_PASSWORD`
repository secrets.

**Release asset file names are a public contract.** IzzyOnDroid matches release
assets by file name. `flow.apk` and `flow-foss.apk` are the universal builds
and must keep those exact names. The per-ABI APKs are published alongside them
as extras. Renaming or removing either universal APK silently breaks the
IzzyOnDroid update feed, so coordinate with IzzyOnDroid before changing them.

**`versionCode` must increase on every release.** F-Droid-format repositories
use it to detect updates. The ABI splits all share one `versionCode`, so the
universal APK is the only artifact IzzyOnDroid should consume.

**A tag build must never publish unsigned APKs.** `app/build.gradle.kts` falls
back to `signingConfig = null` when no keystore is present, which produces
uninstallable APKs. CI hard-fails a `v*` tag build when the keystore secret is
missing rather than publishing them.

## 🎯 Areas We Need Help

- [ ] Improving documentation
- [ ] Writing unit tests
- [ ] UI/UX improvements
- [ ] Performance optimization
- [ ] Bug fixes
- [ ] Accessibility features
- [ ] Translations

## 📜 Code of Conduct

- Be respectful and inclusive
- Welcome newcomers
- Give constructive feedback
- Focus on the code, not the person
- Help create a positive community

## ❓ Questions?

If you have questions, feel free to:

- Open a discussion on GitHub
- Comment on existing issues
- Reach out to maintainers

## 🙏 Thank You!

Every contribution helps make Flow better. Thank you for being part of the community!

---

**Happy Coding! 🚀**
