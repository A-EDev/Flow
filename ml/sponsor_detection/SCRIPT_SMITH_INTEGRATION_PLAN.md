# ScriptSmith Dataset — Exploration & Training Integration Plan

Last updated: 2026-09-19

Status: **reviewed plan; implementation not started.** No ScriptSmith data has been downloaded
and no integration code has been written. Counts below are inventory estimates until checked
against a pinned dataset revision and the actual eligible subtitle rows.

Related docs: `STATUS_AND_NEXT_STEPS.md`, `MODEL_ARCHITECTURE.md`, `PLAN.md`.

---

## 1. Purpose

Evaluate whether
`ScriptSmith/sponsorblock-youtube-metadata-2024`
can be used to **continue training** the current best Ettin-17M sponsor detector (v4,
`checkpoints/ettin_17m_sponsor_v4_full`, initialized from v3 replay).

Answer in one line: **it is a transcript + metadata source, not a labeled dataset.** It contains
no SponsorBlock segment labels. It can be used only after joining it to our own SponsorBlock
mirror and generating aligned char spans ourselves.

---

## 2. Dataset inventory

- HF id: `ScriptSmith/sponsorblock-youtube-metadata-2024`
- License on card: CC-BY-4.0 (covers the compilation; does **not** relicense YouTube transcripts)
- 154,536 videos; selection = top ~167K SponsorBlock videos by vote count that received
  segments during 2024
- Collection window: Aug 9 – Sep 30, 2025, via `yt-dlp`
- Raw archives: 8.2 GB compressed / 130 GB uncompressed (not needed initially)

### 2.1 Configs

| Config | Rows | Columns | Relevance |
|---|---|---|---|
| `video_metadata` | 154,536 | id, title, description, upload_date, timestamp, duration, view/like/comment counts, channel, channel_id, channel_url, follower count, verified, uploader, resolution/codec, age_limit, availability, live_status, language, categories, tags, chapters_json | channel/language/validity + chapter hints |
| `subtitles` | 62,819 (41%) | `video_id`, `language`, `full_text`, `segments_json` (`{start,end,text}[]`) | **the usable part: auto-caption transcripts with cue timestamps** |
| `heatmaps` | ~15M | `video_id`, `start_time`, `end_time`, `value` | engagement dips; not a text label |
| `channel_playlists` | ~20.8M | video/playlist metadata | irrelevant |
| `live_chat` | ~5M | chat messages | irrelevant |

Parquet sizes for `subtitles`: `part-00000` ~958 MB, `part-00001` ~965 MB, `part-00002` ~90 MB
(~2.0 GB total).

### 2.2 Input helper files

| File | Rows/size | Meaning |
|---|---|---|
| `input/2024_video_ids.txt` | 167,051 IDs | full 2024 selection universe |
| `input/2024_video_ids_nonblocked.txt` | 149,485 IDs | not IP-blocked at collection |
| `input/2024_video_ids_without_subtitles.txt` | 91,179 IDs | subtitles unavailable |
| `input/channel_ids.txt` | 502 KB | channel universe |
| `input/log_subs.txt` | 533 KB | collection log |
| `input/script_subtitles.sh` | — | uses `yt-dlp --write-subs --write-auto-subs` (auto-captions) |

### 2.3 Distribution notes (from card)

- Languages: English ~25%, Russian ~16%, Hindi ~2.5%, Polish ~2%, other ~54.5%.
- Categories: Gaming 15%, Entertainment 11%, People & Blogs 8%, Sci&Tech 6%, Education 5%, other 55%.
- Avg duration 28.6 min; avg ~1.6M views.
- ~92% have heatmaps, 41% subtitles, 32% chapters.

### 2.4 The critical limitation

**No config contains SponsorBlock label intervals** — no category, no sponsor start/end. The
name overstates what it is. It is metadata + transcripts.

---

## 3. Overlap analysis (already run, read-only)

ScriptSmith 2024 IDs vs our local corpora:

| Comparison | Count |
|---|---|
| ScriptSmith 2024 IDs total | 167,051 |
| Present in our `sponsor_annotations.parquet` mirror | **167,039** (only 12 missing) |
| Overlap with Xenova `segments.json` videos (98,219) | **72** |
| Overlap with `sponsor_windows_v1/v2` train videos (78,076) | **56** |
| New vs Xenova segments | 166,979 |

Implications:

1. **Mirror rows are available** for essentially every ScriptSmith selection ID. This does
   not mean every video has an eligible sponsor label or a usable transcript.
2. **Direct video overlap is low** (~56–72 videos). This does not establish low channel or
   repeated-campaign overlap; those require separate checks against every existing split and
   the frozen benchmark.
3. The videos are a **different distribution** from the current corpus (newer, top-voted,
   multilingual), which is both the opportunity and the risk.

---

## 4. Local assets we can reuse

| Asset | Path | Provides |
|---|---|---|
| SponsorBlock raw mirror | `sb-mirror/sponsorTimes.csv` (6.8 GB, 21.6M rows) | `videoID, startTime, endTime, votes, locked, incorrectVotes, UUID, timeSubmitted, views, category, actionType, service, videoDuration, hidden, reputation, shadowHidden, ...` |
| Normalized labels | `data/labels/sponsor_annotations.parquet` (5,222,102 rows, 3.4M videos) | sponsor-only eligibility (`eligibility_policy.category = "sponsor"`); **no selfpromo/interaction** |
| Xenova corpus | `data/external/xenova_sponsorblock_768/{train,valid,test}.json`, `segments.json`, `processed_database.json` | existing training data + per-video category segments |
| Transcript assembly | `src/sponsor_detection/inference/windowing.py` | `TranscriptCue`, `AssembledTranscript`, `assemble_transcript()`, `normalize_cue_text()`, `build_transcript_windows()`, char→time mapping |
| Window builder | `src/sponsor_detection/data/training_dataset.py` | `_assess_row`, `extract_sponsor_char_spans`, `DisjointSet`, `_split_for_component`, campaign hashing, leakage checks |
| Token labeling | `src/sponsor_detection/model/token_labels.py` | `bilou_labels_for_offsets()` (char spans → BIOUL) |
| Label builder | `src/sponsor_detection/data/labels.py` | mirror → normalized parquet, eligibility policy |

---

## 5. Target training format (what must be produced)

Existing windows (`data/training/sponsor_windows_v1/train.parquet`) schema:

```
example_id        : string (hash)
video_index       : int32
video_id          : string
channel_id        : string
published_at      : string
source_split      : string          # inherited Xenova train | valid | test; choose a new provenance value
text              : string          # normalized window text
window_start_ms   : int64
window_end_ms     : int64
label_kind        : string          # positive | hard_negative | ordinary_negative
sample_weight     : float           # 1.0 positive/hard-neg, 0.5 ordinary-neg
legacy_categories : list<string>    # e.g. ['SPONSOR']
sponsor_spans     : list<struct<start_char, end_char, start_ms, end_ms,
                                current_segment_id, current_iou, campaign_hash>>
```

Label-kind rules (`training_dataset.py:203-209`):

- sponsor interval present → `positive`
- no sponsor, but any non-sponsor category token appears in `extracted` → `hard_negative`
- neither → `ordinary_negative` (weight 0.5)

Token labels are then `O / B-SPONSOR / I-SPONSOR / L-SPONSOR / U-SPONSOR`.

---

## 6. Gap analysis

The existing builder is **hard-wired to Xenova's format**:

- `_iter_source_rows` reads `train.json/valid.json/test.json` lines with pre-computed
  `video_id, video_index, text, start, end, extracted`.
- `extract_sponsor_char_spans` parses inline `START_SPONSOR_TOKEN` markers to get char spans.
- Categories for hard negatives come from `ALL_CATEGORY_PATTERN` over `extracted`.

ScriptSmith provides none of that. It provides raw cues. Therefore:

| Needed | Source | Status |
|---|---|---|
| Transcript text + cue timestamps | ScriptSmith `segments_json` | available |
| Channel/language/validity | ScriptSmith `video_metadata` | available |
| Sponsor categories (sponsor/selfpromo/interaction) | **our** `sb-mirror/sponsorTimes.csv` | available, must parse raw |
| Char-aligned sponsor spans | **must generate** | missing |
| Channel-disjoint split + leakage control | reuse `training_dataset.py` | reusable |

Genuine upside: **ScriptSmith has cue boundaries.** They support time-to-text alignment and
could support a later cue-level temporal head, after their timing quality is audited.

---

## 7. Step-by-step plan to make it training-ready

### Step A — Acquire

- Pin an immutable Hugging Face dataset commit and record the revision, file names, sizes,
  SHA-256 hashes, and download date. `main` is mutable.
- Inspect a small subtitle/metadata sample first. Confirm timestamp units, cue ordering,
  duplicate caption tracks, language values, nulls, and whether `segments_json` covers the
  relevant SponsorBlock intervals.
- Download `subtitles` config (~2 GB) and `video_metadata` only after that preflight.
- Do not download `raw/` archives initially; `segments_json` is sufficient.

### Step B — Build the eligible universe

1. Inner-join metadata `id` to subtitles `video_id`; select one English subtitle track per
   video using an explicit language preference (`en`, `en-US`, `en-GB`) and log duplicates.
   Check the subtitle language directly; metadata `language` is only a secondary filter and
   may be missing or disagree with the caption track.
2. Keep public, non-live videos with valid duration, ordered/nonempty cues, and a usable
   `channel_id`. Measure caption coverage over the video and over each candidate labeled
   interval. Reject or quarantine poorly covered cases rather than treating absent captions
   as negative text.
3. Exclude videos and channels from all existing training/validation/test splits and the
   frozen mixed benchmark before assigning new splits. Also check exact campaign-text hashes
   across datasets after alignment; shared text must not cross a train/evaluation boundary.

The ~15–16K English-video figure is only `62,819 × 25%`, before the join, quality filters,
and channel exclusions. Measure and report the actual funnel; the final count may differ
substantially.

### Step C — Build labels from our own mirror

Stream `sb-mirror/sponsorTimes.csv` for eligible IDs. Reuse
`data/profile.py:eligibility_rejection_flags` and the exact policy in the current labels
manifest for service, action type, interval validity, minimum votes, hidden flags, and
duration tolerance. Record any category-specific policy change explicitly; the current
helper does not test `incorrectVotes` or `locked`.

- `category == "sponsor"` → positive
- `category ∈ {selfpromo, interaction}` → proposed first-pass hard-negative marker;
  this is narrower than the existing builder's "any category token" rule
- no overlapping eligible segment → ordinary-negative candidate, subject to caption
  coverage and an audit of missing/uncategorized labels

Note: `sponsor_annotations.parquet` is **sponsor-only**, so a raw-CSV parse (or a new normalized
artifact preserving categories) is required. Deduplicate or resolve conflicting/overlapping
SponsorBlock rows by a documented, deterministic rule; never emit overlapping sponsor char
spans, which `bilou_labels_for_offsets()` rejects.

### Step D — Align transcripts to spans (core new work)

Reuse `inference/windowing.py`:

1. `segments_json` → `TranscriptCue(index, start_ms, end_ms, text)`.
2. `assemble_transcript(cues)` → normalized `text` + `cue_ranges` (char↔cue↔time).
3. Add a tested inverse mapping from time interval to overlapping cue ranges. Define
   half-open boundary behavior, gaps, overlapping cues, zero-length intervals, and a minimum
   overlap/coverage rule. Cue-aligned spans are approximate labels; do not interpolate a
   precise character boundary from elapsed time without validating it.
4. Quarantine intervals with absent/very sparse captions, drift, or boundary ambiguity.
   Inspect a stratified sample by interval length, caption coverage, and language before
   training. Record rejection counts and estimated boundary error.
5. Map accepted sponsor intervals to non-overlapping full-transcript char spans. Preserve
   the original SponsorBlock times and UUID in `sponsor_spans`; since the same mirror row is
   the label source, `current_iou` is 1.0. Hash the selected text with `_campaign_hash`.
   Avoid merging unrelated intervals merely because nearby cues touch.
6. Build token windows using the **same pinned tokenizer** as v4. Training uses max length
   1024 with overlap 128; full-video inference currently uses 768 with overlap 128. Keep
   that difference explicit and assess it during evaluation. Slice each global char span
   into every intersecting window and convert its offsets to that window's local text;
   `build_transcript_windows()` currently returns global offsets, whereas the trainer
   tokenizes each emitted `text` independently.
7. Emit rows matching §5. Run `validate_tokenization_from_config` so truncation and
   unaligned-span counts are zero. Use a documented provenance value for `source_split`;
   ScriptSmith has no inherited Xenova train/valid/test split.

**Normalization caveat:** Xenova text uses `URL_TOKEN / NUMBER_TOKEN / PROFANITY_TOKEN`;
`normalize_cue_text` handles URL/NUMBER only and keeps punctuation. Reusing
`assemble_transcript()` makes new examples match inference normalization, but mixes two
training-text distributions. Measure token and prediction differences on a held-out sample
before changing the runtime normalizer.

### Step E — Split, validate, manifest

1. Group by channel and exact campaign text using `DisjointSet`, then use
   `_split_for_component` (train 0.80 / validation 0.10 / test 0.10). Report actual
   fractions because connected components can skew a hash-based split.
2. Enforce zero video/channel/exact-campaign leakage both **within ScriptSmith** and
   **against the existing datasets and frozen benchmark**. Existing v2 checks cover only
   its own splits. Keep the ScriptSmith validation/test splits out of replay training.
3. Emit `data/training/sponsor_windows_scriptsmith/{train,validation,test}.parquet`.
4. Emit a hash-pinned manifest (`reports/sponsor_windows_scriptsmith_manifest.json`) recording
   dataset revision/file SHA-256s, mirror/label-policy SHA-256, tokenizer revision, config
   SHA-256, filter and alignment rejection counts, caption coverage, label-kind breakdown,
   split sizes, and cross-dataset leakage counters. Fail the build on nonzero leakage.

### Step F — Decide training integration

Proceed only after the pilot establishes that enough sponsor and negative windows survive
quality filtering, a manual boundary audit finds the cue-level labels usable, tokenization
validation passes, and all leakage checks are zero. Record these gate results in the manifest;
do not infer them from the 2024-ID overlap counts.

| Option | Description | Risk |
|---|---|---|
| **Replay (recommended first)** | Balanced sample, if the measured eligible counts support it (e.g. up to 4K per label kind); low LR, 1–2 epochs, initialized from v4 | moderate label-noise risk |
| Full retrain | Merge into the full train split, 1 epoch at low LR — like v4 | higher, marginal gains expected |
| Cue-level head | Exploit ScriptSmith cue boundaries to add/learn a temporal head | highest, needs architecture work |

First evaluate the unchanged v4 checkpoint and each candidate with the same decoder on the
existing **39-video frozen mixed pilot** (`reports/mixed_pilot_frozen_v1_manifest.json`). The
v3/v4 comparison already reports identical full-video presence and IoU-0.5 span F1 on those
39 videos, so a small difference there is weak evidence. Use ScriptSmith validation for model
selection, preserve both test splits, and build a larger independently reviewed holdout before
claiming a generalization gain. Do not tune the 0.65 threshold on the frozen pilot. Compare
full-video false positives, sponsor recall, span IoU, boundary error, and inference cost, not
only token F1.

---

## 8. Caveats

1. **Auto-captions** are noisier than curated transcripts; timing drift will likely worsen
   boundary error.
2. **Selection bias**: top-voted SponsorBlock videos are sponsorship-heavy → ordinary negatives
   are scarce and must be mined from non-segment regions.
3. **English-only** filtering reduces the usable set; the dataset card's ~25% English
   figure does not establish the English share among subtitle rows.
4. **Normalization mismatch** between Xenova training text and `normalize_cue_text`.
5. **Uncertain gain**: the quoted ~15–20% data bump is an unfiltered estimate. v4 improved
   window-level metrics only marginally over v3 and tied it on the 39-video frozen pilot;
   that pilot is too small to establish a new model's generalization.
6. **Licensing** (see §9).

---

## 9. Licensing / distribution

- The local SponsorBlock mirror states that its database/API follow **CC BY-NC-SA 4.0**
  unless separate permission is granted. Distribution implications for a trained model
  need review before release.
- The ScriptSmith dataset's CC-BY-4.0 covers the compilation, not the underlying YouTube
  auto-captions.
- Current state: the public fork tracks only code/config/docs (`git ls-files ml/` → 167 files);
  `checkpoints/` and `data/` are gitignored (`.gitignore:55-56`). No model or dataset is
  committed.
- Before distributing a derived dataset or model (bundled or via a public HF link), obtain a
  project-specific licensing review. Document SponsorBlock and ScriptSmith attribution and
  provenance, the intended use, and the model-card license. Do not assume that the dataset
  card's CC-BY-4.0 grants rights to redistribute the underlying YouTube captions.

Deferred TODO: add a licensing/attribution section to the fork README (near `README.md:260`)
plus matching attribution on the HF model card.

---

## 10. Open decisions

1. **Recommended first pass:** English subtitle tracks only; add languages after a separate
   multilingual evaluation and tokenizer audit.
2. **Recommended first pass:** use `selfpromo`/`interaction` as hard negatives and document
   the intentional difference from the current builder's broader category-token rule.
   Audit other categories before broadening this definition.
3. **Recommended first experiment:** a small, quality-filtered replay from v4, followed by
   the existing full-video evaluation. Defer a full retrain until replay shows a clear gain.
4. Defer the cue-level temporal head until timing quality and boundary error are measured.
5. Set the target volume from the measured eligible funnel and alignment audit, rather
   than committing to all estimated English videos in advance.
