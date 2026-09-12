from __future__ import annotations

import json
import tomllib
from collections import Counter
from pathlib import Path

from sponsor_detection.data.benchmark import TARGET_CLASSES, write_json_lines_atomic
from sponsor_detection.data.benchmark_review import load_verified_jsonl
from sponsor_detection.data.profile import sha256_file, write_json_atomic


def apply_benchmark_annotations(
    review_path: Path,
    review_manifest_path: Path,
    annotations_path: Path,
) -> dict[str, object]:
    records, manifest = load_verified_jsonl(review_path, review_manifest_path)
    annotations = json.loads(annotations_path.read_text(encoding="utf-8"))
    if not isinstance(annotations, dict):
        raise ValueError("benchmark annotations must be keyed by video ID")
    expected_ids = {str(record["video_id"]) for record in records}
    annotation_ids = {str(video_id) for video_id in annotations}
    if annotation_ids != expected_ids:
        missing = sorted(expected_ids - annotation_ids)
        unexpected = sorted(annotation_ids - expected_ids)
        raise ValueError(
            f"benchmark annotations do not match review records; "
            f"missing={missing}, unexpected={unexpected}"
        )

    counts: Counter[str] = Counter()
    interval_count = 0
    for record in records:
        video_id = str(record["video_id"])
        annotation = annotations[video_id]
        final_class = annotation.get("final_class")
        intervals = annotation.get("intervals")
        notes = annotation.get("notes", "")
        if final_class not in TARGET_CLASSES:
            raise ValueError(f"video {video_id} has an invalid final class")
        if not isinstance(intervals, list) or not isinstance(notes, str):
            raise ValueError(f"video {video_id} has an invalid annotation")
        duration_ms = record.get("duration_ms")
        for interval in intervals:
            start_ms = interval.get("start_ms") if isinstance(interval, dict) else None
            end_ms = interval.get("end_ms") if isinstance(interval, dict) else None
            if (
                not isinstance(start_ms, int)
                or isinstance(start_ms, bool)
                or not isinstance(end_ms, int)
                or isinstance(end_ms, bool)
                or start_ms < 0
                or end_ms <= start_ms
                or (isinstance(duration_ms, int) and end_ms > duration_ms)
            ):
                raise ValueError(f"video {video_id} contains an invalid interval")
        if final_class == "sponsor_positive" and not intervals:
            raise ValueError(f"positive video {video_id} has no sponsor interval")
        if final_class != "sponsor_positive" and intervals:
            raise ValueError(f"negative video {video_id} has sponsor intervals")
        record["annotation"] = {
            "status": "reviewed",
            "final_class": final_class,
            "intervals": intervals,
            "notes": notes,
        }
        counts[str(final_class)] += 1
        interval_count += len(intervals)

    source_review_sha256 = sha256_file(review_path)
    write_json_lines_atomic(review_path, records)
    manifest["status"] = "reviewed"
    manifest["annotations"] = {
        "source_review_sha256": source_review_sha256,
        "annotations_path": str(annotations_path),
        "annotations_sha256": sha256_file(annotations_path),
        "reviewed_videos": len(records),
        "reviewed_by_class": dict(counts),
        "sponsor_intervals": interval_count,
    }
    manifest["output"] = {
        "path": str(review_path),
        "bytes": review_path.stat().st_size,
        "sha256": sha256_file(review_path),
    }
    write_json_atomic(review_manifest_path, manifest)
    return manifest


def apply_benchmark_annotations_from_config(path: Path) -> dict[str, object]:
    with path.open("rb") as source:
        configuration = tomllib.load(source)["benchmark_annotations"]
    return apply_benchmark_annotations(
        Path(configuration["review_path"]),
        Path(configuration["review_manifest_path"]),
        Path(configuration["annotations_path"]),
    )
