# Always-On Shadow Sponsor Detection and Training Feedback

## Summary

Refactor sponsor detection so every eligible standard video runs on-device ML inference once whenever SponsorBlock is enabled. SponsorBlock remains authoritative when it has data; model predictions become active only when the API returns no segments or fails.

With a separate, default-off “Help improve sponsor detection” consent, the app will save focused transcript excerpts, predictions, SponsorBlock comparisons, and per-segment user feedback to an app-private JSONL journal. Users can periodically export the journal and explicitly clear/reset it afterward. Nothing uploads automatically.

Shorts, music playback, and true live streams remain out of scope for this version.

## Detection and Playback Architecture

- Introduce a focused `SponsorDetectionCoordinator` outside the large player manager. It owns caption loading, API lookup, prediction caching, comparison, training-journal writes, cancellation, and evaluation state.
- On each standard VOD, downloaded video, or local video with subtitles:
  - Select the existing preferred non-translated English caption track.
  - Fetch and parse the transcript once.
  - Launch SponsorBlock lookup and ML inference concurrently.
  - Cancel both when the active video changes or SponsorBlock is disabled.
  - Reject stale results by checking the video ID and request generation before publishing.
- Active playback segment precedence:
  1. Non-empty saved offline SponsorBlock segments.
  2. Non-empty SponsorBlock API result.
  3. On-device predictions when the API is empty, unavailable, or failed.
  4. No segments when captions/model inference are unavailable.
- When saved/API segments exist, keep model predictions in shadow state only. They must not be painted on the seek bar, trigger skips, mute playback, or replace API segments.
- Refactor `SponsorBlockRepository` to return a typed result distinguishing successful data, successful empty response, HTTP failure, network failure, and cancellation. Both empty and failure allow model fallback, but evaluation records preserve the distinction.
- Change the detector result from a plain segment list to `SponsorInferenceResult`, containing:
  - model and tokenizer identity;
  - caption metadata and SHA-256;
  - raw predicted spans and confidence;
  - inference duration;
  - model-compatible SponsorBlock segments.
- Keep the ONNX session lazily loaded, reused across videos, serialized by the existing mutex, and executed on `Dispatchers.Default`. Add explicit release so the session is closed with the player and can be safely recreated.
- Skip shadow inference for missing English captions, failed/empty VTT, true live streams, Shorts, and music playback. Post-live VODs may run when a complete static caption track is available.
- Preserve the current evaluated decoding threshold of `0.0` and retain raw confidence values. Confidence filtering can be tuned later from collected results rather than discarding low-confidence evaluation data now.

## Comparison, Caching, and Transcript Dataset

- Compare only API segments whose category is `sponsor`; other SponsorBlock categories continue to affect playback but are not ground truth for this sponsor-only model.
- Perform deterministic one-to-one matching using the highest temporal IoU, with `IoU >= 0.5` considered a match. Record:
  - matched spans with IoU and start/end boundary errors;
  - model-only spans;
  - API-only spans;
  - successful API-empty cases;
  - API failures separately.
- Treat SponsorBlock comparisons as weak labels, never unquestioned ground truth. Preserve label provenance on every comparison and user verdict.
- Cache inference results under `videoId + model SHA-256 + tokenizer SHA-256 + caption SHA-256`.
  - Cache predictions and metadata only—never transcript text.
  - Use an app-cache file store, not Room, so no database schema/version change is required.
  - Retain entries for 30 days with a maximum of 500 entries, evicting oldest cache entries only.
  - Replays with the same caption/model reuse predictions without rerunning ONNX inference.
- Replace the currently unwired feedback sample with append-only JSONL event contracts:
  - `SponsorEvaluationEvent`: evaluation ID, dedupe key, timestamps, video ID, model/tokenizer metadata, caption metadata/hash, API outcome, API sponsor spans, model spans/confidence, comparison results, focused transcript windows, and inference time.
  - `SponsorFeedbackEvent`: feedback ID, evaluation ID, target span ID, verdict, original range, corrected range where applicable, and feedback timestamp.
- Supported feedback verdicts:
  - `ACCEPTED`: predicted span is a sponsor.
  - `REJECTED`: predicted span is not a sponsor.
  - `CORRECTED`: sponsor exists but boundaries were edited.
  - `MISSED`: user added a sponsor range the model did not predict.
  - `CONFIRMED_NO_SPONSOR`: explicit label for successful zero-prediction reviews.
- Use stable hashes for evaluation IDs and automatic-record deduplication. Replaying a video must not append duplicate evaluation events for the same video/model/tokenizer/caption combination; new user feedback appends a separate linked event.
- When training consent is enabled, persist focused transcript content:
  - Parsed, timestamped caption cues after VTT tag/entity cleanup.
  - Cues overlapping each API, model, or corrected span with 30 seconds of context on both sides.
  - Merge overlapping excerpt ranges.
  - Add up to three deterministic 60-second hard-negative windows outside the protected context ranges.
  - Store the full transcript SHA-256 and preprocessing-version identifier, but not the full transcript.
  - Mark automatically sampled negatives as weak/unverified; only user confirmation creates a strong negative.
- Store training events in `noBackupFilesDir`. Never write transcript text when consent is off.
- Stop new journal writes at 100 MB rather than deleting valuable samples. Continue inference and playback, expose the paused state in settings, and direct the user to export or clear the journal.

## Consent, Review, Export, and Settings UX

- Add a separate `Help improve sponsor detection` preference, defaulting to off.
  - Shadow inference still runs whenever SponsorBlock is enabled.
  - This toggle controls transcript/event persistence and the review UI only.
  - Enabling it opens a Material 3 consent dialog explaining exactly what is stored, that storage is local, that no automatic upload occurs, and that records can be exported or deleted.
  - Store a consent-version value so future material collection changes can require renewed consent.
  - Disabling consent immediately stops new writes and hides feedback entry points; existing records remain until explicitly cleared.
- Add an unobtrusive Material 3 review chip to the standard player’s existing action/info area after every successful inference for opted-in users, including zero-prediction results.
- Open a reusable review bottom sheet from the chip:
  - Show all predicted spans with timestamp, confidence, API overlap status, and reviewed state.
  - Allow seeking to the start of any span for verification.
  - Provide per-span Accept, Reject, and Edit actions.
  - Use a reusable timestamp editor for corrected start/end ranges with `0 <= start < end <= video duration`.
  - Provide “Add missed sponsor” for manually supplied ranges.
  - When no model spans exist, provide “Confirm no sponsor” and “Add missed sponsor.”
  - Save each action as an independent feedback event so partially reviewed videos remain useful.
  - Feedback remains local and never creates or modifies a public SponsorBlock submission.
- Extend SponsorBlock settings with a training-data section showing:
  - consent toggle and concise collection description;
  - evaluation and feedback record counts;
  - current journal size and paused/full status;
  - “Export training data”;
  - “Clear training data.”
- Export a mutex-consistent JSONL snapshot through Android’s document picker with a timestamped `.jsonl` filename.
- A successful export must not delete anything automatically. Show a follow-up action offering to clear the journal; clearing always requires explicit confirmation.
- Clearing resets the training JSONL and its dedupe/index state, but does not clear the disposable prediction cache.
- Put all new user-visible text in the default English `strings.xml` only. Use Material 3 theme typography, color, shape, elevation, chips, dialogs, sheets, and snackbar patterns; add no gradients, glass effects, decorative borders, arbitrary colors, or Material 2 components.

## Public Interfaces and State

- Expose a lifecycle-safe `StateFlow<SponsorDetectionUiState>` containing:
  - current video ID;
  - `IDLE`, `LOADING`, `READY`, `SKIPPED`, or `ERROR`;
  - API outcome;
  - shadow predictions;
  - comparison summary;
  - review progress;
  - whether review is available.
- Expose separate playback segments and shadow/evaluation results. UI seek bars and skip logic must consume playback segments only.
- Give the journal explicit operations:
  - `recordEvaluation`;
  - `recordFeedback`;
  - `observeStats`;
  - `exportTo`;
  - `clear`.
- Use an explicit `SponsorTrainingSink` boundary with the local journal implementation. Do not implement an uploader in this version; the event/export contract is the future backend integration boundary.
- Keep construction explicit within the existing player initialization path without introducing another service locator or opportunistically migrating the high-risk player singleton to Hilt.

## Test and Acceptance Plan

- Unit-test comparison behavior:
  - exact matches, partial overlap, `IoU == 0.5`, below-threshold overlap;
  - one API span against multiple predictions and vice versa;
  - invalid/zero-length ranges;
  - non-sponsor API categories excluded from evaluation.
- Unit-test coordinator behavior:
  - API and ML run concurrently;
  - API segments remain authoritative;
  - API empty/failure activates model results;
  - saved offline segments remain authoritative while ML runs in shadow;
  - disabled SponsorBlock performs no work;
  - missing captions skip inference;
  - rapid video changes cancel and suppress stale results;
  - replay uses cache and does not rerun inference.
- Unit-test transcript collection:
  - context extraction and overlap merging;
  - deterministic hard-negative selection;
  - hashes and dedupe keys;
  - no transcript persistence without consent;
  - label provenance is preserved.
- Unit-test journal behavior:
  - evaluation and per-segment feedback event serialization;
  - automatic deduplication;
  - export snapshot correctness;
  - export does not clear records;
  - explicit clear resets journal/index;
  - 100 MB boundary pauses writes without deleting records.
- Retain the real-device ONNX instrumentation test and add lifecycle/reuse coverage for model load, cached inference, cancellation, and release.
- Add Compose/UI tests for consent, chip visibility, zero-result review, per-span actions, range validation, export, post-export clear confirmation, and full-journal messaging.
- Exercise player golden paths on a device/emulator:
  - API match, API/model disagreement, API empty, offline saved segments, network failure, no captions, replay, queue advance, configuration change, background playback, PiP, casting, local playback, and player release/recreation.
  - Verify no model-only shadow span skips when API data exists.
  - Verify fallback spans still skip when the API has no usable result.
  - Confirm inference and file I/O never run on the main thread and playback starts without waiting for training-journal writes.
- Run `./gradlew ktlintCheck`, `:app:testGithubDebugUnitTest`, `:app:compileGithubDebugKotlin`, `:app:compileFossDebugKotlin`, and relevant flavor builds, including `:app:assembleGithubDebug`.
- Run `graphify update .` after implementation. Do not regenerate the baseline profile because the model remains lazy and this plan does not change cold-start initialization.

## Assumptions and Boundaries

- Standard videos means streamed VODs plus downloaded/local videos with compatible captions; Shorts, music playback, and true live streams are deferred.
- SponsorBlock must be enabled for shadow inference. Training consent alone does not activate inference when SponsorBlock is off.
- Manual JSONL export is the only data-delivery mechanism. There is no network uploader, backend endpoint, authentication, or background upload.
- Existing uncommitted sponsor-detection work is the implementation baseline and must be preserved; unrelated dirty changes must not be overwritten.
- No Room schema change, app-version bump, documentation edit, commit, push, or merge is included.

