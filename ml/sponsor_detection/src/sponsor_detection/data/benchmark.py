from __future__ import annotations

import csv
import hashlib
import heapq
import json
import os
import tempfile
import time
from collections import Counter
from datetime import UTC, datetime
from pathlib import Path
from typing import Callable, Iterable

from sponsor_detection.data.profile import sha256_file, write_json_atomic


HARD_NEGATIVE_CATEGORIES = frozenset(
    {"exclusive_access", "interaction", "selfpromo"}
)
ORDINARY_NEGATIVE_CATEGORIES = frozenset(
    {"filler", "hook", "intro", "music_offtopic", "outro", "poi_highlight", "preview"}
)
TARGET_CLASSES = ("sponsor_positive", "hard_negative", "ordinary_negative")


def _score(seed: str, namespace: str, video_id: str) -> int:
    digest = hashlib.blake2b(
        f"{seed}\0{namespace}\0{video_id}".encode("utf-8"), digest_size=8
    ).digest()
    return int.from_bytes(digest, "big")


class _BottomK:
    def __init__(self, capacity: int, seed: str, namespace: str) -> None:
        self.capacity = capacity
        self.seed = seed
        self.namespace = namespace
        self.heap: list[tuple[int, str]] = []
        self.records: dict[str, dict[str, object]] = {}

    def add(self, video_id: str, record: dict[str, object]) -> None:
        score = _score(self.seed, self.namespace, video_id)
        if video_id in self.records:
            self.records[video_id] = record
            return
        if len(self.heap) < self.capacity:
            heapq.heappush(self.heap, (-score, video_id))
        elif score < -self.heap[0][0]:
            _, evicted = heapq.heapreplace(self.heap, (-score, video_id))
            del self.records[evicted]
        else:
            return
        self.records[video_id] = record

    def sorted_records(self) -> list[dict[str, object]]:
        return sorted(
            self.records.values(),
            key=lambda record: _score(
                self.seed, self.namespace, str(record["video_id"])
            ),
        )


def write_json_lines_atomic(path: Path, records: Iterable[dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        dir=path.parent, prefix=f".{path.name}.", suffix=".tmp", text=True
    )
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as output:
            for record in records:
                json.dump(record, output, sort_keys=True, separators=(",", ":"))
                output.write("\n")
        os.replace(temporary_name, path)
    except BaseException:
        Path(temporary_name).unlink(missing_ok=True)
        raise


def _jsonl_video_ids(path: Path | None) -> set[str]:
    if path is None or not path.is_file():
        return set()
    video_ids: set[str] = set()
    with path.open("r", encoding="utf-8") as source:
        for line_number, line in enumerate(source, start=1):
            try:
                video_ids.add(str(json.loads(line)["video_id"]))
            except (KeyError, TypeError, json.JSONDecodeError) as error:
                raise ValueError(f"invalid JSONL record at {path}:{line_number}") from error
    return video_ids


def _parquet_video_ids(directory: Path) -> set[str]:
    try:
        import pyarrow.parquet as pq
    except ImportError as error:
        raise RuntimeError(
            "Benchmark sampling requires the data extra: pip install -e '.[data]'"
        ) from error

    video_ids: set[str] = set()
    paths = sorted(directory.glob("*.parquet"))
    if not paths:
        raise ValueError(f"training directory contains no Parquet splits: {directory}")
    for path in paths:
        parquet = pq.ParquetFile(path)
        for batch in parquet.iter_batches(columns=["video_id"], batch_size=131_072):
            video_ids.update(str(value) for value in batch.column("video_id").to_pylist())
    return video_ids


def _eligible_mirror_row(row: dict[str, str]) -> bool:
    try:
        return (
            row["service"] == "YouTube"
            and int(row["hidden"]) == 0
            and int(row["shadowHidden"]) == 0
            and int(row["votes"]) >= 0
            and float(row["startTime"]) >= 0
            and float(row["endTime"]) > float(row["startTime"])
        )
    except (KeyError, TypeError, ValueError):
        return False


def _positive_candidates(
    labels_path: Path,
    *,
    excluded_video_ids: set[str],
    capacity: int,
    seed: str,
    batch_size: int,
) -> tuple[list[dict[str, object]], int]:
    import pyarrow.parquet as pq

    sampler = _BottomK(capacity, seed, "sponsor_positive")
    scanned_rows = 0
    columns = [
        "video_id",
        "segment_id",
        "start_ms",
        "end_ms",
        "votes",
        "locked",
        "is_eligible",
    ]
    parquet = pq.ParquetFile(labels_path)
    for batch in parquet.iter_batches(columns=["video_id", "is_eligible"], batch_size=batch_size):
        values = batch.to_pydict()
        for index, video_id_value in enumerate(values["video_id"]):
            scanned_rows += 1
            video_id = str(video_id_value)
            if not values["is_eligible"][index] or video_id in excluded_video_ids:
                continue
            sampler.add(
                video_id,
                {
                    "video_id": video_id,
                    "benchmark_class": "sponsor_positive",
                    "source_categories": ["sponsor"],
                    "segments": [],
                },
            )

    selected = set(sampler.records)
    for batch in parquet.iter_batches(columns=columns, batch_size=batch_size):
        values = batch.to_pydict()
        for index, video_id_value in enumerate(values["video_id"]):
            video_id = str(video_id_value)
            if video_id not in selected or not values["is_eligible"][index]:
                continue
            sampler.records[video_id]["segments"].append(
                {
                    "segment_id": str(values["segment_id"][index]),
                    "start_ms": int(values["start_ms"][index]),
                    "end_ms": int(values["end_ms"][index]),
                    "votes": int(values["votes"][index]),
                    "locked": bool(values["locked"][index]),
                    "category": "sponsor",
                }
            )
    for record in sampler.records.values():
        record["segments"].sort(
            key=lambda segment: (segment["start_ms"], segment["end_ms"])
        )
    return sampler.sorted_records(), scanned_rows


def _negative_candidates(
    mirror_csv_path: Path,
    *,
    excluded_video_ids: set[str],
    pool_per_class: int,
    seed: str,
    sample_modulus: int,
    progress_every_rows: int,
    progress_callback: Callable[[int, float], None] | None,
) -> tuple[dict[str, list[dict[str, object]]], dict[str, int]]:
    sampled: dict[str, dict[str, object]] = {}
    scanned_rows = eligible_rows = 0
    started = time.monotonic()
    next_progress = progress_every_rows
    with mirror_csv_path.open("r", encoding="utf-8", newline="") as source:
        reader = csv.DictReader(source)
        for row in reader:
            scanned_rows += 1
            video_id = row.get("videoID", "")
            if (
                video_id in excluded_video_ids
                or _score(seed, "negative_universe", video_id) % sample_modulus
            ):
                continue
            if not _eligible_mirror_row(row):
                continue
            eligible_rows += 1
            record = sampled.setdefault(
                video_id,
                {"video_id": video_id, "categories": set(), "segments": []},
            )
            category = row["category"]
            record["categories"].add(category)
            if category in HARD_NEGATIVE_CATEGORIES | ORDINARY_NEGATIVE_CATEGORIES:
                record["segments"].append(
                    {
                        "segment_id": row["UUID"],
                        "start_ms": round(float(row["startTime"]) * 1000),
                        "end_ms": round(float(row["endTime"]) * 1000),
                        "votes": int(row["votes"]),
                        "locked": int(row["locked"]) != 0,
                        "category": category,
                    }
                )
            if progress_callback and progress_every_rows and scanned_rows >= next_progress:
                progress_callback(scanned_rows, time.monotonic() - started)
                next_progress += progress_every_rows

    samplers = {
        name: _BottomK(pool_per_class, seed, name)
        for name in ("hard_negative", "ordinary_negative")
    }
    classifications: Counter[str] = Counter()
    for video_id, raw_record in sampled.items():
        categories = set(raw_record["categories"])
        if "sponsor" in categories:
            classifications["excluded_sponsor"] += 1
            continue
        if categories & HARD_NEGATIVE_CATEGORIES:
            benchmark_class = "hard_negative"
        elif categories & ORDINARY_NEGATIVE_CATEGORIES:
            benchmark_class = "ordinary_negative"
        else:
            classifications["no_target_category"] += 1
            continue
        classifications[benchmark_class] += 1
        samplers[benchmark_class].add(
            video_id,
            {
                "video_id": video_id,
                "benchmark_class": benchmark_class,
                "source_categories": sorted(categories),
                "segments": sorted(
                    raw_record["segments"],
                    key=lambda segment: (segment["start_ms"], segment["end_ms"]),
                ),
            },
        )
    records = {name: sampler.sorted_records() for name, sampler in samplers.items()}
    counts = {
        "scanned_mirror_rows": scanned_rows,
        "sampled_eligible_rows": eligible_rows,
        "sampled_videos": len(sampled),
        **dict(classifications),
    }
    return records, counts


def build_mixed_benchmark_candidates(
    mirror_csv_path: Path,
    labels_path: Path,
    labels_manifest_path: Path,
    training_directory: Path,
    output_path: Path,
    manifest_path: Path,
    *,
    existing_pilot_path: Path | None,
    target_per_class: int,
    pool_per_class: int,
    seed: str,
    negative_sample_modulus: int = 64,
    batch_size: int = 131_072,
    progress_every_rows: int = 1_000_000,
    progress_callback: Callable[[int, float], None] | None = None,
) -> dict[str, object]:
    if target_per_class <= 0:
        raise ValueError("target per class must be positive")
    if pool_per_class < target_per_class:
        raise ValueError("pool per class cannot be smaller than the target")
    if negative_sample_modulus <= 0:
        raise ValueError("negative sample modulus must be positive")

    labels_manifest = json.loads(labels_manifest_path.read_text(encoding="utf-8"))
    labels_sha256 = labels_manifest.get("output", {}).get("sha256")
    if not labels_sha256 or sha256_file(labels_path) != labels_sha256:
        raise ValueError("sponsor labels do not match their manifest")

    started = time.monotonic()
    training_video_ids = _parquet_video_ids(training_directory)
    prior_pilot_video_ids = _jsonl_video_ids(existing_pilot_path)
    excluded_video_ids = training_video_ids | prior_pilot_video_ids
    positives, scanned_label_rows = _positive_candidates(
        labels_path,
        excluded_video_ids=excluded_video_ids,
        capacity=pool_per_class,
        seed=seed,
        batch_size=batch_size,
    )
    negatives, mirror_counts = _negative_candidates(
        mirror_csv_path,
        excluded_video_ids=excluded_video_ids,
        pool_per_class=pool_per_class,
        seed=seed,
        sample_modulus=negative_sample_modulus,
        progress_every_rows=progress_every_rows,
        progress_callback=progress_callback,
    )
    records_by_class = {
        "sponsor_positive": positives,
        "hard_negative": negatives["hard_negative"],
        "ordinary_negative": negatives["ordinary_negative"],
    }
    short_classes = {
        name: len(records)
        for name, records in records_by_class.items()
        if len(records) < pool_per_class
    }
    if short_classes:
        raise ValueError(f"insufficient benchmark candidates: {short_classes}")

    interleaved: list[dict[str, object]] = []
    for rank in range(pool_per_class):
        for benchmark_class in TARGET_CLASSES:
            record = dict(records_by_class[benchmark_class][rank])
            record["selection_rank"] = rank
            record["sample_hash"] = (
                f"{_score(seed, benchmark_class, str(record['video_id'])):016x}"
            )
            interleaved.append(record)
    write_json_lines_atomic(output_path, interleaved)
    report = {
        "schema_version": 1,
        "generated_at": datetime.now(tz=UTC).isoformat(),
        "status": "acquisition_pending",
        "sources": {
            "mirror_path": str(mirror_csv_path),
            "mirror_bytes": mirror_csv_path.stat().st_size,
            "labels_path": str(labels_path),
            "labels_sha256": labels_sha256,
            "training_directory": str(training_directory),
        },
        "sampling": {
            "seed": seed,
            "method": "stable hash bucket followed by per-class bottom-k",
            "target_per_class": target_per_class,
            "pool_per_class": pool_per_class,
            "negative_sample_modulus": negative_sample_modulus,
            "queue_order": "round-robin by class and within-class stable rank",
        },
        "exclusions": {
            "training_videos": len(training_video_ids),
            "prior_pilot_videos": len(prior_pilot_video_ids),
            "unique_excluded_videos": len(excluded_video_ids),
        },
        "counts": {
            "scanned_label_rows": scanned_label_rows,
            **mirror_counts,
            "queue_videos": len(interleaved),
            "queue_by_class": {
                name: len(records) for name, records in records_by_class.items()
            },
        },
        "output": {
            "path": str(output_path),
            "bytes": output_path.stat().st_size,
            "sha256": sha256_file(output_path),
        },
        "elapsed_seconds": round(time.monotonic() - started, 3),
    }
    write_json_atomic(manifest_path, report)
    return report
