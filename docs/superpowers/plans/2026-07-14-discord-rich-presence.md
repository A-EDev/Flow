# Discord Rich Presence Implementation Plan

> **For agentic workers:** Execute inline in red-green-refactor order; do not dispatch subagents.

**Goal:** Build the complete settings, lifecycle, playback, flavor, and official Discord Social SDK integration path.

**Architecture:** Common Kotlin code owns preferences, state, playback selection, and orchestration behind a transport seam. Flavor source sets provide either a fail-closed adapter or a GitHub C++/JNI adapter linked to the owner-supplied official AAR.

**Tech Stack:** Kotlin, Compose Material 3, DataStore Preferences, coroutines/Flow, Media3, C++20/JNI, Gradle product flavors, GitHub Actions.

## Global Constraints

- Default disabled and never misleading when unavailable.
- Never commit the Discord SDK AAR, tokens, secrets, keystores, or signing credentials.
- FOSS compiles without proprietary artifacts or Discord operations.
- Use only official Discord Social SDK API names verified from Discord's current documentation.
- Preserve existing player architecture and throttle position sampling to five seconds.

---

### Task 1: Preferences and settings state

**Files:** `DiscordPreferences.kt`, `DiscordSettingsState.kt`, focused JVM tests.

- [ ] Add failing tests for disabled default, persistence, and all row subtitle states.
- [ ] Run the focused test and observe the expected red result.
- [ ] Implement the DataStore component and pure state derivation.
- [ ] Run focused and neighboring Discord tests green.
- [ ] Commit `feat(discord): persist opt-in settings state`.

### Task 2: Playback snapshot selection

**Files:** `DiscordPlaybackSource.kt`, `ShortsPlayerPool.kt`, `ShortsScreen.kt`, focused JVM tests.

- [ ] Add failing tests for video, live, Shorts, music, overlap priority, pause, and media switching.
- [ ] Run and observe red.
- [ ] Implement pure candidates/selection and event-driven plus five-second sampling.
- [ ] Run focused tests green.
- [ ] Commit `feat(discord): select active playback snapshots`.

### Task 3: Coordinator and service lifecycle

**Files:** coordinator tests/code, `DiscordPresenceService.kt`, service tests.

- [ ] Add failing tests for disable, unlink, lifecycle shutdown, retries, stale clears, and transport failure.
- [ ] Run and observe red.
- [ ] Implement minimal lifecycle and error behavior.
- [ ] Run Discord tests green.
- [ ] Commit `feat(discord): manage presence lifecycle`.

### Task 4: Settings UI and navigation

**Files:** `DiscordSettingsScreen.kt`, `SettingsScreen.kt`, `FlowNavigation.kt`, `strings.xml`.

- [ ] Add testable state/copy assertions before UI wiring.
- [ ] Add the search-indexed Content & Playback row and dedicated Material 3 screen.
- [ ] Compile the GitHub and FOSS Kotlin variants.
- [ ] Commit `feat(settings): expose Discord Rich Presence`.

### Task 5: Flavor factories and official adapter

**Files:** flavor factories, GitHub adapter/bridge, CMake, Gradle, manifests, adapter tests.

- [ ] Add failing fake-bridge adapter tests for link/connect/update/clear/unlink/error paths.
- [ ] Run and observe red.
- [ ] Implement the FOSS factory and GitHub native adapter using documented SDK APIs.
- [ ] Configure optional private AAR/Prefab and deep linking without leaking it into FOSS.
- [ ] Run adapter tests and flavor builds.
- [ ] Commit `feat(discord): add official Social SDK transport`.

### Task 6: Application and Activity wiring

**Files:** `FlowApplication.kt`, `MainActivity.kt`.

- [ ] Add lifecycle behavior tests to the service first.
- [ ] Initialize once at application scope and weakly attach/detach Activity.
- [ ] Clear and close on final lifecycle shutdown without disrupting background playback semantics.
- [ ] Run lifecycle tests and builds.
- [ ] Commit `feat(discord): wire application lifecycle`.

### Task 7: CI, documentation, and end-to-end verification

**Files:** `.github/workflows/build.yml`, `docs/discord-rich-presence.md`, `.gitignore`.

- [ ] Make CI decode optional owner-supplied SDK material, test before packaging, verify APK existence, calculate SHA-256, and upload `flow-discord-rpc-nightly-apk`.
- [ ] Document setup, privacy, build, test, limitations, and troubleshooting with official links.
- [ ] Run all requested unit, lint, and assemble task equivalents.
- [ ] Inspect APK contents, manifest, commit, artifact, and digest.
- [ ] Review the full `main...HEAD` diff for leaks, races, stale state, flavor leakage, and misleading UI.
- [ ] Commit `ci(discord): verify nightly integration` and update draft PR #1.
