from __future__ import annotations

import hashlib
import json
import os
import shutil
import tempfile
from heapq import heappop, heappush
from pathlib import Path

from sponsor_detection.data.audit_corrections import _parquet_summary
from sponsor_detection.data.profile import sha256_file, write_json_atomic


def _rank(seed: str, example_id: str) -> int:
    digest = hashlib.sha256(f"{seed}\0{example_id}".encode("utf-8")).hexdigest()
    return int(digest, 16)


def _rows(path: Path):
    import pyarrow.parquet as parquet

    file = parquet.ParquetFile(path)
    for batch in file.iter_batches(batch_size=8192):
        yield from batch.to_pylist()


def _weak_positive_rows(path: Path) -> tuple[list[dict[str, object]], set[str]]:
    rows: list[dict[str, object]] = []
    for row in _rows(path):
        if str(row["label_kind"]) == "weak_positive":
            rows.append(row)
    return rows, {str(row["example_id"]) for row in rows}


def _select_replay_rows(
    path: Path,
    excluded_example_ids: set[str],
    replay_counts: dict[str, int],
    *,
    seed: str,
) -> list[dict[str, object]]:
    heaps: dict[str, list[tuple[int, str, dict[str, object]]]] = {
        label_kind: [] for label_kind in replay_counts
    }
    for row in _rows(path):
        example_id = str(row["example_id"])
        label_kind = str(row["label_kind"])
        target = int(replay_counts.get(label_kind, 0))
        if example_id in excluded_example_ids or target <= 0:
            continue
        entry = (-_rank(seed, example_id), example_id, row)
        heap = heaps[label_kind]
        if len(heap) < target:
            heappush(heap, entry)
        elif entry > heap[0]:
            heappop(heap)
            heappush(heap, entry)
    selected = [entry[2] for heap in heaps.values() for entry in heap]
    selected.sort(key=lambda row: (str(row["label_kind"]), str(row["example_id"])))
    return selected


def _write_rows(path: Path, rows: list[dict[str, object]], schema) -> None:
    import pyarrow as pa
    import pyarrow.parquet as parquet

    descriptor, temporary_name = tempfile.mkstemp(
        prefix="replay-train-", suffix=".parquet"
    )
    os.close(descriptor)
    temporary = Path(temporary_name)
    try:
        writer = parquet.ParquetWriter(
            temporary,
            schema,
            compression="zstd",
            use_dictionary=True,
        )
        try:
            for start in range(0, len(rows), 8192):
                writer.write_table(
                    pa.Table.from_pylist(rows[start : start + 8192], schema=schema)
                )
        finally:
            writer.close()
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def build_replay_dataset(
    source_directory: Path,
    weak_source_directory: Path,
    output_directory: Path,
    source_manifest_path: Path,
    weak_source_manifest_path: Path,
    output_manifest_path: Path,
    *,
    replay_counts: dict[str, int],
    seed: str,
) -> dict[str, object]:
    import pyarrow.parquet as parquet

    source_manifest = json.loads(source_manifest_path.read_text(encoding="utf-8"))
    weak_manifest = json.loads(weak_source_manifest_path.read_text(encoding="utf-8"))
    source_train = source_directory / "train.parquet"
    weak_train = weak_source_directory / "train.parquet"
    if sha256_file(source_train) != source_manifest["outputs"]["train"]["sha256"]:
        raise ValueError("source train dataset does not match its manifest")
    if sha256_file(weak_train) != weak_manifest["outputs"]["train"]["sha256"]:
        raise ValueError("weak-label train dataset does not match its manifest")

    weak_rows, weak_ids = _weak_positive_rows(weak_train)
    replay_rows = _select_replay_rows(
        source_train,
        weak_ids,
        replay_counts,
        seed=seed,
    )
    rows = weak_rows + replay_rows
    rows.sort(key=lambda row: (str(row["label_kind"]), str(row["example_id"])))

    output_directory.mkdir(parents=True, exist_ok=True)
    source_file = parquet.ParquetFile(source_train)
    _write_rows(output_directory / "train.parquet", rows, source_file.schema_arrow)
    for split in ("validation", "test"):
        shutil.copyfile(
            source_directory / f"{split}.parquet",
            output_directory / f"{split}.parquet",
        )

    outputs: dict[str, dict[str, object]] = {}
    for split in ("train", "validation", "test"):
        path = output_directory / f"{split}.parquet"
        outputs[split] = {
            **_parquet_summary(path),
            "path": str(path),
            "bytes": path.stat().st_size,
            "sha256": sha256_file(path),
        }
    manifest = {
        "schema_version": 1,
        "dataset_kind": "incremental_replay",
        "configuration": {
            "seed": seed,
            "replay_counts": replay_counts,
            "source_manifest_path": str(source_manifest_path),
            "source_manifest_sha256": sha256_file(source_manifest_path),
            "weak_source_manifest_path": str(weak_source_manifest_path),
            "weak_source_manifest_sha256": sha256_file(weak_source_manifest_path),
        },
        "selection": {
            "weak_positive_rows": len(weak_rows),
            "replay_rows": len(replay_rows),
            "total_rows": len(rows),
            "replay_label_kinds": {
                label_kind: sum(
                    1 for row in replay_rows if row["label_kind"] == label_kind
                )
                for label_kind in replay_counts
            },
        },
        "outputs": outputs,
        "leakage": source_manifest["leakage"],
    }
    write_json_atomic(output_manifest_path, manifest)
    return manifest
