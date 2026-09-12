from __future__ import annotations

import json
import tomllib
from datetime import UTC, datetime
from pathlib import Path
from typing import Sequence

from sponsor_detection.data.profile import sha256_file, write_json_atomic
from sponsor_detection.evaluation import EvaluationAccumulator, run_checkpoint_inference
from sponsor_detection.model.decoder import DecodedSponsorSpan


def select_operating_point(
    results: Sequence[dict[str, object]],
    *,
    minimum_window_precision: float,
    minimum_span_precision: float,
) -> dict[str, object]:
    eligible = [
        result
        for result in results
        if result["window_presence"]["precision"] >= minimum_window_precision
        and result["span_iou_0.5"]["precision"] >= minimum_span_precision
    ]
    if not eligible:
        raise ValueError("no confidence threshold satisfies the precision constraints")
    return max(
        eligible,
        key=lambda result: (
            result["span_iou_0.5"]["f1"],
            result["window_presence"]["f1"],
            -result["confidence_threshold"],
        ),
    )


def calibrate_from_config(path: Path, *, progress_callback=None) -> dict[str, object]:
    with path.open("rb") as source:
        configuration = tomllib.load(source)
    dataset = configuration["dataset"]
    model_configuration = configuration["model"]
    calibration = configuration["calibration"]
    manifest_path = Path(dataset["manifest_path"])
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    validation_path = Path(dataset["validation_path"])
    if sha256_file(validation_path) != manifest["outputs"]["validation"]["sha256"]:
        raise ValueError("validation dataset does not match its manifest")

    thresholds = sorted({float(value) for value in calibration["confidence_thresholds"]})
    if not thresholds or any(not 0 <= threshold <= 1 for threshold in thresholds):
        raise ValueError("confidence thresholds must be between zero and one")
    accumulators = {
        threshold: EvaluationAccumulator(error_limit=0) for threshold in thresholds
    }

    def add_prediction(
        record: dict[str, object], predicted_spans: Sequence[DecodedSponsorSpan]
    ) -> None:
        expected_spans = [
            (int(span["start_char"]), int(span["end_char"]))
            for span in record["sponsor_spans"]
        ]
        for threshold, accumulator in accumulators.items():
            accumulator.add(
                example_id=str(record["example_id"]),
                video_id=str(record["video_id"]),
                label_kind=str(record["label_kind"]),
                text=str(record["text"]),
                predicted_spans=tuple(
                    span for span in predicted_spans if span.confidence >= threshold
                ),
                expected_spans=expected_spans,
            )

    checkpoint_path = Path(model_configuration["checkpoint_path"])
    runtime = run_checkpoint_inference(
        checkpoint_path,
        validation_path,
        batch_size=int(calibration.get("batch_size", 16)),
        max_length=int(model_configuration.get("max_length", 1024)),
        bf16=bool(calibration.get("bf16", True)),
        prediction_callback=add_prediction,
        progress_callback=progress_callback,
    )
    results = []
    for threshold, accumulator in accumulators.items():
        metrics = accumulator.report()
        results.append(
            {
                "confidence_threshold": threshold,
                "window_presence": metrics["window_presence"],
                "span_iou_0.5": metrics["span_metrics_by_character_iou"]["0.5"],
                "character_coverage": metrics["character_coverage"],
                "predicted_span_count": metrics["predicted_span_confidence"]["count"],
            }
        )
    minimum_window_precision = float(calibration["minimum_window_precision"])
    minimum_span_precision = float(calibration["minimum_span_precision_iou_05"])
    selected = select_operating_point(
        results,
        minimum_window_precision=minimum_window_precision,
        minimum_span_precision=minimum_span_precision,
    )
    report = {
        "schema_version": 1,
        "generated_at": datetime.now(tz=UTC).isoformat(),
        "checkpoint": {
            "path": str(checkpoint_path),
            "model_sha256": sha256_file(checkpoint_path / "model.safetensors"),
        },
        "validation_dataset": {
            "path": str(validation_path),
            "sha256": manifest["outputs"]["validation"]["sha256"],
            "manifest_sha256": sha256_file(manifest_path),
            "rows": runtime["rows"],
        },
        "selection_policy": {
            "objective": "maximize_span_f1_at_character_iou_0.5",
            "minimum_window_precision": minimum_window_precision,
            "minimum_span_precision_iou_0.5": minimum_span_precision,
            "tie_breaker": "window_f1_then_lower_threshold",
        },
        "selected": selected,
        "sweep": results,
        "runtime": {key: value for key, value in runtime.items() if key != "rows"},
    }
    decoder_configuration = {
        "schema_version": 1,
        "confidence_threshold": selected["confidence_threshold"],
        "checkpoint": report["checkpoint"],
        "calibration_report_path": str(calibration["report_path"]),
        "calibration_report_metrics": {
            "window_presence": selected["window_presence"],
            "span_iou_0.5": selected["span_iou_0.5"],
        },
        "validation_dataset": report["validation_dataset"],
    }
    write_json_atomic(Path(calibration["report_path"]), report)
    write_json_atomic(Path(calibration["decoder_config_path"]), decoder_configuration)
    return report
