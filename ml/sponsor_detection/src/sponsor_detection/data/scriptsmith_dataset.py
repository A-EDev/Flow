from __future__ import annotations

import csv
import hashlib
import json
import os
import tempfile
import time
from collections import Counter, defaultdict
from dataclasses import dataclass, replace
from datetime import UTC, datetime
from pathlib import Path
from typing import Callable, Iterable, Iterator, Sequence

from sponsor_detection.data.profile import (
    EligibilityPolicy,
    eligibility_rejection_flags,
    sha256_file,
    write_json_atomic,
)
from sponsor_detection.data.training_dataset import (
    SPLITS,
    DisjointSet,
    _empty_batch,
    _parquet_schema,
    _split_for_component,
)
from sponsor_detection.inference.windowing import (
    AssembledTranscript,
    TranscriptCue,
    TranscriptWindow,
    assemble_transcript,
    build_transcript_windows,
)


@dataclass(frozen=True)
class LabelSegment:
    segment_id: str
    start_ms: int
    end_ms: int
    category: str


def _clean_caption_line(text: str) -> str:
    return " ".join(text.split())


def _longest_overlap_suffix(previous: str, text: str, *, maximum: int = 2000) -> str:
    """Return the portion of ``text`` not already covered by ``previous``.

    YouTube auto-captions emit rolling cues where each cue repeats the tail of
    the previous cue before appending new words. Removing the longest suffix of
    ``previous`` that is a prefix of ``text`` yields the newly spoken words once.
    """

    limit = min(len(previous), len(text), maximum)
    for length in range(limit, 0, -1):
        if previous.endswith(text[:length]):
            return text[length:].strip()
    return text


def reconstruct_caption_lines(
    cues: Iterable[tuple[int, int, str]],
) -> list[tuple[int, int, str]]:
    """Collapse rolling auto-caption cues into a deduplicated ordered line list."""

    lines: list[list[object]] = []
    previous = ""
    for start_ms, end_ms, raw in cues:
        text = _clean_caption_line(raw)
        if not text:
            continue
        new_content = _longest_overlap_suffix(previous, text)
        if not new_content:
            previous = text
            continue
        if lines and new_content == previous:
            previous = text
            continue
        lines.append([start_ms, end_ms, new_content])
        previous = text
    return [(int(start), int(end), str(text)) for start, end, text in lines]


def _load_metadata(directory: Path) -> dict[str, dict[str, object]]:
    import pyarrow.parquet as parquet

    metadata: dict[str, dict[str, object]] = {}
    for path in sorted(directory.glob("*.parquet")):
        file = parquet.ParquetFile(path)
        columns = [
            "id",
            "channel_id",
            "timestamp",
            "upload_date",
            "language",
            "availability",
            "live_status",
            "duration",
        ]
        for batch in file.iter_batches(columns=columns, batch_size=65_536):
            values = [batch.column(column).to_pylist() for column in columns]
            for (
                video_id,
                channel_id,
                timestamp,
                upload_date,
                language,
                availability,
                live_status,
                duration,
            ) in zip(*values, strict=True):
                if video_id is None:
                    continue
                metadata[str(video_id)] = {
                    "channel_id": channel_id,
                    "published_at": _published_at(timestamp, upload_date),
                    "language": language,
                    "availability": availability,
                    "live_status": live_status,
                    "duration": duration,
                }
    return metadata


def _published_at(timestamp: object, upload_date: object) -> str | None:
    if isinstance(timestamp, int) and timestamp > 0:
        return datetime.fromtimestamp(timestamp, tz=UTC).isoformat()
    if isinstance(upload_date, str) and len(upload_date) == 8 and upload_date.isdigit():
        return (
            f"{upload_date[0:4]}-{upload_date[4:6]}-{upload_date[6:8]}T00:00:00+00:00"
        )
    return None


def _load_exclusions(
    directories: Sequence[Path],
) -> tuple[set[str], set[str]]:
    import pyarrow.parquet as parquet

    videos: set[str] = set()
    channels: set[str] = set()
    for directory in directories:
        if not directory.is_dir():
            raise ValueError(f"exclusion directory does not exist: {directory}")
        for split in SPLITS:
            path = directory / f"{split}.parquet"
            if not path.is_file():
                continue
            file = parquet.ParquetFile(path)
            for batch in file.iter_batches(
                columns=["video_id", "channel_id"], batch_size=65_536
            ):
                videos.update(
                    str(value)
                    for value in batch.column("video_id").to_pylist()
                    if value is not None
                )
                channels.update(
                    str(value)
                    for value in batch.column("channel_id").to_pylist()
                    if value is not None
                )
    return videos, channels


def _load_labels(
    mirror_path: Path,
    relevant_video_ids: set[str],
    *,
    allowed_categories: set[str],
) -> dict[str, list[LabelSegment]]:
    policy = EligibilityPolicy()
    labels: dict[str, list[LabelSegment]] = defaultdict(list)
    with mirror_path.open("r", encoding="utf-8", newline="") as source:
        reader = csv.DictReader(source)
        for row in reader:
            video_id = row.get("videoID")
            if video_id not in relevant_video_ids:
                continue
            category = row.get("category") or ""
            if category not in allowed_categories:
                continue
            try:
                start_time = float(row["startTime"])
                end_time = float(row["endTime"])
                votes = int(row["votes"])
                hidden = int(row["hidden"])
                shadow_hidden = int(row["shadowHidden"])
                video_duration = float(row["videoDuration"] or 0.0)
            except (KeyError, TypeError, ValueError):
                continue
            flags = eligibility_rejection_flags(
                policy,
                service=row.get("service") or "",
                action_type=row.get("actionType") or "",
                start_time=start_time,
                end_time=end_time,
                votes=votes,
                hidden=hidden,
                shadow_hidden=shadow_hidden,
                video_duration=video_duration,
            )
            if flags:
                continue
            labels[video_id].append(
                LabelSegment(
                    segment_id=str(row.get("UUID") or ""),
                    start_ms=round(start_time * 1000),
                    end_ms=round(end_time * 1000),
                    category=category,
                )
            )
    for segments in labels.values():
        segments.sort(key=lambda segment: (segment.start_ms, segment.end_ms))
    return labels


def _char_span_for_interval(
    transcript: AssembledTranscript, start_ms: int, end_ms: int
) -> tuple[int, int] | None:
    start_char: int | None = None
    end_char: int | None = None
    for cue_range in transcript.cue_ranges:
        if min(cue_range.end_ms, end_ms) > max(cue_range.start_ms, start_ms):
            if start_char is None or cue_range.start_char < start_char:
                start_char = cue_range.start_char
            if end_char is None or cue_range.end_char > end_char:
                end_char = cue_range.end_char
    if start_char is None or end_char is None or end_char <= start_char:
        return None
    return start_char, end_char


def _campaign_hash(text: str) -> str:
    normalized = " ".join(text.lower().split())
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def _is_english(language: str | None, prefixes: Sequence[str]) -> bool:
    if not language:
        return False
    lowered = language.lower()
    return any(lowered == prefix or lowered.startswith(f"{prefix}-") for prefix in prefixes)


def _iter_subtitle_rows(directory: Path) -> Iterator[dict[str, object]]:
    import pyarrow.parquet as parquet

    for path in sorted(directory.glob("*.parquet")):
        file = parquet.ParquetFile(path)
        for batch in file.iter_batches(
            columns=["video_id", "language", "segments_json"], batch_size=512
        ):
            records = batch.to_pydict()
            for video_id, language, segments_json in zip(
                records["video_id"],
                records["language"],
                records["segments_json"],
                strict=True,
            ):
                yield {
                    "video_id": str(video_id),
                    "language": language,
                    "segments_json": segments_json,
                }


def _parse_cues(segments_json: str) -> list[tuple[int, int, str]]:
    try:
        payload = json.loads(segments_json)
    except (TypeError, ValueError):
        return []
    cues: list[tuple[int, int, str]] = []
    for cue in payload:
        try:
            start_ms = round(float(cue["start"]) * 1000)
            end_ms = round(float(cue["end"]) * 1000)
            text = str(cue["text"])
        except (KeyError, TypeError, ValueError):
            continue
        if end_ms <= start_ms:
            continue
        cues.append((start_ms, end_ms, text))
    cues.sort(key=lambda cue: (cue[0], cue[1]))
    return cues


def _fit_window_to_max_tokens(
    tokenizer, window: TranscriptWindow, max_length: int
) -> TranscriptWindow | None:
    """Trim a window so that re-tokenizing its text stays within ``max_length``.

    ``build_transcript_windows`` tokenizes the whole transcript at once, but the
    trainer re-tokenizes each window independently. Byte-level merges across the
    slice boundary can add a token or two, so windows are trimmed to the last
    token boundary that a standalone encoding retains.
    """

    text = window.text
    for _ in range(max_length):
        if not text:
            return None
        encoded = tokenizer(
            text,
            truncation=True,
            max_length=max_length,
            return_offsets_mapping=True,
        )
        content_offsets = [
            (int(start), int(end))
            for start, end in encoded["offset_mapping"]
            if end > start
        ]
        if not content_offsets:
            return None
        candidate = text[: content_offsets[-1][1]].rstrip()
        if not candidate:
            return None
        if len(tokenizer(candidate, truncation=False)["input_ids"]) <= max_length:
            if candidate == window.text:
                return window
            return replace(
                window,
                text=candidate,
                end_char=window.start_char + len(candidate),
            )
        text = candidate[:-1]
    return None


def build_scriptsmith_dataset(
    subtitles_directory: Path,
    metadata_directory: Path,
    mirror_path: Path,
    exclude_split_directories: Sequence[Path],
    output_directory: Path,
    manifest_path: Path,
    *,
    encoder: str,
    encoder_revision: str,
    max_length: int,
    overlap_tokens: int,
    seed: str,
    train_fraction: float,
    validation_fraction: float,
    language_prefixes: Sequence[str],
    positive_categories: Sequence[str],
    hard_negative_categories: Sequence[str],
    ordinary_negative_windows_per_video: int,
    maximum_video_duration_seconds: int,
    minimum_cues: int,
    ordinary_negative_weight: float = 0.5,
    provenance_path: Path | None = None,
    maximum_videos: int = 0,
    progress_callback: Callable[[str], None] | None = None,
) -> dict[str, object]:
    if not 0 < train_fraction < 1:
        raise ValueError("train fraction must be between zero and one")
    if not 0 < validation_fraction < 1 - train_fraction:
        raise ValueError("validation fraction leaves no room for the test split")
    if not 0 <= ordinary_negative_windows_per_video:
        raise ValueError("ordinary negative cap must not be negative")
    if maximum_video_duration_seconds <= 0:
        raise ValueError("maximum video duration must be positive")
    if minimum_cues <= 0:
        raise ValueError("minimum cue count must be positive")

    from transformers import AutoTokenizer

    started = time.monotonic()
    positive = set(positive_categories)
    hard_negative = set(hard_negative_categories)
    allowed = positive | hard_negative

    if progress_callback:
        progress_callback("loading metadata")
    metadata = _load_metadata(metadata_directory)
    if maximum_videos:
        metadata = dict(list(metadata.items())[:maximum_videos])

    if progress_callback:
        progress_callback("loading exclusions from existing datasets")
    excluded_videos, excluded_channels = _load_exclusions(exclude_split_directories)

    relevant_ids = set(metadata) - excluded_videos
    if progress_callback:
        progress_callback(f"loading labels for {len(relevant_ids):,} videos")
    labels = _load_labels(
        mirror_path,
        relevant_ids,
        allowed_categories=allowed,
    )
    for video_id in relevant_ids:
        labels.setdefault(video_id, [])

    if progress_callback:
        progress_callback("loading tokenizer")
    tokenizer = AutoTokenizer.from_pretrained(encoder, revision=encoder_revision)

    funnel: Counter[str] = Counter()
    accepted_videos: set[str] = set()
    campaigns_by_video: dict[str, set[str]] = defaultdict(set)
    label_kind_counts: Counter[str] = Counter()

    output_directory.mkdir(parents=True, exist_ok=True)
    temporary_directory = Path(
        tempfile.mkdtemp(prefix=".scriptsmith-windows-", dir=output_directory)
    )
    all_records_path = temporary_directory / "all.parquet"
    schema_metadata = {
        b"sponsor_detection.schema_version": b"1",
        b"sponsor_detection.dataset_revision": b"scriptsmith-2024",
        b"sponsor_detection.split_seed": seed.encode(),
    }
    schema = _parquet_schema(schema_metadata)
    writer = None
    batch = _empty_batch()
    video_index = 0

    def flush_all() -> None:
        nonlocal writer
        import pyarrow as pa
        import pyarrow.parquet as parquet

        if not batch["video_id"]:
            return
        if writer is None:
            writer = parquet.ParquetWriter(
                all_records_path, schema, compression="zstd", use_dictionary=True
            )
        writer.write_table(pa.Table.from_pydict(batch, schema=schema))
        for key in batch:
            batch[key] = []

    if progress_callback:
        progress_callback("building windows")
    try:
        for row in _iter_subtitle_rows(subtitles_directory):
            video_id = str(row["video_id"])
            if video_id not in relevant_ids:
                funnel["not_in_metadata_or_excluded"] += 1
                continue
            if not _is_english(row["language"], language_prefixes):
                funnel["non_english"] += 1
                continue
            meta = metadata.get(video_id, {})
            availability = meta.get("availability")
            if availability is not None and availability != "public":
                funnel["not_public"] += 1
                continue
            if meta.get("live_status") == "was_live":
                funnel["was_live"] += 1
                continue
            duration = meta.get("duration")
            if isinstance(duration, int) and duration > maximum_video_duration_seconds:
                funnel["too_long"] += 1
                continue
            channel_id = meta.get("channel_id")
            if not channel_id:
                funnel["no_channel"] += 1
                continue

            raw_cues = _parse_cues(str(row["segments_json"]))
            if len(raw_cues) < minimum_cues:
                funnel["too_few_cues"] += 1
                continue
            reconstructed = reconstruct_caption_lines(raw_cues)
            if not reconstructed:
                funnel["empty_reconstruction"] += 1
                continue
            cues = [
                TranscriptCue(index=index, start_ms=start, end_ms=end, text=text)
                for index, (start, end, text) in enumerate(reconstructed)
            ]
            transcript = assemble_transcript(cues)
            if not transcript.text.strip():
                funnel["empty_transcript"] += 1
                continue

            video_labels = labels.get(video_id, [])
            sponsor_segments = [s for s in video_labels if s.category in positive]
            hard_segments = [s for s in video_labels if s.category in hard_negative]

            windows = []
            for raw_window in build_transcript_windows(
                transcript,
                tokenizer,
                max_length=max_length,
                overlap_tokens=overlap_tokens,
            ):
                fitted = _fit_window_to_max_tokens(tokenizer, raw_window, max_length)
                if fitted is not None:
                    windows.append(fitted)
            if not windows:
                funnel["no_windows"] += 1
                continue

            video_accepted = False
            ordinary_emitted = 0
            for window in windows:
                window_sponsor = []
                for segment in sponsor_segments:
                    char_span = _char_span_for_interval(
                        transcript, segment.start_ms, segment.end_ms
                    )
                    if char_span is None:
                        continue
                    local_start = max(char_span[0], window.start_char) - window.start_char
                    local_end = min(char_span[1], window.end_char) - window.start_char
                    if local_end <= local_start or local_start < 0:
                        continue
                    text = transcript.text[char_span[0] : char_span[1]]
                    window_sponsor.append(
                        {
                            "start_char": local_start,
                            "end_char": local_end,
                            "start_ms": segment.start_ms,
                            "end_ms": segment.end_ms,
                            "current_segment_id": segment.segment_id,
                            "current_iou": 1.0,
                            "campaign_hash": _campaign_hash(text),
                        }
                    )
                if window_sponsor:
                    window_sponsor.sort(
                        key=lambda span: (span["start_char"], span["end_char"])
                    )
                    deduped: list[dict[str, object]] = []
                    last_end = -1
                    for span in window_sponsor:
                        if int(span["start_char"]) < last_end:
                            continue
                        deduped.append(span)
                        last_end = int(span["end_char"])
                    window_sponsor = deduped
                    label_kind = "positive"
                    categories = sorted({c.upper() for c in positive})
                    sample_weight = 1.0
                else:
                    overlaps_hard = any(
                        min(segment.end_ms, window.end_ms)
                        > max(segment.start_ms, window.start_ms)
                        for segment in hard_segments
                    )
                    if overlaps_hard:
                        label_kind = "hard_negative"
                        categories = sorted({c.upper() for c in hard_negative})
                        sample_weight = 1.0
                    else:
                        if ordinary_emitted >= ordinary_negative_windows_per_video:
                            continue
                        ordinary_emitted += 1
                        label_kind = "ordinary_negative"
                        categories = []
                        sample_weight = ordinary_negative_weight

                identity = (
                    f"{video_id}\0{window.start_ms}\0{window.end_ms}\0{window.text}"
                )
                record = {
                    "example_id": hashlib.sha256(identity.encode("utf-8")).hexdigest(),
                    "video_index": video_index,
                    "video_id": video_id,
                    "channel_id": channel_id,
                    "published_at": meta.get("published_at"),
                    "source_split": "scriptsmith",
                    "text": window.text,
                    "window_start_ms": window.start_ms,
                    "window_end_ms": window.end_ms,
                    "label_kind": label_kind,
                    "sample_weight": sample_weight,
                    "legacy_categories": categories,
                    "sponsor_spans": window_sponsor,
                }
                for field in batch:
                    batch[field].append(record[field])
                label_kind_counts[label_kind] += 1
                videos_by_label = campaigns_by_video.setdefault(video_id, set())
                for span in window_sponsor:
                    videos_by_label.add(str(span["campaign_hash"]))
                video_accepted = True
                if len(batch["video_id"]) >= 8192:
                    flush_all()

            if video_accepted:
                accepted_videos.add(video_id)
            else:
                funnel["no_accepted_windows"] += 1
            video_index += 1
            if progress_callback and video_index % 1000 == 0:
                progress_callback(
                    f"processed {video_index:,} videos, {len(accepted_videos):,} accepted"
                )
        flush_all()
        if writer is not None:
            writer.close()
            writer = None
        if not all_records_path.exists():
            import pyarrow as pa
            import pyarrow.parquet as parquet

            parquet.write_table(
                pa.Table.from_pydict(_empty_batch(), schema=schema),
                all_records_path,
                compression="zstd",
            )

        if progress_callback:
            progress_callback("splitting channel-disjoint")
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
            split = _split_for_component(
                min(members),
                seed=seed,
                train_fraction=train_fraction,
                validation_fraction=validation_fraction,
            )
            for video_id in members:
                split_by_video[video_id] = split

        if progress_callback:
            progress_callback("routing split outputs")
        import pyarrow.parquet as parquet

        split_batches = {split: _empty_batch() for split in SPLITS}
        split_writers: dict[str, object] = {}
        counts_by_split = {split: Counter() for split in SPLITS}
        videos_by_split = {split: set() for split in SPLITS}
        channels_by_split = {split: set() for split in SPLITS}
        campaigns_by_split = {split: set() for split in SPLITS}

        def flush_split(split: str) -> None:
            import pyarrow as pa

            split_batch = split_batches[split]
            if not split_batch["video_id"]:
                return
            split_writer = split_writers.get(split)
            if split_writer is None:
                split_writer = parquet.ParquetWriter(
                    temporary_directory / f"{split}.parquet",
                    schema,
                    compression="zstd",
                    use_dictionary=True,
                )
                split_writers[split] = split_writer
            split_writer.write_table(pa.Table.from_pydict(split_batch, schema=schema))
            for key in split_batch:
                split_batch[key] = []

        all_file = parquet.ParquetFile(all_records_path)
        for record_batch in all_file.iter_batches():
            records = record_batch.to_pydict()
            for index in range(len(records["video_id"])):
                record = {key: values[index] for key, values in records.items()}
                split = split_by_video.get(str(record["video_id"]))
                if split is None:
                    continue
                split_batch = split_batches[split]
                for field in split_batch:
                    split_batch[field].append(record[field])
                counts_by_split[split][str(record["label_kind"])] += 1
                videos_by_split[split].add(str(record["video_id"]))
                if record["channel_id"]:
                    channels_by_split[split].add(str(record["channel_id"]))
                campaigns_by_split[split].update(
                    str(span["campaign_hash"]) for span in record["sponsor_spans"]
                )
                if len(split_batch["video_id"]) >= 8192:
                    flush_split(split)
        for split in SPLITS:
            flush_split(split)
        for split_writer in split_writers.values():
            split_writer.close()

        import pyarrow as pa

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
    finally:
        if writer is not None:
            writer.close()
        for path in temporary_directory.glob("*"):
            path.unlink(missing_ok=True)
        temporary_directory.rmdir()

    leakage = {
        "video": {
            "train_validation": len(videos_by_split["train"] & videos_by_split["validation"]),
            "train_test": len(videos_by_split["train"] & videos_by_split["test"]),
            "validation_test": len(
                videos_by_split["validation"] & videos_by_split["test"]
            ),
        },
        "channel": {
            "train_validation": len(
                channels_by_split["train"] & channels_by_split["validation"]
            ),
            "train_test": len(channels_by_split["train"] & channels_by_split["test"]),
            "validation_test": len(
                channels_by_split["validation"] & channels_by_split["test"]
            ),
        },
        "exact_campaign": {
            "train_validation": len(
                campaigns_by_split["train"] & campaigns_by_split["validation"]
            ),
            "train_test": len(campaigns_by_split["train"] & campaigns_by_split["test"]),
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

    for split in SPLITS:
        funnel[f"split_{split}_rows"] = sum(counts_by_split[split].values())

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
        }
    manifest = {
        "schema_version": 1,
        "generated_at": datetime.now(tz=UTC).isoformat(),
        "configuration": {
            "seed": seed,
            "train_fraction": train_fraction,
            "validation_fraction": validation_fraction,
            "test_fraction": round(1 - train_fraction - validation_fraction, 6),
            "encoder": encoder,
            "encoder_revision": encoder_revision,
            "max_length": max_length,
            "overlap_tokens": overlap_tokens,
            "language_prefixes": list(language_prefixes),
            "positive_categories": sorted(positive),
            "hard_negative_categories": sorted(hard_negative),
            "ordinary_negative_windows_per_video": ordinary_negative_windows_per_video,
            "maximum_video_duration_seconds": maximum_video_duration_seconds,
            "minimum_cues": minimum_cues,
            "exclude_split_directories": [str(path) for path in exclude_split_directories],
        },
        "sources": {
            "subtitles_directory": str(subtitles_directory),
            "metadata_directory": str(metadata_directory),
            "mirror_path": str(mirror_path),
            "provenance_path": str(provenance_path) if provenance_path else None,
            "provenance_sha256": (
                sha256_file(provenance_path)
                if provenance_path is not None and provenance_path.exists()
                else None
            ),
        },
        "exclusions": {
            "excluded_videos": len(excluded_videos),
            "excluded_channels": len(excluded_channels),
        },
        "funnel": dict(funnel),
        "label_kinds": dict(label_kind_counts),
        "groups": {
            "components": len(component_members),
            "accepted_videos": len(accepted_videos),
            "channels": len(first_by_channel),
            "exact_campaigns": len(first_by_campaign),
            "component_size_videos": {
                "p50": component_percentile(0.50),
                "p95": component_percentile(0.95),
                "p99": component_percentile(0.99),
                "max": component_sizes[-1] if component_sizes else 0,
            },
        },
        "leakage": leakage,
        "outputs": outputs,
        "elapsed_seconds": round(time.monotonic() - started, 3),
    }
    write_json_atomic(manifest_path, manifest)
    return manifest
