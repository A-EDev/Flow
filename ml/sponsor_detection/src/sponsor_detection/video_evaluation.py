from __future__ import annotations

import hashlib
import json
import tomllib
from collections import Counter, defaultdict
from pathlib import Path
from time import perf_counter
from typing import Sequence

from sponsor_detection.data.profile import sha256_file, write_json_atomic
from sponsor_detection.evaluation import character_overlap, match_spans
from sponsor_detection.inference.pipeline import FullTranscriptSponsorDetector
from sponsor_detection.inference.windowing import TranscriptCue


Interval = tuple[int, int]
TEMPORAL_IOU_THRESHOLDS = (0.3, 0.5, 0.7)


def _ratio(numerator: int, denominator: int) -> float:
    return numerator / denominator if denominator else 0.0


def _f1(precision: float, recall: float) -> float:
    return 2 * precision * recall / (precision + recall) if precision + recall else 0.0


def _percentile(values: Sequence[int], fraction: float) -> int | None:
    if not values:
        return None
    ordered = sorted(values)
    return ordered[round((len(ordered) - 1) * fraction)]


def temporal_span_counts(
    predicted: Sequence[Interval], expected: Sequence[Interval], *, iou_threshold: float
) -> Counter[str]:
    matches = match_spans(predicted, expected, iou_threshold=iou_threshold)
    return Counter(
        true_positive=len(matches),
        false_positive=len(predicted) - len(matches),
        false_negative=len(expected) - len(matches),
    )


def _load_expected_segments(path: Path, video_ids: set[str]) -> dict[str, list[Interval]]:
    import pyarrow.dataset as dataset

    source = dataset.dataset(path, format="parquet")
    table = source.to_table(
        columns=["video_id", "start_ms", "end_ms", "is_eligible"],
        filter=dataset.field("video_id").isin(sorted(video_ids)),
    )
    segments: dict[str, list[Interval]] = defaultdict(list)
    for row in table.to_pylist():
        if row["is_eligible"]:
            segments[str(row["video_id"])].append(
                (int(row["start_ms"]), int(row["end_ms"]))
            )
    for video_segments in segments.values():
        video_segments.sort()
    return segments


def load_frozen_benchmark(
    benchmark_path: Path, manifest_path: Path
) -> tuple[list[Path], dict[str, list[Interval]], dict[str, object]]:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("status") != "frozen":
        raise ValueError("benchmark manifest is not frozen")
    expected_sha256 = manifest.get("output", {}).get("sha256")
    if not expected_sha256 or sha256_file(benchmark_path) != expected_sha256:
        raise ValueError("benchmark JSONL does not match its manifest")

    transcript_paths: list[Path] = []
    expected_by_video: dict[str, list[Interval]] = {}
    classes: Counter[str] = Counter()
    with benchmark_path.open("r", encoding="utf-8") as source:
        for line_number, line in enumerate(source, start=1):
            try:
                record = json.loads(line)
                video_id = str(record["video_id"])
                benchmark_class = str(record["benchmark_class"])
                transcript = record["transcript"]
                transcript_path = Path(transcript["path"])
                intervals = [
                    (int(interval["start_ms"]), int(interval["end_ms"]))
                    for interval in record["intervals"]
                ]
            except (KeyError, TypeError, ValueError, json.JSONDecodeError) as error:
                raise ValueError(
                    f"invalid frozen benchmark record at line {line_number}"
                ) from error
            if video_id in expected_by_video:
                raise ValueError(f"duplicate benchmark video: {video_id}")
            if transcript_path.stem != video_id or not transcript_path.is_file():
                raise ValueError(f"missing benchmark transcript: {video_id}")
            if sha256_file(transcript_path) != transcript.get("sha256"):
                raise ValueError(f"benchmark transcript checksum mismatch: {video_id}")
            if benchmark_class == "sponsor_positive" and not intervals:
                raise ValueError(f"positive benchmark video has no intervals: {video_id}")
            if benchmark_class != "sponsor_positive" and intervals:
                raise ValueError(f"negative benchmark video has intervals: {video_id}")
            transcript_paths.append(transcript_path)
            expected_by_video[video_id] = sorted(intervals)
            classes[benchmark_class] += 1
    if not transcript_paths:
        raise ValueError("frozen benchmark contains no videos")
    return (
        transcript_paths,
        expected_by_video,
        {
            "benchmark_path": str(benchmark_path),
            "benchmark_sha256": expected_sha256,
            "benchmark_manifest_path": str(manifest_path),
            "benchmark_classes": dict(classes),
        },
    )


def evaluate_full_videos_with_detector(
    transcript_paths: Sequence[Path],
    expected_by_video: dict[str, list[Interval]],
    detector,
    dataset_provenance: dict[str, object],
    *,
    checkpoint_provenance: dict[str, object],
    decoder_config_path: Path | None,
    report_path: Path,
    progress_callback=None,
) -> dict[str, object]:
    threshold_counts = {
        threshold: Counter() for threshold in TEMPORAL_IOU_THRESHOLDS
    }
    video_counts: Counter[str] = Counter()
    coverage_counts: Counter[str] = Counter()
    start_errors: list[int] = []
    end_errors: list[int] = []
    inference_seconds: list[float] = []
    video_reports = []
    transcript_digest = hashlib.sha256()
    for index, transcript_path in enumerate(transcript_paths, start=1):
        transcript_sha = sha256_file(transcript_path)
        transcript_digest.update(f"{transcript_path.stem}\0{transcript_sha}\n".encode())
        transcript = json.loads(transcript_path.read_text(encoding="utf-8"))
        cues = [
            TranscriptCue(
                index=int(cue["index"]),
                start_ms=int(cue["start_ms"]),
                end_ms=int(cue["end_ms"]),
                text=str(cue["text"]),
            )
            for cue in transcript["cues"]
        ]
        started_at = perf_counter()
        prediction = detector.predict(cues)
        elapsed_seconds = perf_counter() - started_at
        inference_seconds.append(elapsed_seconds)
        predicted = sorted(
            (span.start_ms, span.end_ms) for span in prediction.sponsor_spans
        )
        expected = expected_by_video.get(transcript_path.stem, [])
        predicted_positive = bool(predicted)
        expected_positive = bool(expected)
        outcome = (
            "true_positive"
            if predicted_positive and expected_positive
            else "false_positive"
            if predicted_positive
            else "false_negative"
            if expected_positive
            else "true_negative"
        )
        video_counts[outcome] += 1
        matches_at_half = ()
        for threshold in TEMPORAL_IOU_THRESHOLDS:
            matches = match_spans(predicted, expected, iou_threshold=threshold)
            threshold_counts[threshold].update(
                true_positive=len(matches),
                false_positive=len(predicted) - len(matches),
                false_negative=len(expected) - len(matches),
            )
            if threshold == 0.5:
                matches_at_half = matches
        for predicted_index, expected_index, _ in matches_at_half:
            start_errors.append(
                abs(predicted[predicted_index][0] - expected[expected_index][0])
            )
            end_errors.append(
                abs(predicted[predicted_index][1] - expected[expected_index][1])
            )
        predicted_duration = sum(end - start for start, end in predicted)
        expected_duration = sum(end - start for start, end in expected)
        overlap_duration = character_overlap(predicted, expected)
        coverage_counts.update(
            predicted=predicted_duration,
            expected=expected_duration,
            overlap=overlap_duration,
        )
        video_reports.append(
            {
                "video_id": transcript_path.stem,
                "transcript_sha256": transcript_sha,
                "cues": len(cues),
                "windows": len(prediction.windows),
                "inference_seconds": elapsed_seconds,
                "expected_spans": [
                    {"start_ms": start, "end_ms": end} for start, end in expected
                ],
                "predicted_spans": [
                    {
                        "start_ms": span.start_ms,
                        "end_ms": span.end_ms,
                        "confidence": span.confidence,
                        "supporting_windows": list(span.supporting_windows),
                    }
                    for span in prediction.sponsor_spans
                ],
                "presence_outcome": outcome,
            }
        )
        if progress_callback:
            progress_callback(index, len(transcript_paths), transcript_path.stem)

    span_metrics = {}
    for threshold, counts in threshold_counts.items():
        precision = _ratio(
            counts["true_positive"],
            counts["true_positive"] + counts["false_positive"],
        )
        recall = _ratio(
            counts["true_positive"],
            counts["true_positive"] + counts["false_negative"],
        )
        span_metrics[str(threshold)] = {
            **dict(counts),
            "precision": precision,
            "recall": recall,
            "f1": _f1(precision, recall),
        }
    video_precision = _ratio(
        video_counts["true_positive"],
        video_counts["true_positive"] + video_counts["false_positive"],
    )
    video_recall = _ratio(
        video_counts["true_positive"],
        video_counts["true_positive"] + video_counts["false_negative"],
    )
    coverage_precision = _ratio(
        coverage_counts["overlap"], coverage_counts["predicted"]
    )
    coverage_recall = _ratio(
        coverage_counts["overlap"], coverage_counts["expected"]
    )
    report = {
        "schema_version": 1,
        "checkpoint": checkpoint_provenance,
        "decoder": {
            "config_path": str(decoder_config_path) if decoder_config_path else None,
            "confidence_threshold": detector.confidence_threshold,
        },
        "windowing": {
            "max_length": detector.max_length,
            "overlap_tokens": detector.overlap_tokens,
            "merge_gap_characters": detector.merge_gap_characters,
            "merge_gap_ms": detector.merge_gap_ms,
        },
        "dataset": {
            **dataset_provenance,
            "transcript_set_sha256": transcript_digest.hexdigest(),
            "videos": len(transcript_paths),
        },
        "runtime": {
            "total_inference_seconds": sum(inference_seconds),
            "per_video_median_seconds": _float_percentile(inference_seconds, 0.5),
            "per_video_p95_seconds": _float_percentile(inference_seconds, 0.95),
            "total_windows": sum(record["windows"] for record in video_reports),
        },
        "video_presence": {
            **dict(video_counts),
            "precision": video_precision,
            "recall": video_recall,
            "f1": _f1(video_precision, video_recall),
        },
        "span_metrics_by_temporal_iou": span_metrics,
        "temporal_coverage": {
            **dict(coverage_counts),
            "precision": coverage_precision,
            "recall": coverage_recall,
            "f1": _f1(coverage_precision, coverage_recall),
        },
        "boundary_error_ms_at_iou_0.5": {
            "matched_spans": len(start_errors),
            "start_median": _percentile(start_errors, 0.5),
            "start_p95": _percentile(start_errors, 0.95),
            "end_median": _percentile(end_errors, 0.5),
            "end_p95": _percentile(end_errors, 0.95),
        },
        "videos": video_reports,
    }
    write_json_atomic(report_path, report)
    return report


def _float_percentile(values: Sequence[float], fraction: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    return ordered[round((len(ordered) - 1) * fraction)]


def evaluate_full_videos_from_config(
    path: Path, *, progress_callback=None
) -> dict[str, object]:
    with path.open("rb") as source:
        configuration = tomllib.load(source)
    dataset_configuration = configuration["dataset"]
    model_configuration = configuration["model"]
    evaluation = configuration["evaluation"]
    benchmark_path_value = dataset_configuration.get("benchmark_path")
    if benchmark_path_value:
        transcript_paths, expected_by_video, dataset_provenance = load_frozen_benchmark(
            Path(benchmark_path_value),
            Path(dataset_configuration["benchmark_manifest_path"]),
        )
        transcript_directory = None
    else:
        transcript_directory = Path(dataset_configuration["transcript_directory"])
        transcript_paths = sorted(
            path
            for path in transcript_directory.glob("*.json")
            if path.name != "state.json"
        )
        if not transcript_paths:
            raise ValueError("transcript directory contains no video transcripts")
        video_ids = {path.stem for path in transcript_paths}
        labels_path = Path(dataset_configuration["labels_path"])
        labels_manifest_path = Path(dataset_configuration["labels_manifest_path"])
        labels_manifest = json.loads(labels_manifest_path.read_text(encoding="utf-8"))
        if sha256_file(labels_path) != labels_manifest["output"]["sha256"]:
            raise ValueError("sponsor labels do not match their manifest")
        expected_by_video = _load_expected_segments(labels_path, video_ids)
        dataset_provenance = {
            "transcript_directory": str(transcript_directory),
            "labels_sha256": sha256_file(labels_path),
        }

    checkpoint_path = Path(model_configuration["checkpoint_path"])
    decoder_config_value = model_configuration.get("decoder_config_path")
    if decoder_config_value:
        decoder_config_path = Path(decoder_config_value)
        decoder_configuration = json.loads(
            decoder_config_path.read_text(encoding="utf-8")
        )
        confidence_threshold = float(decoder_configuration["confidence_threshold"])
        expected_model_sha256 = str(
            decoder_configuration["checkpoint"]["model_sha256"]
        )
        checkpoint_provenance = decoder_configuration["checkpoint"]
    else:
        decoder_config_path = None
        confidence_threshold = float(model_configuration["confidence_threshold"])
        expected_model_sha256 = str(model_configuration["model_sha256"])
        checkpoint_provenance = {
            "path": str(checkpoint_path),
            "model_sha256": expected_model_sha256,
        }
    if sha256_file(checkpoint_path / "model.safetensors") != expected_model_sha256:
        raise ValueError("decoder configuration does not match the checkpoint")
    detector = FullTranscriptSponsorDetector(
        checkpoint_path,
        confidence_threshold=confidence_threshold,
        max_length=int(model_configuration.get("max_length", 768)),
        overlap_tokens=int(model_configuration.get("overlap_tokens", 128)),
        batch_size=int(evaluation.get("batch_size", 16)),
        bf16=bool(evaluation.get("bf16", True)),
        merge_gap_characters=int(evaluation.get("merge_gap_characters", 24)),
        merge_gap_ms=int(evaluation.get("merge_gap_ms", 1500)),
    )

    return evaluate_full_videos_with_detector(
        transcript_paths,
        expected_by_video,
        detector,
        dataset_provenance,
        checkpoint_provenance=checkpoint_provenance,
        decoder_config_path=decoder_config_path,
        report_path=Path(evaluation["report_path"]),
        progress_callback=progress_callback,
    )
