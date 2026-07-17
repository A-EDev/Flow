## Summary

<!-- Explain what changed and why. Focus on observable behavior and important implementation decisions. -->

## Related issue

<!-- Use "Closes #123" when this PR should close an issue. -->

## Change type

- [ ] Bug fix
- [ ] Feature
- [ ] Refactor or maintenance
- [ ] Build, packaging, or CI
- [ ] Documentation

## Validation

<!-- List the exact checks you ran and their results. Mark non-applicable checks as such instead of claiming they passed. -->

- [ ] `./gradlew :app:assembleGithubDebug`
- [ ] `./gradlew :app:testGithubDebugUnitTest`
- [ ] I ran any additional flavor-specific build or test tasks affected by this change.
- [ ] I manually tested the affected behavior on an Android device or emulator.

**Test device and Android version:**

## Screenshots or recordings

<!-- Required for visible UI changes. Remove this section when it does not apply. -->

## Risk and compatibility

<!-- Note database or preference migrations, permissions, network behavior, playback impact, background work, battery impact, and known limitations. -->

- [ ] The change does not introduce secrets, private data, or unexpected telemetry.
- [ ] New user-facing text uses Android string resources.
- [ ] Dependency and lockfile changes are intentional and limited to this PR.
- [ ] Room schema changes include the required version bump and migration, or this PR does not change the Room schema.
- [ ] Breaking changes and upgrade steps are clearly documented.
