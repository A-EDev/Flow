# Jev-Assisted Dataset Review — Deferred Experiment Plan

Created: 2026-09-19

Status: **deferred**. Return to this after the ScriptSmith integration and its baseline evaluation
are complete. This plan authorizes no Jev API calls, data upload, label changes, or training run.

Related local work: `SCRIPT_SMITH_INTEGRATION_PLAN.md`, `SCRIPT_SMITH_PREFLIGHT.md`,
`src/sponsor_detection/data/scriptsmith_dataset.py`, `src/sponsor_detection/disagreement.py`,
`reports/sponsor_windows_scriptsmith_manifest.json`, and
`reports/mixed_pilot_frozen_v1_manifest.json`.

## 1. Goal

Test whether Jev helps reviewers find **actionable training-data problems** more efficiently
than the existing detector scores and deterministic checks. SponsorBlock remains the source of
segment categories and timestamps. Jev may rank or flag examples; it must not silently rewrite
labels or become the source of exact time boundaries.

The first proposed use is offline dataset triage, not an Android runtime dependency. A positive
result would justify a small, provenance-tracked reviewed correction set and one controlled
replay experiment. A negative result ends the Jev experiment without changing the dataset.

## 2. Why Jev might fit, and where it does not

Jev accepts text state and returns typed Choice, Score, or yes/no probability answers. Its
documented strengths fit narrow semantic questions such as whether a caption excerpt describes
a third-party promotion, creator self-promotion, or ordinary editorial content. Its current
documentation says it accepts text only, is strongest in English, and can struggle with numeric
precision, large irrelevant context, and generation. Keep caption timing, interval overlap,
coverage, deduplication, and span alignment in deterministic code.

Sources to recheck when resuming:

- [Jev announcement](https://typesafe.ai/blog/introducing-system-one-models-and-jev)
- [TypeSafe primitives](https://docs.typesafe.ai/primitives)
- [State and input limits](https://docs.typesafe.ai/concepts/state)
- [Jev 1.13 known limitations](https://docs.typesafe.ai/model-jaggedness/jev-1.13)
- [Confidence semantics](https://docs.typesafe.ai/confidence)
- [Data handling documents](https://docs.typesafe.ai/legal)

The vendor's published speed, cost, and calibration claims are hypotheses for this use case.
Validate on our reviewed sponsor data rather than using those claims as acceptance criteria.

## 3. Resume gate: finish the current dataset work first

Before building this experiment, record:

1. The final, hash-pinned ScriptSmith source and split manifests, caption reconstruction and
   alignment audit, tokenization validation, and zero within/cross-dataset leakage checks.
2. The chosen baseline checkpoint, decoder threshold, and its results on ScriptSmith
   validation/test and the existing frozen 39-video mixed pilot. Do not tune using the frozen
   pilot; preserve its current role as a comparison set.
3. The exact train-split windows and review artifacts available for mining. Confirm that
   candidate videos and channels are outside held-out evaluation sets.
4. Jev access, current API/model version and limits, transcript transfer permissions,
   retention terms, and whether sending ScriptSmith/YouTube caption text to the service is
   acceptable for this project. Stop before external calls if this gate is unresolved.

Do not alter the current ScriptSmith builder merely to accommodate Jev. The pilot can consume
its emitted Parquet and prediction reports as a separate, optional analysis stage.

## 4. Candidate pools

Mine from the **ScriptSmith train split only** at first. Save each candidate's `example_id`,
`video_id`, `channel_id`, source dataset hash, SponsorBlock segment UUID/category if present,
model checkpoint/hash, prediction, and reason for selection. Use stable hashes to deduplicate
near-identical excerpts and keep all windows from one video together.

| Pool | Deterministic trigger | Question Jev could help answer |
|---|---|---|
| Model misses a SponsorBlock sponsor | Low sponsor score or no decoded span inside an eligible sponsor interval | Does the caption actually contain promotional language, or is the sponsor absent from the transcript? |
| Model fires in an unlabeled region | High sponsor score away from known eligible sponsor intervals | Plausible missed sponsor, self-promotion, ordinary discussion, or insufficient context? |
| Category ambiguity | Sponsor and self-promotion/interaction evidence are close or overlap | Which semantic category best describes the text? |
| Boundary/coverage anomaly | Cue reconstruction, coverage, or span-alignment checks flag a window | Is the excerpt semantically usable? Exact boundary quality stays a code/reviewer check. |

Absence from SponsorBlock is **not** proof of a negative. A model–SponsorBlock disagreement is
a review candidate, not evidence that either side is wrong. Do not send the original
SponsorBlock category in Jev's first semantic judgment; compare it afterward in code to limit
anchoring. Include only the target excerpt and enough neighboring caption text for context.

## 5. Jev question design

Use one small, versioned schema and freeze it before the blind evaluation. Proposed fields:

- A Choice among `third_party_promotion`, `creator_self_promotion`,
  `ordinary_editorial_discussion`, and `insufficient_or_ambiguous`. Define the categories
  with concrete examples and edge cases from the project's annotation policy.
- A separate yes/no probability for whether the excerpt contains explicit promotional
  language (for example, a sponsor disclosure or call to action).
- A separate Choice for whether the excerpt contains enough information to judge the
  category. Low coverage or missing speech should route to review, not to a negative label.

Keep exact timestamp arithmetic and conflicts between answers in code. Store the full
probability distribution and the model's confidence statistic, not only its winning class.
Treat probabilities as **uncalibrated on our domain until measured**. Repeating the same
question as a Choice and yes/no question is not an assumed consistency check; TypeSafe warns
that those outputs need not satisfy simple arithmetic identities.

## 6. Pilot and comparison

1. From train-only candidates, create a small prompt-development set and a separate,
   channel-disjoint blind audit set. Include random candidates as well as each trigger pool,
   so yield estimates are not based solely on easy disagreements. Freeze IDs and hashes.
2. Have a reviewer inspect the full transcript context and SponsorBlock evidence for the
   blind set, with `correct`, `wrong_category`, `missing_label`, `boundary_or_caption_issue`,
   and `cannot_determine` outcomes. Review must not see Jev's answer first. Use a second
   reviewer or adjudication for disputed cases.
3. Rank the same candidates three ways: detector uncertainty/disagreement alone,
   deterministic quality rules plus detector scores, and those signals plus Jev. Match
   review budgets and report actionable findings per 100 reviewed, precision at fixed K,
   category confusion, abstention rate, latency, token use, and cost. Stratify by trigger
   type and caption quality.
4. Choose any Jev routing threshold on the prompt-development set only. Evaluate it once
   on the blind audit set; do not use the frozen 39-video benchmark to tune Jev prompts or
   thresholds.
5. Continue only if Jev adds a meaningful, repeatable review-yield gain over the best
   non-Jev ranking at an acceptable cost. If the gain disappears under blind review or is
   concentrated in caption errors that code already detects, stop.

The initial pilot size and numeric acceptance threshold should be fixed after inspecting
candidate counts and reviewer capacity, before looking at blind-set outcomes. Report
uncertainty intervals; a handful of examples cannot support a broad claim.

## 7. If the pilot succeeds: reviewed replay

1. Write **human-adjudicated** corrections to a separate, hash-pinned overlay artifact with
   original label, reviewed decision, evidence, reviewer status, Jev model/schema version,
   and source checkpoint. Never overwrite raw SponsorBlock rows or the original Parquet.
2. Include only corrected train-split examples in a small replay dataset. Keep validation,
   test, and the frozen benchmark untouched. Preserve video/channel/campaign separation.
3. Compare an unchanged baseline, a replay using cases selected without Jev, and a replay
   using the same review budget with Jev. Keep training budget, sampling, decoder, and
   evaluation procedure matched.
4. Judge success by full-video false positives, sponsor recall, span IoU and boundary error
   on held-out data, plus review effort. A higher token F1 alone is insufficient.
5. Retain the overlay only if an independently evaluated model improves the intended
   operating point without a material regression. Record both positive and negative
   findings so the experiment can be reproduced.

## 8. Deliverables when resumed

- A frozen candidate manifest and a versioned Jev question schema.
- A blind, adjudicated audit set and report comparing review rankings at equal budget.
- A go/no-go decision with measured cost, limitations, and data-handling status.
- If warranted, a separate reviewed correction overlay, replay manifest, and matched
  model comparison. No model or dataset publication is part of this plan.
