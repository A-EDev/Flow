from __future__ import annotations

import csv
import hashlib
import json
import math
import os
import tempfile
import time
from collections import Counter
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Callable


REQUIRED_COLUMNS = frozenset(
    {
        "UUID",
        "actionType",
        "category",
        "endTime",
        "hidden",
        "incorrectVotes",
        "locked",
        "reputation",
        "service",
        "shadowHidden",
        "startTime",
        "timeSubmitted",
        "userID",
        "videoDuration",
        "videoID",
        "views",
        "votes",
    }
)


@dataclass(frozen=True)
class EligibilityPolicy:
    category: str = "sponsor"
    service: str = "YouTube"
    action_type: str = "skip"
    minimum_votes: int = 0
    exclude_hidden: bool = True
    exclude_shadow_hidden: bool = True
    duration_tolerance_seconds: float = 1.0
    duration_tolerance_ratio: float = 0.01


class HyperLogLog:
    """Small bounded-memory cardinality estimator for large snapshot profiles."""

    def __init__(self, precision: int = 14) -> None:
        if not 4 <= precision <= 18:
            raise ValueError("precision must be between 4 and 18")
        self.precision = precision
        self.register_count = 1 << precision
        self.registers = bytearray(self.register_count)

    def add(self, value: str) -> None:
        digest = hashlib.blake2b(value.encode("utf-8"), digest_size=8).digest()
        hashed = int.from_bytes(digest, "big")
        index = hashed >> (64 - self.precision)
        remaining = (hashed << self.precision) & ((1 << 64) - 1)
        rank = 64 - remaining.bit_length() + 1 if remaining else 65 - self.precision
        self.registers[index] = max(self.registers[index], rank)

    def estimate(self) -> int:
        register_count = self.register_count
        if register_count == 16:
            alpha = 0.673
        elif register_count == 32:
            alpha = 0.697
        elif register_count == 64:
            alpha = 0.709
        else:
            alpha = 0.7213 / (1 + 1.079 / register_count)

        raw_estimate = alpha * register_count**2 / sum(
            2.0 ** -register for register in self.registers
        )
        zero_registers = self.registers.count(0)
        if raw_estimate <= 2.5 * register_count and zero_registers:
            raw_estimate = register_count * math.log(register_count / zero_registers)
        return round(raw_estimate)


def sha256_file(path: Path, chunk_size: int = 8 * 1024 * 1024) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(chunk_size):
            digest.update(chunk)
    return digest.hexdigest()


def _parse_int(row: dict[str, str], key: str) -> int:
    return int(row[key])


def _parse_float(row: dict[str, str], key: str) -> float:
    value = float(row[key])
    if not math.isfinite(value):
        raise ValueError(f"{key} is not finite")
    return value


def _vote_bucket(votes: int) -> str:
    if votes < 0:
        return "negative"
    if votes == 0:
        return "zero"
    if votes == 1:
        return "one"
    if votes < 5:
        return "two_to_four"
    if votes < 10:
        return "five_to_nine"
    return "ten_or_more"


def _timestamp_to_iso(timestamp_ms: int | None) -> str | None:
    if timestamp_ms is None:
        return None
    return datetime.fromtimestamp(timestamp_ms / 1000, tz=UTC).isoformat()


def eligibility_rejection_flags(
    policy: EligibilityPolicy,
    *,
    service: str,
    action_type: str,
    start_time: float,
    end_time: float,
    votes: int,
    hidden: int,
    shadow_hidden: int,
    video_duration: float,
) -> set[str]:
    rejection_flags: set[str] = set()
    if service != policy.service:
        rejection_flags.add("wrong_service")
    if action_type != policy.action_type:
        rejection_flags.add("wrong_action_type")
    if start_time < 0 or end_time <= start_time:
        rejection_flags.add("invalid_interval")
    if votes < policy.minimum_votes:
        rejection_flags.add("below_minimum_votes")
    if policy.exclude_hidden and hidden != 0:
        rejection_flags.add("hidden")
    if policy.exclude_shadow_hidden and shadow_hidden != 0:
        rejection_flags.add("shadow_hidden")
    if video_duration > 0:
        tolerance = max(
            policy.duration_tolerance_seconds,
            video_duration * policy.duration_tolerance_ratio,
        )
        if start_time > video_duration + tolerance or end_time > video_duration + tolerance:
            rejection_flags.add("duration_mismatch")
    return rejection_flags


def _mirror_metadata(input_path: Path) -> dict[str, str | None]:
    mirror_directory = input_path.parent
    update_path = mirror_directory / "lastUpdate.txt"
    license_path = mirror_directory / "licence.md"
    return {
        "mirror_last_update": (
            update_path.read_text(encoding="utf-8").strip() if update_path.is_file() else None
        ),
        "license_file": str(license_path) if license_path.is_file() else None,
    }


def profile_csv(
    input_path: Path,
    policy: EligibilityPolicy,
    *,
    compute_sha256: bool = True,
    progress_every_rows: int = 0,
    progress_callback: Callable[[int, float], None] | None = None,
) -> dict[str, object]:
    started = time.monotonic()
    categories: Counter[str] = Counter()
    action_types: Counter[str] = Counter()
    services: Counter[str] = Counter()
    rejection_flags: Counter[str] = Counter()
    vote_buckets: Counter[str] = Counter()
    all_videos = HyperLogLog()
    sponsor_videos = HyperLogLog()
    eligible_videos = HyperLogLog()
    total_rows = 0
    malformed_rows = 0
    invalid_intervals = 0
    target_rows = 0
    eligible_rows = 0
    locked_target_rows = 0
    minimum_timestamp_ms: int | None = None
    maximum_timestamp_ms: int | None = None

    with input_path.open("r", encoding="utf-8", newline="") as source:
        reader = csv.DictReader(source)
        fieldnames = reader.fieldnames or []
        missing_columns = sorted(REQUIRED_COLUMNS.difference(fieldnames))
        if missing_columns:
            raise ValueError(f"missing required columns: {', '.join(missing_columns)}")

        for row in reader:
            total_rows += 1
            if None in row or any(row.get(column) is None for column in REQUIRED_COLUMNS):
                malformed_rows += 1
                continue

            category = row["category"]
            action_type = row["actionType"]
            service = row["service"]
            video_id = row["videoID"]
            categories[category] += 1
            action_types[action_type] += 1
            services[service] += 1
            if video_id:
                all_videos.add(video_id)

            try:
                start_time = _parse_float(row, "startTime")
                end_time = _parse_float(row, "endTime")
                video_duration = _parse_float(row, "videoDuration")
                votes = _parse_int(row, "votes")
                locked = _parse_int(row, "locked")
                hidden = _parse_int(row, "hidden")
                shadow_hidden = _parse_int(row, "shadowHidden")
                timestamp_ms = _parse_int(row, "timeSubmitted")
            except (TypeError, ValueError):
                malformed_rows += 1
                if category == policy.category:
                    target_rows += 1
                    rejection_flags["invalid_numeric_value"] += 1
                continue

            minimum_timestamp_ms = (
                timestamp_ms
                if minimum_timestamp_ms is None
                else min(minimum_timestamp_ms, timestamp_ms)
            )
            maximum_timestamp_ms = (
                timestamp_ms
                if maximum_timestamp_ms is None
                else max(maximum_timestamp_ms, timestamp_ms)
            )

            interval_valid = start_time >= 0 and end_time > start_time
            if not interval_valid:
                invalid_intervals += 1

            if category != policy.category:
                if progress_every_rows and total_rows % progress_every_rows == 0:
                    if progress_callback:
                        progress_callback(total_rows, time.monotonic() - started)
                continue

            target_rows += 1
            sponsor_videos.add(video_id)
            vote_buckets[_vote_bucket(votes)] += 1
            if locked:
                locked_target_rows += 1

            row_rejections = eligibility_rejection_flags(
                policy,
                service=service,
                action_type=action_type,
                start_time=start_time,
                end_time=end_time,
                votes=votes,
                hidden=hidden,
                shadow_hidden=shadow_hidden,
                video_duration=video_duration,
            )

            rejection_flags.update(row_rejections)
            if not row_rejections:
                eligible_rows += 1
                eligible_videos.add(video_id)

            if progress_every_rows and total_rows % progress_every_rows == 0:
                if progress_callback:
                    progress_callback(total_rows, time.monotonic() - started)

    stat = input_path.stat()
    source_profile: dict[str, object] = {
        "path": str(input_path),
        "bytes": stat.st_size,
        "modified_at": datetime.fromtimestamp(stat.st_mtime, tz=UTC).isoformat(),
        "sha256": sha256_file(input_path) if compute_sha256 else None,
        **_mirror_metadata(input_path),
    }
    return {
        "schema_version": 1,
        "generated_at": datetime.now(tz=UTC).isoformat(),
        "source": source_profile,
        "eligibility_policy": asdict(policy),
        "totals": {
            "rows": total_rows,
            "malformed_rows": malformed_rows,
            "invalid_intervals_all_categories": invalid_intervals,
            "approximate_unique_videos": all_videos.estimate(),
            "target_rows": target_rows,
            "approximate_unique_target_videos": sponsor_videos.estimate(),
            "eligible_target_rows": eligible_rows,
            "approximate_unique_eligible_target_videos": eligible_videos.estimate(),
            "locked_target_rows": locked_target_rows,
        },
        "coverage": {
            "minimum_time_submitted": _timestamp_to_iso(minimum_timestamp_ms),
            "maximum_time_submitted": _timestamp_to_iso(maximum_timestamp_ms),
        },
        "counts": {
            "categories": dict(categories.most_common()),
            "action_types": dict(action_types.most_common()),
            "services": dict(services.most_common()),
            "target_vote_buckets": dict(vote_buckets),
            "target_rejection_flags": dict(rejection_flags.most_common()),
        },
        "profiling_seconds": round(time.monotonic() - started, 3),
    }


def write_json_atomic(output_path: Path, report: dict[str, object]) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        dir=output_path.parent,
        prefix=f".{output_path.name}.",
        suffix=".tmp",
        text=True,
    )
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as output:
            json.dump(report, output, indent=2, sort_keys=True)
            output.write("\n")
        os.replace(temporary_name, output_path)
    except BaseException:
        Path(temporary_name).unlink(missing_ok=True)
        raise
