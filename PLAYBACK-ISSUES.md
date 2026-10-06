# Playback issues and follow-up work

Updated: 2026-10-06. Reviewed for commit at the user's request. Device-specific renderer changes remain deferred; partial tablet validation and user testing are recorded below.

This tracks the current work rather than treating every deferred finding in `HANDOFF-2026-10-06.md` or `BUGFIX-AUDIT-2026-10-06.md` as still open.

## User-reported issues — closed with local regression coverage

### 1. On-device sponsor detection does not consistently start

**Reported:** Opening a video sometimes does not load/run the sponsorship model. Closing and reopening the app does not always recover it.

**Expected:** With the feature enabled, an installed usable model and supported captions, each eligible video starts detection or displays an accurate reason for skipping/failing. Cancellation of an old video must not suppress a newer evaluation. Installing/enabling the model should allow a later evaluation to run.

**Confirmed cause / locally validated fix:** A video with cached SponsorBlock segments bypassed the coordinator entirely, so on-device inference never started. The cache-hit path now starts a cancellable shadow evaluation with cached segments supplied as authoritative input; it does not refetch the API or delay applying cached playback segments. Disabled detection, missing models and unsupported/missing captions remain separate reasons to skip.

**Additional fix:** Player-scoped, event-driven observation of the existing Hilt model repository and preferences retries a matching unavailable evaluation when the model becomes usable. Retries are bounded per readiness change and handle readiness changing during an evaluation. On-device detection runs independently of the online SponsorBlock toggle; disabling online fetching uses an authoritative empty API list for ordinary videos. Disabling the model clears model predictions while preserving enabled API segments. Results and deferred cleanup are guarded against a changed video or evaluation generation. Explicitly saved offline segments retain their existing behavior.

**Verification:** Coordinator policy, readiness-flow and manager lifecycle/retry tests. On the connected tablet, enabling on-device detection with online SponsorBlock disabled downloaded the model and automatically started real inference for the still-open video (98 caption cues; two predicted spans).

### 2. Last-played history / saved playback position is not restored

**Reported:** Playing a previously watched video starts at the beginning, and last-played state does not appear to be maintained.

**Expected:** Save progress during playback and at appropriate exit boundaries; reopening normally resumes an unfinished video when resume is enabled. Preserve intentional restart behavior for completed videos. An explicit incoming timestamp takes precedence over saved history.

**Confirmed causes / locally validated fixes:** HLS was being treated as proof of a live stream in history saving and resume preparation, including on-demand videos. History touching also read an old position and later replaced the entire row, which could overwrite a concurrent progress save. Use actual live state, allow on-demand HLS resume, and update history metadata without replacing existing progress. Progress writes now require a prepared player with the matching video id. Closing or replacing a video captures its real position before clearing the player, including an intentional zero after a prepared seek. Tablet testing also found that the mini-player component stopped playback before the route could capture progress. Close now delegates directly to the route-owned callback so saving happens before clearing.

**Verification:** Save/restore and close/reopen regression tests, including completed videos and explicit timestamp precedence. Additional tablet coverage is recorded below; the final mini-player close fix still needs a device retest.

### 3. YouTube share timestamps are not reliably honored

**Reported:** Sharing a YouTube URL with a timestamp sometimes opens at the wrong position.

**Expected:** Parse supported timestamp-bearing URLs consistently through share and link intents, retain the position through navigation, and honor it during media preparation. Sharing text containing a URL should also work. An absent timestamp must not be confused with an explicit zero.

**Confirmed causes / locally validated fixes:** Explicit zero timestamps were discarded at parsing, routing and resume resolution, allowing saved progress to win. Fragment timestamps, encoded values and trailing punctuation were not handled consistently. Preserve an explicit nonnegative timestamp through the player route; prefer valid query timestamps over fragment timestamps and incoming timestamps over saved history. An exact failing shared URL has been requested for any cases not covered by the regression examples.

**Additional tablet-found fix:** An explicit timestamp for an already-open video used the ordinary closest-keyframe seek, so a requested 65 seconds landed at 63.1 seconds. Explicit incoming positions now use Media3 exact seeking while restoring the previous seek policy for ordinary scrubbing; queued SABR rebuilds apply this policy inside the matching request.

**Verification:** URL-format and intent/route tests for seconds, unit-form timestamps, query/fragment forms, explicit zero and URLs embedded in shared text. Tablet tests confirmed cold fragment timestamps and explicit zero; the final warm exact-seek fix has regression coverage but still needs a device retest.

## Previously identified follow-ups — closed with local regression coverage

| Issue | Status | Next step |
| --- | --- | --- |
| PlayerFactory retains old buffering/audio/call settings across player recreation | Closed; locally validated | Refresh the immutable snapshot on async preparation and invalidate on release. Included in the final validation below. Active playback is not rebuilt when settings change. |
| Rapid SABR seeks launch independent session rebuild requests | Closed; locally validated | Cancel superseded queued requests, guard player/video/session identity before rebuilding, and cancel on close, replacement, direct boundary seek, stop and release. No added debounce delay. |
| UMP frames / incomplete media segments can accumulate before bounded playback queues | Closed; locally validated | Bound retained UMP data, incomplete segment count and per-track bytes, plus decompressed output. Reject oversized responses with a terminal fallback error, clear partial state, and reject the failing SABR format for the current video. |
| Download queued as the worker completes may stay pending | Closed; locally validated | Durable pending mutations append a successor to the unique WorkManager chain with `APPEND_OR_REPLACE`; ordinary queue kicks retain `KEEP`. This covers enqueue after the running worker's final database check without parallel queue workers. |
| Queue autosave misses changes while playback is paused | Closed; locally validated | Compare structural queue/automix snapshots, including ordering and metadata. Only advance the unchanged-state guard after a successful DataStore edit; retry failed saves on the next tick. |
| Sleep timer retains player/Activity callbacks without matching owner cleanup | Closed; locally validated | Composition owner tokens, matching detach, active-media eligibility and weak Activity references. Detach clears the exit callback; an active background timer retains its pause target until cancellation/expiry or replacement. |

Stream limits are operational guardrails aligned with the existing queue budgets, not protocol-defined maxima: 32 MiB retained UMP payload/input, eight incomplete segments, 4 MiB aggregate incomplete audio and 32 MiB video, and matching per-track decompression limits. Valid fragmented input and byte-budget release on finish/replacement/reset have regression coverage. Existing compressed `contentLength` validation semantics remain unchanged.

## Streaming changes already locally validated

- Bounded SABR playback queues with high-water pacing, critical-lead and heartbeat bypasses, and explicit capacity failure handling.
- Meter actual SABR HTTP response-body bytes; mark memory-buffer transfers local to avoid counting them as network throughput.
- Pause follow-up polling until resume or the server heartbeat.
- Avoid repeatedly retrying the same overflowing video format.
- Remove the sponsor-boundary load stop that could strand playback before a skip. Media3 may buffer bytes inside a sponsor segment before it is skipped; avoiding that download is no longer a buffering constraint.

Compilation and unit tests verify code behavior, not measured streaming smoothness, battery use or device-specific playback reliability. No such performance claim has been established yet.

## Final validation for this closure pass

- `ktlintFormat` and `ktlintCheck`: passed.
- `:app:testGithubDebugUnitTest`: 3217 passed, 55 skipped, zero failures/errors.
- `:app:compileGithubDebugKotlin`, `:app:compileFossDebugKotlin` and `:app:assembleGithubDebug`: passed.
- Actual in-memory Room tests verify that touching a history row preserves saved progress and existing metadata.
- Manager integration tests cover cached-video evaluation, online-disabled on-device detection, installing a model during an unavailable evaluation, and removal of provisional spans by an empty final result. Inference itself is faked in unit tests.
- Parsing, intent-to-route, resume preparation, explicit-zero/near-end precedence and pre-clear progress capture have regression coverage.
- Seek/session identity, close cancellation, UMP fragmentation/resource rejection, accumulator budget release, decompression caps and sleep-timer owner cleanup have focused regression coverage.
- Download tests verify successor-work policy and pending database writes; the exact WorkManager completion window was not exercised with a real scheduler.
- Final Gradle validation used `--max-workers=1` and a temporary 6 GiB Kotlin daemon heap after concurrent flavor compilation exhausted the previous 4 GiB compiler heap. No build configuration file was changed.
- `graphify update .` and `git diff --check`: passed (graph extraction retains its existing partial-extraction warnings).
- Room schema and app version are unchanged. No push was requested.

## Tablet validation and remaining limits

- Xiaomi Pad 6 (`23043RP34I`, Android 14), debug package `io.github.aedev.flow.debug`, connected over wireless ADB. Existing app data was preserved.
- Direct video playback and a paused 1080p-to-720p quality switch worked; the quality switch preserved the paused state and position.
- An explicit zero timestamp overrode saved progress. A cold fragment link requesting 65 seconds prepared and rendered at 65 seconds. Warm timestamp seeking and mini-player closing revealed the additional fixes above. Their updated APK was built, but those two device retests were not completed before the user requested review and commit.
- Model installation triggered inference on the still-open video with online SponsorBlock disabled. This verifies actual model execution and readiness retry, not prediction accuracy across videos.
- A paused idle UI produced zero frames during an approximately 11-second `dumpsys gfxinfo` observation. This is a limited observation, not a battery, thermal or general smoothness benchmark.
- The user reported that most issues worked in their own testing, without identifying each tested case. Harder cases retain automated-test coverage rather than being marked device-verified.
- The exercised video used direct streams, not SABR. SABR queue limits, accounting, fallback and concurrency have unit coverage; sustained on-device SABR streaming remains unverified.
- Instrumentation APK compilation passed, but the real-model golden instrumentation suite was not run. External casting, device-to-device sync, scheduler race reproduction and the full background/PiP/configuration-change matrix remain unverified.
- During tablet testing, autoplay was disabled and online SponsorBlock was disabled; on-device detection was enabled and its model installed. These tablet settings were not restored before review/commit.
