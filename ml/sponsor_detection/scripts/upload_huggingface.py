#!/usr/bin/env python3
"""Stage and upload Flow sponsor-detection model weights to Hugging Face.

Uploads trained encoder checkpoints and the Android ORT package. Does not
upload transcripts, SponsorBlock dumps, training parquet, optimizer states,
or the custom ONNX Runtime source tree.
"""

from __future__ import annotations

import json
import shutil
from pathlib import Path

from huggingface_hub import HfApi

ROOT = Path(__file__).resolve().parents[3]
ML = ROOT / "ml" / "sponsor_detection"
STAGING = ML / "artifacts" / "hf_staging"
NAMESPACE = "CuriousDragon"
LICENSE = "cc-by-nc-sa-4.0"
BASE_ENCODER = "jhu-clsp/ettin-encoder-17m"
BASE_REVISION = "987607455c61e7a5bbc85f7758e0512ea6d0ae4c"
COLLECTION_TITLE = "Flow Sponsor Detection"

LICENSE_NOTICE = """\
## License

These weights are released under [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/).

The base encoder [`jhu-clsp/ettin-encoder-17m`](https://huggingface.co/jhu-clsp/ettin-encoder-17m)
is MIT. Fine-tuning used [SponsorBlock](https://sponsor.ajay.app/) labels
(CC BY-NC-SA 4.0) and the pinned [`Xenova/sponsorblock-768`](https://huggingface.co/datasets/Xenova/sponsorblock-768)
window corpus. Non-commercial use and share-alike apply to this derivative.

This project is not affiliated with SponsorBlock.
"""

SCRIPT_SMITH_NOTICE = """\
## ScriptSmith corpus

This model additionally used the
[`ScriptSmith/sponsorblock-youtube-metadata-2024`](https://huggingface.co/datasets/ScriptSmith/sponsorblock-youtube-metadata-2024)
transcript/metadata dataset (CC BY 4.0 compilation; its YouTube auto-captions
remain under YouTube's terms). Those transcripts are rolling auto-captions that
were reconstructed before alignment, so caption boundaries are approximate.
"""

SHARED_LIMITATIONS = """\
## Limitations

- English transcripts only.
- Target is **external paid sponsorships**. Self-promotion, memberships,
  merchandise, and interaction reminders are treated as negatives.
- SponsorBlock labels are noisy and incomplete. Evaluations against them are
  weak labels, not ground truth.
- Full-video reconstruction interpolates timestamps inside caption cues.
- Window-level scores do not by themselves skip video; Flow stitches overlapping
  768-token windows with a 128-token overlap.
"""


def _write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def _copy(src: Path, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dest)


def _special_tokens_map() -> str:
    return json.dumps(
        {
            "cls_token": "[CLS]",
            "mask_token": "[MASK]",
            "pad_token": "[PAD]",
            "sep_token": "[SEP]",
            "unk_token": "[UNK]",
        },
        indent=2,
    ) + "\n"


def _license_file() -> str:
    return (
        "Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International\n"
        "https://creativecommons.org/licenses/by-nc-sa/4.0/\n"
    )


def _pytorch_card(
    *,
    repo_id: str,
    title: str,
    summary: str,
    status: str,
    metrics_block: str,
    extra: str = "",
) -> str:
    return f"""---
license: {LICENSE}
language:
- en
base_model: {BASE_ENCODER}
base_model_relation: finetune
pipeline_tag: token-classification
library_name: transformers
tags:
- sponsorblock
- youtube
- token-classification
- modernbert
- ettin
- flow
---

# {title}

{summary}

- **Status:** {status}
- **Base encoder:** `{BASE_ENCODER}` @ `{BASE_REVISION}`
- **Architecture:** ModernBERT-family Ettin 17M (`ModernBertForTokenClassification`)
- **Labels:** `O`, `B-SPONSOR`, `I-SPONSOR`, `L-SPONSOR`, `U-SPONSOR`
- **Repo:** `{repo_id}`

## Use with Transformers

```python
from transformers import AutoModelForTokenClassification, AutoTokenizer

repo = "{repo_id}"
tokenizer = AutoTokenizer.from_pretrained(repo)
model = AutoModelForTokenClassification.from_pretrained(repo)
```

For full YouTube transcripts, tokenize into overlapping 768-token windows
(128-token overlap), decode BILOU spans, map characters back onto caption
cues, and merge gaps of 24 normalized characters or 1500 ms. See the Flow
`ml/sponsor_detection` inference pipeline.

{metrics_block}

## Intended use

On-device or local inference over English video transcripts to propose paid
sponsor ranges. In Flow, SponsorBlock remains authoritative when it has
segments; these weights are a fallback / shadow detector.

{SHARED_LIMITATIONS}

{extra}

{LICENSE_NOTICE}
"""


def stage_pytorch(
    *,
    repo_name: str,
    checkpoint: Path,
    title: str,
    summary: str,
    status: str,
    metrics_block: str,
    extra_files: list[tuple[Path, str]] | None = None,
    extra: str = "",
) -> Path:
    dest = STAGING / repo_name
    if dest.exists():
        shutil.rmtree(dest)
    dest.mkdir(parents=True)

    for name in (
        "model.safetensors",
        "config.json",
        "tokenizer.json",
        "tokenizer_config.json",
        "metrics.json",
        "training_args.bin",
    ):
        src = checkpoint / name
        if src.is_file():
            _copy(src, dest / name)

    repo_id = f"{NAMESPACE}/{repo_name}"
    _write(
        dest / "README.md",
        _pytorch_card(
            repo_id=repo_id,
            title=title,
            summary=summary,
            status=status,
            metrics_block=metrics_block,
            extra=extra,
        ),
    )
    _write(dest / "LICENSE", _license_file())
    _write(dest / "special_tokens_map.json", _special_tokens_map())
    for src, name in extra_files or []:
        _copy(src, dest / name)
    return dest


def stage_android_package(
    *,
    repo_name: str,
    source_directory: Path,
    artifact_stem: str,
    export_report: Path,
    source_model: str,
    confidence_threshold: str,
    extra_notice: str = "",
) -> Path:
    dest = STAGING / repo_name
    if dest.exists():
        shutil.rmtree(dest)
    dest.mkdir(parents=True)

    packaged = source_directory / "android"
    copies = {
        packaged / f"{artifact_stem}.int8.ort": f"{artifact_stem}.int8.ort",
        packaged / "config.json": "config.json",
        packaged / "tokenizer.json": "tokenizer.json",
        packaged / "tokenizer_config.json": "tokenizer_config.json",
        packaged / "required_operators_and_types.config": "required_operators_and_types.config",
        packaged / "tokenizer_goldens.json": "tokenizer_goldens.json",
        packaged / "sponsor_feedback_v1.schema.json": "sponsor_feedback_v1.schema.json",
        packaged / "manifest.json": "android_manifest.json",
        source_directory / f"{artifact_stem}.fp32.onnx": f"{artifact_stem}.fp32.onnx",
        source_directory / f"{artifact_stem}.int8.onnx": f"{artifact_stem}.int8.onnx",
        export_report: "export_report.json",
    }
    for src, name in copies.items():
        _copy(src, dest / name)

    _write(dest / "LICENSE", _license_file())
    _write(
        dest / "README.md",
        f"""---
license: {LICENSE}
language:
- en
base_model: {BASE_ENCODER}
base_model_relation: quantized
pipeline_tag: token-classification
library_name: onnxruntime
tags:
- sponsorblock
- youtube
- onnx
- onnxruntime
- android
- ettin
- flow
- quantized
---

# Ettin 17M sponsor detector (Android INT8)

ONNX Runtime package used by Flow for on-device sponsor-span detection.
Quantized from [`{source_model}`](https://huggingface.co/{source_model})
(`embedding_int8_per_channel`, ORT format, opset 18).

- **Primary runtime file:** `{artifact_stem}.int8.ort` (~28 MB)
- **Also included:** FP32 ONNX and INT8 ONNX for desktop/debug
- **Tokenizer:** same Ettin / ModernBERT tokenizer as the PyTorch checkpoints
- **Confidence threshold in the Android package:** `{confidence_threshold}`

## Files

| File | Role |
| --- | --- |
| `{artifact_stem}.int8.ort` | Shipping Android model |
| `{artifact_stem}.int8.onnx` | INT8 ONNX before ORT packing |
| `{artifact_stem}.fp32.onnx` | Unquantized ONNX |
| `tokenizer.json` / `tokenizer_config.json` | Tokenizer |
| `required_operators_and_types.config` | Reduced ORT operator config |
| `tokenizer_goldens.json` | Tokenization goldens |
| `sponsor_feedback_v1.schema.json` | Local training-journal schema |
| `android_manifest.json` | Export manifest and hashes |

{extra_notice}{SHARED_LIMITATIONS}

{LICENSE_NOTICE}
""",
    )
    return dest


def stage_android() -> Path:
    return stage_android_package(
        repo_name="ettin-17m-sponsor-v1-android",
        source_directory=ML / "artifacts" / "android" / "ettin_17m_sponsor_v1",
        artifact_stem="sponsor_detector_v1",
        export_report=ML / "reports" / "ettin_17m_v1_android_export.json",
        source_model=f"{NAMESPACE}/ettin-17m-sponsor-v1",
        confidence_threshold="0.0",
    )


def stage_android_combined() -> Path:
    return stage_android_package(
        repo_name="ettin-17m-sponsor-combined-android",
        source_directory=ML / "artifacts" / "android" / "ettin_17m_sponsor_combined",
        artifact_stem="sponsor_detector_combined",
        export_report=ML / "reports" / "ettin_17m_combined_android_export.json",
        source_model=f"{NAMESPACE}/ettin-17m-sponsor-combined",
        confidence_threshold="0.7",
        extra_notice=SCRIPT_SMITH_NOTICE + "\n",
    )


def stage_all() -> dict[str, Path]:
    STAGING.mkdir(parents=True, exist_ok=True)
    staged: dict[str, Path] = {}

    staged["ettin-17m-sponsor-v1"] = stage_pytorch(
        repo_name="ettin-17m-sponsor-v1",
        checkpoint=ML / "checkpoints" / "ettin_17m_sponsor_v1",
        title="Ettin 17M sponsor detector v1",
        summary=(
            "Complete three-epoch fine-tune of Ettin 17M on 298,069 leakage-safe "
            "English transcript windows. Stronger-recall reference model, and the "
            "source of the Android INT8 export."
        ),
        status="reference / Android export source",
        metrics_block="""\
## Token metrics (held-out test, 33,578 windows)

| Split | Sponsor token P / R / F1 | Token accuracy |
| --- | --- | --- |
| Validation | 0.931 / 0.899 / 0.915 | 0.974 |
| Test | 0.936 / 0.887 / 0.911 | 0.972 |
""",
        extra="""\
## Related repos

- Android INT8: [`CuriousDragon/ettin-17m-sponsor-v1-android`](https://huggingface.co/CuriousDragon/ettin-17m-sponsor-v1-android)
- Precision-oriented replay: [`CuriousDragon/ettin-17m-sponsor-v3-replay`](https://huggingface.co/CuriousDragon/ettin-17m-sponsor-v3-replay)
""",
    )

    staged["ettin-17m-sponsor-v2"] = stage_pytorch(
        repo_name="ettin-17m-sponsor-v2",
        checkpoint=ML / "checkpoints" / "ettin_17m_sponsor_v2",
        title="Ettin 17M sponsor detector v2 (archived)",
        summary=(
            "Full retraining experiment on the audited dataset revision. It did "
            "not replace v1 and is kept for comparison only."
        ),
        status="archived experiment",
        metrics_block="""\
## Token metrics (held-out test, 33,578 windows)

| Split | Sponsor token P / R / F1 | Token accuracy |
| --- | --- | --- |
| Validation | 0.937 / 0.892 / 0.914 | 0.974 |
| Test | 0.939 / 0.880 / 0.908 | 0.972 |
""",
        extra="Use v1 or v3-replay instead of this checkpoint for new work.",
    )

    staged["ettin-17m-sponsor-v3-replay"] = stage_pytorch(
        repo_name="ettin-17m-sponsor-v3-replay",
        checkpoint=ML / "checkpoints" / "ettin_17m_sponsor_v3_replay",
        title="Ettin 17M sponsor detector v3 replay",
        summary=(
            "Release-candidate weights. Initialized from v1 and trained for two "
            "epochs on 12,048 corrected/replay windows at 1e-6. Improves precision "
            "on self-promo and similar negatives at some recall cost. Calibrated "
            "decoder threshold is 0.65."
        ),
        status="release candidate",
        metrics_block="""\
## Token metrics (held-out test, 33,578 windows)

| Split | Sponsor token P / R / F1 | Token accuracy |
| --- | --- | --- |
| Validation | 0.933 / 0.898 / 0.915 | 0.974 |
| Test | 0.937 / 0.886 / 0.911 | 0.972 |

## Calibrated span metrics (threshold 0.65)

Validation, character IoU 0.5: P 0.935 / R 0.846 / F1 0.888

Held-out test windows:

- Window presence: P 0.964 / R 0.823 / F1 0.888
- Span character-IoU 0.5: P 0.933 / R 0.794 / F1 0.858
- Character coverage: P 0.944 / R 0.882 / F1 0.912
""",
        extra_files=[
            (ML / "reports" / "ettin_17m_replay_decoder.json", "decoder.json"),
            (
                ML / "reports" / "ettin_17m_sponsor_v3_replay_rc1_manifest.json",
                "release_manifest.json",
            ),
        ],
        extra="""\
`decoder.json` is bound to these weights by SHA-256. Do not mix it with another checkpoint.

Related: [`CuriousDragon/ettin-17m-sponsor-v1`](https://huggingface.co/CuriousDragon/ettin-17m-sponsor-v1)
""",
    )

    staged["ettin-17m-sponsor-v4"] = stage_pytorch(
        repo_name="ettin-17m-sponsor-v4",
        checkpoint=ML / "checkpoints" / "ettin_17m_sponsor_v4_full",
        title="Ettin 17M sponsor detector v4",
        summary=(
            "One-epoch continuation from v3-replay over the full 298,069-window "
            "training split. Calibrated decoder threshold remains 0.65."
        ),
        status="experiment",
        metrics_block="""\
## Token metrics (held-out test, 33,578 windows)

| Split | Sponsor token P / R / F1 | Token accuracy |
| --- | --- | --- |
| Validation | 0.931 / 0.900 / 0.915 | 0.974 |
| Test | 0.935 / 0.889 / 0.911 | 0.972 |

Calibrated validation span IoU 0.5: P 0.929 / R 0.850 / F1 0.888
""",
        extra_files=[
            (ML / "reports" / "ettin_17m_v4_decoder.json", "decoder.json"),
        ],
    )

    staged["ettin-17m-sponsor-combined"] = stage_pytorch(
        repo_name="ettin-17m-sponsor-combined",
        checkpoint=ML / "checkpoints" / "ettin_17m_sponsor_combined",
        title="Ettin 17M sponsor detector (combined)",
        summary=(
            "Production candidate. One low-learning-rate epoch from v4 over the "
            "combined leakage-safe train split: 298,069 Xenova windows plus "
            "209,390 ScriptSmith auto-caption windows (507,459 total). Chosen for "
            "the strongest generalization profile across both transcript "
            "distributions and the frozen full-video pilot. Calibrated decoder "
            "threshold 0.70."
        ),
        status="production candidate (rc1)",
        metrics_block="""\
## Token metrics (combined held-out test)

Sponsor token P / R / F1: 0.923 / 0.848 / **0.884**

## Cross-evaluation, raw decoder (threshold 0)

| Held-out set | Window P/R/F1 | Span IoU 0.5 P/R/F1 | Coverage F1 |
| --- | --- | --- | --- |
| Xenova test | 0.943 / 0.862 / 0.901 | 0.901 / 0.824 / 0.861 | 0.915 |
| ScriptSmith test | 0.910 / 0.804 / 0.854 | 0.839 / 0.733 / 0.783 | 0.853 |

## Calibrated decoder (threshold 0.70)

| Held-out set | Window P/R/F1 | Span IoU 0.5 P/R/F1 | Coverage F1 |
| --- | --- | --- | --- |
| Xenova test | 0.970 / 0.807 / 0.881 | 0.945 / 0.782 / 0.856 | 0.911 |
| ScriptSmith test | 0.942 / 0.718 / 0.815 | 0.901 / 0.669 / 0.768 | 0.842 |

Frozen 39-video mixed pilot (13 positive / 13 hard negative / 13 ordinary
negative): video presence F1 0.923; temporal span IoU 0.5 P 0.941 / R 0.842 /
F1 0.889; temporal coverage F1 0.909.
""",
        extra_files=[
            (ML / "reports" / "ettin_17m_combined_decoder.json", "decoder.json"),
            (
                ML / "reports" / "ettin_17m_sponsor_combined_rc1_manifest.json",
                "release_manifest.json",
            ),
        ],
        extra=SCRIPT_SMITH_NOTICE
        + """\
`decoder.json` is bound to these weights by SHA-256. Do not mix it with another checkpoint.

Related: [`CuriousDragon/ettin-17m-sponsor-v4`](https://huggingface.co/CuriousDragon/ettin-17m-sponsor-v4)
(same lineage on the Xenova corpus only).
""",
    )

    staged["ettin-17m-sponsor-scriptsmith-replay"] = stage_pytorch(
        repo_name="ettin-17m-sponsor-scriptsmith-replay",
        checkpoint=ML / "checkpoints" / "ettin_17m_sponsor_scriptsmith_replay",
        title="Ettin 17M sponsor detector (ScriptSmith replay, 1 epoch)",
        summary=(
            "Challenger. One low-learning-rate epoch from v4 over the 209,390-window "
            "ScriptSmith auto-caption train split only. Slightly better recall than "
            "the combined model on the Xenova test set. Calibrated decoder threshold "
            "0.75. Kept for head-to-head comparison on a larger frozen video set."
        ),
        status="challenger",
        metrics_block="""\
## Cross-evaluation, raw decoder (threshold 0)

| Held-out set | Window P/R/F1 | Span IoU 0.5 P/R/F1 | Coverage F1 |
| --- | --- | --- | --- |
| Xenova test | 0.936 / 0.872 / 0.903 | 0.894 / 0.833 / 0.862 | 0.915 |
| ScriptSmith test | 0.911 / 0.802 / 0.853 | 0.842 / 0.732 / 0.783 | 0.854 |

Calibrated decoder threshold 0.75. Frozen 39-video mixed pilot: temporal span
IoU 0.5 P 0.941 / R 0.842 / F1 0.889; temporal coverage F1 0.905.
""",
        extra_files=[
            (ML / "reports" / "ettin_17m_scriptsmith_decoder.json", "decoder.json"),
        ],
        extra=SCRIPT_SMITH_NOTICE
        + "Related: [`CuriousDragon/ettin-17m-sponsor-combined`](https://huggingface.co/CuriousDragon/ettin-17m-sponsor-combined)",
    )

    staged["ettin-17m-sponsor-scriptsmith-replay3"] = stage_pytorch(
        repo_name="ettin-17m-sponsor-scriptsmith-replay3",
        checkpoint=ML / "checkpoints" / "ettin_17m_sponsor_scriptsmith_replay3",
        title="Ettin 17M sponsor detector (ScriptSmith replay, 3 epochs)",
        summary=(
            "Experiment only. Three epochs from v4 over the ScriptSmith train split. "
            "Gains are concentrated on the ScriptSmith test set while the "
            "independent full-video pilot does not improve, indicating adaptation "
            "to the new source rather than a general improvement. Superseded by the "
            "combined and 1-epoch replay models."
        ),
        status="experiment (overfits ScriptSmith; not recommended)",
        metrics_block="""\
## Cross-evaluation, raw decoder (threshold 0)

| Held-out set | Window P/R/F1 | Span IoU 0.5 P/R/F1 | Coverage F1 |
| --- | --- | --- | --- |
| Xenova test | 0.938 / 0.868 / 0.901 | 0.895 / 0.829 / 0.861 | 0.913 |
| ScriptSmith test | 0.912 / 0.813 / 0.860 | 0.850 / 0.748 / 0.796 | 0.860 |

Calibrated decoder threshold 0.80. Frozen 39-video mixed pilot: temporal span
IoU 0.5 F1 0.857; temporal coverage F1 0.896.
""",
        extra_files=[
            (
                ML / "reports" / "ettin_17m_scriptsmith_replay3_decoder.json",
                "decoder.json",
            ),
        ],
        extra=SCRIPT_SMITH_NOTICE
        + "Superseded by [`CuriousDragon/ettin-17m-sponsor-combined`](https://huggingface.co/CuriousDragon/ettin-17m-sponsor-combined).",
    )

    staged["ettin-17m-sponsor-v1-android"] = stage_android()
    staged["ettin-17m-sponsor-combined-android"] = stage_android_combined()
    return staged


def upload_all(staged: dict[str, Path]) -> list[str]:
    api = HfApi()
    urls: list[str] = []
    for repo_name, path in staged.items():
        repo_id = f"{NAMESPACE}/{repo_name}"
        print(f"creating {repo_id}", flush=True)
        api.create_repo(
            repo_id=repo_id,
            repo_type="model",
            exist_ok=True,
            private=False,
        )
        print(f"uploading {repo_id} from {path}", flush=True)
        api.upload_folder(
            folder_path=str(path),
            repo_id=repo_id,
            repo_type="model",
            commit_message="Upload Flow sponsor-detection weights and model card",
        )
        urls.append(f"https://huggingface.co/{repo_id}")
        print(f"done {repo_id}", flush=True)
    return urls


def ensure_collection(repo_names: list[str]) -> str:
    api = HfApi()
    description = (
        "On-device English paid-sponsor detectors for Flow. Fine-tunes of "
        "Ettin 17M on SponsorBlock-derived transcript windows. CC BY-NC-SA 4.0."
    )
    collection = api.create_collection(
        title=COLLECTION_TITLE,
        namespace=NAMESPACE,
        description=description,
        private=False,
        exists_ok=True,
    )
    slug = collection.slug
    existing = {item.item_id for item in collection.items}
    notes = {
        "ettin-17m-sponsor-v1": "Reference / Android export source",
        "ettin-17m-sponsor-v2": "Archived experiment",
        "ettin-17m-sponsor-v3-replay": "Prior release candidate (Xenova only)",
        "ettin-17m-sponsor-v4": "Full-data continuation from v3 (Xenova only)",
        "ettin-17m-sponsor-combined": "Production candidate: Xenova + ScriptSmith",
        "ettin-17m-sponsor-scriptsmith-replay": "Challenger: ScriptSmith-only 1-epoch replay",
        "ettin-17m-sponsor-scriptsmith-replay3": "Experiment: ScriptSmith-only 3-epoch replay",
        "ettin-17m-sponsor-v1-android": "INT8 ORT package (legacy v1 model)",
        "ettin-17m-sponsor-combined-android": "INT8 ORT package for the combined model",
    }
    for repo_name in repo_names:
        item_id = f"{NAMESPACE}/{repo_name}"
        if item_id in existing:
            continue
        api.add_collection_item(
            collection_slug=slug,
            item_id=item_id,
            item_type="model",
            note=notes.get(repo_name),
            exists_ok=True,
        )
    return f"https://huggingface.co/collections/{slug}"


def main() -> None:
    staged = stage_all()
    print("staged:", ", ".join(staged), flush=True)
    urls = upload_all(staged)
    collection_url = ensure_collection(list(staged))
    print("MODELS", flush=True)
    for url in urls:
        print(url, flush=True)
    print("COLLECTION", collection_url, flush=True)


if __name__ == "__main__":
    main()
