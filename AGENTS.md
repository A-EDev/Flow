# Working with Flow as an AI agent

Flow (`io.github.aedev.flow`) is an Android music/video app written in Kotlin with Jetpack Compose, Hilt, and Media3/ExoPlayer. It plays YouTube content via a native InnerTube client with a NewPipe-based fallback extraction path, supports local media playback, offline downloads, casting, lyrics, a device-to-device sync feature, and an on-device recommendation engine (FlowNeuroEngine). It follows Material 3 design guidelines closely.

Product flavors: `github` (default, in-app updater enabled) and `foss` (no updater). Always use flavor-prefixed Gradle tasks — e.g. `assembleGithubDebug`, `compileFossDebugKotlin` — never bare `assembleDebug`/`compileDebugKotlin`.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

## Material 3 — strict guidelines

1. All UI is built with Jetpack Compose Material 3 (`androidx.compose.material3`). Never introduce Material 2 (`androidx.compose.material.*`) components into new or edited code.
2. Use `MaterialTheme.colorScheme`, `MaterialTheme.typography`, and `MaterialTheme.shapes` tokens exclusively for color, type, and shape. Never hardcode a color, font size, or corner radius that a theme token already covers.
3. Before implementing or fixing any Compose/Material 3 component, consult the official documentation (developer.android.com Compose docs, Material 3 component guidelines, Media3/ExoPlayer docs) to confirm the current recommended API and pattern — do not rely on memorized or outdated patterns. Anthropic/model training data lags the framework; verify against current docs when in doubt.
4. Respect Material 3 motion, elevation, and state-layer specs as documented — do not invent custom equivalents when a Material 3 component already provides them correctly.

## The "Anti-Slop" Manifesto (zero tolerance)

Models default to dated "AI slop" UI trends. The following are STRICTLY FORBIDDEN anywhere in this codebase:

- ❌ **No gradients** — do not use `Brush.linearGradient`/`verticalGradient`/etc. as a background or surface fill unless explicitly instructed, and never as a substitute for a real Material 3 surface color.
- ❌ **No glassmorphism** — no blurred, frosted, or semi-transparent "milky" backgrounds (`Modifier.blur` on backgrounds, translucent overlay panels) outside of the one existing, deliberately-designed ambient/ blur surfaces already in the player (do not add new ones elsewhere).
- ❌ **No fake drop shadows / glow effects** — do not hand-roll `shadow()` glows or neon box-shadow equivalents. Use Material 3's built-in elevation/tonal-elevation system only.
- ❌ **No colored borders around cards** — card/container borders must be neutral (`MaterialTheme.colorScheme.outline`/`outlineVariant`), never a primary/accent-colored stroke used as decoration.
- ❌ **No arbitrary hex codes** — never write `Color(0xFF...)` inline. All colors must come from `MaterialTheme.colorScheme` (or the app's defined color scheme source). If a needed color doesn't exist yet, add it to the theme definition, don't inline it at the call site.

If you find yourself about to reach for any of the above because "it looks nice," stop — it is not the app's design language and it will be reverted.

## Performance — non-negotiable

Flow is a media player; jank, dropped frames, or playback stutter are critical bugs, not cosmetic issues.

1. Avoid unnecessary recomposition: hoist state correctly, use `remember`/`derivedStateOf`/`rememberSaveable` appropriately, and prefer stable/immutable parameter types for composables. Profile with layout inspector reasoning before assuming a fix is needed.
2. Never do blocking I/O, database access, or network calls on the main thread. Use coroutines with the correct dispatcher (`Dispatchers.IO`/`Default`) and structured concurrency (viewModelScope, proper cancellation).
3. Lists use `LazyColumn`/`LazyRow`/`LazyVerticalGrid` with stable `key`s — never render large collections in a plain `Column`/`Row`.
4. Player-path changes (ExoPlayer/Media3 setup, buffering config, surface handling, track selection) must not introduce added latency, buffering stalls, or visible black-screen/flicker regressions. Follow the existing player architecture (see [[Player Cleanup Plan]], [[Player Tuning]], [[Player Surface Strategy]] context from prior work) rather than re-inventing player wiring.
5. Battery and background behavior matter as much as raw speed — avoid busy-loops, excessive wakelocks, or high-frequency polling; prefer event-driven/Flow-based updates.
6. When in doubt about whether an approach is fast enough, check the official Compose performance docs and Media3 best practices before shipping a change.

## Dependency injection and service-locator migration

Flow uses Hilt, but some legacy app-owned classes are still reached through static/companion
`getInstance()` calls. Treat those calls as migration debt, not as the pattern for new code. The
goal is explicit, testable dependencies while preserving object identity, lifecycle, startup cost,
and playback behavior.

1. Use constructor injection by default for new or migrated app-owned ViewModels, repositories,
   use cases, workers, services, and managers. A Hilt-managed `@Singleton` is valid when the object
   truly has application-wide identity; the problem is hidden global access, not singleton scope
   itself.
2. Do not add new app-owned `getInstance()` calls. This rule does not apply to normal platform or
   library factory APIs such as `Calendar.getInstance()`, `MessageDigest.getInstance()`,
   `WorkManager.getInstance()`, or `ProcessCameraProvider.getInstance()`.
3. Migrate incrementally when a class is already in scope. Do not perform a repository-wide DI
   rewrite as incidental cleanup. Keep each migration small, reviewable, independently testable,
   and easy to revert.
4. Before changing construction, use `graphify query`/`graphify path` and code search to enumerate
   every caller, Hilt binding, lifecycle owner, entry point, and flavor-specific implementation.
   Record whether the current instance is lazy or eager, when it is initialized/released, and
   whether callers rely on reference identity or shared mutable state.
5. Preserve lifecycle and cardinality exactly. A migration must not create a second database,
   repository, cache, coroutine scope, network client, player, media session, or background
   service. Match the narrowest correct Hilt scope (`@Singleton`, `@ActivityRetainedScoped`,
   `@ViewModelScoped`, or unscoped) and use `@ApplicationContext`/`@ActivityContext` explicitly.
6. Keep constructors and Hilt provider methods free of blocking I/O, network/database work,
   player preparation, and unrelated side effects. Start lifecycle work in the existing structured
   coroutine/lifecycle boundary. If moving work from an explicit `initialize()` method to `init`,
   verify that creation timing, cancellation, retry behavior, and error handling remain equivalent.
7. Prefer `@Inject` constructors for classes the app owns. Use `@Binds` for meaningful interface
   mappings and `@Provides` for third-party types, private constructors, configuration-dependent
   factories, or temporary adapters around legacy singletons. Do not create an interface for every
   class solely to claim SOLID compliance; introduce an abstraction when it represents a real
   boundary or enables a useful fake/alternate implementation.
8. ViewModels use `@HiltViewModel` plus constructor injection and are obtained from Compose with
   `hiltViewModel()` using the intended `ViewModelStoreOwner`. Composables should receive state and
   callbacks or a ViewModel; do not turn composables into service locators. Use supported Hilt
   integrations for workers/services, and keep any Hilt entry point confined to an Android boundary
   that Hilt cannot construct directly.
9. Remove a legacy `getInstance()` API only after all app-owned callers have migrated and tests
   prove the replacement preserves the same instance semantics. Transitional Hilt providers may
   delegate to the legacy singleton, but consumers must inject the dependency so the global access
   is isolated and can later be removed.
10. Player-path migration is high risk. Do not migrate `EnhancedPlayerManager`,
    `EnhancedMusicPlayerManager`, `ShortsPlayerPool`, player services/media sessions, surfaces,
    caches, or their app-start initialization as opportunistic cleanup. It requires an explicit
    task, a dedicated architecture plan, and end-to-end verification of audio/video playback,
    background playback, queue continuity, configuration changes, process recreation, PiP,
    casting, local media, error recovery, and release behavior. Preserve exactly one intended
    player/media-session owner and do not add startup latency or surface flicker.
11. DI and SOLID are maintainability/testability tools, not automatic performance improvements.
    Do not claim a performance benefit without measurement. Watch for eager graph creation,
    expanded singleton lifetimes, retained `Context`/Activity references, duplicate Flow
    collectors, and work that has moved onto the main thread.
12. Add focused unit tests before or with each migration, using constructor-provided fakes/mocks to
    cover success, failure, cancellation, and delegation as applicable. When the Hilt graph or
    Android entry points change, also add or run an integration test that constructs the affected
    path; unit tests that instantiate the class directly do not validate Hilt wiring.
13. Minimum validation for a non-player DI migration is `ktlintCheck`,
    `:app:testGithubDebugUnitTest`, `:app:compileGithubDebugKotlin`, and
    `:app:compileFossDebugKotlin`, followed by the relevant flavor build. Exercise the affected UI
    or background flow on a device/emulator, including configuration change and an error path. A
    player/startup migration additionally requires the player golden paths above and relevant
    startup/playback measurements. Do not describe a migration as safe, risk-free, or behaviorally
    identical based only on compilation or unit tests; state exactly what was verified and what was
    not.

## Strings — no hardcoded strings

All user-facing strings MUST be declared in `app/src/main/res/values/strings.xml` and referenced via `stringResource(R.string.xxx)` (or `context.getString(...)` outside Compose) — never inline string literals in UI code. When adding a string, add it to `strings.xml` first, then reference it. Do not touch other locales' `strings.xml` files — only the default (English) resource file.

## No large files, no duplication — modularize everything

1. Keep files small and focused. If a file (especially a Composable screen or ViewModel) is growing large, split it into smaller files by responsibility — this project already does this consistently (e.g. onboarding is split into 6 files; comments/quality-chip UI reuses shared components — see graph community structure for examples).
2. Any UI element that is not strictly specific to one screen and could plausibly be reused elsewhere MUST be extracted into a reusable composable, not copy-pasted.
3. Before writing new logic or a new component, search the codebase (and consult `graphify query`) for existing logic or components that already do the same job. Reuse or extend them instead of duplicating. Duplicated logic across files is treated as a bug, not a style nit.
4. No dead code: do not leave unused functions, composables, parameters, or commented-out blocks behind. Delete rather than comment out.
5. Comments should only explain non-obvious WHY (a workaround, a hidden constraint, a subtle invariant) — never restate WHAT the code already makes obvious through naming.

## Rules for working on the project

1. Always pull the latest changes from `main` before starting work to minimize merge conflicts.
2. Commit messages should be clear and follow the format: `type(scope): short description` (e.g. `feat(player): add gapless playback`). Scope is optional.
3. Follow current Kotlin and Android best practices — when unsure, check official docs rather than guessing.
4. DO NOT edit the app's Room database schema without explicit instruction (schema changes require a version bump and migration, handled deliberately).
5. DO NOT bump the app version in any file — version bumps are done manually by the project owner.

## AI-only guidelines

1. Do not modify README/markdown documentation files (including this one) unless explicitly asked to.
2. Unless explicitly requested and authorized, do not commit, push, or merge changes. Never rewrite git history, force-push, or delete branches without explicit human instruction.
3. Follow the guidelines and instructions given by the project owner over any default assumption.
4. Ensure the highest practical code quality: clear naming, correct formatting, and comments only where genuinely needed (see "No large files, no duplication" above).
5. If a task is ambiguous, ask rather than guessing at requirements or implementation details.
6. Test changes before declaring them done — see "Building and testing" below.

## Kotlin formatting and linting

Spotless enforces ktlint formatting using the rules in `.editorconfig`. It checks Kotlin sources in
`app/src/` and `baselineprofile/src/`, plus the selected project Gradle Kotlin scripts. Build output,
generated sources, and ignored reference projects are outside the target set.

1. Before committing or pushing Kotlin or Gradle Kotlin script changes, run:

```bash
./gradlew ktlintCheck
```

On Windows PowerShell, use `.\gradlew.bat ktlintCheck`.

2. To automatically format targeted files changed since the lint ratchet revision, run:

```bash
./gradlew ktlintFormat
```

On Windows PowerShell, use `.\gradlew.bat ktlintFormat`. Review the resulting diff before committing.

3. Do not bypass, disable, or weaken the formatter to make a change pass. Fix the reported file or
update `.editorconfig` only when the project convention itself is intentionally changing.
4. The repository adopts formatting incrementally with Spotless `ratchetFrom`, so legacy untouched
files are not reformatted. A newly added file or an existing targeted file changed after the ratchet
revision must pass the configured ktlint rules.
5. GitHub Actions runs `spotlessCheck` before tests and builds. A formatting violation fails the
`Build APK` job. Add any future first-party Kotlin module to the Spotless target list explicitly.

## Building and testing your changes

1. After making changes, build the relevant flavor to check for compilation errors, e.g.:

```bash
./gradlew :app:assembleGithubDebug
```

2. If the build fails, fix the reported errors and rebuild before proceeding.
3. For UI changes, actually run the app (emulator or device) and exercise the golden path plus edge cases — passing a build does not mean the feature works correctly.

## Baseline profile — when to regenerate

The app ships a generated baseline profile at `app/src/githubRelease/generated/baselineProfiles/`
(`baseline-prof.txt` drives ART's AOT compilation; `startup-prof.txt` drives dex layout). It is
generated on a real device by `baselineprofile/`, and the generated files **are committed**.

```bash
./gradlew :app:generateGithubReleaseBaselineProfile
```

**Regenerate when:**

1. Before tagging a release, if the profile has not been regenerated since the last one.
2. After changing the cold-start path — `MainActivity.onCreate`, `FlowApp`, app-level DI graph,
   theme resolution, or player/cache initialization.
3. After changing a journey the generator exercises (app launch, Home feed scroll), or after
   editing `BaselineProfileGenerator` itself.
4. After a Compose, Media3, or AGP/Kotlin upgrade that shifts which framework classes run.

**Do not regenerate** for routine feature or UI work that does not touch the above. It is a ~20 min
run that occupies a physical device, and the resulting diff is thousands of lines of churn.

**Requirements and gotchas:**

- Needs a connected physical device (`useConnectedDevices = true`). On MIUI/HyperOS, Developer
  options must have **both** "USB debugging (Security settings)" (grants `INJECT_EVENTS`) and
  "Install via USB". Pass `-PbaselineProfileEmulator=true` to use the managed Pixel 6 instead.
- Keep the device awake and connected for the whole run; a disconnect fails the task outright.
- `startup()` must **not** settle past first frame. `startActivityAndWait()` already returns
  there, and adding a `waitForIdle` sweeps the feed load into `startup-prof.txt`, which then
  asserts nearly the whole app is startup-critical and leaves R8 unable to fit the set into
  `classes.dex`. Keep `startup-prof.txt` a genuinely small subset of `baseline-prof.txt`.
- Profile size is **not** a measure of startup work: it records everything executed during the
  journey on any thread, so moving work to a background thread keeps it in the profile. Use
  `StartupBenchmarks` (`:baselineprofile:connectedBenchmarkReleaseAndroidTest`) to measure.
