from __future__ import annotations

import hashlib
import json
from collections import Counter
from datetime import UTC, datetime
from pathlib import Path

from sponsor_detection.data.benchmark import TARGET_CLASSES, write_json_lines_atomic
from sponsor_detection.data.profile import sha256_file, write_json_atomic


def load_verified_jsonl(
    path: Path, manifest_path: Path
) -> tuple[list[dict[str, object]], dict[str, object]]:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    expected_sha256 = manifest.get("output", {}).get("sha256")
    if not expected_sha256 or sha256_file(path) != expected_sha256:
        raise ValueError(f"JSONL file does not match its manifest: {path}")
    records = []
    with path.open("r", encoding="utf-8") as source:
        for line_number, line in enumerate(source, start=1):
            try:
                record = json.loads(line)
            except json.JSONDecodeError as error:
                raise ValueError(f"invalid JSON at {path}:{line_number}") from error
            if not isinstance(record, dict) or "video_id" not in record:
                raise ValueError(f"invalid record at {path}:{line_number}")
            records.append(record)
    return records, manifest


def _load_metadata(path: Path) -> dict[str, dict[str, object]]:
    records: dict[str, dict[str, object]] = {}
    with path.open("r", encoding="utf-8") as source:
        for line_number, line in enumerate(source, start=1):
            try:
                record = json.loads(line)
                records[str(record["video_id"])] = record
            except (KeyError, TypeError, json.JSONDecodeError) as error:
                raise ValueError(f"invalid metadata at {path}:{line_number}") from error
    return records


def _training_channel_ids(directory: Path) -> set[str]:
    try:
        import pyarrow.parquet as pq
    except ImportError as error:
        raise RuntimeError(
            "Benchmark review preparation requires the data extra: pip install -e '.[data]'"
        ) from error

    channel_ids: set[str] = set()
    paths = sorted(directory.glob("*.parquet"))
    if not paths:
        raise ValueError(f"training directory contains no Parquet splits: {directory}")
    for path in paths:
        for batch in pq.ParquetFile(path).iter_batches(
            columns=["channel_id"], batch_size=131_072
        ):
            channel_ids.update(
                str(value)
                for value in batch.column("channel_id").to_pylist()
                if value
            )
    return channel_ids


def _transcript_record(path: Path) -> tuple[dict[str, object], str]:
    record = json.loads(path.read_text(encoding="utf-8"))
    if str(record.get("video_id")) != path.stem:
        raise ValueError(f"transcript video ID does not match filename: {path}")
    return record, sha256_file(path)


def _is_english_transcript(language_code: object) -> bool:
    normalized = str(language_code).strip().lower().removeprefix("asr-")
    return normalized.partition("-")[0] == "en"


def prepare_benchmark_review(
    candidates_path: Path,
    candidates_manifest_path: Path,
    transcript_directory: Path,
    metadata_path: Path,
    training_directory: Path,
    output_path: Path,
    manifest_path: Path,
    *,
    target_per_class: int = 20,
) -> dict[str, object]:
    if target_per_class <= 0:
        raise ValueError("target per class must be positive")
    candidates, candidates_manifest = load_verified_jsonl(
        candidates_path, candidates_manifest_path
    )
    metadata_by_id = _load_metadata(metadata_path)
    training_channels = _training_channel_ids(training_directory)
    selected_channels: set[str] = set()
    selected_counts: Counter[str] = Counter()
    rejection_counts: Counter[str] = Counter()
    review_records: list[dict[str, object]] = []

    for candidate in candidates:
        benchmark_class = str(candidate.get("benchmark_class"))
        if benchmark_class not in TARGET_CLASSES:
            rejection_counts["unknown_candidate_class"] += 1
            continue
        if selected_counts[benchmark_class] >= target_per_class:
            continue
        video_id = str(candidate["video_id"])
        transcript_path = transcript_directory / f"{video_id}.json"
        if not transcript_path.is_file():
            rejection_counts["missing_transcript"] += 1
            continue
        transcript, transcript_sha256 = _transcript_record(transcript_path)
        if not _is_english_transcript(transcript.get("language_code")):
            rejection_counts["non_english_transcript"] += 1
            continue
        metadata = metadata_by_id.get(video_id)
        if metadata is None:
            rejection_counts["missing_metadata"] += 1
            continue
        if metadata.get("privacy_status") != "public":
            rejection_counts["not_public"] += 1
            continue
        channel_id = metadata.get("channel_id")
        if not channel_id:
            rejection_counts["missing_channel"] += 1
            continue
        channel_id = str(channel_id)
        if channel_id in training_channels:
            rejection_counts["training_channel_overlap"] += 1
            continue
        if channel_id in selected_channels:
            rejection_counts["pilot_channel_overlap"] += 1
            continue

        selected_channels.add(channel_id)
        selected_counts[benchmark_class] += 1
        review_records.append(
            {
                "schema_version": 1,
                "video_id": video_id,
                "candidate_class": benchmark_class,
                "selection_rank": int(candidate["selection_rank"]),
                "split": "pilot",
                "channel_id": channel_id,
                "published_at": metadata.get("published_at"),
                "duration_ms": metadata.get("duration_ms"),
                "transcript": {
                    "path": str(transcript_path),
                    "sha256": transcript_sha256,
                    "language_code": transcript.get("language_code"),
                    "is_generated": transcript.get("is_generated"),
                    "cues": len(transcript.get("cues", [])),
                },
                "source_categories": candidate.get("source_categories", []),
                "source_segments": candidate.get("segments", []),
                "model_predictions": [],
                "annotation": {
                    "status": "pending",
                    "final_class": None,
                    "intervals": [],
                    "notes": "",
                },
            }
        )

    write_json_lines_atomic(output_path, review_records)
    deficits = {
        benchmark_class: target_per_class - selected_counts[benchmark_class]
        for benchmark_class in TARGET_CLASSES
        if selected_counts[benchmark_class] < target_per_class
    }
    report = {
        "schema_version": 1,
        "generated_at": datetime.now(tz=UTC).isoformat(),
        "status": "annotation_pending" if not deficits else "acquisition_incomplete",
        "source_candidates_sha256": candidates_manifest["output"]["sha256"],
        "selection": {
            "target_per_class": target_per_class,
            "required_total": target_per_class * len(TARGET_CLASSES),
            "channel_disjoint_from_training": True,
            "unique_channel_within_pilot": True,
        },
        "counts": {
            "selected": len(review_records),
            "selected_by_class": dict(selected_counts),
            "deficits_by_class": deficits,
            "rejections": dict(rejection_counts),
        },
        "output": {
            "path": str(output_path),
            "bytes": output_path.stat().st_size,
            "sha256": sha256_file(output_path),
        },
    }
    write_json_atomic(manifest_path, report)
    return report


def _validated_intervals(record: dict[str, object]) -> list[dict[str, object]]:
    annotation = record.get("annotation")
    if not isinstance(annotation, dict) or annotation.get("status") != "reviewed":
        raise ValueError(f"video {record['video_id']} has not been reviewed")
    final_class = annotation.get("final_class")
    if final_class not in TARGET_CLASSES:
        raise ValueError(f"video {record['video_id']} has an invalid final class")
    raw_intervals = annotation.get("intervals")
    if not isinstance(raw_intervals, list):
        raise ValueError(f"video {record['video_id']} intervals must be a list")
    intervals: list[dict[str, object]] = []
    for interval in raw_intervals:
        if not isinstance(interval, dict):
            raise ValueError(f"video {record['video_id']} contains an invalid interval")
        start_ms = interval.get("start_ms")
        end_ms = interval.get("end_ms")
        if (
            not isinstance(start_ms, int)
            or isinstance(start_ms, bool)
            or not isinstance(end_ms, int)
            or isinstance(end_ms, bool)
            or start_ms < 0
            or end_ms <= start_ms
        ):
            raise ValueError(f"video {record['video_id']} contains an invalid interval")
        intervals.append(
            {
                "start_ms": start_ms,
                "end_ms": end_ms,
                "category": "external_sponsor",
                "confidence": interval.get("confidence", "reviewed"),
            }
        )
    if final_class == "sponsor_positive" and not intervals:
        raise ValueError(f"positive video {record['video_id']} has no sponsor interval")
    if final_class != "sponsor_positive" and intervals:
        raise ValueError(f"negative video {record['video_id']} has sponsor intervals")
    intervals.sort(key=lambda interval: (interval["start_ms"], interval["end_ms"]))
    return intervals


def freeze_reviewed_benchmark(
    review_path: Path,
    review_manifest_path: Path,
    output_path: Path,
    manifest_path: Path,
    *,
    target_per_class: int = 20,
) -> dict[str, object]:
    records, review_manifest = load_verified_jsonl(review_path, review_manifest_path)
    selected_counts: Counter[str] = Counter()
    frozen_records: list[dict[str, object]] = []
    transcript_digest = hashlib.sha256()
    for record in records:
        intervals = _validated_intervals(record)
        final_class = str(record["annotation"]["final_class"])
        if selected_counts[final_class] >= target_per_class:
            continue
        selected_counts[final_class] += 1
        transcript = record["transcript"]
        transcript_digest.update(
            f"{record['video_id']}\0{transcript['sha256']}\n".encode("utf-8")
        )
        frozen_records.append(
            {
                "schema_version": 1,
                "video_id": record["video_id"],
                "benchmark_class": final_class,
                "split": record["split"],
                "channel_id": record["channel_id"],
                "published_at": record.get("published_at"),
                "duration_ms": record.get("duration_ms"),
                "transcript": transcript,
                "intervals": intervals,
            }
        )
    deficits = {
        benchmark_class: target_per_class - selected_counts[benchmark_class]
        for benchmark_class in TARGET_CLASSES
        if selected_counts[benchmark_class] < target_per_class
    }
    if deficits:
        raise ValueError(f"insufficient reviewed benchmark videos: {deficits}")

    write_json_lines_atomic(output_path, frozen_records)
    report = {
        "schema_version": 1,
        "generated_at": datetime.now(tz=UTC).isoformat(),
        "status": "frozen",
        "source_review_sha256": review_manifest["output"]["sha256"],
        "transcript_set_sha256": transcript_digest.hexdigest(),
        "counts": {
            "videos": len(frozen_records),
            "by_class": dict(selected_counts),
            "sponsor_intervals": sum(len(record["intervals"]) for record in frozen_records),
        },
        "output": {
            "path": str(output_path),
            "bytes": output_path.stat().st_size,
            "sha256": sha256_file(output_path),
        },
    }
    write_json_atomic(manifest_path, report)
    return report
