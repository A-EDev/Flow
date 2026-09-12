from __future__ import annotations

import json
import re
import shutil
from collections import Counter
from datetime import UTC, datetime
from pathlib import Path

from sponsor_detection.data.profile import sha256_file, write_json_atomic
from sponsor_detection.data.training_dataset import _campaign_hash


SELF_PROMOTION_PATTERN = re.compile(
    r"(?i)\b(?:sponsor(?:ed)? by (?:me|my|us|our)|sponsor is (?:me|myself)|"
    r"brought to you by (?:me|us|our)|partnered with me|my online "
    r"(?:master|course)|my (?:etsy|protein|merch|store|shop))\b"
)


def _external_correction_candidates(
    report: dict[str, object], *, minimum_confidence: float
) -> dict[str, list[dict[str, object]]]:
    candidates: dict[str, list[dict[str, object]]] = {}
    for candidate in report["candidates"]:
        spans = candidate["predicted_spans"]
        confidence = max(float(span["confidence"]) for span in spans)
        if confidence < minimum_confidence or SELF_PROMOTION_PATTERN.search(
            str(candidate["text"])
        ):
            continue
        candidates[str(candidate["example_id"])] = [
            {
                "start_char": int(span["start_char"]),
                "end_char": int(span["end_char"]),
                "confidence": float(span["confidence"]),
            }
            for span in spans
        ]
    return candidates


def _corrected_spans(
    text: str, predicted_spans: list[dict[str, object]]
) -> list[dict[str, object]]:
    return [
        {
            "start_char": int(span["start_char"]),
            "end_char": int(span["end_char"]),
            "start_ms": None,
            "end_ms": None,
            "current_segment_id": None,
            "current_iou": None,
            "campaign_hash": _campaign_hash(
                text[int(span["start_char"]) : int(span["end_char"])]
            ),
        }
        for span in predicted_spans
    ]


def _parquet_summary(path: Path) -> dict[str, object]:
    import pyarrow.parquet as parquet

    label_kinds: Counter[str] = Counter()
    source_splits: Counter[str] = Counter()
    videos: set[str] = set()
    channels: set[str] = set()
    campaigns: set[str] = set()
    file = parquet.ParquetFile(path)
    for batch in file.iter_batches(
        columns=[
            "video_id",
            "channel_id",
            "sponsor_spans",
            "label_kind",
            "source_split",
        ],
        batch_size=8192,
    ):
        values = batch.to_pydict()
        for video_id, channel_id, spans, label_kind, source_split in zip(
            values["video_id"],
            values["channel_id"],
            values["sponsor_spans"],
            values["label_kind"],
            values["source_split"],
            strict=True,
        ):
            videos.add(str(video_id))
            if channel_id:
                channels.add(str(channel_id))
            campaigns.update(str(span["campaign_hash"]) for span in spans)
            label_kinds[str(label_kind)] += 1
            source_splits[str(source_split)] += 1
    return {
        "rows": sum(label_kinds.values()),
        "videos": len(videos),
        "channels": len(channels),
        "campaigns": len(campaigns),
        "label_kinds": dict(label_kinds),
        "legacy_source_splits": dict(source_splits),
    }


def apply_audit_corrections(
    source_directory: Path,
    output_directory: Path,
    source_manifest_path: Path,
    output_manifest_path: Path,
    candidates_path: Path,
    *,
    minimum_confidence: float,
) -> dict[str, object]:
    import pyarrow as pa
    import pyarrow.parquet as parquet

    source_manifest = json.loads(source_manifest_path.read_text(encoding="utf-8"))
    candidates_report = json.loads(candidates_path.read_text(encoding="utf-8"))
    source_train_path = source_directory / "train.parquet"
    expected_source_sha = source_manifest["outputs"]["train"]["sha256"]
    if sha256_file(source_train_path) != expected_source_sha:
        raise ValueError("source train dataset does not match its manifest")
    corrections = _external_correction_candidates(
        candidates_report,
        minimum_confidence=minimum_confidence,
    )

    output_directory.mkdir(parents=True, exist_ok=True)
    for split in ("validation", "test"):
        shutil.copyfile(
            source_directory / f"{split}.parquet",
            output_directory / f"{split}.parquet",
        )

    source_file = parquet.ParquetFile(source_train_path)
    writer = parquet.ParquetWriter(
        output_directory / "train.parquet",
        source_file.schema_arrow,
        compression="zstd",
        use_dictionary=True,
    )
    corrected_rows = 0
    corrected_examples: list[str] = []
    try:
        for batch in source_file.iter_batches(batch_size=8192):
            values = batch.to_pydict()
            for index, example_id in enumerate(values["example_id"]):
                predicted_spans = corrections.get(str(example_id))
                if predicted_spans is None:
                    continue
                values["label_kind"][index] = "weak_positive"
                values["sample_weight"][index] = 0.5
                values["sponsor_spans"][index] = _corrected_spans(
                    str(values["text"][index]), predicted_spans
                )
                values["legacy_categories"][index] = list(
                    values["legacy_categories"][index]
                ) + ["audit_weak_positive"]
                corrected_rows += 1
                corrected_examples.append(str(example_id))
            writer.write_table(pa.Table.from_pydict(values, schema=source_file.schema_arrow))
    finally:
        writer.close()

    outputs = {}
    for split in ("train", "validation", "test"):
        path = output_directory / f"{split}.parquet"
        source_output = source_manifest["outputs"][split]
        outputs[split] = {
            **source_output,
            **_parquet_summary(path),
            "path": str(path),
            "bytes": path.stat().st_size,
            "sha256": sha256_file(path),
        }
    manifest = {
        "schema_version": 1,
        "generated_at": datetime.now(tz=UTC).isoformat(),
        "configuration": {
            "minimum_confidence": minimum_confidence,
            "source_manifest_path": str(source_manifest_path),
            "source_manifest_sha256": sha256_file(source_manifest_path),
            "candidates_path": str(candidates_path),
        },
        "corrections": {
            "rows": corrected_rows,
            "examples": corrected_examples,
            "candidate_report_sha256": sha256_file(candidates_path),
        },
        "outputs": outputs,
        "leakage": source_manifest["leakage"],
    }
    write_json_atomic(output_manifest_path, manifest)
    return manifest
