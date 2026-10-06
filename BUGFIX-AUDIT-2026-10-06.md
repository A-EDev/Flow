# Bug-fix audit — video playback, performance, and the wider codebase

Current status: [PLAYBACK-ISSUES.md](PLAYBACK-ISSUES.md) supersedes the historical deferred findings and validation counts below. Later fixes and tests are included in the reviewed commit; tablet coverage and remaining verification limits are recorded there.

Date: 2026-10-06
Branch: `sync/upstream-20260929` (working tree only; nothing committed)

Scope: a deep audit of the video-playback path and app-wide performance, followed by an
audit of the remaining subsystems (data/recommendation, downloads/extraction, non-player UI,
services/workers/sync/notifications). Fixes are ordered video-playback first, then the rest.

## Verification

Run after the full set of changes:

| Check | Result |
| --- | --- |
| `./gradlew :app:compileGithubDebugKotlin` | pass |
| `./gradlew :app:compileFossDebugKotlin` | pass |
| `./gradlew ktlintCheck` | pass |
| `./gradlew :app:testGithubDebugUnitTest` | pass (3157 tests, 0 failures, 55 skipped) |

Two changes were reverted because the existing test suite pins the current behaviour as
intended (see "Reverted").

No commit, push, merge, version bump, or Room schema change was made. `graphify update` was
not run because the `graphify` binary is not installed in this environment.

---

## 1. Video playback

### 1.1 SABR segment buffer spun the ExoPlayer loading thread
`player/sabr/integration/SabrSegmentBuffer.kt`

After any bytes had been returned in a call, a refill used a non-blocking `queue.poll()`; an
empty queue fell through to `continue`, re-entering the loop and polling again immediately —
a tight CPU spin on ExoPlayer's loading thread during every network gap (heat/battery, the
exact regression class the project treats as critical). Now the partial read is returned when
the non-blocking poll is empty, and the caller re-reads later.

### 1.2 Autoplay countdown survived `stop()` and resumed playback
`player/EnhancedPlayerManager.kt`

`stop()` cancelled `autoplayJob` but never the autoplay countdown. Closing the player while a
countdown was showing left it running; when it elapsed, `performAutoAdvance()` fired and
playback restarted. `stop()` now calls `autoplayCountdownController.stop()`;
`clearCurrentVideo()` also stops it and releases the advance wake lock.

### 1.3 `release()` left lifetime work running
`player/EnhancedPlayerManager.kt`

`release()` now cancels `autoplayJob`, stops the countdown, and cancels/nulls `eqObserver`
(previously left collecting the equalizer spec against a released player).

### 1.4 Changing audio track or quality started playback while paused
`player/EnhancedPlayerManager.kt`

`switchAudioTrack()` and the quality `onQualitySwitch` callback called `loadMediaInternal()`
without `playWhenReady`, defaulting to `true`. It now passes the player's current
`playWhenReady`. This also fixes the buffering case, where the old code keyed off `isPlaying`
and could fail to resume.

### 1.5 Cross-thread visibility in SABR state
`player/sabr/core/SabrSessionState.kt`, `player/sabr/integration/SabrOrchestrator.kt`,
`player/sabr/integration/SabrDownloadEngine.kt`

- `playheadPositionMs` is written on the main playhead tick and read on the SABR
  request-builder thread → `@Volatile`.
- `SabrOrchestrator.consecutiveErrors` is written from both main and IO → `@Volatile`.
- `SabrDownloadEngine`'s local `consecutiveErrors` is captured by two coroutines → replaced
  with an `AtomicInteger` (a local `var` cannot be `@Volatile`).

### 1.6 Protobuf parser had no bounds checks
`player/sabr/proto/ProtobufReader.kt`

`readRawVarint` could read past the buffer; fixed64/fixed32 reads were unchecked; a
length-delimited field accepted a negative or oversized length (`pos += length` could move the
cursor backwards). Added explicit bounds checks that throw a domain `IllegalStateException`
instead of `ArrayIndexOutOfBoundsException` / corrupted state.

---

## 2. Video player UI and performance

### 2.1 Fullscreen side drawer recomposed the whole player host every frame
`ui/screens/player/stage/FullscreenSidePanel.kt`,
`ui/screens/player/VideoPlayerHost.kt`,
`ui/screens/player/stage/SponsorSkipLayer.kt`

The drawer's `Animatable.value` was read in composition and turned into `drawerOffset` /
`playerWidth` `Dp`s consumed in composition, so the entire host (video stage, controls,
sponsor layer) recomposed on every frame of the open/close tween and every drag event — the
documented rule-3 anti-pattern. The state now exposes layout-phase providers (`drawerOffsetPx`,
`playerWidthPx`); the drawer uses `Modifier.offset { }` and the player/sponsor widths use
`Modifier.layout`. The arithmetic is unchanged, so output is pixel-identical.

### 2.2 Scrub controller read the playhead in composition
`ui/components/videoplayer/controls/PlayerScrubController.kt`

After a seek, `livePosition()` was invoked in composition, subscribing the whole controls
overlay to the position tick until the seek settled. Replaced with a `snapshotFlow` inside a
`LaunchedEffect`.

### 2.3 Lock-mode LIVE dot was not visibility-gated
`ui/components/videoplayer/controls/PlayerControlsOverlay.kt`,
`PlayerLockedControls.kt`

The locked branch dropped the `isLayerOnScreen` gate the rest of the overlay threads through,
so the infinite LIVE pulse kept running while the layer was composed-but-unplaced. Threaded
the existing gate into `PlayerLockedControls` → `PlayerTimePill`.

### 2.4 Forward double-tap could seek to 0
`ui/components/videoplayer/gesture/PlayerTapGestures.kt`

`(base + step).coerceAtMost(currentDuration)` clamps to `0` while duration is still unknown
(and momentarily on re-buffer), rewinding to the start. Clamp only when duration > 0.

### 2.5 Vertical-drag HUD hide timer raced the next gesture
`ui/components/videoplayer/gesture/PlayerDragGestures.kt`

The 500 ms hide timer was launched on the shared scope and never cancelled, so a new
brightness/volume swipe within that window had its overlays cleared mid-gesture. The job is now
held and cancelled on drag start (and by the cancel path).

### 2.6 Premiere countdown ticked with no lifecycle gate
`ui/components/videoplayer/UpcomingVideoOverlay.kt`

The 1 Hz loop is now wrapped in `repeatOnLifecycle(STARTED)` and stops at expiry.

### 2.7 Queue row recomposed every frame of a swipe
`ui/components/musicplayer/queue/QueueTrackRow.kt`

`offsetX.value` was read in the row's composition and passed down. The panel now takes a
provider and reads it in its own recomposition scope, and the clickable gate uses
`derivedStateOf` so the row itself recomposes only when the swipe settles.

### 2.8 QR scanner leaked a camera binding and an executor
`ui/screens/sync/QrComponents.kt`

The analyzer executor is now shut down and the camera `unbindAll()`-ed on dispose; the
`ProcessCameraProvider` listener checks a disposed flag so a late-resolving future cannot bind
the camera to the host lifecycle or register an analyzer on a shut-down executor.

---

## 3. DLNA / casting

### 3.1 Proxy server outlived the cast picker
`player/dlna/DlnaCastManager.kt`

`startDiscovery()` starts the `StreamProxyServer`, but the picker's dismiss path only called
`stopDiscovery()`, which never stopped it; `proxy.stop()` was reachable only via `disconnect()`.
The socket stayed bound for the process lifetime. `stopDiscovery()` now stops the proxy when no
cast is active (`_currentDevice == null`).

### 3.2 OkHttp response leaked on a mid-stream failure
`player/dlna/StreamProxyServer.kt`

`response.close()` ran only on the success path; an I/O error while reading/writing skipped it.
The streaming block is now wrapped in `try { … } finally { response.close() }`.

---

## 4. Downloads and storage

### 4.1 Partial file orphaned when a cross-filesystem move failed
`data/video/storage/DownloadFiles.kt`

`moveInto` returned null on a failed copy but left the half-written destination in the user's
chosen folder. It now deletes the partial target.

### 4.2 Download error counter raced across coroutines
`player/sabr/integration/SabrDownloadEngine.kt`

See 1.5 — `consecutiveErrors` is now an `AtomicInteger`, so the error budget is actually
enforced.

---

## 5. Data, network, and recommendation

### 5.1 `negativeSignals` was never persisted
`data/recommendation/NeuroStorage.kt`

`TopicEvidence.negativeSignals` existed and was used by scoring (`explorationBonus`) and
written on dislike, but was absent from `SerializableTopicEvidence` and both conversion
directions, so every process restart silently reset it to 0 and disliked topics were
re-surfaced as "exploration". Added the field with default 0 (backward compatible) and copied
it in `toSerializable()` and `toTopicEvidence()`.

### 5.2 A new `OkHttpClient` was built per request
`data/repository/SponsorBlockRepository.kt`, `data/repository/DeArrowRepository.kt`

Both used a `get()`-only property, allocating a fresh client (its own connection pool and
dispatcher) on every fetch. Both now cache one client keyed by `AppProxyManager.currentSignature()`,
rebuilding only when the proxy changes.

### 5.3 `featureCache.clear()` was not synchronized with its guarded access
`data/recommendation/FlowNeuroEngine.kt`

Three `clear()` calls ran under `brainMutex` while gets/puts were guarded by
`synchronized(featureCache)` and ran outside it, mutating a plain `LinkedHashMap` from two
threads. All `clear()` calls are now inside `synchronized(featureCache)`.

### 5.4 `NewPipeExtractor.init()` was unsynchronized
`innertube/pages/NewPipe.kt`

Concurrent extraction and download threads could race the init and rebuild the downloader
twice. `init()` and `invalidateClient()` are now `@Synchronized`.

---

## 6. Non-player UI, notifications

### 6.1 `ReminderReceiver` did DataStore I/O on the main thread
`notification/ReminderReceiver.kt`

`onReceive` runs on the main thread and called notification helpers that block on DataStore.
It now uses `goAsync()` and a `Dispatchers.IO` coroutine (with `finish()` in `finally`).

### 6.2 Hardcoded reminder strings
`notification/ReminderReceiver.kt`, `res/values/strings.xml`

The bedtime/break titles and bodies were inline literals. Moved to
`reminder_bedtime_message`, `reminder_bedtime_message_detail`, `reminder_break_message`,
`reminder_break_message_detail` in the default `strings.xml`.

### 6.3 QR expiry countdown ran forever
`ui/screens/sync/SyncSessionContent.kt`

The loop is now bounded by `remaining > 0`.

### 6.4 Screens collected state without lifecycle awareness
`ui/screens/shorts/ShortsScreen.kt`, `ui/screens/channel/ChannelScreen.kt`

`uiState` / `communityUiState` switched to `collectAsStateWithLifecycle()`.

### 6.5 Queue auto-save wrote to DataStore while idle
`data/local/QueuePersistence.kt`

The 30 s auto-save now skips the write when the position and track id are unchanged, so a
paused/idle app no longer rewrites identical data.

---

## Reverted (tests pin the behaviour as intended)

- **SponsorBlock skipped while disabled.** `checkForSkip` / `getNextSkipCheckDelayMs` gate on
  `!isEnabled && !offlineSegmentsLoaded`; changing it to require `isEnabled` broke four
  `SponsorBlockHandlerTest` cases. Per the tests (and `loadSegmentsFromList`'s contract),
  offline/on-device segments are meant to apply regardless of the online toggle. Its
  doc comment is misleading; no code change kept.
- **`DownloadController` `ExistingWorkPolicy.KEEP` → `APPEND_OR_REPLACE`.** `DownloadControllerTest`
  asserts `KEEP`. Reverted; the suspected stranded-pending window is documented below instead.

---

## Identified but not changed (deferred, with reasons)

These are real findings from the audit that were **not** applied. They need a product
decision, live-device verification, or a larger refactor, and changing them blind risked
regressions the test suite cannot catch.

### Security / exposure
- **DLNA proxy binds `0.0.0.0` with no auth and no connection cap**
  (`player/dlna/StreamProxyServer.kt`). `MAX_CONNECTIONS` is only the `ServerSocket` backlog;
  every accepted socket spawns a coroutine. Binding to the Wi-Fi address and enforcing a real
  concurrency limit is a deliberate behaviour change (could break casting on some networks).
  The dismiss-path leak (3.1) is fixed; the exposure is not.
- **`StreamProxyServer.start()` race** can leak a bound socket if two `start()` calls overlap
  (guard is set only after the socket is created, inside the coroutine). A proper fix needs a
  synchronous start with a lock/`CompletableDeferred`.

### Playback behaviour (needs device verification)
- **`CustomMediaCodecVideoRenderer` forces `codecNeedsSetOutputSurfaceWorkaround = true` for
  every codec**, re-creating the codec on every surface swap (PiP, popup, rotation,
  background) on hardware that supports in-place switching. Likely a deliberate workaround for
  specific devices; no comment explains it, so it was left alone rather than risk frozen
  frames on the fleet it exists for.
- **`registerHlsCast` fallback returns a video-only googlevideo URL** when the proxy is not
  running, which the caller then hands to the renderer as `application/vnd.apple.mpegurl`
  (silent video). Fixing it means defining the intended fallback path.
- **Download resume is discarded when the resolved format has no `contentLength`**
  (`TransferState.restoreInto` compares a pre-probe `0L` to the saved size), so pause / lost
  network / work-stop restarts the file. Fixing it re-keys restore on itag+role and validates
  size after the probe.
- **`RangeDownloader` accepts HTTP 200 for googlevideo URLs without verifying `Content-Range`
  start** (`transfer/RangeDownloader.kt`). The comment claims GVS honours the query range while
  answering 200; verifying or refusing needs live traffic.
- **Live-chat poll timeout clamped to ≤ 1.5 s** (`data/repository/LiveChatRepository.kt`),
  overriding YouTube's server-directed 5–10 s interval (3–6× more requests). May be a
  deliberate latency choice.
- **`disconnect()` teardown races a fast reconnect** (`DlnaCastManager.stop()` nulls
  `_currentDevice`/cancels polling in a coroutine after an up-to-8 s SOAP call), which can drop
  a new cast.
- **SABR `contentLength` is checked before decompression** (`SabrStreamController`), which
  would drop GZIP/Brotli segments if the server ever sets a compression flag.
- **SABR `SabrSessionState` collections are mutated from several coroutines without
  synchronization** (`sabrContexts`, `sabrContextsToSend`, buffered-range lists). The scalar
  fields are now `@Volatile`; the collections still need a single-writer or concurrent
  structure.
- **`UmpFrameDecoder` has no maximum part size**, so a corrupt length varint buffers
  unbounded memory. A cap risks wedging legitimate large parts, so it needs a chosen ceiling.

### Architecture / feature wiring
- **SponsorBlock MUTE and SHOW_TOAST actions emit flows nobody consumes**
  (`SponsorBlockHandler._muteEvent` / `_toastEvent`). Wiring them needs a volume/preview-state
  design so a user's volume is restored correctly.
- **`SleepTimerManager` is a process-wide singleton** that never calls `detachPlayer()`
  (no callers) and holds Activity-capturing `exitCallback`/`pauseCallback`. A correct fix is a
  lifecycle-driven attach/detach design.
- **`FeedInvalidationBus.emit` uses `tryEmit` with an 8-slot buffer and no replay**, so
  invalidation events can be dropped when the buffer is full or no collector is attached. A fix
  needs a collector-presence guarantee or a suspend/acknowledged emit.
- **`EqualizerViewModel.onCleared` resets the singleton repository's bypass/preview** even
  though the shell holds a second, activity-scoped instance of the same ViewModel over the
  shared repository. Needs a shared-instance decision.
- **`NotificationHelper` still uses `runBlocking` in other call paths** (only the receiver path
  is fixed); caching the preference flags or making methods suspend is the broader fix.
- **`DownloadController` `KEEP` race** can strand a row queued as a drain finishes; fixing it
  without changing the pinned policy means re-checking for queued work after `drain()` returns
  inside the worker (needs DAO access from `DownloadQueue`).
- **`QueuePersistence` auto-save** is now dirty-checked but the 30 s timer still runs; the
  debounced save already covers change-driven writes, so the timer could be removed entirely
  after confirming position persistence across process death is still adequate.
