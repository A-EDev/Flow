# ScriptSmith — Step A Preflight Report

Date: 2026-09-19
Scope: pin the dataset revision and inspect samples before any bulk download or integration code.
Status: **preflight complete; findings require a Step D amendment.**

Machine-readable provenance: `reports/scriptsmith_dataset_provenance.json`.

No bulk download was performed. Two shards were downloaded and verified:

- `subtitles/part-00002.parquet` — 89,624,197 bytes, SHA-256
  `b11716269e3736c6f343ea72d5fec19e2c9b846b628b230427b6cec47e744a6c` ✅
- `video_metadata/part-00003.parquet` — 4,680,883 bytes, SHA-256
  `f1e4b32be2a1b29fc08f2c7042009275aad506266f18201b89f1626289556c19` ✅

---

## 1. Pin

- HF id: `ScriptSmith/sponsorblock-youtube-metadata-2024`
- **Immutable revision: `74ef9e552073db1869f18082cbf7e875ab104655`** (repo last modified 2026-07-27)
- `main` is mutable; always resolve at the pinned revision.
- File sizes + SHA-256 for both configs are recorded in the provenance JSON for the eventual
  manifest.

---

## 2. What the samples show

### 2.1 Subtitles shard

- 2,818 rows; columns `video_id, language, full_text, segments_json`.
- No nulls, no empty text, no duplicate `video_id` within the shard.
- `segments_json` cues have exactly `{start, end, text}`.
- **Timestamps are seconds (float)** — the existing pipeline is in milliseconds.
- Cues are ordered, non-overlapping, no zero/negative durations in the sample.
- `full_text` always equals the whitespace-normalized concatenation of cue text.
- 3,018,245 cues total in this shard alone; max cue end ~36,022 s (~10 h).
- Language field is **dirty**: alongside `en`, `en-US`, `en-GB`, `en-IN`, `en-CA` there are
  malformed values such as `en-ehkg1hFWq8A`, `en-Akx1aOF-4Mg`, and cross-language codes like
  `en-zh`, `en-ru`, `en-ko`.

#### Rolling-caption duplication (the important finding)

YouTube auto-captions here are emitted in a rolling format. Example from row 0:

```
   3.429-   3.439 dur= 0.010  'Good day, my dear ladies and'
   3.439-   6.710 dur= 3.271  'Good day, my dear ladies and\ngentlemen, worms, worms, worms. I release'
   6.710-   6.720 dur= 0.010  'gentlemen, worms, worms, worms. I released the'
   6.720-   9.070 dur= 2.350  'gentlemen, worms, worms, worms. I released the\nlast video and there wa'
```

Quantified over the shard:

| Metric | Value |
|---|---|
| Cues under 50 ms | 45.9% |
| Characters carried by cues under 50 ms | 30.4% |
| Consecutive cues with prefix/suffix overlap | **90.3%** |
| Exact repeated consecutive cue text | 0.2% |

**Implication:** feeding these cues raw into `assemble_transcript()` emits each phrase roughly
twice, inflating the transcript by a large factor and corrupting char-span alignment. A
rolling-caption **deduplication / line-reconstruction** step is mandatory before assembly.

### 2.2 Metadata shard

- 4,536 rows, 38 columns; no duplicate `id`, no null `channel_id`.
- **`language` is null in 1,953/4,536 rows (~43%)** — cannot be the primary language filter.
- `availability` null in 1,745 rows (~38%); `live_status`: 4,498 `not_live`, 38 `was_live`.
- Durations: min 14 s, p50 ~19.3 min, p95 ~80.7 min, max ~21.2 h (livestream VOD).

---

## 3. Required plan amendment (Step D)

The current Step D begins "`segments_json` → `TranscriptCue`". It must instead begin with a
**caption reconstruction** stage:

1. Drop or collapse sub-50 ms cues and reconstruct stable caption lines from the rolling
   prefix/suffix-overlapping stream (dedupe before text assembly).
2. Shift timestamps from seconds → milliseconds.
3. Then `TranscriptCue` → `assemble_transcript` → alignment, as planned.
4. Record per-video caption reconstruction stats (dropped cues, reconstructed line count,
   text inflation ratio) for the manifest and quarantine thresholds.

The existing plan's note to "quarantine intervals with absent/very sparse captions, drift, or
boundary ambiguity" is now backed by evidence and should be treated as a first-class filter.

---

## 4. Other confirmations

- `data/profile.py:129 eligibility_rejection_flags` exists and, as the plan states, does not
  test `incorrectVotes` or `locked`.
- `train.py:78 validate_tokenization_from_config` exists and reports `overlength_windows`,
  `clipped_sponsor_spans`, `unaligned_sponsor_spans`, as Step D.7 assumes.
- The earlier 25%-English estimate should not be trusted: this shard was ~92% English-prefixed,
  so per-shard language shares vary and the true eligible count requires a full pass.

---

## 5. Next steps

1. Amend Step D with the caption-reconstruction stage (above).
2. Run a full-corpus funnel pass (still read-only) to count eligible English-prefixed videos
   with reconstructable transcripts.
3. Only then decide replay vs full retrain and download the remaining shards.
