# Sponsor-Detection ML Project Plan

## Summary

Build a standalone Python ML pipeline under `ml/sponsor_detection/`. Android integration remains out of scope. The model will convert timestamped transcripts into paid-sponsor skip ranges.

Do not train a text encoder from scratch for v1. Fine-tune pretrained bidirectional encoders, with optional YouTube-domain masked-language pretraining and teacher distillation only when controlled experiments justify them.

## Current bootstrap status

- Pinned source: `Xenova/sponsorblock-768` revision `09cace13dc72455e7558cfd2ad13e60a41688c8d`.
- Audited 371,406 source windows and accepted 365,803 after exact text-span checks and validation against the current local SponsorBlock mirror.
- Collected official metadata for all 96,644 unique source videos: 90,698 returned and 5,946 unavailable.
- Built deterministic channel/campaign/video-grouped Parquet splits: 298,069 train, 34,156 validation, and 33,578 test rows.
- Verified zero leakage for video IDs, all known channel IDs, and exact normalized sponsor campaigns. Known channel coverage is 93.84% of accepted videos.
- Validated every window with both pinned Ettin tokenizers. No sponsor span is clipped or unaligned at the configured 1,024-token limit.
- Completed one-step BF16 GPU training and evaluation smoke tests for both Ettin 17M and Ettin 32M.

The bootstrap corpus contains preassembled transcript text and aligned sponsor character ranges, but not the original transcript cue boundaries. It is ready for the token-extraction model. Cue-aware temporal refinement remains a later stage using newly collected timestamped transcripts.

## Phase 0: acquire and audit the data

The bootstrap dataset and metadata audit are complete. Further timestamped-transcript acquisition is optional for the cue-aware refinement stage rather than a blocker for initial training.

- Download a versioned snapshot of the official SponsorBlock database dump instead of crawling the per-video API.
- Record the download timestamp, source URL, checksum, database schema version, and applicable license. The SponsorBlock database/API is currently published under CC BY-NC-SA 4.0, so model or dataset distribution requires a dedicated license review.
- Extract YouTube video IDs and paid-sponsor annotations first. Do not collect transcripts until the label inventory, validity rate, language coverage, and expected storage requirements have been profiled.
- Build a resumable transcript acquisition job with caching, bounded concurrency, retry/backoff, and explicit unavailable/private/deleted/age-restricted states.
- Capture transcript provenance: source, language, whether manually authored or automatically generated, retrieval time, and cue timestamps.
- Begin with a statistically useful pilot sample before attempting a full corpus. Use it to measure transcript availability, alignment quality, duplicate sponsorship campaigns, class balance, and compute/storage cost.
- Keep raw database snapshots, transcripts, generated datasets, checkpoints, and model artifacts outside Git. Commit only code, schemas, configurations, small manifests, and aggregate reports.
- Do not begin large-scale acquisition or publish derived artifacts until the relevant SponsorBlock and transcript-source terms have been reviewed for the intended use.

## Dataset and preprocessing

- Target English paid-sponsor segments for v1 while preserving language metadata for future multilingual training.
- Store normalized Parquet tables for videos, transcript cues, SponsorBlock annotations, derived consensus segments, and training examples.
- Retain timestamps, punctuation, case, numbers, URLs, and brand-like tokens. Apply Unicode and whitespace normalization without destructive text cleanup.
- Reject invalid timestamps, hidden or shadow-hidden submissions, material video-duration mismatches, missing transcripts, and corrupt cues. Keep low-confidence annotations as ambiguous data rather than treating them as negatives.
- Merge agreeing sponsor submissions into consensus boundaries. Record confidence, provenance, vote/lock evidence, and disagreement instead of discarding source annotations.
- Produce cue-level BILOU labels plus start/end offsets inside boundary cues.
- Sample negatives from ordinary content, content adjacent to boundaries, product reviews, calls to action, and verified sponsor-free videos. Unannotated regions are not automatically trusted negatives.
- Cluster duplicate sponsor scripts and campaigns before splitting. Create channel-disjoint train/validation/test partitions plus a recent temporal holdout.
- Version every dataset with source snapshot identifiers, checksums, schema version, preprocessing configuration, and split seed.

## Model architecture and experiments

### Candidate encoders

Primary candidates:

- [`jhu-clsp/ettin-encoder-17m`](https://huggingface.co/jhu-clsp/ettin-encoder-17m)
- [`jhu-clsp/ettin-encoder-32m`](https://huggingface.co/jhu-clsp/ettin-encoder-32m)

Controls:

- [`google/electra-small-discriminator`](https://huggingface.co/google/electra-small-discriminator)
- [`microsoft/MiniLM-L12-H384-uncased`](https://huggingface.co/microsoft/MiniLM-L12-H384-uncased)

Ettin is the preferred modern family because it offers encoder-only 17M and 32M checkpoints, an open training recipe, and longer-context support. MiniLM remains an important control because its generic benchmark performance is still competitive; newer will not be assumed to mean better.

### Detection model

- Represent transcripts as overlapping windows with explicit cue boundaries.
- Pool token states into cue representations and combine them with normalized cue duration, relative video position, and neighboring-gap features.
- Add a constrained BILOU sponsor/non-sponsor sequence-classification head.
- Add boundary-offset regression for accurate start and end timestamps inside boundary cues.
- Evaluate 256-, 512-, and 1,024-token windows with 50% overlap. Fuse overlapping predictions and apply constrained decoding before producing ranges.
- Fine-tune pretrained encoders directly before attempting additional pretraining.
- Test continued masked-language pretraining using only the training transcript partition. Adopt it only if it improves segment F1 by at least 0.5 absolute points without harming unseen-channel or temporal performance.
- If required, fine-tune an Ettin 150M teacher and distill its logits and cue representations into the compact models. Keep distillation only under the same improvement gate.
- Export FP32 and INT8 ONNX artifacts. Select Ettin 17M unless the 32M model improves segment F1 by at least 1 absolute point at the same precision target while remaining below 50 MB after quantization.

## Interfaces and reproducibility

Provide configuration-driven commands for:

- `data acquire-labels`: snapshot and inventory SponsorBlock annotations.
- `data acquire-transcripts`: fetch and cache timestamped transcripts resumably.
- `data profile`: report coverage, confidence, language, duration, disagreement, transcript availability, and leakage risks.
- `data build`: normalize, align, deduplicate, split, and write the training dataset.
- `train`: run a pinned encoder/configuration experiment.
- `evaluate`: score validation, channel-disjoint, and temporal sets.
- `export`: create ONNX/INT8 artifacts and verify prediction parity.

Each training example will expose cue text and timing, BILOU labels, boundary targets, confidence/sample weights, split assignment, and provenance. Each experiment will emit its resolved configuration, source revision, dataset fingerprint, metrics JSON, model weights, and model card.

### Ready-to-run bootstrap commands

```bash
ml/sponsor_detection/.venv/bin/sponsor-detection data build-training-dataset \
  --config ml/sponsor_detection/config/training_dataset.toml
ml/sponsor_detection/.venv/bin/sponsor-detection data validate-tokenization \
  --config ml/sponsor_detection/config/train_ettin_17m.toml
ml/sponsor_detection/.venv/bin/sponsor-detection train \
  --config ml/sponsor_detection/config/train_ettin_17m.toml
```

## Implementation order

1. Scaffold the independent Python project, configuration layout, data schemas, and ignored artifact directories.
2. Implement SponsorBlock snapshot ingestion and generate a label-only inventory report.
3. Acquire a pilot transcript sample and quantify availability, alignment quality, language coverage, and cost.
4. Implement cleanup, consensus construction, campaign deduplication, leakage-safe splits, and dataset manifests.
5. Reproduce the historical SponsorBlock-ML approach and simple lexical/timing baselines.
6. Fine-tune Ettin 17M, Ettin 32M, ELECTRA-small, and MiniLM under identical splits and budgets.
7. Evaluate domain-adaptive pretraining and distillation only if the direct fine-tuning results justify the added complexity.
8. Calibrate decoding, quantize the Pareto-winning model, and export reproducible artifacts.

## Testing and acceptance criteria

- Unit-test transcript normalization, interval validation, consensus construction, cue alignment, BILOU generation, overlapping-window fusion, and boundary decoding.
- Assert deterministic splits and zero video, channel, or duplicate-campaign leakage across partitions.
- Run a small-batch overfit test to verify the complete loss and decoding path.
- Report segment precision/recall/F1 at temporal IoU 0.3, 0.5, and 0.7; false-positive seconds per playback hour; missed sponsor seconds; boundary median/P95 error; and calibration.
- Select checkpoints by highest recall at a minimum 95% segment precision, then boundary accuracy, artifact size, and CPU latency.
- Require INT8 segment F1 to remain within 1 absolute point of the FP32 model and verify exported predictions against PyTorch.
- Reproduce the outdated SponsorBlock-ML approach as a historical baseline, not as the production architecture.

## Assumptions and boundaries

- Paid sponsorship is the only positive category in v1.
- English is the initial training and evaluation language, but schemas remain multilingual-ready.
- Both Ettin 17M and 32M will be benchmarked; no encoder will be trained from scratch for v1.
- Flow application integration, UI, playback behavior, and Android inference wiring are deferred.
- Large datasets and generated artifacts are not committed to the application repository.
- Exact Hugging Face revisions and licenses are recorded and validated before downloading training artifacts.
- Data acquisition begins with a pilot and an audit; preprocessing and model training do not begin until that audit passes.
