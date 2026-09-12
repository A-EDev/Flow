from __future__ import annotations

import json
import tomllib
from collections import Counter
from pathlib import Path
from typing import Sequence

from sponsor_detection.data.profile import sha256_file, write_json_atomic
from sponsor_detection.evaluation import character_overlap, run_checkpoint_inference
from sponsor_detection.model.decoder import DecodedSponsorSpan


Interval = tuple[int, int]


def _serialized(spans: Sequence[DecodedSponsorSpan]) -> list[dict[str, object]]:
    return [
        {
            "start_char": span.start_char,
            "end_char": span.end_char,
            "confidence": span.confidence,
        }
        for span in spans
    ]


def _coverage_metrics(
    predicted_spans: Sequence[DecodedSponsorSpan], expected_spans: Sequence[Interval]
) -> dict[str, float | int]:
    predicted = sorted((span.start_char, span.end_char) for span in predicted_spans)
    expected = sorted(expected_spans)
    predicted_characters = sum(end - start for start, end in predicted)
    expected_characters = sum(end - start for start, end in expected)
    overlap = character_overlap(predicted, expected)
    precision = overlap / predicted_characters if predicted_characters else 0.0
    recall = overlap / expected_characters if expected_characters else 0.0
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    return {
        "predicted_characters": predicted_characters,
        "expected_characters": expected_characters,
        "overlap": overlap,
        "precision": precision,
        "recall": recall,
        "f1": f1,
    }


def presence_disagreement(
    v1_spans: Sequence[DecodedSponsorSpan],
    v3_spans: Sequence[DecodedSponsorSpan],
    *,
    expected_positive: bool,
) -> str:
    v1_positive = bool(v1_spans)
    v3_positive = bool(v3_spans)
    if v1_positive and not v3_positive:
        return "v3_introduced_false_negative" if expected_positive else "v3_removed_false_positive"
    if v3_positive and not v1_positive:
        return "v3_recovered_positive" if expected_positive else "v3_added_false_positive"
    return "same_presence"


def _threshold(configuration: dict[str, object]) -> float:
    decoder_path = configuration.get("decoder_config_path")
    if decoder_path is None:
        return float(configuration.get("confidence_threshold", 0.0))
    decoder = json.loads(Path(decoder_path).read_text(encoding="utf-8"))
    checkpoint_path = Path(configuration["checkpoint_path"])
    if sha256_file(checkpoint_path / "model.safetensors") != decoder["checkpoint"][
        "model_sha256"
    ]:
        raise ValueError("decoder configuration does not match its checkpoint")
    return float(decoder["confidence_threshold"])


def audit_from_config(path: Path, *, progress_callback=None) -> dict[str, object]:
    with path.open("rb") as source:
        configuration = tomllib.load(source)
    dataset = configuration["dataset"]
    audit = configuration["audit"]
    manifest_path = Path(dataset["manifest_path"])
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    test_path = Path(dataset["test_path"])
    if sha256_file(test_path) != manifest["outputs"]["test"]["sha256"]:
        raise ValueError("test dataset does not match its manifest")

    records: dict[str, dict[str, object]] = {}
    v1_predictions: dict[str, tuple[DecodedSponsorSpan, ...]] = {}
    v3_predictions: dict[str, tuple[DecodedSponsorSpan, ...]] = {}
    model_v1 = configuration["model_v1"]
    model_v3 = configuration["model_v3"]
    v1_threshold = _threshold(model_v1)
    v3_threshold = _threshold(model_v3)

    def collect_v1(
        record: dict[str, object], predicted_spans: Sequence[DecodedSponsorSpan]
    ) -> None:
        example_id = str(record["example_id"])
        records[example_id] = record
        v1_predictions[example_id] = tuple(
            span for span in predicted_spans if span.confidence >= v1_threshold
        )

    def collect_v3(
        record: dict[str, object], predicted_spans: Sequence[DecodedSponsorSpan]
    ) -> None:
        example_id = str(record["example_id"])
        if example_id not in records:
            raise ValueError("model inference traversed different dataset rows")
        v3_predictions[example_id] = tuple(
            span for span in predicted_spans if span.confidence >= v3_threshold
        )

    def progress(model_name: str):
        if progress_callback is None:
            return None
        return lambda completed, total, elapsed: progress_callback(
            model_name, completed, total, elapsed
        )

    runtime_v1 = run_checkpoint_inference(
        Path(model_v1["checkpoint_path"]),
        test_path,
        batch_size=int(audit.get("batch_size", 16)),
        max_length=int(model_v1.get("max_length", 1024)),
        bf16=bool(audit.get("bf16", True)),
        prediction_callback=collect_v1,
        progress_callback=progress("v1"),
    )
    runtime_v3 = run_checkpoint_inference(
        Path(model_v3["checkpoint_path"]),
        test_path,
        batch_size=int(audit.get("batch_size", 16)),
        max_length=int(model_v3.get("max_length", 1024)),
        bf16=bool(audit.get("bf16", True)),
        prediction_callback=collect_v3,
        progress_callback=progress("v3"),
    )

    counts: Counter[str] = Counter()
    quality_counts: Counter[str] = Counter()
    examples: dict[str, list[tuple[float, dict[str, object]]]] = {
        "v3_removed_false_positive": [],
        "v3_introduced_false_negative": [],
        "v3_recovered_positive": [],
        "v3_added_false_positive": [],
        "v3_better_coverage": [],
        "v3_worse_coverage": [],
    }
    for example_id, record in records.items():
        v1_spans = v1_predictions[example_id]
        v3_spans = v3_predictions[example_id]
        expected = [
            (int(span["start_char"]), int(span["end_char"]))
            for span in record["sponsor_spans"]
        ]
        category = presence_disagreement(
            v1_spans,
            v3_spans,
            expected_positive=bool(expected),
        )
        counts[category] += 1
        v1_quality = _coverage_metrics(v1_spans, expected)
        v3_quality = _coverage_metrics(v3_spans, expected)
        delta = float(v3_quality["f1"]) - float(v1_quality["f1"])
        quality_category = (
            "v3_better_coverage"
            if delta > 1e-12
            else "v3_worse_coverage"
            if delta < -1e-12
            else "equal_coverage"
        )
        quality_counts[quality_category] += 1
        record_for_report = {
            "example_id": example_id,
            "video_id": str(record["video_id"]),
            "label_kind": str(record["label_kind"]),
            "text": str(record["text"]),
            "expected_spans": [
                {"start_char": start, "end_char": end} for start, end in expected
            ],
            "v1_spans": _serialized(v1_spans),
            "v3_spans": _serialized(v3_spans),
            "v1_coverage": v1_quality,
            "v3_coverage": v3_quality,
            "coverage_f1_delta": delta,
        }
        if category != "same_presence":
            confidence = max(
                (span.confidence for span in (*v1_spans, *v3_spans)),
                default=0.0,
            )
            examples[category].append((confidence, record_for_report))
        if quality_category != "equal_coverage":
            examples[quality_category].append((abs(delta), record_for_report))

    limit = int(audit.get("example_limit", 100))
    report = {
        "schema_version": 1,
        "dataset": {
            "path": str(test_path),
            "sha256": manifest["outputs"]["test"]["sha256"],
            "rows": len(records),
        },
        "models": {
            "v1": {
                "checkpoint_path": str(model_v1["checkpoint_path"]),
                "confidence_threshold": v1_threshold,
                "runtime": runtime_v1,
            },
            "v3": {
                "checkpoint_path": str(model_v3["checkpoint_path"]),
                "confidence_threshold": v3_threshold,
                "runtime": runtime_v3,
            },
        },
        "presence_disagreements": dict(counts),
        "coverage_comparison": dict(quality_counts),
        "examples": {
            category: [
                record
                for _, record in sorted(scored, key=lambda item: item[0], reverse=True)[
                    :limit
                ]
            ]
            for category, scored in examples.items()
        },
    }
    write_json_atomic(Path(audit["report_path"]), report)
    return report
