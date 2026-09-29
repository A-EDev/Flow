# Contributing to Flow

Thank you for your interest in contributing to Flow! We welcome contributions from the community.

Flow (`io.github.aedev.flow`) is an Android music/video app written in Kotlin with Jetpack Compose,
Hilt, and Media3/ExoPlayer. It plays YouTube content via a native InnerTube client with a
NewPipe-based fallback extraction path, and supports local media playback, offline downloads,
casting, lyrics, device-to-device sync, and an on-device recommendation engine (FlowNeuroEngine).

**Read this file before opening a pull request.** Most review round-trips on this project come from
one of the rules below, not from the feature itself. `AGENTS.md` in the repository root carries the
same rules in the form AI coding agents consume — the two files are kept in sync, so if you use an
AI assistant, point it at `AGENTS.md`.

## ❓ Asking Questions About the Codebase

**Use [GitHub Discussions](https://github.com/A-EDev/Flow/discussions) — that is the right place, and
questions are welcome.** The codebase is deliberately light on comments (see "Comments" below), so
asking is expected rather than a sign you missed something.

Please ask **before** writing a large change, especially anything that touches architecture, the
player path, or more than a handful of files. A design question answered in a discussion thread
costs everyone far less than a rewritten pull request.

## 🐛 Reporting Bugs

Before creating bug reports, please check existing issues to avoid duplicates. When creating a bug
report, include:

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
2. Create a new branch from `main`
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

Always pull the latest `main` before starting work, to minimize merge conflicts.

### Product Flavors

Flow builds two flavors: `github` (default, in-app updater enabled) and `foss` (no updater).
**Always use flavor-prefixed Gradle tasks** — `assembleGithubDebug`, `compileFossDebugKotlin` —
never bare `assembleDebug` or `compileDebugKotlin`.

## 🎨 Material 3 — Strict Guidelines

1. All UI is built with Jetpack Compose Material 3 (`androidx.compose.material3`). **Never introduce
   Material 2** (`androidx.compose.material.*`) components into new or edited code.
2. Use `MaterialTheme.colorScheme`, `MaterialTheme.typography`, `MaterialTheme.shapes`, and
   `MaterialTheme.motionScheme` tokens exclusively for color, type, shape, and motion. Never
   hardcode a color, font size, corner radius, or animation spec that a theme token already covers.
3. Before implementing or fixing any Compose/Material 3 component, consult the official
   documentation (developer.android.com Compose docs, Material 3 component guidelines, Media3 docs)
   to confirm the current recommended API. The app tracks alpha Compose/M3, so blog posts and
   memory are frequently out of date.
4. Respect Material 3 motion, elevation, and state-layer specs as documented — do not invent custom
   equivalents when a Material 3 component already provides them correctly.

### The "Anti-Slop" Manifesto (zero tolerance)

The following are **strictly forbidden** anywhere in this codebase. Pull requests containing them
will be asked to remove them before review continues:

- ❌ **No gradients** — do not use `Brush.linearGradient`/`verticalGradient`/etc. as a background or
  surface fill unless explicitly agreed, and never as a substitute for a real Material 3 surface
  color.
- ❌ **No glassmorphism** — no blurred, frosted, or semi-transparent "milky" backgrounds
  (`Modifier.blur` on backgrounds, translucent overlay panels) outside the one existing,
  deliberately-designed ambient/blur surface in the player. Do not add new ones.
- ❌ **No fake drop shadows / glow effects** — do not hand-roll `shadow()` glows or neon box-shadow
  equivalents. Use Material 3's built-in elevation/tonal-elevation system only.
- ❌ **No colored borders around cards** — card and container borders must be neutral
  (`MaterialTheme.colorScheme.outline`/`outlineVariant`), never a primary/accent-colored stroke used
  as decoration.
- ❌ **No arbitrary hex codes** — never write `Color(0xFF...)` inline. All colors come from
  `MaterialTheme.colorScheme` or the app's color scheme source. If a color you need does not exist
  yet, add it to the theme definition in `ui/theme/`, do not inline it at the call site.

If you find yourself reaching for any of the above because "it looks nice", stop — it is not the
app's design language and it will be reverted.

## 🧩 Use the Platform — Never Hand-Roll an API a Dependency Already Provides

**The default is always the library implementation.** Hand-rolling is a fallback that must be
*earned* by verification, never a first move. A hand-rolled equivalent of a shipped API is strictly
worse: it misses accessibility, RTL, theming, state restoration, edge cases and security fixes the
real one already handles, it will not track upstream behaviour changes, and it becomes code the
project has to maintain forever.

This applies to Material 3 and M3 Expressive components, Compose foundation/animation/gesture APIs,
Media3/ExoPlayer, AndroidX (WorkManager, Paging, Room, DataStore, Lifecycle, Glance), the platform
SDK, and every third-party library in `gradle/libs.versions.toml`. It applies equally to protocols
and formats — do not write a parser, codec, cipher, signature scheme or transport by hand when a
maintained implementation is on the classpath.

### The procedure — verify, then decide

Before writing any component, effect, animation, formatter, parser, scheduler, or player behaviour:

1. **Check what the app already has.** Read `ui/components/shared/` and `utils/`. The commonest
   waste is re-implementing something this codebase already exports — `FlowEmptyState`,
   `FlowErrorState`, `MediaRow`, `MediaThumbnail`, `MediaBadges`, `FlowSearchField`,
   `ShimmerLoading`, and `FastScrollbar` all exist already.
2. **Check the version catalog.** `gradle/libs.versions.toml` is the list of what is available.
3. **Check the actual artifact, not your memory.** The app is on alpha Compose/M3; confirm against
   the bytecode before concluding an API is missing:

   ```bash
   find ~/.gradle/caches/modules-2/files-2.1/androidx.compose.material3 -name '*.aar'
   unzip -p <path>/material3.aar classes.jar > /tmp/c.jar
   unzip -l /tmp/c.jar | grep -i carousel
   javap -classpath /tmp/c.jar androidx.compose.material3.MotionScheme
   ```

   Several versions can sit in the cache at once — confirm which resolves with
   `./gradlew :app:dependencies`.
4. **Check the official docs** for the recommended pattern. An API existing is not the same as it
   being the right one.
5. **Only if steps 1–4 come back empty may you hand-roll it** — and you must say so explicitly in
   the PR description, naming what you checked and what you found missing.

### The current stack — reach for these first

| Need | Use | Do not hand-roll |
| --- | --- | --- |
| Any UI component | `androidx.compose.material3` (M3 Expressive) | Custom buttons, chips, sheets, FABs, carousels, progress indicators, search fields |
| Motion | `MaterialTheme.motionScheme` specs | Bespoke `tween`/`spring` constants at call sites |
| Shapes | `MaterialTheme.shapes`, `androidx.graphics.shapes` | Hand-drawn paths for standard shapes |
| Haptics | `HapticFeedbackType` constants | `Vibrator` calls with hardcoded durations |
| Pull to refresh | M3 `PullToRefreshBox` | Custom drag-to-refresh |
| Reordering | `sh.calvin.reorderable` | Custom drag-and-drop |
| Paging | Paging 3 (`androidx.paging`) | New hand-rolled page cursors |
| Playback | Media3 — exoplayer, hls, dash, session, datasource-okhttp | Custom player wiring, manifest handling, media notifications |
| Background work | WorkManager | Custom threads, timers, alarms, wakelocks |
| Persistence | Room, DataStore Preferences | Hand-rolled file or SharedPreferences layers |
| JSON | `kotlinx.serialization` | Gson for new code (legacy DTOs only; it carries an R8 reflection hazard) |
| HTTP | OkHttp / Ktor | Raw sockets or `HttpURLConnection` |
| Dates and times | `java.time` (desugaring enabled, minSdk 26) | `SimpleDateFormat`, `Calendar`, manual millisecond arithmetic |
| Images | Coil | Custom loaders, caches, decoders |
| Widgets | Glance + `glance-material3` | Hand-built `RemoteViews` |

### When hand-rolling is legitimate

Only when the API is **verifiably absent or verifiably unfit**, and the reason is recorded in the PR.
The reference case is `ui/components/shared/FastScrollbar.kt`: Compose Foundation ships no scrollbar
API, so a custom one was justified. Note what it still does — it is built *on* the platform
(`draggable`, `LazyListState.requestScrollToItem`, `MotionScheme` specs, `HapticFeedbackType`), it
does not reimplement any of them.

A legitimate hand-rolled component must be built from framework primitives rather than around them,
take theme tokens for every colour/shape/type/motion value, live in `ui/components/shared/` if more
than one feature uses it, and duplicate no behaviour the library already provides.

### Never

- **Never fork, vendor, or copy-paste a library's source** into the app to change one thing.
- **Never add a new dependency that overlaps one already on the list.** A new dependency needs a
  stated reason that an existing one cannot cover.
- **Never hand-roll security-relevant code** — crypto, signing, token handling, TLS.
- **Never re-invent player wiring.** Media3 owns playback, the media session, and the media
  notification.
- **Never disable, wrap, or work around a library API because its behaviour surprised you** until
  you have confirmed the behaviour against its documentation. The surprise is usually a misuse.

## ⚡ Performance, Battery, and Thermals — Non-Negotiable

Flow is a media player that runs for hours at a time. Jank, dropped frames, playback stutter, device
heat, and battery drain are **critical bugs, not cosmetic issues**. Every rule below is anchored in a
real shipped regression that had to be found and fixed on-device.

### Frame discipline — nothing animates that the user cannot see

1. **The invisible-animation rule.** Several player surfaces deliberately stay composed while hidden
   (the full player sheet is kept warm behind the mini player; the lyrics panel is retained after
   first open; the mini bar stays composed under the expanded player). Anything animating inside a
   hidden layer burns a full frame budget at 60–120 Hz for entire listening sessions — this exact
   pattern caused a 30%-battery-in-90-minutes overheating regression. **Every continuous animation
   must be gated on its own layer's visibility and pause when the layer is hidden.** Gate on anchor
   state (e.g. `state.isExpanded`) or a `derivedStateOf` over the fraction. Never remove the warm
   composition itself — it exists to make first-expand jank-free.
2. **Audit list for continuous work.** When adding or reviewing UI, search the affected tree for:
   `withFrameMillis`/`withFrameNanos` loops, `rememberInfiniteTransition`, `basicMarquee`,
   `animate*AsState` whose target changes on a timer (a 1 Hz-retargeted tween *is* a continuous
   animation), wavy/squiggly/indeterminate indicators, and polling `LaunchedEffect`s with short
   `delay`s. Each needs an explicit answer to: "what stops this when its layer is hidden, when
   playback is paused, and when the screen is off?"
3. **Per-frame values are read in the layout/draw phase, never in composition.** The player sheets
   pass animated values as lambdas consumed inside `Modifier.layout`/`graphicsLayer`/`drawBehind` so
   dragging never recomposes the tree. Preserve that contract. For booleans derived from a fraction,
   use `derivedStateOf` so recomposition happens only on the flip.
4. **Expensive draw effects.** Full-screen `blur`/`RenderEffect` layers must hold static content
   (invalidated only on track/state change, as `PlayerBackground` does). Never attach a blur or
   render effect to a node that invalidates per frame.

### Cadence contracts — polling and updates

5. **Position cadence is a contract.** `EnhancedMusicPlayerManager` emits `playerState` at 1 Hz
   (whole-second coarsening) and a precise 250 ms tick **only** while a refcounted consumer
   (`acquirePreciseProgress`/`releasePreciseProgress`, tied to the expanded sheet) is present. Never
   widen these, never add a new high-frequency position `StateFlow`, and never collect the precise
   tick from a surface that renders whole seconds. New sub-second consumers must use the same
   refcounted acquire/release pattern with a `DisposableEffect`.
6. **Event-driven over polling.** Prefer player callbacks, Flow emissions, and `snapshotFlow`/
   `collect` chains to timer loops. Any unavoidable polling loop must suspend while paused, never
   busy-wait, and its interval must be justified against what actually changes at that rate.
7. **Battery/background behaviour is part of every change.** Dynamic `wakeMode` (LOCAL foreground /
   NETWORK background audio) stays as is; no new wakelocks; no work keyed to the frame clock while
   the screen is off; WorkManager jobs must tolerate App Standby buckets.

### Work economy — network, CPU, and ViewModel scope

8. **One fetch per cause.** Every network call must be traceable to exactly one triggering event,
   deduped by id where re-triggering is possible. Effects in warm-kept trees fire on every key change
   even while invisible: gate their network side effects on visibility, or dedupe in the ViewModel.
9. **ViewModel scoping is a performance decision.** A bare `hiltViewModel<MusicViewModel>()` at a
   navigation route creates a fresh backStackEntry-scoped instance whose `init` re-runs the full
   home-load pipeline (~60 network calls) on every page open — this caused 30-second Daily Mix loads
   and device heat. Shared surfaces use the activity-scoped `sharedMusicViewModel()`; never
   reintroduce per-route instances of ViewModels with expensive `init`. New ViewModels must not fire
   network floods from `init` at all — load lazily, cache, and refresh on staleness.
10. **The shared pools are finite.** `PerformanceDispatcher.networkIO` is a fixed 4–16 thread pool.
    Launch parallel work as one bounded round (`map { async { … } }.awaitAll()`), never as an
    unbounded per-item fan-out, and never queue a second copy of a pipeline already in flight.
11. **Flow lifecycle.** `stateIn(WhileSubscribed(5_000))` on UI-facing flows is load-bearing —
    subscription count gates work to actual UI visibility. Never switch to `Eagerly`/`Lazily` for
    convenience, and never add hot collectors that outlive their surface.
12. **Caches must be honored and invalidated.** Check for an existing cache (music home cache,
    related-lane caches, Coil) before adding a fetch. New caches need explicit invalidation (TTL,
    region change, refresh) and must be read *through*, not around.

### Overheating triage

13. Sustained heat while the app is open = per-frame work; drain with the screen off = CPU/network
    loops. Diagnose in that order: (a) run the rule-2 audit over every composed-but-hidden tree;
    (b) count fetches per user action in logcat — any unexplained second fetch is the bug;
    (c) check `adb shell dumpsys gfxinfo io.github.aedev.flow` for continuous frame production while
    the UI should be idle; (d) only then suspect the player path. Do not "fix" heat by degrading
    visible design, motion, or update smoothness — find the invisible work instead.

### Proof — no performance claims without evidence

14. **Never describe a change as faster, lighter, or cooler based on reasoning alone.** State exactly
    what was measured and what was not. Recommendation-engine changes require the offline benchmarks
    (`MusicBenchmarkTest` / `NeuroBenchmarkTest` regression floors) before and after; startup changes
    require `StartupBenchmarks`, not baseline-profile size.
15. Player-path changes (ExoPlayer/Media3 setup, buffering config, surface handling, track selection)
    must not introduce added latency, buffering stalls, or black-screen/flicker regressions.
16. The Compose basics still apply everywhere: hoist state, use `remember`/`derivedStateOf`/
    `rememberSaveable` correctly, keep composable parameters stable/immutable, use lazy containers
    with stable `key`s for any large collection, never block the main thread with I/O or DB/network
    work, and use structured concurrency with the correct dispatchers and cancellation.

## 🏗️ Code Structure — Where Every File Goes

This is the authoritative placement guide. It is not aspirational: `ui/components/shared/`,
`ui/components/library/`, `ui/components/music/*` and `ui/components/layout/topbar/` are already
built this way, and new work must match them.

### The package map

| Package | Holds | Visibility |
| --- | --- | --- |
| `ui/screens/<feature>/` | The route entry composable, its ViewModel, and pure state/policy helpers for that feature only | `internal` or `private` |
| `ui/components/<feature>/` | Composables owned by one feature but reused across several screens or routes inside it | `internal` |
| `ui/components/shared/` | Cross-feature building blocks with no feature knowledge | `public` |
| `ui/components/layout/` | App chrome: top bars, scaffolds, navigation surfaces | `public` |
| `ui/theme/` | Colour, type, shape and motion tokens. Every literal colour lives here | `public` |
| `ui/utils/` | Compose-only helpers (modifiers, form factor, fading edges) | `public` |
| `utils/` | Non-Compose helpers (formatters, parsers, dispatchers). **Do not add a second utils package** | `public` |

A file in `ui/screens/` may **not** be imported by another feature's screen package. If two features
need it, it moves — that is the whole signal.

### The placement decision tree

Answer these in order for any composable, and stop at the first "yes":

1. **Used only inside this one file, and under ~40 lines?** Keep it `private` in the file.
2. **Used by only one screen, but the screen file is over budget?** Split it into a sibling file in
   the same `ui/screens/<feature>/` package, `internal`.
3. **Used by two or more screens or routes of the same feature?** Move it to
   `ui/components/<feature>/`, `internal`.
4. **Used by two or more different features, or encodes no feature vocabulary at all** (a chip, a
   badge, a row, an empty state, a search field, a thumbnail)? Move it to `ui/components/shared/`,
   `public`, stripping every feature-specific parameter on the way.

**Promote on the second real consumer, never on the first speculative one.** A component with one
call site that "might be reused later" belongs next to its call site. **Demote too** — if a `shared/`
component ends up with a single caller after a refactor, move it back down.

### When something earns its own folder

Create `ui/components/<feature>/` when the feature owns **three or more** component files, or when
one screen's components exceed ~600 lines in total. Sub-folder a feature package once it passes
**eight files**, splitting by *role*: `card/`, `item/`, `row/`, `section/`, `header/`, `detail/`,
`sheet/`, `common/`. Do **not** create `utils/`, `misc/`, `helpers/` or `other/` — a folder that
cannot be named by role is a folder that should not exist.

### File size budgets

| File kind | Target | Hard ceiling |
| --- | --- | --- |
| Screen entry (`*Screen.kt`) | 250 lines | 400 |
| Component file | 300 lines | 500 |
| ViewModel | 400 lines | 600 |
| Anything else | 400 lines | 600 |

Over the ceiling, splitting is mandatory before the change lands. Under it, do **not** split a file
you are not otherwise working in — this mirrors the Spotless ratchet: new and touched code meets the
bar, untouched legacy code is left alone until someone has a reason to open it.

### How to split a file

Split by **responsibility**, never by line count:

1. **Lift the leaves.** Move the leaf composables out first; the screen keeps layout and state.
2. **Separate state from pixels.** Pure functions (filtering, sorting, grouping, formatting) move to
   their own file and become unit-testable — `HomePagination.kt`, `SubscriptionEnrichmentPolicy.kt`
   and `HomeUiStateNormalization.kt` are the pattern to copy.
3. **Group by surface.** A screen with a list, a header and three sheets becomes `FooScreen.kt` +
   `FooHeader.kt` + `FooList.kt` + `FooSheets.kt`.
4. **Never split into `FooScreen2.kt`, `FooScreenPart2.kt` or `FooExtra.kt`.** Every file name must
   describe what is inside it.

### Naming conventions (already in force — match them)

- `shared/` primitives with no domain meaning take the **`Flow`** prefix: `FlowFilterChip`,
  `FlowSearchField`, `FlowEmptyState`, `FlowLoadingIndicator`.
- `shared/` components in the media vocabulary take the **`Media`** prefix: `MediaRow`,
  `MediaThumbnail`, `MediaBadges`, `MediaKindSelector`.
- Feature components take the **feature** prefix: `LibraryShelf`, `MusicTrackItem`,
  `PlaylistDetailComponents`, `HistoryComponents`.
- The file is named after its primary export. One dominant export per file; a `*Components.kt` or
  `*Rows.kt` file may hold a small family of siblings and nothing else.
- Private `const val` are `SCREAMING_SNAKE_CASE`; private `val` holding a `Dp`/`Color`/spec token
  stay `PascalCase`. ktlint enforces both.

### Sharing without redesigning

- **Identical output, different call sites** → merge into one component. Delete the loser.
- **Same shape, different values** → merge, and expose the difference as a **parameter whose default
  preserves today's behaviour**. `VideoCardFullWidth(useInternalPadding = true)` is the reference:
  the default keeps every existing call site pixel-identical, and the container-padded screens opt
  out explicitly.
- **Same name, genuinely different purpose** → leave both. Merging them produces a parameter bag
  nobody can read.
- **Different design** → stop. Consolidation is not a licence to change how anything looks.

**A refactor that changes pixels is not a refactor.** Visual and behavioural output must be identical
before and after, unless the task explicitly asked for a visual change.

### What a screen file is allowed to contain

The route composable, its state hoisting, its effects and its layout. **Not**: bespoke cards, rows,
empty/error states, badges, formatters, or a second copy of a `shared/` component.

### ViewModels

- **One ViewModel per feature, living in `ui/screens/<feature>/` beside the screens it serves**,
  annotated `@HiltViewModel` with constructor injection. This co-location is intentional: a feature
  is a folder, and everything that belongs only to that feature lives in it.
- A ViewModel shared by several routes is obtained through an explicit activity-scoped accessor
  (`sharedMusicViewModel()`, `sharedMusicPlayerViewModel()`), never a bare `hiltViewModel<T>()` at
  each route. See performance rule 9 — this is a measured regression, not a style choice.
- **Each ViewModel owns its own `UiState` data class**, exposed as a `StateFlow` and mutated with
  `MutableStateFlow.update { }` from `kotlinx.coroutines`. That `update` is the standard library
  extension, not a project API, and there is no central registry of states — each feature's state is
  local to that feature by design. To learn one feature's state, read that feature's `UiState` class;
  it is the complete list for that screen.
- **There is deliberately no ViewModel superclass.** A shared base class across ~70 ViewModels would
  couple every feature to one lifecycle and one state shape, and the project's warm-composition and
  activity-scoping rules mean the ViewModels genuinely do not share a lifecycle. Please open a
  discussion before proposing one.
- Pure logic (paging, filtering, normalization, policy) comes **out** of the ViewModel into its own
  file so it can be unit-tested without Android.

### Dependency injection

Flow uses Hilt, but some legacy app-owned classes are still reached through static `getInstance()`
calls. Treat those as migration debt, not as a pattern to copy.

1. **Use constructor injection by default** for new ViewModels, repositories, use cases, workers,
   services and managers.
2. **Do not add new app-owned `getInstance()` calls.** (This does not apply to platform/library
   factories like `Calendar.getInstance()` or `WorkManager.getInstance()`.)
3. **Migrate incrementally**, only when a class is already in scope for your change. Do not perform a
   repository-wide DI rewrite as incidental cleanup.
4. **Preserve lifecycle and cardinality exactly.** A migration must not create a second database,
   repository, cache, coroutine scope, network client, player, media session, or background service.
5. **Keep constructors and Hilt providers free of blocking I/O**, network/database work, player
   preparation, and unrelated side effects.
6. **Do not create an interface for every class** solely to claim SOLID compliance. Introduce an
   abstraction when it represents a real boundary or enables a useful fake.
7. **Player-path migration is high risk.** Do not migrate `EnhancedPlayerManager`,
   `EnhancedMusicPlayerManager`, `ShortsPlayerPool`, player services/media sessions, surfaces,
   caches, or their app-start initialization as opportunistic cleanup — it requires an explicit,
   separately agreed task.
8. **DI is a maintainability tool, not a performance improvement.** Do not claim a performance
   benefit without measurement.

### Refactor hygiene

1. **No dead code.** Delete, never comment out. A moved component leaves nothing behind.
2. **Comments explain non-obvious WHY only** — a workaround, a hidden constraint, a subtle
   invariant. Never restate WHAT the code already says. This is why the codebase looks sparsely
   commented; it is intentional, and adding descriptive comments is not the documentation
   improvement we are looking for.
3. **Moving a file makes it ratchet-eligible.** Spotless `ratchetFrom` treats a moved or touched file
   as new, so it must now pass ktlint in full, including import order and property naming. This is
   the most common cause of a surprise red build during a move.
4. **Strings move with the component**, and only in `values/strings.xml`.

## 🔤 Strings — No Hardcoded Strings

All user-facing strings **must** be declared in `app/src/main/res/values/strings.xml` and referenced
via `stringResource(R.string.xxx)` (or `context.getString(...)` outside Compose) — never inline
string literals in UI code. Add the string to `strings.xml` first, then reference it.

**Do not touch other locales' `strings.xml` files.** Translations are managed through Weblate; edit
only the default (English) resource file.

## 🚫 Hard Don'ts

1. **Do not edit the Room database schema** without agreeing it first — schema changes require a
   version bump and a migration, handled deliberately.
2. **Do not bump the app version** in any file. Version bumps are done by the project owner.
3. **Do not touch non-English `strings.xml`** files (Weblate owns them).
4. **Do not regenerate the baseline profile** for routine feature or UI work. It is a ~20 minute run
   on a physical device and produces thousands of lines of churn. It is regenerated when the
   cold-start path changes, when the generator's journeys change, or before a release.

## ✍️ Commit Messages

Use the Conventional Commits format: `type(scope): short description`. Scope is optional but
preferred. Keep the subject in the imperative mood.

```
feat(player): add gapless playback
fix(music): recover the feed when a load-more page appends nothing
refactor(library): reorganise library components and share them across surfaces
perf(home): gate the marquee on sheet visibility
docs: document the ViewModel placement rules
test(neuro): add a regression floor for weak-tail coverage
chore(ci): limit concurrent Kotlin compilations
```

**Types:** `feat`, `fix`, `perf`, `refactor`, `docs`, `test`, `build`, `ci`, `chore`.

Older commits in the history use emoji prefixes (`✨`, `🐛`, …). That convention is retired — please
use the `type(scope):` form for new work.

## 🎯 Formatting and Linting

Spotless enforces ktlint formatting using the rules in `.editorconfig`, over Kotlin sources in
`app/src/` and `baselineprofile/src/` plus the selected Gradle Kotlin scripts.

Before committing or pushing Kotlin or Gradle Kotlin script changes:

```bash
./gradlew ktlintCheck          # Windows: .\gradlew.bat ktlintCheck
```

To auto-format the files changed since the lint ratchet revision:

```bash
./gradlew ktlintFormat         # Windows: .\gradlew.bat ktlintFormat
```

Review the resulting diff before committing.

- **Do not bypass, disable, or weaken the formatter** to make a change pass. Fix the reported file,
  or change `.editorconfig` only when the project convention itself is intentionally changing.
- The repository adopts formatting incrementally with Spotless `ratchetFrom`, so untouched legacy
  files are not reformatted. A newly added file, or an existing targeted file changed after the
  ratchet revision, must pass the configured ktlint rules in full.
- GitHub Actions runs `spotlessCheck` before tests and builds — a formatting violation fails the
  `Build APK` job.

## 🧪 Building and Testing

Build the relevant flavor to check for compilation errors:

```bash
./gradlew :app:assembleGithubDebug
```

The minimum validation for a non-trivial change:

```bash
./gradlew ktlintCheck
./gradlew :app:testGithubDebugUnitTest
./gradlew :app:compileGithubDebugKotlin
./gradlew :app:compileFossDebugKotlin
```

Both flavors must compile — a change that only builds `github` will fail CI on `foss`.

- **For UI changes, actually run the app** on an emulator or device and exercise the golden path plus
  edge cases. A passing build does not mean the feature works.
- Add focused unit tests with any new pure logic (the state/policy files split out of ViewModels
  exist precisely so this is easy).
- Note in the PR description **what you verified and what you did not**. "Compiles and unit tests
  pass" is a fine thing to say; "behaviourally identical" is not, unless you exercised it.

## 📤 Submitting Your PR

1. **Update your fork:**
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Push your changes:**
   ```bash
   git push origin feature/your-feature-name
   ```

3. **Create the Pull Request:**
   - Fill out the PR template
   - Link any related issues
   - State what you verified on-device, and what you did not
   - If you hand-rolled anything (see "Use the platform"), say what you checked and what was missing

**Keep pull requests focused.** One concern per PR. A UI fix bundled with a refactor and a dependency
bump takes three times as long to review and is three times as likely to be reverted.

## 📋 Code Review Process

- Maintainers will review your PR
- Address any feedback or requested changes
- Once approved, your PR will be merged
- Your contribution will be credited in releases

## 🔐 Release and Signing Invariants

Flow is distributed through GitHub Releases and
[IzzyOnDroid](https://apt.izzysoft.de/packages/io.github.aedev.flow). Both pin properties of the
published artifacts, so the following are hard constraints. Breaking one of them cannot be fixed by a
follow-up release — it forces every installed user to uninstall and reinstall, losing their local
data.

**The signing key never changes.** Every release APK must be signed with the official key,
certificate SHA-256 `4322294ed4caa2d4294140095818080ffe8acc1fbe3cdc76107df45c5286be40`. Android
refuses to install an update signed by a different key. CI enforces this in the
`Verify release signing certificate` step, which fails the build on a mismatch. Never regenerate
`release.keystore`, and never rotate the `RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS`, or
`KEY_PASSWORD` repository secrets.

**Release asset file names are a public contract.** IzzyOnDroid matches release assets by file name.
`flow.apk` and `flow-foss.apk` are the universal builds and must keep those exact names. The per-ABI
APKs are published alongside them as extras. Renaming or removing either universal APK silently
breaks the IzzyOnDroid update feed, so coordinate with IzzyOnDroid before changing them.

**`versionCode` must increase on every release.** F-Droid-format repositories use it to detect
updates. The ABI splits all share one `versionCode`, so the universal APK is the only artifact
IzzyOnDroid should consume.

**A tag build must never publish unsigned APKs.** `app/build.gradle.kts` falls back to
`signingConfig = null` when no keystore is present, which produces uninstallable APKs. CI hard-fails
a `v*` tag build when the keystore secret is missing rather than publishing them.

## 🎯 Areas We Need Help

- [ ] Writing unit tests
- [ ] UI/UX improvements
- [ ] Performance optimization
- [ ] Bug fixes
- [ ] Accessibility features
- [ ] Translations (via [Weblate](https://hosted.weblate.org/engage/flow/), not by editing
      locale files directly)

## 📜 Code of Conduct

- Be respectful and inclusive
- Welcome newcomers
- Give constructive feedback
- Focus on the code, not the person
- Help create a positive community

## ❓ Questions?

- Open a [discussion](https://github.com/A-EDev/Flow/discussions) — including questions about how the
  codebase works
- Comment on existing issues
- Reach out to maintainers

## 🙏 Thank You!

Every contribution helps make Flow better. Thank you for being part of the community!

---

**Happy Coding! 🚀**
