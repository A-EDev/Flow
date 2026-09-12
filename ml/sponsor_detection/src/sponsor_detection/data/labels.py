from __future__ import annotations

import csv
import json
import os
import tempfile
import time
from dataclasses import asdict
from datetime import UTC, datetime
from pathlib import Path
from typing import Callable

from sponsor_detection.data.profile import (
    REQUIRED_COLUMNS,
    EligibilityPolicy,
    eligibility_rejection_flags,
    sha256_file,
    write_json_atomic,
)


def _load_verified_source_profile(input_path: Path, profile_path: Path) -> dict[str, object]:
    profile = json.loads(profile_path.read_text(encoding="utf-8"))
    source = profile.get("source")
    if not isinstance(source, dict):
        raise ValueError("profile does not contain source metadata")
    expected_size = source.get("bytes")
    if expected_size != input_path.stat().st_size:
        raise ValueError("input CSV size differs from the profiled snapshot")
    expected_sha256 = source.get("sha256")
    if not expected_sha256:
        raise ValueError("profile does not contain a source SHA-256 fingerprint")
    if sha256_file(input_path) != expected_sha256:
        raise ValueError("input CSV checksum differs from the profiled snapshot")
    return source


def _parquet_schema(source: dict[str, object], policy: EligibilityPolicy):
    try:
        import pyarrow as pa
    except ImportError as error:
        raise RuntimeError(
            "Parquet extraction requires the data extra: pip install -e '.[data]'"
        ) from error

    metadata = {
        b"sponsor_detection.schema_version": b"1",
        b"sponsor_detection.source_sha256": str(source["sha256"]).encode("utf-8"),
        b"sponsor_detection.eligibility_policy": json.dumps(
            asdict(policy), sort_keys=True, separators=(",", ":")
        ).encode("utf-8"),
    }
    return pa.schema(
        [
            ("video_id", pa.string()),
            ("segment_id", pa.string()),
            ("start_ms", pa.int64()),
            ("end_ms", pa.int64()),
            ("votes", pa.int32()),
            ("locked", pa.bool_()),
            ("incorrect_votes", pa.int32()),
            ("views", pa.int64()),
            ("time_submitted_ms", pa.int64()),
            ("video_duration_ms", pa.int64()),
            ("hidden", pa.bool_()),
            ("shadow_hidden", pa.bool_()),
            ("reputation", pa.float32()),
            ("is_eligible", pa.bool_()),
            ("rejection_flags", pa.list_(pa.string())),
        ],
        metadata=metadata,
    )


def _new_batch() -> dict[str, list[object]]:
    return {
        "video_id": [],
        "segment_id": [],
        "start_ms": [],
        "end_ms": [],
        "votes": [],
        "locked": [],
        "incorrect_votes": [],
        "views": [],
        "time_submitted_ms": [],
        "video_duration_ms": [],
        "hidden": [],
        "shadow_hidden": [],
        "reputation": [],
        "is_eligible": [],
        "rejection_flags": [],
    }


def _append_annotation(
    batch: dict[str, list[object]],
    row: dict[str, str],
    policy: EligibilityPolicy,
) -> bool:
    start_time = float(row["startTime"])
    end_time = float(row["endTime"])
    video_duration = float(row["videoDuration"])
    votes = int(row["votes"])
    locked = int(row["locked"])
    hidden = int(row["hidden"])
    shadow_hidden = int(row["shadowHidden"])
    rejection_flags = eligibility_rejection_flags(
        policy,
        service=row["service"],
        action_type=row["actionType"],
        start_time=start_time,
        end_time=end_time,
        votes=votes,
        hidden=hidden,
        shadow_hidden=shadow_hidden,
        video_duration=video_duration,
    )
    batch["video_id"].append(row["videoID"])
    batch["segment_id"].append(row["UUID"])
    batch["start_ms"].append(round(start_time * 1000))
    batch["end_ms"].append(round(end_time * 1000))
    batch["votes"].append(votes)
    batch["locked"].append(locked != 0)
    batch["incorrect_votes"].append(int(row["incorrectVotes"]))
    batch["views"].append(int(row["views"]))
    batch["time_submitted_ms"].append(int(row["timeSubmitted"]))
    batch["video_duration_ms"].append(
        round(video_duration * 1000) if video_duration > 0 else None
    )
    batch["hidden"].append(hidden != 0)
    batch["shadow_hidden"].append(shadow_hidden != 0)
    batch["reputation"].append(float(row["reputation"]))
    batch["is_eligible"].append(not rejection_flags)
    batch["rejection_flags"].append(sorted(rejection_flags))
    return not rejection_flags


def build_label_parquet(
    input_path: Path,
    profile_path: Path,
    output_path: Path,
    manifest_path: Path,
    policy: EligibilityPolicy,
    *,
    batch_size: int = 100_000,
    compression: str = "zstd",
    progress_every_rows: int = 0,
    progress_callback: Callable[[int, int, float], None] | None = None,
) -> dict[str, object]:
    try:
        import pyarrow as pa
        import pyarrow.parquet as pq
    except ImportError as error:
        raise RuntimeError(
            "Parquet extraction requires the data extra: pip install -e '.[data]'"
        ) from error

    if batch_size <= 0:
        raise ValueError("batch_size must be positive")
    source_profile = _load_verified_source_profile(input_path, profile_path)
    schema = _parquet_schema(source_profile, policy)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        dir=output_path.parent,
        prefix=f".{output_path.name}.",
        suffix=".tmp",
    )
    os.close(descriptor)
    temporary_path = Path(temporary_name)
    total_rows = target_rows = eligible_rows = malformed_target_rows = 0
    started = time.monotonic()
    batch = _new_batch()

    try:
        with input_path.open("r", encoding="utf-8", newline="") as source:
            reader = csv.DictReader(source)
            missing_columns = sorted(REQUIRED_COLUMNS.difference(reader.fieldnames or []))
            if missing_columns:
                raise ValueError(f"missing required columns: {', '.join(missing_columns)}")
            with pq.ParquetWriter(
                temporary_path,
                schema,
                compression=compression,
                use_dictionary=True,
            ) as writer:
                for row in reader:
                    total_rows += 1
                    if row.get("category") != policy.category:
                        if progress_every_rows and total_rows % progress_every_rows == 0:
                            if progress_callback:
                                progress_callback(total_rows, target_rows, time.monotonic() - started)
                        continue
                    target_rows += 1
                    try:
                        eligible_rows += _append_annotation(batch, row, policy)
                    except (TypeError, ValueError):
                        malformed_target_rows += 1
                        continue
                    if len(batch["video_id"]) >= batch_size:
                        writer.write_table(pa.Table.from_pydict(batch, schema=schema))
                        batch = _new_batch()
                    if progress_every_rows and total_rows % progress_every_rows == 0:
                        if progress_callback:
                            progress_callback(total_rows, target_rows, time.monotonic() - started)
                if batch["video_id"]:
                    writer.write_table(pa.Table.from_pydict(batch, schema=schema))
        os.replace(temporary_path, output_path)
    except BaseException:
        temporary_path.unlink(missing_ok=True)
        raise

    profile_totals = json.loads(profile_path.read_text(encoding="utf-8"))["totals"]
    expected_target_rows = profile_totals["target_rows"]
    if target_rows != expected_target_rows:
        output_path.unlink(missing_ok=True)
        raise ValueError(
            f"extracted {target_rows} target rows, expected {expected_target_rows} from profile"
        )
    expected_eligible_rows = profile_totals.get("eligible_target_rows")
    if expected_eligible_rows is not None and eligible_rows != expected_eligible_rows:
        output_path.unlink(missing_ok=True)
        raise ValueError(
            f"extracted {eligible_rows} eligible rows, expected {expected_eligible_rows} from profile"
        )

    report = {
        "schema_version": 1,
        "generated_at": datetime.now(tz=UTC).isoformat(),
        "source_sha256": source_profile["sha256"],
        "eligibility_policy": asdict(policy),
        "output": {
            "path": str(output_path),
            "bytes": output_path.stat().st_size,
            "sha256": sha256_file(output_path),
            "compression": compression,
        },
        "counts": {
            "source_rows": total_rows,
            "target_rows": target_rows,
            "written_rows": target_rows - malformed_target_rows,
            "eligible_target_rows": eligible_rows,
            "malformed_target_rows": malformed_target_rows,
        },
        "build_seconds": round(time.monotonic() - started, 3),
    }
    write_json_atomic(manifest_path, report)
    return report
