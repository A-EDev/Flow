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


def stage_android() -> Path:
    repo_name = "ettin-17m-sponsor-v1-android"
    dest = STAGING / repo_name
    if dest.exists():
        shutil.rmtree(dest)
    dest.mkdir(parents=True)

    android = ML / "artifacts" / "android" / "ettin_17m_sponsor_v1"
    packaged = android / "android"
    copies = {
        packaged / "sponsor_detector_v1.int8.ort": "sponsor_detector_v1.int8.ort",
        packaged / "config.json": "config.json",
        packaged / "tokenizer.json": "tokenizer.json",
        packaged / "tokenizer_config.json": "tokenizer_config.json",
        packaged / "required_operators_and_types.config": "required_operators_and_types.config",
        packaged / "tokenizer_goldens.json": "tokenizer_goldens.json",
        packaged / "sponsor_feedback_v1.schema.json": "sponsor_feedback_v1.schema.json",
        packaged / "manifest.json": "android_manifest.json",
        android / "sponsor_detector_v1.fp32.onnx": "sponsor_detector_v1.fp32.onnx",
        android / "sponsor_detector_v1.int8.onnx": "sponsor_detector_v1.int8.onnx",
        ML / "reports" / "ettin_17m_v1_android_export.json": "export_report.json",
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
Quantized from [`{NAMESPACE}/ettin-17m-sponsor-v1`](https://huggingface.co/CuriousDragon/ettin-17m-sponsor-v1)
(`embedding_int8_per_channel`, ORT format, opset 18).

- **Primary runtime file:** `sponsor_detector_v1.int8.ort` (~28 MB)
- **Also included:** FP32 ONNX and INT8 ONNX for desktop/debug
- **Tokenizer:** same Ettin / ModernBERT tokenizer as the PyTorch checkpoints
- **Confidence threshold in the Android package:** `0.0` (raw scores retained
  for evaluation; Flow can filter later)

## Files

| File | Role |
| --- | --- |
| `sponsor_detector_v1.int8.ort` | Shipping Android model |
| `sponsor_detector_v1.int8.onnx` | INT8 ONNX before ORT packing |
| `sponsor_detector_v1.fp32.onnx` | Unquantized ONNX |
| `tokenizer.json` / `tokenizer_config.json` | Tokenizer |
| `required_operators_and_types.config` | Reduced ORT operator config |
| `tokenizer_goldens.json` | Tokenization goldens |
| `sponsor_feedback_v1.schema.json` | Local training-journal schema |
| `android_manifest.json` | Export manifest and hashes |

{SHARED_LIMITATIONS}

{LICENSE_NOTICE}
""",
    )
    return dest


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

    staged["ettin-17m-sponsor-v1-android"] = stage_android()
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
        "ettin-17m-sponsor-v3-replay": "Release candidate",
        "ettin-17m-sponsor-v4": "Full-data continuation from v3",
        "ettin-17m-sponsor-v1-android": "INT8 ORT package used by Flow",
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
