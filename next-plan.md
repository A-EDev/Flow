# Flow Smart Segments
## Product, ML, Data, Android Integration, Evaluation and Rollout Specification

**Project:** `seshuthota/Flow`
**Feature:** Smart Segments
**Status:** Proposed
**Primary goal:** Evolve Flow’s existing on-device sponsor detection system into a general video-segment understanding system capable of identifying multiple semantic regions in YouTube videos and allowing the player to act on them.

---

# 1. Executive Summary

Flow currently contains an on-device sponsor detection system built around a fine-tuned Ettin 17M encoder.

The existing system already performs the difficult end-to-end workflow:

```text
Video
  ↓
Caption track
  ↓
Transcript reconstruction
  ↓
Tokenization / overlapping windows
  ↓
Ettin 17M ONNX inference
  ↓
BILOU span decoding
  ↓
Timestamp reconstruction
  ↓
Sponsor segment
  ↓
Player action
  ↓
Skip
```

Smart Segments generalizes this architecture from:

```text
"Where is the sponsor?"
```

to:

```text
"What type of content is occurring throughout this video?"
```

The intended long-term output is a semantic timeline such as:

```text
00:00 ─ 00:18    HOOK
00:18 ─ 01:04    INTRO
08:42 ─ 09:51    SPONSOR
18:11 ─ 18:27    INTERACTION
31:02 ─ 32:15    SELF_PROMO
47:20 ─ 48:02    OUTRO
```

Flow can then associate user-configurable behavior with each category.

Examples:

```text
SPONSOR       → auto skip
INTERACTION   → skip
SELF_PROMO    → show skip button
RECAP         → play at 2×
INTRO         → show timeline marker
FILLER        → marker only
```

The core requirement is that Smart Segments remain:

- primarily on-device;
- compatible with Flow's existing transcript pipeline;
- independent of large-scale YouTube scraping;
- incremental over the existing sponsor model;
- highly conservative around automatic skipping;
- isolated from Flow's YouTube/network layer so upstream changes remain mergeable.

---

# 2. Why Build This

SponsorBlock currently answers:

> “Where are portions of this video that I may want to skip?”

Smart Segments answers:

> “What is happening throughout this video, and how do I want Flow to treat each type of content?”

This moves Flow from being a player with SponsorBlock into a player that understands the structure of the content being consumed.

The feature should eventually enable other systems as well:

```text
Smart Segments
      │
      ├── semantic timeline
      ├── automatic skip
      ├── dynamic playback speed
      ├── smart chapters
      ├── focused viewing
      ├── video summaries
      ├── resume-with-context
      └── Dynamic Cut / Quick Watch
```

Smart Segments should therefore be designed as reusable infrastructure rather than as another SponsorBlock-specific feature.

---

# 3. Current Technical Baseline

The existing fork already contains substantial relevant infrastructure.

Important Android components include:

```text
app/src/main/java/io/github/aedev/flow/data/sponsordetection/
    OnDeviceSponsorDetector.kt
    SponsorCaptionLoader.kt
    SponsorDetectionCoordinator.kt
    SponsorDetectionData.kt
    SponsorFeedbackJournal.kt
    SponsorFeedbackModels.kt
    SponsorModelRepository.kt
    SponsorModelStore.kt
    SponsorTokenizer.kt
    SponsorTranscriptInference.kt
```

Existing playback integration includes:

```text
player/sponsorblock/SponsorBlockHandler.kt
```

There are also existing model settings, review UI, unit tests, Android instrumentation tests and performance benchmarks.

The existing ML workspace is:

```text
ml/sponsor_detection/
```

The current production candidate is based on:

```text
jhu-clsp/ettin-encoder-17m
```

using token-level BILOU classification.

Current output labels are effectively:

```text
O
B-SPONSOR
I-SPONSOR
L-SPONSOR
U-SPONSOR
```

The model is already exported to ONNX and executed locally through ONNX Runtime.

The current combined sponsor model uses category-specific calibration with a sponsor confidence threshold around the current production configuration rather than relying on raw classifier confidence.

Smart Segments MUST preserve sponsor-detection quality while adding additional categories.

---

# 4. Product Scope

## 4.1 Smart Segments V1

V1 should intentionally be narrow.

Initial semantic categories:

```text
SPONSOR
SELF_PROMO
INTERACTION
```

Definitions:

### SPONSOR

Externally paid sponsorship or advertisement integrated into the creator's video.

Example:

```text
"This video is sponsored by..."
```

### SELF_PROMO

Promotion of the creator's own products, services, courses, Patreon, merchandise, newsletter, events or related properties.

Example:

```text
"If you want the full course, check out..."
```

### INTERACTION

Requests to like, subscribe, comment, enable notifications, follow, share or otherwise interact with the channel/video.

Example:

```text
"Remember to subscribe and hit the bell."
```

Everything not confidently belonging to one of these categories remains:

```text
CONTENT
```

`CONTENT` does not need its own explicit span head in V1.

It is effectively:

```text
no active semantic segment
```

---

# 5. Future Categories

Once V1 is stable, expand to:

```text
PREVIEW_RECAP
HOOK
INTRO
OUTRO
MUSIC_OFFTOPIC
FILLER
```

These must NOT all be introduced into the first model simultaneously.

The difficulty varies considerably.

### PREVIEW_RECAP

Examples:

```text
"Previously..."
"Coming up..."
"Here's what we're going to cover..."
```

Likely achievable primarily from text.

### HOOK

Opening teaser designed to capture attention.

Often transcript-detectable but less objectively labeled.

### INTRO

Creator intro or branded introduction.

Some examples contain speech, but many intros are music/animation only.

A transcript-only model cannot reliably detect silent visual intros.

### OUTRO

Creator closing section.

Text is useful, but relative video position is extremely informative.

### MUSIC_OFFTOPIC

Sections containing non-primary music content.

This likely requires audio information eventually.

### FILLER

Tangents or unnecessary material.

This is subjective and must be treated conservatively.

FILLER must initially be:

```text
MARKER ONLY
```

and must never default to automatic skipping.

---

# 6. User Experience

Smart Segments should appear as a first-class player feature.

## 6.1 Timeline

Detected categories should be represented on the seek bar.

Example:

```text
0:00                                                   52:00
|────HOOK──|──────────────CONTENT──────────────|SP|──────────|
```

Each category may use its own visual style.

The user should be able to tap a segment marker and see:

```text
Sponsor
08:42 – 09:51
Detected on device
Confidence: High
```

Avoid exposing raw probability unless developer/debug mode is enabled.

---

# 7. Per-Category Actions

Introduce:

```kotlin
enum class SmartSegmentAction {
    AUTO_SKIP,
    SHOW_SKIP_BUTTON,
    SPEED_UP,
    SHOW_MARKER,
    IGNORE
}
```

Potential future action:

```text
MUTE
```

where applicable.

Configuration example:

```text
Smart Segments

Sponsors
Auto skip

Self promotion
Show skip button

Like / subscribe reminders
Auto skip

Recaps
Play faster

Hooks
Show marker
```

---

# 8. Conservative Defaults

Existing SponsorBlock behavior must remain unchanged unless explicitly migrated by the user.

For new ML-generated categories, initial defaults should be conservative:

```text
SPONSOR       existing behavior
SELF_PROMO    SHOW_MARKER / SHOW_SKIP_BUTTON
INTERACTION   SHOW_SKIP_BUTTON
PREVIEW       SHOW_MARKER
HOOK          SHOW_MARKER
INTRO         SHOW_MARKER
OUTRO         SHOW_MARKER
FILLER        SHOW_MARKER
```

Automatic skipping should be an explicit user choice until sufficient validation exists.

This prevents Smart Segments from silently removing real content because of an incorrect prediction.

---

# 9. Core ML Architecture

Do NOT train a new encoder from scratch.

Do NOT throw away the existing sponsor model.

Use the current trained Ettin 17M model as the starting checkpoint.

The desired architecture is:

```text
                     Ettin 17M
                  shared encoder
                       │
             token hidden states
                       │
             ┌─────────┼─────────┐
             ▼         ▼         ▼
          SPONSOR   SELF_PROMO INTERACTION
             │         │         │
          BILOU      BILOU      BILOU
```

Additional category heads are added later.

---

# 10. Why Not Use One Multiclass BILOU Head

A normal multiclass classifier such as:

```text
O
B-SPONSOR
B-SELF_PROMO
B-INTERACTION
...
```

forces every token to belong to exactly one category.

That becomes problematic when categories overlap or when category boundaries are ambiguous.

Instead Smart Segments should support independent categories.

Conceptually:

```text
token
 ├ sponsor        → O / B / I / L / U
 ├ selfpromo      → O / B / I / L / U
 └ interaction    → O / B / I / L / U
```

This allows:

```text
SPONSOR = positive
SELF_PROMO = positive
```

on the same token if necessary.

---

# 11. Efficient Multi-Head Implementation

The Android model should not literally require many separate neural networks.

The implementation can use a single output projection:

```text
hidden_state
    ↓
Linear(hidden_size, category_count × 5)
    ↓
reshape
    ↓
[batch, sequence, category, BILOU]
```

For three categories:

```text
output dimension = 3 × 5 = 15
```

This adds negligible parameter count compared with the 17M parameter encoder.

Expected ONNX model-size increase should therefore be minimal.

---

# 12. Model Initialization

Start from the existing combined sponsor checkpoint.

Reuse:

```text
Ettin encoder weights
Sponsor classifier weights
Tokenizer
Normalization
Windowing logic
```

Initialize new heads randomly:

```text
SELF_PROMO
INTERACTION
```

Optionally use a higher learning rate for new heads and a lower learning rate for the pretrained encoder.

Example conceptual training configuration:

```text
encoder_lr     = low
sponsor_lr     = low
new_head_lr    = higher
```

The goal is continual/multi-task fine-tuning rather than full retraining.

---

# 13. Preventing Sponsor Regression

Sponsor detection is already working.

Smart Segments is not successful if the new model reduces sponsor quality substantially.

Every model candidate must therefore run against the existing frozen sponsor evaluations.

Required comparison:

```text
CURRENT SPONSOR MODEL
vs
SMART SEGMENTS MODEL
```

Evaluate:

```text
sponsor precision
sponsor recall
sponsor span F1
temporal IoU
boundary error
false-positive seconds/hour
missed sponsor seconds/hour
```

Sponsor regression should block release.

---

# 14. Data Problem

The SponsorBlock mirror contains an enormous number of semantic labels but does not contain video transcripts.

Therefore:

```text
SponsorBlock label count ≠ usable training-example count
```

Smart Segments MUST NOT depend on downloading transcripts for millions of YouTube videos.

Large-scale caption scraping is explicitly outside the project architecture.

---

# 15. Existing Data Sources

Smart Segments should primarily use data already available locally or through public datasets that already contain transcript text.

Primary sources:

```text
1. Existing Xenova sponsorblock dataset
2. ScriptSmith sponsorblock YouTube metadata/transcript dataset
3. Local SponsorBlock mirror
4. Existing manually reviewed Flow benchmark data
5. Future user-generated correction data
```

No large new transcript acquisition campaign is required for V1.

---

# 16. SponsorBlock Mirror

Current local mirror contains approximately:

```text
21.6M total annotations
```

with categories including approximately:

```text
SPONSOR          5.22M
INTRO            3.72M
FILLER           2.99M
OUTRO            2.88M
SELF_PROMO       2.15M
INTERACTION      1.71M
PREVIEW          730K
MUSIC_OFFTOPIC   553K
HOOK              85K
```

The important insight is that the current pipeline deliberately extracts only:

```toml
category = "sponsor"
```

from this source.

Smart Segments must generalize this pipeline.

---

# 17. ScriptSmith Dataset

The existing training work already uses the ScriptSmith dataset containing tens of thousands of videos with timestamped reconstructed auto-captions.

These transcripts can be joined locally against the SponsorBlock mirror by:

```text
video_id
```

Example:

```text
ScriptSmith
video_id = ABC
timestamped transcript

+

SponsorBlock mirror
video_id = ABC
category = interaction
start = 43.1
end = 48.4

↓

training example

INTERACTION
43.1s – 48.4s
"before we continue remember to subscribe..."
```

No YouTube request is required during this operation.

---

# 18. Data Audit — Mandatory First Step

Before any new training occurs, implement two reproducible Smart Segments audits.

These are Milestones 1A and 1B. Do not proceed to dataset construction or model training until both reports exist and the resulting data supports a Go decision.

## 18.1 Audit A — Raw Data Availability

This audit is independent of model preprocessing. It calculates the intersection between transcript-bearing dataset snapshots and eligible SponsorBlock annotations.

Required output:

```text
smart_segments_raw_data_audit.json
```

| Category | Transcript Videos | Segments | Positive Duration |
|---|---:|---:|---:|
| Sponsor | | | |
| Self promo | | | |
| Interaction | | | |
| Preview | | | |
| Hook | | | |
| Intro | | | |
| Outro | | | |
| Filler | | | |

Also report:

```text
unique channels
unique videos
median segment length
P95 segment length
segments per video
overlapping-category counts
videos with multiple categories
caption coverage
auto-caption vs manual-caption counts where available
```

The report must record:

```text
SponsorBlock snapshot path and SHA-256
ScriptSmith dataset revision and snapshot hashes
metadata snapshot hashes
eligibility-policy version and complete configuration
transcript-reconstruction version
language policy
audit code version
```

## 18.2 Audit B — Trainability

This audit applies the exact proposed normalization, tokenization, windowing and timestamp-alignment pipeline to the eligible records from Audit A.

Required output:

```text
smart_segments_trainability_audit.json
```

For each category, report:

```text
aligned and unaligned intervals
alignment rejection reasons
positive windows
unknown windows
category-confirmed negative windows
positive tokens and duration
window and token imbalance
truncated or boundary-crossing spans
```

The report must additionally record:

```text
tokenizer revision and SHA-256
normalization version
maximum token count
window overlap
alignment algorithm version
window-builder version
all input audit and artifact hashes
```

Candidate-window counts belong only in Audit B. They must never be reported without the preprocessing configuration that produced them.

---

# 19. New Dataset Schema

Create a canonical generalized semantic-segment annotation structure. This is the only normalized annotation input that downstream Xenova and ScriptSmith builders may consume.

Conceptually:

```text
SmartSegmentAnnotation

video_id
segment_id
category
start_ms
end_ms
votes
locked
hidden
shadow_hidden
action_type
video_duration_ms
is_eligible
source
```

Output:

```text
smart_segment_annotations.parquet
```

Required flow:

```text
raw SponsorBlock mirror
        ↓
canonical annotation normalization
        ↓
smart_segment_annotations.parquet
        ├──────────────┐
        ↓              ↓
Xenova builder   ScriptSmith builder
        └───────┬──────┘
                ↓
        training datasets
```

Duplicate or overlapping intervals may be normalized within one category according to an explicit, versioned policy. Cross-category overlaps must always survive dataset construction.

For example, both of these annotations must be retained:

```text
42.0–55.0 SPONSOR
49.0–52.0 INTERACTION
```

Do not destroy or replace the existing sponsor-only dataset.

Sponsor-only training must remain reproducible.

---

# 20. Transcript-Aligned Dataset

After joining transcripts and annotations, create:

```text
SmartSegmentTrainingExample

example_id
video_id
channel_id
text
window_start_ms
window_end_ms

category_spans:
    SPONSOR:
        start_char
        end_char
        start_ms
        end_ms

    SELF_PROMO:
        ...

    INTERACTION:
        ...

category_supervision:
    SPONSOR:
        state: POSITIVE | NEGATIVE_CONFIRMED | UNKNOWN
        evidence_source
        evidence_id

    SELF_PROMO:
        ...

    INTERACTION:
        ...

source
split
```

Store train/validation/test independently.

Every stored span must retain its category and canonical annotation identifier. Builders must not collapse configured positive categories into a sponsor-only field or destructively deduplicate overlaps across different categories.

---

# 21. Unknown Labels Are Critical

An unlabeled region in SponsorBlock is NOT necessarily negative.

For example:

```text
No INTERACTION annotation
```

does not prove that the creator never said:

```text
"Like and subscribe."
```

It may simply mean nobody submitted the segment.

Therefore supervision is independently defined for every token and category. Each category needs three states:

```text
POSITIVE
NEGATIVE_CONFIRMED
UNKNOWN
```

UNKNOWN tokens must not be blindly treated as negative.

Example:

| Evidence | SPONSOR | SELF_PROMO | INTERACTION |
|---|---|---|---|
| Known sponsor | POSITIVE | UNKNOWN | UNKNOWN |
| Known self promotion | UNKNOWN | POSITIVE | UNKNOWN |
| Known interaction | UNKNOWN | UNKNOWN | POSITIVE |
| Human-reviewed: no sponsor | NEGATIVE_CONFIRMED | UNKNOWN | UNKNOWN |

The presence of one category never establishes the absence of another category. Smart Segments V1 is a positive-unlabeled, multi-label sequence-learning problem unless stronger category-specific evidence exists.

---

# 22. Training Mask

The training target should therefore support a per-category mask.

Conceptually:

```text
labels[token][category]
loss_mask[token][category]
```

Examples:

```text
known positive
loss_mask = 1

known negative
loss_mask = 1

unknown
loss_mask = 0
```

UNKNOWN examples contribute no loss for that category.

This is essential to avoid teaching the model that missing SponsorBlock annotations are ground truth negatives.

---

# 23. Sources of Known Negatives

`NEGATIVE_CONFIRMED(category)` requires evidence specifically establishing the absence of that category.

Valid sources may include:

```text
existing curated negatives whose contract explicitly covers that category
complete videos or spans manually reviewed for that category
explicit human rejection or correction for that category
datasets whose annotation contract guarantees exhaustive coverage for that category
synthetic augmentation used cautiously and identified as synthetic
```

An annotation for a different category is not a confirmed negative. A window containing an INTERACTION marker remains UNKNOWN for SPONSOR and SELF_PROMO unless independent evidence says otherwise.

Every confirmed negative must carry its category, evidence source and review/provenance identifier. Synthetic data must never be used as the evaluation benchmark.

---

# 24. Data Splitting

Continue the strong leakage controls already present in the sponsor project.

Splits must be:

```text
video-disjoint
channel-disjoint
```

where channel information exists.

Campaign leakage prevention should remain for sponsors.

Where possible also detect repeated or near-identical promotional text across videos.

The test set must be frozen before model selection.

---

# 25. Position Features

Text alone is insufficient for several future categories.

Examples:

```text
INTRO
OUTRO
HOOK
```

are strongly correlated with relative position.

Phase 2 should add lightweight temporal features such as:

```text
relative video position
cue duration
gap before cue
gap after cue
video duration
```

For example:

```text
relativePosition = timestamp / videoDuration
```

A sentence like:

```text
"thanks for watching"
```

at:

```text
relativePosition = 0.98
```

is far more likely to be an outro than the same sentence at 0.20.

---

# 26. Multimodal Future

Smart Segments V1 remains transcript-based.

Later versions may incorporate:

```text
audio transitions
music detection
silence
scene changes
sparse video frames
visual title cards
```

This will be useful for:

```text
silent intros
animated intros
music interludes
visual outros
non-spoken transitions
```

Do NOT add multimodal processing to V1.

First validate the product with text.

---

# 27. Inference Pipeline

The runtime should retain the current efficient streaming behavior.

Desired flow:

```text
captions
   ↓
transcript
   ↓
normalize
   ↓
tokenize incrementally
   ↓
create overlapping windows
   ↓
prioritize windows near current playhead
   ↓
ONNX inference
   ↓
decode category BILOU spans
   ↓
map spans to transcript timestamps
   ↓
stitch across windows
   ↓
publish provisional Smart Segments
   ↓
continue processing remaining video
```

The user should not need to wait for the complete transcript inference before early segments become actionable.

---

# 28. Runtime Result Contract

Introduce a general model:

```kotlin
data class SmartSegment(
    val id: String,
    val videoId: String,
    val category: SmartSegmentCategory,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float,
    val source: SmartSegmentSource
)
```

Categories:

```kotlin
enum class SmartSegmentCategory {
    SPONSOR,
    SELF_PROMO,
    INTERACTION,
    PREVIEW_RECAP,
    HOOK,
    INTRO,
    OUTRO,
    MUSIC_OFFTOPIC,
    FILLER
}
```

Not every category must be enabled by the first model.

---

# 29. Segment Source

Track where each segment came from:

```kotlin
enum class SmartSegmentSource {
    SPONSORBLOCK_API,
    ON_DEVICE_MODEL,
    USER_CORRECTION
}
```

Potential future:

```text
CREATOR_CHAPTER
REMOTE_MODEL
```

Source tracking is important for:

```text
debugging
evaluation
feedback
priority resolution
```

---

# 30. Conflict Resolution

A segment may exist from both:

```text
SponsorBlock API
and
on-device model
```

Define precedence.

For SPONSOR:

```text
trusted SponsorBlock API interval
        ↓
prefer API interval

model-only interval
        ↓
use model prediction if above threshold
```

The model may still run for evaluation even when SponsorBlock already has a segment.

That preserves the existing feedback/evaluation flywheel.

This source precedence does not resolve simultaneous semantic categories. Source reconciliation and player-action arbitration are separate operations; reconciliation must not delete valid semantic overlaps.

## 30.1 Action Arbitration Contract

Define the action state machine before Android shadow integration, even though user-visible actions are implemented later.

```text
SmartSegmentDetector
        ↓
semantic spans
        ↓
SmartSegmentActionResolver
        ↓
one effective player state
```

The resolver consumes active semantic segments without modifying them. It must define and test:

```text
same-category interval merging
cross-category simultaneous intervals
action priority
touching and overlapping skip ranges
skip re-arming after rewinds
seeking directly into a segment
entering or leaving multiple segments in one position update
speed-override precedence
restoring the user's actual prior speed rather than 1.0×
the user changing speed while an override is active
idempotence and seek-loop prevention
```

Example:

```text
active semantic segments:
RECAP
SELF_PROMO
SPONSOR

        ↓

effective action:
AUTO_SKIP sponsor
```

Action policy is user configuration. It must not be embedded in the model, decoder or semantic-span reconciliation logic.

---

# 31. Category Calibration

Do NOT use one confidence threshold globally.

Calibrate independently:

```text
SPONSOR        threshold A
SELF_PROMO     threshold B
INTERACTION    threshold C
PREVIEW        threshold D
...
```

The classifier must export category-specific decoder configuration.

Example:

```json
{
  "sponsor": {
    "confidence_threshold": 0.70
  },
  "self_promo": {
    "confidence_threshold": 0.82
  },
  "interaction": {
    "confidence_threshold": 0.86
  }
}
```

Numbers above are illustrative only.

Actual thresholds come from validation data.

---

# 32. Display Threshold vs Action Threshold

Eventually support two thresholds per category:

```text
display threshold
automatic-action threshold
```

Example:

```text
SELF_PROMO

>= 0.75
show timeline marker

>= 0.95
eligible for automatic action
```

This lets Smart Segments expose useful uncertain detections without automatically acting on them.

---

# 33. Player Architecture

Long term, avoid coupling semantic intelligence to:

```text
SponsorBlockHandler
```

Introduce:

```text
SmartSegmentHandler
SmartSegmentActionResolver
```

responsible for:

```text
active segments
player position tracking
segment entry/exit
actions
skip events
speed changes
markers
feedback context
```

However, this refactor should happen incrementally.

Do not rewrite existing SponsorBlock playback behavior before the new ML model is validated.

---

# 34. Migration Strategy

Phase A:

```text
existing SponsorBlockHandler
existing sponsor model

+

new Smart Segments model in shadow mode
```

No player behavior changes.

Phase B:

```text
SmartSegmentHandler
    ↓
SPONSOR adapter
    ↓
existing skip behavior
```

Phase C:

```text
additional category actions
```

This allows sponsor behavior to remain stable throughout development.

---

# 35. Shadow Mode

Before exposing Smart Segments publicly, run new categories in shadow mode.

Flow performs inference but does not act.

Store:

```text
predictions
confidence
timestamps
category
comparison with SponsorBlock where applicable
```

In-memory debug display and persistent collection are separate capabilities. Persistent storage of video identifiers, predictions or transcript context requires an explicit policy covering user consent, retention duration, size limits, deletion, export and app-data clearing. Existing sponsor-training consent semantics must not be weakened during migration.

Developer/debug UI can display them.

This gives us real-device testing without risking accidental skipping.

---

# 36. Feedback System

Extend the existing sponsor review/feedback infrastructure.

For every prediction:

```text
SELF_PROMO
12:41 – 13:03
```

allow:

```text
Correct
Wrong
Wrong category
Fix start/end
```

Wrong category should allow:

```text
SPONSOR
SELF_PROMO
INTERACTION
PREVIEW
INTRO
OUTRO
FILLER
CONTENT
```

---

# 37. Feedback Data Contract

Feedback event should contain:

```text
video_id
model_version
category
predicted_start_ms
predicted_end_ms
predicted_confidence

verdict

corrected_category
corrected_start_ms
corrected_end_ms

local transcript window
timestamp
```

Store locally.

Do not upload automatically.

Any future training-data export must remain explicit.

---

# 38. User-Generated Training Flywheel

Long-term:

```text
base public datasets
       +
SponsorBlock weak labels
       +
personal corrections
       ↓
Smart Segments v2
```

The highest-quality examples will likely become user corrections because they contain explicit:

```text
correct category
correct boundaries
real production transcript
```

---

# 39. Evaluation Dataset

The training data is weakly supervised.

Evaluation must NOT simply compare the model against the same SponsorBlock labels it learned from.

Create a manually reviewed, frozen, channel-disjoint Smart Segments benchmark.

V1 target:

```text
SPONSOR
SELF_PROMO
INTERACTION
CONTENT-heavy negatives
```

The reviewer must examine complete transcripts/videos rather than only reviewing existing labeled intervals.

---

# 40. Evaluation Metrics

Report per category:

```text
video presence precision
video presence recall
video presence F1

span precision
span recall
span F1

temporal IoU 0.3
temporal IoU 0.5
temporal IoU 0.7

boundary error
false-positive seconds/hour
missed-category seconds/hour
```

For categories supporting automatic skip, precision matters more than recall.

Missing one interaction reminder is acceptable.

Skipping genuine content incorrectly is much worse.

---

# 41. Automatic Action Release Gate

A category should not receive a recommended/default automatic action merely because model F1 is high.

Automatic actions require very high independently reviewed precision.

Before inspecting candidate test results, freeze an automatic-action contract for each eligible category:

```text
benchmark dataset SHA-256
minimum reviewed video hours
minimum positive span count
required lower confidence-interval bound for precision
maximum false-positive seconds/hour
boundary-error limit where relevant
confidence-interval method
```

The numerical values should be chosen after the audit establishes how large a credible benchmark can be, but they must be committed before candidate test evaluation. Point estimates alone are insufficient.

Until then:

```text
SHOW_MARKER
or
SHOW_SKIP_BUTTON
```

should be preferred.

Especially:

```text
FILLER
HOOK
INTRO
```

must remain non-destructive until strong evidence supports automation.

---

# 42. Sponsor Parity Gate

First produce and freeze:

```text
SponsorBaselineContract

model_sha256
tokenizer_sha256
decoder_config_sha256
benchmark_dataset_sha256
evaluation_code_version
precision
recall
span_f1
false_positive_seconds_per_hour
missed_sponsor_seconds_per_hour
boundary_error
confidence_intervals
```

Then, before inspecting Smart Segments candidate test results, freeze a numerical non-inferiority contract defining the permitted delta and confidence-interval rule for every blocking metric.

Before replacing the current sponsor model:

```text
Smart Segments SPONSOR head
```

must pass that frozen non-inferiority contract on the frozen sponsor benchmark.

If it does not:

```text
keep existing sponsor model
+
run Smart Segments only for new categories
```

There is no requirement that one model must immediately replace everything.

Correctness is more important than architectural elegance.

---

# 43. Performance Requirements

Multi-category inference must remain practical on Android.

Target:

```text
single shared encoder pass
```

not one Ettin inference per category.

The additional heads should add negligible compute relative to the encoder.

Performance regression targets:

```text
model size increase: minimal
inference latency increase: <10% target
peak memory increase: <10% target
```

These are engineering targets rather than release guarantees.

Before performance becomes a release gate, freeze the device tiers, Android/runtime versions, input workloads, warm/cold-session conditions, percentile statistics and numerical limits. Model size, latency and memory results without this benchmark context are not comparable.

Use the existing:

```text
OnDeviceSponsorBenchmarkTest
```

as the basis for a future:

```text
OnDeviceSmartSegmentsBenchmarkTest
```

---

# 44. Caching

Cache inference by:

```text
videoId
transcriptSha256
modelSha256
decoderVersion
```

A cached result should contain all category spans.

Changing:

```text
category action preferences
```

must not invalidate inference.

Changing:

```text
model
transcript
decoder
```

should invalidate it.

---

# 45. Model Distribution

Retain external, hash-pinned model download, but deliberately migrate from the current application-known artifact to a self-describing Smart Segments model bundle.

The model package must remain hash pinned.

Bundle contents:

```text
model file
tokenizer
decoder config
model manifest
```

The manifest itself must be obtained from a pinned revision and verified against an application-pinned SHA-256 before any manifest-provided hashes are trusted. The manifest then pins every other file in the bundle.

Downloads must remain staged, verified and atomically promoted. A failed update must leave the previously installed compatible bundle usable.

---

# 46. Model Schema Versioning

The model artifact is an ABI between the ML exporter and Android. Introduce a versioned contract such as:

```json
{
  "schema_version": 1,
  "model_version": "smart-segments-v1",
  "model": {
    "file": "smart_segments.int8.ort",
    "sha256": "..."
  },
  "tokenizer": {
    "file": "tokenizer.json",
    "sha256": "..."
  },
  "inputs": {
    "input_ids": {
      "dtype": "int64",
      "shape": ["batch", "sequence"]
    },
    "attention_mask": {
      "dtype": "int64",
      "shape": ["batch", "sequence"]
    }
  },
  "outputs": {
    "segment_logits": {
      "dtype": "float32",
      "shape": ["batch", "sequence", "category", "bilou"]
    }
  },
  "categories": {
    "0": "sponsor",
    "1": "self_promo",
    "2": "interaction"
  },
  "labels": {
    "0": "O",
    "1": "B",
    "2": "I",
    "3": "L",
    "4": "U"
  },
  "normalization_version": 1,
  "decoder_version": 1,
  "decoder_config_sha256": "...",
  "minimum_android_runtime_version": 1
}
```

The exact JSON representation may change, but the contract must define:

```text
input and output names
dtypes
tensor ranks and axis order
ordered category-to-axis mapping
ordered BILOU label mapping
normalization version
tokenizer and model hashes
decoder version and configuration hash
model version
minimum compatible Android runtime
```

Android must validate the pinned manifest, every artifact hash, supported schema/runtime versions, ORT input signatures and output rank/layout before inference. It must reject incompatible bundles safely rather than relying only on a fixed `LABEL_COUNT` or silently interpreting an unknown tensor layout.

---

# 47. Settings

Add a Smart Segments settings section.

Proposed screen:

```text
Smart Segments

[✓] Enable Smart Segments

Sponsors
Auto skip

Self promotion
Show skip button

Interaction reminders
Show skip button

Show Smart Segments on timeline
[✓]

Run detection on device
[✓]

Help improve Smart Segments
[ ]
```

Developer/debug options may expose:

```text
confidence
model version
segment source
inference time
window count
raw predictions
```

---

# 48. Failure Behavior

Smart Segments must fail silently and safely.

If:

```text
model unavailable
caption unavailable
unsupported language
inference crashes
tokenizer mismatch
model hash mismatch
```

then:

```text
normal playback continues
```

Existing SponsorBlock API behavior must remain functional.

Smart Segments must never make playback dependent on successful ML inference.

---

# 49. Language Scope

V1 remains:

```text
English
```

Do not pretend the model supports arbitrary languages.

If captions are unsupported:

```text
Smart Segments unavailable
```

rather than generating unreliable classifications.

Multilingual support can become a separate model/data project.

---

# 50. Transcript Acquisition Policy

Smart Segments must NOT depend on:

```text
bulk YouTube transcript crawling
IP rotation
proxy farms
large background caption scraping
```

Primary training should use transcript-bearing datasets already available.

Runtime inference should primarily operate on captions Flow already retrieves as part of normal user playback.

This keeps the custom intelligence layer largely post-acquisition.

---

# 51. Opportunistic Local Dataset

Later, Flow may optionally retain transcripts for videos the user actually watches.

This should happen only through normal playback behavior.

Potential future local pipeline:

```text
user watches video
       ↓
Flow obtains captions normally
       ↓
Smart Segments inference
       ↓
user corrects prediction
       ↓
store small labeled example locally
```

This is not a crawler.

---

# 52. Recommended Repository Organization

Do not immediately rename every existing sponsor package.

Large renames make upstream merging unnecessarily painful.

Near-term:

```text
data/sponsordetection/
```

remains intact while Smart Segments is experimentally introduced.

Add generalized abstractions around it.

Target long-term structure:

```text
data/intelligence/
    transcript/
    segments/
        SmartSegment.kt
        SmartSegmentCategory.kt
        SmartSegmentPolicy.kt

    inference/
        SmartSegmentDetector.kt
        SmartSegmentDecoder.kt

    feedback/
        SmartSegmentFeedback.kt
```

The existing sponsor code can gradually become a compatibility layer.

---

# 53. ML Repository Organization

Likewise, avoid immediately deleting:

```text
ml/sponsor_detection/
```

First generalize the tooling.

Possible final target:

```text
ml/smart_segments/
    config/
    data/
    reports/
    src/smart_segments/
    tests/
```

Migration should happen in a dedicated commit after the first Smart Segments model proves viable.

This makes debugging easier.

---

# 54. Exact Initial Engineering Work

## Milestone 0 — Establish Upstream Sync Explicitly

The canonical upstream is:

```text
https://github.com/A-EDev/Flow.git
```

Configure and fetch it explicitly, then compare the fork's `main` with `upstream/main`.

```bash
git remote add upstream https://github.com/A-EDev/Flow.git
git fetch upstream
```

Do not treat synchronization with `origin/main` as synchronization with upstream. Do not routinely rebase already-published `main` history containing the ML work.

Use a dedicated sync branch:

```text
upstream/main
       ↓
sync/upstream-YYYYMMDD
       ↓
resolve conflicts, build and test
       ↓
main
```

Record the upstream commit, fork merge base, sync strategy and validation performed.

---

## Milestone 1A — Raw Multicategory Data Audit

Implement the model-independent raw-data audit defined in Section 18.1.

Inputs:

```text
SponsorBlock mirror
ScriptSmith transcripts
Xenova data
existing metadata
eligibility and language policies
```

Output:

```text
smart_segments_raw_data_audit.json
```

No training and no model-dependent window counts.

---

## Milestone 1B — Trainability and Window Audit

Apply the exact proposed preprocessing pipeline to the records counted by Audit A.

Output:

```text
smart_segments_trainability_audit.json
```

The two audits form the Go/No-Go gate. Their manifests must contain all source hashes, configuration and implementation versions needed to reproduce every count. Audit code may use read-only source adapters before the canonical artifact exists; Milestone 3 must later reproduce the accepted audit counts from the canonical annotation input within explicitly documented tolerances.

---

# 55. Milestone 2 — Canonical Multicategory Annotation Layer

Generalize annotation normalization to produce:

```text
smart_segment_annotations.parquet
```

The canonical artifact must retain category, timestamps, action type, quality metadata, eligibility, provenance and cross-category overlaps. Maintain the original sponsor-only builder and its outputs for reproducibility.

---

# 56. Milestone 3 — Canonical Annotation Consumers

Refactor the Xenova and ScriptSmith builders to consume the canonical annotation parquet rather than independently interpreting the raw SponsorBlock mirror.

Generalize transcript alignment from:

```text
transcript + sponsor timestamp → sponsor span
```

to:

```text
transcript + (category, timestamp) → semantic span
```

Produce V1 categories only:

```text
SPONSOR
SELF_PROMO
INTERACTION
```

Add regression tests proving category identity is preserved and cross-category overlaps are not removed.

---

# 57. Milestone 4 — Supervision Contract

Implement category-specific:

```text
POSITIVE
NEGATIVE_CONFIRMED
UNKNOWN
```

and per-token, per-category loss masks. Require provenance for every confirmed negative. Add tests proving that a positive annotation for one category leaves unrelated categories UNKNOWN.

---

# 58. Milestone 5 — Frozen Dataset and Manual Benchmark

Build hash-pinned, channel-disjoint:

```text
train.parquet
validation.parquet
test.parquet
```

with category spans, masks, evidence provenance and leakage reports.

Separately construct and freeze a manually reviewed, full-video benchmark covering all V1 categories and content-heavy negatives. The test and manual benchmark labels must be frozen before candidate model selection.

---

# 59. Milestone 6 — Freeze Measurable Release Gates

Run the current sponsor model on the frozen sponsor benchmark and create `SponsorBaselineContract`.

Before inspecting candidate test results, commit:

```text
sponsor non-inferiority margins and confidence-interval rules
per-category model evaluation rules
automatic-action eligibility rules
performance device matrix and percentile limits
```

Do not use qualitative requirements such as "high precision" or "production-quality parity" as release gates.

---

# 60. Milestone 7 — Multi-Head Model and Export

Modify the Ettin stack using the current combined sponsor checkpoint. Preserve the sponsor classifier weights, initialize new category parameters, and train with category-specific loss masking.

Export the versioned multi-category output and verify prediction parity within a frozen tolerance across:

```text
PyTorch/Transformers
FP32 ONNX
INT8 ONNX
```

Evaluate against the frozen contracts. A candidate that fails sponsor non-inferiority must not replace the sponsor model.

---

# 61. Milestone 8 — Artifact Manifest and Android Loader Migration

Produce the self-describing, hash-pinned model bundle and manifest defined in Sections 45 and 46.

Migrate Android download, storage and runtime loading so it validates the manifest, input/output signatures, axis mappings, artifact hashes and runtime compatibility before inference. Preserve the existing sponsor model path until the new path has passed integration and physical-device performance tests.

---

# 62. Milestone 9 — Android Shadow Mode and Timeline

Add `OnDeviceSmartSegmentDetector` or a generalized equivalent.

Do not change playback behavior. Publish predictions for debug inspection and render new categories as markers only. Persist predictions or transcript context only under the defined consent, retention and deletion policy.

---

# 63. Milestone 10 — Action-Arbitration Engine

Implement and unit-test `SmartSegmentActionResolver` against the contract in Section 30.1 before enabling new actions in the player.

Integrate it incrementally behind shadow/debug controls. Verify rewinds, seeks, touching and overlapping intervals, simultaneous categories, user speed changes and restoration of prior player state.

---

# 64. Milestone 11 — User-Visible Actions and Feedback

After the resolver and category release gates pass, enable eligible per-category actions:

```text
SHOW_SKIP_BUTTON
AUTO_SKIP
SPEED_UP
IGNORE
```

Sponsor retains existing behavior unless its migration gate passes. New categories remain conservative by default.

Extend review tooling for correct, wrong, boundary correction and category correction, and create an explicitly exportable local feedback dataset.

---

# 65. Milestone 12 — V2 Categories

Only after V1 is validated, investigate `PREVIEW_RECAP`, `HOOK`, `INTRO` and `OUTRO`, adding relative-position features where justified.

---

# 66. Milestone 13 — V3 / Experimental Categories

Investigate `FILLER` and `MUSIC_OFFTOPIC`, potentially with audio/video features. Do not default these to automatic skipping.

---

# 67. Acceptance Criteria — V1

Smart Segments V1 is considered successful when the frozen contracts define numerical gates for applicable claims and all of the following hold:

```text
1. The SPONSOR head passes the precommitted SponsorNonInferiorityGate, including its confidence-interval rules.

2. SELF_PROMO passes its precommitted benchmark gate as a distinct semantic category.

3. INTERACTION passes its precommitted benchmark gate as a distinct semantic category.

4. All three categories can coexist in the same model.

5. One shared encoder inference produces all category predictions.

6. Results are deterministically mapped to playback timestamps under the versioned alignment contract.

7. Smart Segments can be rendered on the timeline.

8. New categories can be assigned independent user actions through the tested action resolver.

9. Inference does not block playback and passes the frozen physical-device performance contract.

10. Missing captions/model failures degrade gracefully.

11. Model training does not require large-scale new YouTube transcript scraping.

12. Audits, datasets, evaluation contracts and model artifacts remain reproducible and hash pinned.

13. User corrections can be stored locally for future retraining.
```

---

# 68. Explicit Non-Goals for V1

Do NOT include:

```text
full multimodal video understanding
frame-level computer vision
audio embeddings
all SponsorBlock categories
multilingual support
cloud inference requirement
automatic filler deletion
background YouTube crawling
replacement of Flow's playback stack
replacement of NewPipe/InnerTube
large UI redesign
```

Smart Segments should remain an isolated intelligence feature.

---

# 69. Product Evolution

The strategic reason to build Smart Segments is larger than skipping interaction reminders.

Once Flow has:

```text
semantic video timeline
```

other features become much easier.

For example:

### Smart Chapters

```text
semantic structure
+
transcript topics
→ meaningful chapters
```

### Resume With Context

```text
watch position
+
segments before position
+
transcript
→ "Previously..."
```

### Focused Watch

```text
user intent
+
semantic segments
→ alter playback
```

### Dynamic Cut

```text
segment importance
+
semantic structure
+
sponsor/selfpromo filtering
→ Full / Focused / Quick
```

### Ask This Video

Timestamp-aware transcript intelligence can cite the same semantic timeline.

Smart Segments should therefore be treated as foundational Flow intelligence infrastructure.

---

# 70. Final Architecture

Long-term:

```text
                         VIDEO
                           │
                           ▼
                       CAPTIONS
                           │
                           ▼
                     TRANSCRIPT
                           │
                ┌──────────┴──────────┐
                │                     │
                ▼                     ▼
         Smart Segments          Semantic AI
            Ettin 17M             future LLM
                │                     │
                ▼                     ▼
       temporal understanding     Q&A / summary
                │                     │
                └──────────┬──────────┘
                           │
                           ▼
                     FLOW PLAYER
                           │
           ┌───────────────┼────────────────┐
           ▼               ▼                ▼
          skip            seek             explain
           │               │                │
           └───────────────┼────────────────┘
                           ▼
                    PERSONAL FLOW UX
```

The important architectural boundary remains:

```text
YouTube acquisition
      ↓
Flow core
      ↓
stable transcript/player data
      ↓
OUR INTELLIGENCE LAYER
```

Smart Segments should not care whether Flow obtained the underlying YouTube data through NewPipe, InnerTube or another implementation in the future.

---

# 71. Immediate Next Task

Do NOT begin by retraining the model.

Do NOT begin by changing Android UI.

Do NOT begin by collecting more YouTube transcripts.

After Milestone 0 establishes the explicit upstream comparison, the first feature implementation should be:

> **Smart Segments Raw Data Audit, followed by the Trainability Audit**

Audit A should answer model-independent questions:

```text
How many transcript-bearing videos do we already have?

Which Smart Segment categories appear in those videos?

How many eligible intervals and positive-duration hours exist per category?

How balanced is the corpus?

How much category overlap exists?

How many unique channels exist?

Which categories are sufficiently represented to train immediately?
```

Audit B should then answer preprocessing-dependent questions:

```text
How many intervals align successfully?

How many positive, confirmed-negative and unknown windows exist per category?

How many tokens and windows are usable under the pinned tokenizer, normalization and windowing configuration?

What rejection, truncation and boundary-crossing cases occur?
```

Both reports must include their complete source hashes, policy/configuration values and implementation versions. Once those numbers are known, the training architecture can be finalized based on actual available data rather than assumptions.

Together, the two audits are the Go/No-Go gate for the rest of Smart Segments V1.
