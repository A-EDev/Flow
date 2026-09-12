from __future__ import annotations

import hashlib
import json
import math
import re
import time
from collections import Counter
from datetime import UTC, datetime
from pathlib import Path
from typing import Iterable

from sponsor_detection.data.profile import sha256_file, write_json_atomic


VIDEO_ID_PATTERN = re.compile(r"^[A-Za-z0-9_-]{11}$")
CATEGORY_PATTERN = re.compile(r"START_([A-Z]+)_TOKEN")
SPLIT_NAMES = ("train", "valid", "test")
REQUIRED_ROW_FIELDS = frozenset(
    {"video_index", "video_id", "text", "start", "end", "extracted"}
)


def _percentiles(values: list[float]) -> dict[str, float | None]:
    if not values:
        return {"minimum": None, "p50": None, "p95": None, "p99": None, "maximum": None}
    values.sort()

    def percentile(fraction: float) -> float:
        index = min(len(values) - 1, math.floor(fraction * (len(values) - 1)))
        return round(values[index], 3)

    return {
        "minimum": round(values[0], 3),
        "p50": percentile(0.50),
        "p95": percentile(0.95),
        "p99": percentile(0.99),
        "maximum": round(values[-1], 3),
    }


def _record_fingerprint(record: dict[str, object]) -> bytes:
    digest = hashlib.blake2b(digest_size=16)
    for field in ("video_id", "start", "end", "text", "extracted"):
        digest.update(str(record.get(field)).encode("utf-8"))
        digest.update(b"\0")
    return digest.digest()


def _profile_split(
    path: Path,
    *,
    all_fingerprints: set[bytes],
    window_labels: dict[tuple[str, object, object], str],
) -> tuple[dict[str, object], set[str]]:
    rows = invalid_json = missing_fields = invalid_video_ids = 0
    invalid_timestamps = empty_text = exact_duplicates = conflicting_windows = 0
    categories: Counter[str] = Counter()
    label_shapes: Counter[str] = Counter()
    videos: set[str] = set()
    video_indexes: set[int] = set()
    durations: list[float] = []
    text_lengths: list[float] = []

    with path.open("r", encoding="utf-8") as source:
        for line_number, line in enumerate(source, start=1):
            try:
                record = json.loads(line)
            except json.JSONDecodeError:
                invalid_json += 1
                continue
            if not isinstance(record, dict):
                invalid_json += 1
                continue
            rows += 1
            missing = REQUIRED_ROW_FIELDS.difference(record)
            if missing:
                missing_fields += 1
                continue

            video_id = str(record["video_id"])
            videos.add(video_id)
            if not VIDEO_ID_PATTERN.fullmatch(video_id):
                invalid_video_ids += 1
            if isinstance(record["video_index"], int):
                video_indexes.add(record["video_index"])

            text = str(record["text"])
            text_lengths.append(float(len(text)))
            if not text.strip():
                empty_text += 1

            start = record["start"]
            end = record["end"]
            if (
                not isinstance(start, (int, float))
                or isinstance(start, bool)
                or not isinstance(end, (int, float))
                or isinstance(end, bool)
                or start < 0
                or end <= start
            ):
                invalid_timestamps += 1
            else:
                durations.append(float(end - start))

            extracted = str(record["extracted"])
            found_categories = sorted(set(CATEGORY_PATTERN.findall(extracted)))
            if extracted == "NO_SEGMENT_TOKEN":
                label_shapes["negative"] += 1
            elif found_categories:
                label_shapes["single_category" if len(found_categories) == 1 else "multi_category"] += 1
                categories.update(found_categories)
            else:
                label_shapes["unknown"] += 1

            fingerprint = _record_fingerprint(record)
            if fingerprint in all_fingerprints:
                exact_duplicates += 1
            else:
                all_fingerprints.add(fingerprint)

            window = (video_id, start, end)
            previous_label = window_labels.get(window)
            if previous_label is not None and previous_label != extracted:
                conflicting_windows += 1
            else:
                window_labels[window] = extracted

    return (
        {
            "path": str(path),
            "bytes": path.stat().st_size,
            "rows": rows,
            "unique_video_ids": len(videos),
            "unique_video_indexes": len(video_indexes),
            "invalid_json_rows": invalid_json,
            "rows_missing_required_fields": missing_fields,
            "invalid_video_ids": invalid_video_ids,
            "invalid_timestamp_rows": invalid_timestamps,
            "empty_text_rows": empty_text,
            "exact_duplicates_seen_so_far": exact_duplicates,
            "conflicting_windows_seen_so_far": conflicting_windows,
            "label_shapes": dict(label_shapes),
            "category_rows": dict(categories),
            "window_duration_seconds": _percentiles(durations),
            "text_characters": _percentiles(text_lengths),
        },
        videos,
    )


def _profile_annotation_map(path: Path) -> tuple[dict[str, object], set[str]]:
    with path.open("r", encoding="utf-8") as source:
        document = json.load(source)
    if not isinstance(document, dict):
        raise ValueError(f"annotation file must contain an object: {path}")
    categories: Counter[str] = Counter()
    invalid_segments = 0
    segment_count = 0
    for segments in document.values():
        if not isinstance(segments, list):
            invalid_segments += 1
            continue
        for segment in segments:
            segment_count += 1
            if not isinstance(segment, dict):
                invalid_segments += 1
                continue
            categories[str(segment.get("category", "missing"))] += 1
            start = segment.get("start")
            end = segment.get("end")
            if (
                not isinstance(start, (int, float))
                or isinstance(start, bool)
                or not isinstance(end, (int, float))
                or isinstance(end, bool)
                or start < 0
                or end <= start
            ):
                invalid_segments += 1
    video_ids = {str(video_id) for video_id in document}
    return (
        {
            "path": str(path),
            "bytes": path.stat().st_size,
            "videos": len(video_ids),
            "segments": segment_count,
            "invalid_segments": invalid_segments,
            "categories": dict(categories),
        },
        video_ids,
    )


def _current_label_overlap(path: Path, dataset_videos: set[str]) -> dict[str, object]:
    import pyarrow.parquet as parquet

    file = parquet.ParquetFile(path)
    matched_videos: set[str] = set()
    eligible_videos: set[str] = set()
    matched_rows = eligible_rows = 0
    for batch in file.iter_batches(columns=["video_id", "is_eligible"], batch_size=131_072):
        video_ids = batch.column("video_id").to_pylist()
        eligibility = batch.column("is_eligible").to_pylist()
        for video_id, is_eligible in zip(video_ids, eligibility, strict=True):
            if video_id not in dataset_videos:
                continue
            matched_rows += 1
            matched_videos.add(video_id)
            if is_eligible:
                eligible_rows += 1
                eligible_videos.add(video_id)
    return {
        "path": str(path),
        "source_rows": file.metadata.num_rows,
        "matched_rows": matched_rows,
        "matched_videos": len(matched_videos),
        "eligible_rows": eligible_rows,
        "videos_with_eligible_current_labels": len(eligible_videos),
        "dataset_video_coverage": round(len(matched_videos) / len(dataset_videos), 6)
        if dataset_videos
        else None,
    }


def profile_xenova_dataset(
    dataset_directory: Path,
    report_path: Path,
    *,
    revision: str,
    expected_sha256: dict[str, str],
    current_labels_path: Path | None = None,
    progress_callback=None,
) -> dict[str, object]:
    started = time.monotonic()
    checksums: dict[str, dict[str, object]] = {}
    for filename, expected in expected_sha256.items():
        path = dataset_directory / filename
        if not path.is_file():
            raise FileNotFoundError(path)
        actual = sha256_file(path)
        checksums[filename] = {
            "expected": expected,
            "actual": actual,
            "matches": actual == expected,
        }
        if actual != expected:
            raise ValueError(f"checksum mismatch: {path}")

    split_reports: dict[str, object] = {}
    split_videos: dict[str, set[str]] = {}
    fingerprints: set[bytes] = set()
    window_labels: dict[tuple[str, object, object], str] = {}
    for split in SPLIT_NAMES:
        if progress_callback:
            progress_callback(f"profiling {split} split")
        split_report, videos = _profile_split(
            dataset_directory / f"{split}.json",
            all_fingerprints=fingerprints,
            window_labels=window_labels,
        )
        split_reports[split] = split_report
        split_videos[split] = videos

    all_videos = set().union(*split_videos.values())
    intersections = {
        "train_valid": len(split_videos["train"] & split_videos["valid"]),
        "train_test": len(split_videos["train"] & split_videos["test"]),
        "valid_test": len(split_videos["valid"] & split_videos["test"]),
    }

    annotation_reports: dict[str, object] = {}
    annotation_videos: dict[str, set[str]] = {}
    for filename in ("segments.json", "processed_database.json"):
        if progress_callback:
            progress_callback(f"profiling {filename}")
        report, videos = _profile_annotation_map(dataset_directory / filename)
        annotation_reports[filename] = report
        annotation_videos[filename] = videos

    current_overlap = None
    if current_labels_path is not None:
        if progress_callback:
            progress_callback("joining current SponsorBlock labels")
        current_overlap = _current_label_overlap(current_labels_path, all_videos)

    report = {
        "schema_version": 1,
        "generated_at": datetime.now(tz=UTC).isoformat(),
        "source": {
            "repository": "Xenova/sponsorblock-768",
            "revision": revision,
            "directory": str(dataset_directory),
            "checksums": checksums,
        },
        "splits": split_reports,
        "totals": {
            "rows": sum(int(split_reports[name]["rows"]) for name in SPLIT_NAMES),
            "unique_video_ids": len(all_videos),
            "exact_duplicate_rows": sum(
                int(split_reports[name]["exact_duplicates_seen_so_far"])
                for name in SPLIT_NAMES
            ),
            "conflicting_windows": sum(
                int(split_reports[name]["conflicting_windows_seen_so_far"])
                for name in SPLIT_NAMES
            ),
        },
        "split_video_overlap": intersections,
        "annotation_maps": annotation_reports,
        "coverage": {
            "dataset_videos_in_segments_map": len(all_videos & annotation_videos["segments.json"]),
            "dataset_videos_in_processed_database": len(
                all_videos & annotation_videos["processed_database.json"]
            ),
            "current_sponsorblock_labels": current_overlap,
        },
        "elapsed_seconds": round(time.monotonic() - started, 3),
    }
    write_json_atomic(report_path, report)
    return report


def xenova_video_ids(dataset_directory: Path) -> list[str]:
    video_ids: set[str] = set()
    for split in SPLIT_NAMES:
        with (dataset_directory / f"{split}.json").open("r", encoding="utf-8") as source:
            for line in source:
                record = json.loads(line)
                video_ids.add(str(record["video_id"]))
    return sorted(video_ids)
