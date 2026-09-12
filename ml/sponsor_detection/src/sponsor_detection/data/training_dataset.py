from __future__ import annotations

import hashlib
import json
import os
import re
import tempfile
import time
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Iterable, Sequence

from sponsor_detection.data.profile import sha256_file, write_json_atomic


SPONSOR_SPAN_PATTERN = re.compile(
    r"START_SPONSOR_TOKEN (.*?) END_SPONSOR_TOKEN"
)
ALL_CATEGORY_PATTERN = re.compile(r"START_([A-Z]+)_TOKEN")
SPLITS = ("train", "validation", "test")


@dataclass(frozen=True)
class CurrentSegment:
    segment_id: str
    start_ms: int
    end_ms: int


class DisjointSet:
    def __init__(self, values: Iterable[str]) -> None:
        self.parent = {value: value for value in values}
        self.rank = {value: 0 for value in values}

    def find(self, value: str) -> str:
        parent = self.parent[value]
        if parent != value:
            self.parent[value] = self.find(parent)
        return self.parent[value]

    def union(self, left: str, right: str) -> None:
        left_root = self.find(left)
        right_root = self.find(right)
        if left_root == right_root:
            return
        if self.rank[left_root] < self.rank[right_root]:
            left_root, right_root = right_root, left_root
        self.parent[right_root] = left_root
        if self.rank[left_root] == self.rank[right_root]:
            self.rank[left_root] += 1


def extract_sponsor_char_spans(text: str, extracted: str) -> list[tuple[int, int]]:
    spans: list[tuple[int, int]] = []
    cursor = 0
    for match in SPONSOR_SPAN_PATTERN.finditer(extracted):
        sponsor_text = match.group(1)
        start = text.find(sponsor_text, cursor)
        if start < 0:
            raise ValueError("sponsor target text does not occur in the input window")
        end = start + len(sponsor_text)
        spans.append((start, end))
        cursor = end
    return spans


def temporal_iou(
    left_start_ms: int,
    left_end_ms: int,
    right_start_ms: int,
    right_end_ms: int,
) -> float:
    intersection = max(
        0,
        min(left_end_ms, right_end_ms) - max(left_start_ms, right_start_ms),
    )
    union = max(left_end_ms, right_end_ms) - min(left_start_ms, right_start_ms)
    return intersection / union if union else 0.0


def _campaign_hash(text: str) -> str:
    normalized = " ".join(text.lower().split())
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def _iter_source_rows(dataset_directory: Path):
    for source_split in ("train", "valid", "test"):
        with (dataset_directory / f"{source_split}.json").open(
            "r", encoding="utf-8"
        ) as source:
            for line in source:
                yield source_split, json.loads(line)


def _load_current_segments(
    path: Path, relevant_video_ids: set[str]
) -> dict[str, list[CurrentSegment]]:
    import pyarrow.parquet as parquet

    segments: dict[str, list[CurrentSegment]] = defaultdict(list)
    file = parquet.ParquetFile(path)
    columns = ["video_id", "segment_id", "start_ms", "end_ms", "is_eligible"]
    for batch in file.iter_batches(columns=columns, batch_size=131_072):
        values = [batch.column(column).to_pylist() for column in columns]
        for video_id, segment_id, start_ms, end_ms, eligible in zip(
            *values, strict=True
        ):
            if eligible and video_id in relevant_video_ids:
                segments[video_id].append(
                    CurrentSegment(
                        segment_id=str(segment_id),
                        start_ms=int(start_ms),
                        end_ms=int(end_ms),
                    )
                )
    for video_segments in segments.values():
        video_segments.sort(key=lambda segment: (segment.start_ms, segment.end_ms))
    return segments


def _load_metadata(path: Path | None) -> dict[str, dict[str, object]]:
    if path is None or not path.is_file():
        return {}
    metadata: dict[str, dict[str, object]] = {}
    with path.open("r", encoding="utf-8") as source:
        for line in source:
            record = json.loads(line)
            metadata[str(record["video_id"])] = record
    return metadata


def _overlaps(start_ms: int, end_ms: int, segment: CurrentSegment) -> bool:
    return min(end_ms, segment.end_ms) > max(start_ms, segment.start_ms)


def _assess_row(
    row: dict[str, object],
    old_segments: dict[str, list[dict[str, object]]],
    current_segments: dict[str, list[CurrentSegment]],
    *,
    current_iou_threshold: float,
) -> tuple[dict[str, object] | None, str]:
    video_id = str(row["video_id"])
    text = str(row["text"])
    extracted = str(row["extracted"])
    window_start_ms = round(float(row["start"]) * 1000)
    window_end_ms = round(float(row["end"]) * 1000)
    categories = sorted(set(ALL_CATEGORY_PATTERN.findall(extracted)))
    try:
        char_spans = extract_sponsor_char_spans(text, extracted)
    except ValueError:
        return None, "unaligned_sponsor_text"

    current = current_segments.get(video_id, [])
    sponsor_spans: list[dict[str, object]] = []
    if char_spans:
        legacy = sorted(
            (
                segment
                for segment in old_segments.get(video_id, [])
                if segment.get("category") == "sponsor"
                and min(window_end_ms, round(float(segment["end"]) * 1000))
                > max(window_start_ms, round(float(segment["start"]) * 1000))
            ),
            key=lambda segment: (float(segment["start"]), float(segment["end"])),
        )
        if len(legacy) != len(char_spans):
            return None, "span_segment_count_mismatch"
        for (start_char, end_char), legacy_segment in zip(
            char_spans, legacy, strict=True
        ):
            start_ms = round(float(legacy_segment["start"]) * 1000)
            end_ms = round(float(legacy_segment["end"]) * 1000)
            matches = [
                (
                    temporal_iou(
                        start_ms,
                        end_ms,
                        candidate.start_ms,
                        candidate.end_ms,
                    ),
                    candidate,
                )
                for candidate in current
            ]
            best_iou, best_segment = max(matches, default=(0.0, None), key=lambda item: item[0])
            if best_segment is None or best_iou < current_iou_threshold:
                return None, "stale_positive"
            sponsor_text = text[start_char:end_char]
            sponsor_spans.append(
                {
                    "start_char": start_char,
                    "end_char": end_char,
                    "start_ms": start_ms,
                    "end_ms": end_ms,
                    "current_segment_id": best_segment.segment_id,
                    "current_iou": round(best_iou, 6),
                    "campaign_hash": _campaign_hash(sponsor_text),
                }
            )
        label_kind = "positive"
        sample_weight = 1.0
    else:
        if any(_overlaps(window_start_ms, window_end_ms, segment) for segment in current):
            return None, "stale_negative"
        label_kind = "hard_negative" if categories else "ordinary_negative"
        sample_weight = 1.0 if categories else 0.5

    return (
        {
            "video_index": int(row["video_index"]),
            "video_id": video_id,
            "text": text,
            "window_start_ms": window_start_ms,
            "window_end_ms": window_end_ms,
            "label_kind": label_kind,
            "sample_weight": sample_weight,
            "legacy_categories": categories,
            "sponsor_spans": sponsor_spans,
        },
        "accepted",
    )


def _split_for_component(
    component_id: str,
    *,
    seed: str,
    train_fraction: float,
    validation_fraction: float,
) -> str:
    digest = hashlib.sha256(f"{seed}\0{component_id}".encode("utf-8")).digest()
    value = int.from_bytes(digest[:8], "big") / 2**64
    if value < train_fraction:
        return "train"
    if value < train_fraction + validation_fraction:
        return "validation"
    return "test"


def _parquet_schema(metadata: dict[bytes, bytes]):
    import pyarrow as pa

    span = pa.struct(
        [
            ("start_char", pa.int32()),
            ("end_char", pa.int32()),
            ("start_ms", pa.int64()),
            ("end_ms", pa.int64()),
            ("current_segment_id", pa.string()),
            ("current_iou", pa.float32()),
            ("campaign_hash", pa.string()),
        ]
    )
    return pa.schema(
        [
            ("example_id", pa.string()),
            ("video_index", pa.int32()),
            ("video_id", pa.string()),
            ("channel_id", pa.string()),
            ("published_at", pa.string()),
            ("source_split", pa.string()),
            ("text", pa.string()),
            ("window_start_ms", pa.int64()),
            ("window_end_ms", pa.int64()),
            ("label_kind", pa.string()),
            ("sample_weight", pa.float32()),
            ("legacy_categories", pa.list_(pa.string())),
            ("sponsor_spans", pa.list_(span)),
        ],
        metadata=metadata,
    )


def _empty_batch() -> dict[str, list[object]]:
    return {
        "example_id": [],
        "video_index": [],
        "video_id": [],
        "channel_id": [],
        "published_at": [],
        "source_split": [],
        "text": [],
        "window_start_ms": [],
        "window_end_ms": [],
        "label_kind": [],
        "sample_weight": [],
        "legacy_categories": [],
        "sponsor_spans": [],
    }


def build_training_dataset(
    dataset_directory: Path,
    dataset_profile_path: Path,
    current_labels_path: Path,
    current_labels_manifest_path: Path,
    output_directory: Path,
    manifest_path: Path,
    *,
    seed: str,
    train_fraction: float,
    validation_fraction: float,
    current_iou_threshold: float,
    batch_size: int = 8192,
    metadata_path: Path | None = None,
    progress_callback=None,
) -> dict[str, object]:
    if not 0 < train_fraction < 1:
        raise ValueError("train fraction must be between zero and one")
    if not 0 < validation_fraction < 1 - train_fraction:
        raise ValueError("validation fraction leaves no room for the test split")
    if not 0 <= current_iou_threshold <= 1:
        raise ValueError("current IoU threshold must be between zero and one")
    if batch_size <= 0:
        raise ValueError("batch size must be positive")

    started = time.monotonic()
    dataset_profile = json.loads(dataset_profile_path.read_text(encoding="utf-8"))
    current_manifest = json.loads(
        current_labels_manifest_path.read_text(encoding="utf-8")
    )
    expected_labels_sha = current_manifest.get("output", {}).get("sha256")
    if not expected_labels_sha or sha256_file(current_labels_path) != expected_labels_sha:
        raise ValueError("current label Parquet does not match its manifest")

    with (dataset_directory / "segments.json").open("r", encoding="utf-8") as source:
        old_segments = json.load(source)
    relevant_video_ids = set(old_segments)
    if progress_callback:
        progress_callback("loading current SponsorBlock intervals")
    current_segments = _load_current_segments(current_labels_path, relevant_video_ids)
    metadata = _load_metadata(metadata_path)

    accepted_videos: set[str] = set()
    campaigns_by_video: dict[str, set[str]] = defaultdict(set)
    first_pass_counts: Counter[str] = Counter()
    if progress_callback:
        progress_callback("validating examples and collecting split groups")
    for _, row in _iter_source_rows(dataset_directory):
        assessed, status = _assess_row(
            row,
            old_segments,
            current_segments,
            current_iou_threshold=current_iou_threshold,
        )
        first_pass_counts[status] += 1
        if assessed is None:
            continue
        video_id = str(assessed["video_id"])
        accepted_videos.add(video_id)
        for span in assessed["sponsor_spans"]:
            campaigns_by_video[video_id].add(str(span["campaign_hash"]))

    groups = DisjointSet(accepted_videos)
    first_by_channel: dict[str, str] = {}
    first_by_campaign: dict[str, str] = {}
    for video_id in sorted(accepted_videos):
        channel_id = metadata.get(video_id, {}).get("channel_id")
        if channel_id:
            previous = first_by_channel.setdefault(str(channel_id), video_id)
            groups.union(previous, video_id)
        for campaign_hash in campaigns_by_video.get(video_id, ()):
            previous = first_by_campaign.setdefault(campaign_hash, video_id)
            groups.union(previous, video_id)

    component_members: dict[str, list[str]] = defaultdict(list)
    for video_id in accepted_videos:
        component_members[groups.find(video_id)].append(video_id)
    component_sizes = sorted(len(members) for members in component_members.values())

    def component_percentile(fraction: float) -> int:
        if not component_sizes:
            return 0
        return component_sizes[round((len(component_sizes) - 1) * fraction)]

    split_by_video: dict[str, str] = {}
    for members in component_members.values():
        component_id = min(members)
        split = _split_for_component(
            component_id,
            seed=seed,
            train_fraction=train_fraction,
            validation_fraction=validation_fraction,
        )
        for video_id in members:
            split_by_video[video_id] = split

    output_directory.mkdir(parents=True, exist_ok=True)
    temporary_directory = Path(
        tempfile.mkdtemp(prefix=".sponsor-windows-", dir=output_directory)
    )
    metadata_bytes = {
        b"sponsor_detection.schema_version": b"1",
        b"sponsor_detection.dataset_revision": str(
            dataset_profile["source"]["revision"]
        ).encode(),
        b"sponsor_detection.current_labels_sha256": expected_labels_sha.encode(),
        b"sponsor_detection.split_seed": seed.encode(),
    }
    schema = _parquet_schema(metadata_bytes)
    batches = {split: _empty_batch() for split in SPLITS}
    writers = {}
    counts_by_split = {split: Counter() for split in SPLITS}
    videos_by_split = {split: set() for split in SPLITS}
    channels_by_split = {split: set() for split in SPLITS}
    campaigns_by_split = {split: set() for split in SPLITS}
    source_split_counts = {split: Counter() for split in SPLITS}

    def flush(split: str) -> None:
        import pyarrow as pa
        import pyarrow.parquet as parquet

        batch = batches[split]
        if not batch["video_id"]:
            return
        writer = writers.get(split)
        if writer is None:
            writer = parquet.ParquetWriter(
                temporary_directory / f"{split}.parquet",
                schema,
                compression="zstd",
                use_dictionary=True,
            )
            writers[split] = writer
        writer.write_table(pa.Table.from_pydict(batch, schema=schema))
        batches[split] = _empty_batch()

    try:
        if progress_callback:
            progress_callback("writing grouped Parquet splits")
        for source_split, row in _iter_source_rows(dataset_directory):
            assessed, status = _assess_row(
                row,
                old_segments,
                current_segments,
                current_iou_threshold=current_iou_threshold,
            )
            if assessed is None:
                continue
            video_id = str(assessed["video_id"])
            split = split_by_video[video_id]
            video_metadata = metadata.get(video_id, {})
            identity = (
                f"{video_id}\0{assessed['window_start_ms']}\0"
                f"{assessed['window_end_ms']}\0{assessed['text']}"
            )
            record = {
                "example_id": hashlib.sha256(identity.encode("utf-8")).hexdigest(),
                **assessed,
                "channel_id": video_metadata.get("channel_id"),
                "published_at": video_metadata.get("published_at"),
                "source_split": source_split,
            }
            batch = batches[split]
            for field in batch:
                batch[field].append(record[field])
            label_kind = str(record["label_kind"])
            counts_by_split[split][label_kind] += 1
            source_split_counts[split][source_split] += 1
            videos_by_split[split].add(video_id)
            if record["channel_id"]:
                channels_by_split[split].add(str(record["channel_id"]))
            campaigns_by_split[split].update(
                str(span["campaign_hash"]) for span in record["sponsor_spans"]
            )
            if len(batch["video_id"]) >= batch_size:
                flush(split)
        for split in SPLITS:
            flush(split)
        for writer in writers.values():
            writer.close()
        import pyarrow as pa
        import pyarrow.parquet as parquet

        for split in SPLITS:
            temporary_path = temporary_directory / f"{split}.parquet"
            if not temporary_path.exists():
                parquet.write_table(
                    pa.Table.from_pydict(_empty_batch(), schema=schema),
                    temporary_path,
                    compression="zstd",
                )
        for split in SPLITS:
            os.replace(
                temporary_directory / f"{split}.parquet",
                output_directory / f"{split}.parquet",
            )
    except BaseException:
        for writer in writers.values():
            writer.close()
        raise
    finally:
        for path in temporary_directory.glob("*"):
            path.unlink(missing_ok=True)
        temporary_directory.rmdir()

    channel_grouping_enabled = bool(metadata)
    accepted_with_metadata = accepted_videos.intersection(metadata)
    accepted_with_channel = {
        video_id
        for video_id in accepted_videos
        if metadata.get(video_id, {}).get("channel_id")
    }
    leakage = {
        "video": {
            "train_validation": len(videos_by_split["train"] & videos_by_split["validation"]),
            "train_test": len(videos_by_split["train"] & videos_by_split["test"]),
            "validation_test": len(videos_by_split["validation"] & videos_by_split["test"]),
        },
        "channel": {
            "train_validation": len(
                channels_by_split["train"] & channels_by_split["validation"]
            ) if channel_grouping_enabled else None,
            "train_test": len(channels_by_split["train"] & channels_by_split["test"])
            if channel_grouping_enabled
            else None,
            "validation_test": len(
                channels_by_split["validation"] & channels_by_split["test"]
            ) if channel_grouping_enabled else None,
        },
        "exact_campaign": {
            "train_validation": len(
                campaigns_by_split["train"] & campaigns_by_split["validation"]
            ),
            "train_test": len(
                campaigns_by_split["train"] & campaigns_by_split["test"]
            ),
            "validation_test": len(
                campaigns_by_split["validation"] & campaigns_by_split["test"]
            ),
        },
    }
    if any(
        value
        for category in leakage.values()
        for value in category.values()
        if value is not None
    ):
        raise AssertionError(f"group leakage detected: {leakage}")

    outputs = {}
    for split in SPLITS:
        path = output_directory / f"{split}.parquet"
        outputs[split] = {
            "path": str(path),
            "bytes": path.stat().st_size,
            "sha256": sha256_file(path),
            "rows": sum(counts_by_split[split].values()),
            "videos": len(videos_by_split[split]),
            "channels": len(channels_by_split[split]),
            "campaigns": len(campaigns_by_split[split]),
            "label_kinds": dict(counts_by_split[split]),
            "legacy_source_splits": dict(source_split_counts[split]),
        }
    manifest = {
        "schema_version": 1,
        "generated_at": datetime.now(tz=UTC).isoformat(),
        "configuration": {
            "seed": seed,
            "train_fraction": train_fraction,
            "validation_fraction": validation_fraction,
            "test_fraction": round(1 - train_fraction - validation_fraction, 6),
            "current_iou_threshold": current_iou_threshold,
            "metadata_path": str(metadata_path) if metadata_path else None,
        },
        "sources": {
            "xenova_revision": dataset_profile["source"]["revision"],
            "xenova_profile_sha256": sha256_file(dataset_profile_path),
            "current_labels_sha256": expected_labels_sha,
            "metadata_sha256": (
                sha256_file(metadata_path)
                if metadata_path is not None and metadata_path.exists()
                else None
            ),
        },
        "validation_counts": dict(first_pass_counts),
        "groups": {
            "components": len(component_members),
            "component_size_videos": {
                "p50": component_percentile(0.50),
                "p95": component_percentile(0.95),
                "p99": component_percentile(0.99),
                "max": component_sizes[-1] if component_sizes else 0,
            },
            "channel_grouping_enabled": channel_grouping_enabled,
            "channels": len(first_by_channel),
            "exact_campaigns": len(first_by_campaign),
            "accepted_videos": len(accepted_videos),
            "accepted_videos_with_metadata": len(accepted_with_metadata),
            "accepted_videos_with_channel": len(accepted_with_channel),
            "accepted_video_channel_coverage": round(
                len(accepted_with_channel) / len(accepted_videos), 6
            )
            if accepted_videos
            else 0,
        },
        "leakage": leakage,
        "outputs": outputs,
        "elapsed_seconds": round(time.monotonic() - started, 3),
    }
    write_json_atomic(manifest_path, manifest)
    return manifest
