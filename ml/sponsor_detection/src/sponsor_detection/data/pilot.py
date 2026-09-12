from __future__ import annotations

import hashlib
import heapq
import json
import os
import tempfile
import time
from datetime import UTC, datetime
from pathlib import Path
from typing import Callable

from sponsor_detection.data.profile import sha256_file, write_json_atomic


class DeterministicVideoSampler:
    """Keeps the videos with the smallest stable hashes in bounded memory."""

    def __init__(self, capacity: int, seed: str) -> None:
        if capacity <= 0:
            raise ValueError("sample size must be positive")
        self.capacity = capacity
        self.seed = seed
        self._heap: list[tuple[int, str]] = []
        self._records: dict[str, dict[str, object]] = {}

    def _score(self, video_id: str) -> int:
        digest = hashlib.blake2b(
            f"{self.seed}\0{video_id}".encode("utf-8"), digest_size=8
        ).digest()
        return int.from_bytes(digest, "big")

    def add_segment(
        self,
        *,
        video_id: str,
        segment_id: str,
        start_ms: int,
        end_ms: int,
        votes: int,
        locked: bool,
    ) -> None:
        record = self._records.get(video_id)
        if record is None:
            score = self._score(video_id)
            if len(self._heap) < self.capacity:
                heapq.heappush(self._heap, (-score, video_id))
            elif score < -self._heap[0][0]:
                _, evicted_video_id = heapq.heapreplace(self._heap, (-score, video_id))
                del self._records[evicted_video_id]
            else:
                return
            record = {
                "video_id": video_id,
                "sample_hash": f"{score:016x}",
                "segments": [],
            }
            self._records[video_id] = record

        record["segments"].append(
            {
                "segment_id": segment_id,
                "start_ms": start_ms,
                "end_ms": end_ms,
                "votes": votes,
                "locked": locked,
            }
        )

    def records(self) -> list[dict[str, object]]:
        return sorted(self._records.values(), key=lambda record: record["sample_hash"])


def _write_json_lines_atomic(output_path: Path, records: list[dict[str, object]]) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        dir=output_path.parent,
        prefix=f".{output_path.name}.",
        suffix=".tmp",
        text=True,
    )
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as output:
            for record in records:
                json.dump(record, output, sort_keys=True, separators=(",", ":"))
                output.write("\n")
        os.replace(temporary_name, output_path)
    except BaseException:
        Path(temporary_name).unlink(missing_ok=True)
        raise


def build_transcript_pilot(
    labels_path: Path,
    labels_manifest_path: Path,
    output_path: Path,
    manifest_path: Path,
    *,
    sample_size: int,
    seed: str,
    batch_size: int = 65_536,
    progress_every_rows: int = 0,
    progress_callback: Callable[[int, float], None] | None = None,
) -> dict[str, object]:
    try:
        import pyarrow.parquet as pq
    except ImportError as error:
        raise RuntimeError(
            "Pilot generation requires the data extra: pip install -e '.[data]'"
        ) from error

    if batch_size <= 0:
        raise ValueError("batch_size must be positive")
    labels_manifest = json.loads(labels_manifest_path.read_text(encoding="utf-8"))
    expected_sha256 = labels_manifest.get("output", {}).get("sha256")
    if not expected_sha256:
        raise ValueError("labels manifest does not contain an output SHA-256 fingerprint")
    if sha256_file(labels_path) != expected_sha256:
        raise ValueError("labels Parquet checksum differs from its manifest")

    sampler = DeterministicVideoSampler(sample_size, seed)
    columns = [
        "video_id",
        "segment_id",
        "start_ms",
        "end_ms",
        "votes",
        "locked",
        "is_eligible",
    ]
    scanned_rows = eligible_rows = 0
    started = time.monotonic()
    next_progress = progress_every_rows
    parquet = pq.ParquetFile(labels_path)
    for batch in parquet.iter_batches(batch_size=batch_size, columns=columns):
        values = batch.to_pydict()
        for index, is_eligible in enumerate(values["is_eligible"]):
            scanned_rows += 1
            if not is_eligible:
                continue
            eligible_rows += 1
            sampler.add_segment(
                video_id=values["video_id"][index],
                segment_id=values["segment_id"][index],
                start_ms=values["start_ms"][index],
                end_ms=values["end_ms"][index],
                votes=values["votes"][index],
                locked=values["locked"][index],
            )
        if progress_callback and progress_every_rows and scanned_rows >= next_progress:
            progress_callback(scanned_rows, time.monotonic() - started)
            next_progress += progress_every_rows

    records = sampler.records()
    if len(records) != sample_size:
        raise ValueError(
            f"requested {sample_size} pilot videos but only selected {len(records)}"
        )
    _write_json_lines_atomic(output_path, records)
    segment_count = sum(len(record["segments"]) for record in records)
    report = {
        "schema_version": 1,
        "generated_at": datetime.now(tz=UTC).isoformat(),
        "source_labels_sha256": expected_sha256,
        "sampling": {
            "method": "bottom-k blake2b-64 over unique video IDs",
            "seed": seed,
            "requested_videos": sample_size,
        },
        "output": {
            "path": str(output_path),
            "bytes": output_path.stat().st_size,
            "sha256": sha256_file(output_path),
        },
        "counts": {
            "scanned_label_rows": scanned_rows,
            "eligible_label_rows": eligible_rows,
            "pilot_videos": len(records),
            "pilot_segments": segment_count,
        },
        "build_seconds": round(time.monotonic() - started, 3),
    }
    write_json_atomic(manifest_path, report)
    return report
