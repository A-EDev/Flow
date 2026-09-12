# Sponsor Detection ML: Status and Next Steps

Last updated: 2026-08-31 (Asia/Kolkata)

## Quick resumption summary

The text-only sponsor detector is trained, calibrated, and usable as a release candidate. The
current model is `ettin-17m-sponsor-v3-replay-rc1`, based on the pretrained
`jhu-clsp/ettin-encoder-17m` encoder. It was not trained from scratch.

The remaining work before export or Android integration is to construct and review a genuinely
mixed full-video benchmark containing:

- 20 videos with external paid sponsorships;
- 20 hard negatives such as self-promotion, memberships, merchandise, and interaction prompts;
- 20 ordinary negatives with no external paid sponsorship.

All code needed to sample candidates, collect transcripts, select a channel-disjoint review set,
freeze reviewed annotations, and run the mixed evaluation is implemented. Transcript acquisition
is currently blocked because YouTube is rejecting requests from the present outbound IP.

When back on a different trusted network, resume at [Resume transcript acquisition](#resume-transcript-acquisition).

## Scope and decisions

- Android/Flow integration remains intentionally deferred.
- The immediate target is transcript-based detection of external paid sponsor segments.
- Self-promotion is a negative category for this model, not a paid-sponsor positive.
- English is the first supported language.
- We fine-tune a compact pretrained bidirectional encoder rather than training a text encoder from
  scratch or using the old generative T5 design.
- Large mirrors, transcripts, datasets, virtual environments, and checkpoints are ignored by Git.
- Licensing/distribution review remains deferred at the owner's request. It must be revisited before
  publishing a model or derived dataset.

## Data already available

### SponsorBlock mirror

- Local source: `sb-mirror/sponsorTimes.csv`
- Size: 6,812,995,962 bytes
- Rows scanned by the current benchmark sampler: 21,660,746
- Normalized sponsor annotations: 5,222,102 rows
- Normalized file: `data/labels/sponsor_annotations.parquet`
- Label SHA-256: `d8a64a89a1a04d641367ae920d8d752feba0fc4844a65b3b4717a7d2e42dacd0`

The mirror and generated data are intentionally excluded from Git.

### Bootstrap training corpus

The starting corpus is the pinned `Xenova/sponsorblock-768` dataset at revision
`09cace13dc72455e7558cfd2ad13e60a41688c8d`.

- Source windows audited: 371,406
- Accepted windows: 365,803
- Unique source videos: 96,644
- Metadata returned: 90,698 videos
- Unavailable metadata: 5,946 videos
- Original leakage-safe split:
  - train: 298,069 windows
  - validation: 34,156 windows
  - test: 33,578 windows
- Verified leakage controls:
  - zero repeated video IDs across splits;
  - zero overlap among known channel IDs;
  - zero exact normalized sponsor-campaign leakage.

The Xenova windows contain aligned text and character spans, but not the original transcript cue
boundaries. They are sufficient for token/span training but not for learning a cue-level temporal
head from scratch.

## Model architecture

### Encoder and token head

- Pretrained encoder: `jhu-clsp/ettin-encoder-17m`
- Pinned revision: `987607455c61e7a5bbc85f7758e0512ea6d0ae4c`
- Encoder size: approximately 17 million parameters
- Architecture: seven-layer bidirectional ModernBERT-family encoder
- Hidden size: 256
- Training labels: `O`, `B-SPONSOR`, `I-SPONSOR`, `L-SPONSOR`, `U-SPONSOR`
- Training objective: token-level cross entropy
- Training precision: BF16 on CUDA
- Training micro-batch: 8
- Gradient accumulation: 4
- Evaluation batch: 16

At inference time, full transcripts are normalized, tokenized into overlapping windows, decoded
into character spans, mapped back to transcript cue timestamps, and stitched into final sponsor
ranges.

### Full-transcript inference settings

- Maximum window length: 768 tokens
- Window overlap: 128 tokens
- Confidence threshold: 0.65
- Merge gap: 24 normalized characters or 1,500 ms
- Duplicate predictions from overlapping windows are unioned before gap merging.

Core implementation:

- `src/sponsor_detection/inference/windowing.py`
- `src/sponsor_detection/inference/stitching.py`
- `src/sponsor_detection/inference/pipeline.py`
- `src/sponsor_detection/model/decoder.py`

## Training history

### v1: complete baseline fine-tuning

Checkpoint: `checkpoints/ettin_17m_sponsor_v1`

- Started from the pretrained Ettin 17M encoder.
- Trained over the complete 298,069-window training split for three epochs.
- This remains the stronger-recall reference model.

### v2: complete audited-data retraining

Checkpoint: `checkpoints/ettin_17m_sponsor_v2`

- A complete retraining experiment using the audited revision.
- It did not justify replacing v1 and is retained only as an archived experiment.

### v3: v1 plus corrected examples and deterministic replay

Checkpoint: `checkpoints/ettin_17m_sponsor_v3_replay`

- Initialized from v1 rather than starting over.
- Train set: 12,048 corrected/replay windows
- Validation set: 34,156 windows
- Test set: 33,578 windows
- Epochs: 2
- Learning rate: `1e-6`
- Training loss: 0.19810
- Validation sponsor-token precision/recall/F1: 0.93262 / 0.89808 / 0.91502
- Test sponsor-token precision/recall/F1: 0.93728 / 0.88570 / 0.91076

This replay approach was kept because it improved precision on troublesome negative patterns while
avoiding a second complete training pass.

## Decoder calibration

Calibration used only the validation split. The selected threshold was 0.65, optimizing span F1 at
character IoU 0.5 subject to precision constraints.

Validation results at 0.65:

- Window presence precision/recall/F1: 0.96518 / 0.87582 / 0.91833
- Span IoU 0.5 precision/recall/F1: 0.93504 / 0.84569 / 0.88812
- Character coverage precision/recall/F1: 0.93899 / 0.89731 / 0.91768

Decoder configuration:

- `reports/ettin_17m_replay_decoder.json`
- `reports/ettin_17m_replay_calibration.json`

The decoder configuration is tied to the checkpoint by SHA-256 so it cannot silently be used with
different weights.

## Held-out local-window evaluation

Calibrated v3 results on 33,578 untouched test windows:

- Window presence precision/recall/F1: 0.96415 / 0.82337 / 0.88821
- Span character-IoU 0.3 precision/recall/F1: 0.94878 / 0.80724 / 0.87231
- Span character-IoU 0.5 precision/recall/F1: 0.93312 / 0.79391 / 0.85791
- Span character-IoU 0.7 precision/recall/F1: 0.89598 / 0.76231 / 0.82376
- Character coverage precision/recall/F1: 0.94363 / 0.88158 / 0.91155
- Median matched-boundary error: 4 characters at the start and 3 at the end
- P95 matched-boundary error: 171 characters at the start and 107 at the end
- CUDA BF16 evaluation runtime: 156.4 seconds

Compared with raw v1, v3 reduces false positives but misses more sponsors. This is intentional for
the current high-precision operating point, but the recall loss is real and must be assessed on the
mixed full-video benchmark.

Relevant report:

- `reports/ettin_17m_replay_calibrated_local_span_evaluation.json`

## Disagreement audit

v1 raw and v3 calibrated were compared across all 33,578 test windows.

- Same sponsor-presence decision: 33,023 windows
- v3 introduced false negatives: 365 windows
- v3 removed false positives: 190 windows
- Coverage equal: 31,782 windows
- v3 coverage better: 781 windows
- v3 coverage worse: 1,015 windows

Many removed predictions were self-promotion, memberships, merchandise, courses, or affiliate-style
calls to action. Some removed predictions looked like plausible but unlabeled commercials. The new
false negatives include clear external sponsors, confirming that the precision gain has a recall
cost.

Reports:

- `reports/ettin_17m_v1_v3_disagreements.json`
- `reports/ettin_17m_v1_v3_disagreement_audit_summary.json`

## Existing 20-video full-transcript canary

The first complete-video evaluation used 20 locally collected transcripts. All 20 are
sponsor-positive, so this is useful for testing windowing and temporal reconstruction but cannot
measure negative-video false-positive rate.

Results:

- Video presence: 17 true positives, 3 false negatives
- Video precision/recall/F1: 1.00000 / 0.85000 / 0.91892
- Temporal span IoU 0.3 precision/recall/F1: 0.79167 / 0.79167 / 0.79167
- Temporal span IoU 0.5 precision/recall/F1: 0.75000 / 0.75000 / 0.75000
- Temporal span IoU 0.7 precision/recall/F1: 0.66667 / 0.66667 / 0.66667
- Temporal coverage precision/recall/F1: 0.85950 / 0.82180 / 0.84023
- Median start/end error: 1,326 ms / 1,337 ms
- P95 start/end error: 15,813 ms / 10,303 ms

Some apparent false positives are likely missing SponsorBlock annotations, including an additional
sponsor outro found in one canary video. That observation is why the next benchmark requires manual
ground-truth review rather than treating absence from SponsorBlock as proof of a negative.

Report: `reports/ettin_17m_replay_full_video_evaluation.json`

## Frozen model release candidate

- Name: `ettin-17m-sponsor-v3-replay-rc1`
- Status: release candidate
- Model file: `checkpoints/ettin_17m_sponsor_v3_replay/model.safetensors`
- Model size: 67,462,820 bytes
- Model SHA-256: `da7e03a80f00a4854a63289e3eda9f1142a34e1930e780f86c535ec6428d6a59`
- Confidence threshold: 0.65

The release manifest hash-pins the model, tokenizer, configuration, metrics, decoder, datasets, and
evaluation reports:

- `reports/ettin_17m_sponsor_v3_replay_rc1_manifest.json`

This is not yet the final deployable artifact. ONNX export, INT8 quantization, parity testing, and
Android integration are deliberately waiting for the mixed full-video benchmark.

## Mixed full-video benchmark implemented

### Candidate design

The pilot target is 60 reviewed videos:

- 20 sponsor-positive videos;
- 20 hard negatives;
- 20 ordinary negatives.

Candidate acquisition is oversampled to 600 videos, 200 per class, because videos may be private,
deleted, non-English, missing captions, or rejected by channel leakage checks.

The queue is round-robin by class so a partially completed collection run remains approximately
balanced. Candidate selection is deterministic, based on a stable hash seed. It excludes every
video in the original training corpus and every video in the previous transcript pilot.

Generated queue statistics:

- Sponsor-positive candidates: 200
- Hard-negative candidates: 200
- Ordinary-negative candidates: 200
- Unique queue videos: 600
- Training video IDs excluded: 96,028
- Previous pilot video IDs excluded: 10,000
- Unique excluded video IDs: 105,841
- Sampled mirror videos inspected for negative classification: 139,567
- Sampled videos rejected because they also contained a sponsor category: 48,245
- Negative candidates carrying a sponsor category after filtering: zero

Files:

- Queue: `data/benchmarks/mixed_pilot_candidates_v1.jsonl`
- Queue manifest: `reports/mixed_pilot_candidates_v1_manifest.json`
- Sampler: `src/sponsor_detection/data/benchmark.py`
- Sampler configuration: `config/benchmark_pilot.toml`

### Review and freeze safeguards

`src/sponsor_detection/data/benchmark_review.py` implements the remaining dataset gates:

- require a successfully downloaded English transcript;
- require public-video metadata and a known channel ID;
- reject channels seen in training;
- permit only one selected video per channel in the 60-video pilot;
- initialize explicit pending review records;
- require every frozen record to be marked reviewed;
- require positive videos to contain at least one valid external-sponsor interval;
- require negative videos to contain no external-sponsor intervals;
- enforce exact class balance before freezing;
- hash-pin the reviewed JSONL and every transcript.

Commands added:

- `data sample-benchmark`
- `data prepare-benchmark-review`
- `data freeze-benchmark`

The full-video evaluator now accepts the frozen benchmark JSONL directly, including completely
negative videos. Configuration: `config/evaluate_mixed_pilot.toml`.

## Current acquisition blocker

Transcript collection uses `yt-dlp`, does not use the YouTube Data API key, and is configured to be
deliberately slow:

- Target successes: 120, providing headroom to select a valid balanced 60
- Maximum attempts per invocation: 10
- Random delay: 15 to 25 seconds
- Maximum transient attempts per video: 2
- Stop immediately on a block: enabled
- Local safety cooldown after a block: 21,600 seconds (six hours)

Two bounded checks were made from the current outbound IP:

1. 2026-08-31 00:40 IST approximately: first request blocked; collection stopped.
2. 2026-08-31 13:01 IST approximately: first request blocked again; collection stopped.

Current result:

- Successful mixed-pilot transcripts: 0
- Last status: `YtDlpBlockedError`
- The first candidate has reached its two-attempt limit and will be skipped automatically later.
- Latest local cooldown expiry: approximately 2026-08-31 19:01 IST.

This is not evidence of a permanent YouTube account ban. No account or cookies were used. It is an
outbound-IP or transcript-endpoint restriction. The six-hour timer is our own safety mechanism and
does not predict when YouTube will lift its restriction.

Free public proxies should not be used: they are untrusted, often already blocked, and automatic IP
rotation risks escalating the restriction. Ngrok is an inbound tunnel and does not change the
outbound IP seen by YouTube. A mobile hotspot, a reputable VPN/proxy controlled by the owner, or one
trusted remote machine is the appropriate test.

State and report:

- `data/transcripts/mixed_pilot_v1/state.json`
- `reports/mixed_pilot_transcript_collection_v1.json`

## Resume transcript acquisition

### Preferred safe test at home

1. Connect the computer to a mobile hotspot or another trusted network.
2. Confirm that the public IP differs from the blocked connection.
3. Ensure the local cooldown has expired. After 19:01 IST on 2026-08-31, the current cooldown will
   already be clear.
4. Run one bounded collection invocation:

```bash
ml/sponsor_detection/.venv/bin/sponsor-detection data collect-transcripts \
  --config ml/sponsor_detection/config/benchmark_collection.toml
```

The command will attempt at most ten candidates, sleep 15–25 seconds between attempts, cache each
successful transcript, and stop immediately if the new IP is blocked.

Do not delete `state.json`. It contains retry counts, cached successes, permanent failures, and the
block cooldown needed for safe resumption.

### Continue collection

If the first alternate-network run succeeds without blocking, run the same command again as needed.
Every invocation resumes from existing state. The configured target is 120 successful transcripts.

Inspect progress with:

```bash
cat ml/sponsor_detection/reports/mixed_pilot_transcript_collection_v1.json
```

If collection reaches 120 but the later review-preparation step reports a class deficit, increase
`target_successes` in `config/benchmark_collection.toml` and continue. Do not weaken the channel or
language filters merely to reach 60.

## Steps after transcript collection

### 1. Acquire metadata

This step uses the official YouTube Data API only for metadata such as channel ID, publication date,
duration, caption availability, and public status. It does not retrieve transcripts.

```bash
ml/sponsor_detection/.venv/bin/sponsor-detection data acquire-metadata \
  --config ml/sponsor_detection/config/benchmark_metadata.toml
```

For 120 transcripts, the configuration permits at most three batches of 50 video IDs. The API key
is loaded from `.env` and is not written into reports.

### 2. Select the channel-disjoint 60-video review set

```bash
ml/sponsor_detection/.venv/bin/sponsor-detection data prepare-benchmark-review \
  --config ml/sponsor_detection/config/benchmark_review.toml
```

Expected output:

- `data/benchmarks/mixed_pilot_review_v1.jsonl`
- `reports/mixed_pilot_review_v1_manifest.json`

The command returns a nonzero status if it cannot select 20 valid videos from every class. Inspect
`deficits_by_class` and `rejections` in the manifest before collecting more transcripts.

### 3. Generate review assistance and audit every selected transcript

The initialized review records contain SponsorBlock source segments and an empty
`model_predictions` list. Before annotation, run v1 and v3 over each complete transcript and attach
their suggested intervals and local text context. This prediction-enrichment command has not yet
been implemented.

The review must examine the complete transcript, not only SponsorBlock intervals. For every video:

- mark `annotation.status` as `reviewed`;
- set `annotation.final_class` to `sponsor_positive`, `hard_negative`, or `ordinary_negative`;
- for positives, record corrected `start_ms` and `end_ms` intervals for external paid sponsors;
- for negatives, explicitly confirm no external sponsor and leave intervals empty;
- keep creator products, memberships, merchandise, Patreon, channel promotion, and ordinary calls
  to action separate from external paid sponsorship;
- note sponsorship that is purely visual or absent from captions as outside the text model's scope.

The assistant can perform this transcript review; the owner does not need to inspect every video
manually. Ambiguous records should be excluded or adjudicated rather than forced into a class.

### 4. Freeze the reviewed benchmark

```bash
ml/sponsor_detection/.venv/bin/sponsor-detection data freeze-benchmark \
  --config ml/sponsor_detection/config/benchmark_freeze.toml
```

This will refuse to freeze unless all selected records are valid and the final reviewed classes
contain exactly 20 usable videos each.

Expected output:

- `data/benchmarks/mixed_pilot_frozen_v1.jsonl`
- `reports/mixed_pilot_frozen_v1_manifest.json`

### 5. Run the mixed full-video evaluation

```bash
ml/sponsor_detection/.venv/bin/sponsor-detection evaluate-videos \
  --config ml/sponsor_detection/config/evaluate_mixed_pilot.toml
```

The report will finally measure the quantities absent from the positive-only canary:

- false-positive rate on completely negative videos;
- sponsor-positive video recall;
- temporal span precision/recall/F1 at IoU 0.3, 0.5, and 0.7;
- temporal coverage precision/recall/F1;
- median and P95 start/end boundary error;
- inference windows and runtime per video.

Do not adjust the 0.65 threshold using the final frozen benchmark. If the 60-video pilot is treated
as development data, use it to choose changes and later create a larger untouched test set. The
planned larger benchmark is approximately 200 reviewed videos: 80 sponsor-positive, 60 hard
negatives, and 60 ordinary negatives, split channel-disjointly into development and final test sets.

## Decision after the mixed evaluation

Compare at least these two operating points on the same reviewed videos:

1. v1 with its raw decoder, representing stronger recall;
2. v3 replay with the calibrated 0.65 threshold, representing stronger precision.

Choose based primarily on negative-video false positives and sponsor-positive video recall, not
token accuracy. Likely follow-up experiments are threshold adjustment, targeted replay examples, or
separate treatment of self-promotion. A new complete three-epoch training run should happen only if
the mixed benchmark demonstrates that targeted replay and calibration cannot fix the observed
errors.

After selecting an operating point:

1. export the model to ONNX;
2. create an INT8 version;
3. verify PyTorch/ONNX/INT8 prediction parity on the frozen benchmark;
4. measure CPU latency and peak memory;
5. require INT8 span F1 to remain within one absolute point of FP32;
6. only then design the Android on-device integration.

## Tests and repository state

- Current ML test count: 49
- Command:

```bash
ml/sponsor_detection/.venv/bin/python -m unittest discover \
  -s ml/sponsor_detection/tests -p 'test_*.py'
```

- Latest result: all 49 tests passed.
- Python compilation and CLI help checks passed.
- `git diff --check` reported no whitespace errors in the ML changes.
- The ML directory is currently untracked in Git, and existing unrelated Android changes remain in
  the working tree.
- No commit, push, merge, or version bump has been performed.

## Important files

### Model and evaluation

- `MODEL_ARCHITECTURE.md`
- `checkpoints/ettin_17m_sponsor_v1/`
- `checkpoints/ettin_17m_sponsor_v3_replay/`
- `reports/ettin_17m_replay_decoder.json`
- `reports/ettin_17m_replay_calibration.json`
- `reports/ettin_17m_replay_calibrated_local_span_evaluation.json`
- `reports/ettin_17m_replay_full_video_evaluation.json`
- `reports/ettin_17m_sponsor_v3_replay_rc1_manifest.json`

### Mixed benchmark

- `src/sponsor_detection/data/benchmark.py`
- `src/sponsor_detection/data/benchmark_review.py`
- `src/sponsor_detection/video_evaluation.py`
- `config/benchmark_pilot.toml`
- `config/benchmark_collection.toml`
- `config/benchmark_metadata.toml`
- `config/benchmark_review.toml`
- `config/benchmark_freeze.toml`
- `config/evaluate_mixed_pilot.toml`
- `data/benchmarks/mixed_pilot_candidates_v1.jsonl`
- `reports/mixed_pilot_candidates_v1_manifest.json`
- `reports/mixed_pilot_transcript_collection_v1.json`

### Tests added for this phase

- `tests/test_benchmark.py`
- `tests/test_benchmark_review.py`
- `tests/test_video_evaluation.py`
