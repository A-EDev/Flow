from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

import pyarrow as pa
import pyarrow.parquet as parquet

from sponsor_detection.data.profile import sha256_file
from sponsor_detection.data.training_dataset import (
    build_training_dataset,
    extract_sponsor_char_spans,
    temporal_iou,
)


class TrainingDatasetTest(unittest.TestCase):
    def test_extracts_exact_sponsor_character_spans(self) -> None:
        text = "intro this video is sponsored by acme outro"
        extracted = (
            "START_SPONSOR_TOKEN this video is sponsored by acme "
            "END_SPONSOR_TOKEN"
        )
        self.assertEqual(extract_sponsor_char_spans(text, extracted), [(6, 37)])

    def test_temporal_iou(self) -> None:
        self.assertAlmostEqual(temporal_iou(1000, 5000, 2000, 6000), 0.6)

    def test_builds_disjoint_validated_splits(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source"
            source.mkdir()
            positive = {
                "video_index": 1,
                "video_id": "abcdefghijk",
                "text": "intro sponsored by acme outro",
                "start": 0.0,
                "end": 10.0,
                "extracted": "START_SPONSOR_TOKEN sponsored by acme END_SPONSOR_TOKEN",
            }
            negative = {
                "video_index": 2,
                "video_id": "lmnopqrstuv",
                "text": "ordinary content",
                "start": 0.0,
                "end": 10.0,
                "extracted": "NO_SEGMENT_TOKEN",
            }
            for split, records in (
                ("train", [positive]),
                ("valid", [negative]),
                ("test", []),
            ):
                (source / f"{split}.json").write_text(
                    "".join(json.dumps(record) + "\n" for record in records),
                    encoding="utf-8",
                )
            (source / "segments.json").write_text(
                json.dumps(
                    {
                        "abcdefghijk": [
                            {"start": 1.0, "end": 5.0, "category": "sponsor"}
                        ],
                        "lmnopqrstuv": [],
                    }
                ),
                encoding="utf-8",
            )
            profile_path = root / "profile.json"
            profile_path.write_text(
                json.dumps({"source": {"revision": "fixture"}}), encoding="utf-8"
            )
            labels_path = root / "labels.parquet"
            parquet.write_table(
                pa.table(
                    {
                        "video_id": ["abcdefghijk"],
                        "segment_id": ["segment-1"],
                        "start_ms": [1000],
                        "end_ms": [5000],
                        "is_eligible": [True],
                    }
                ),
                labels_path,
            )
            labels_manifest = root / "labels-manifest.json"
            labels_manifest.write_text(
                json.dumps({"output": {"sha256": sha256_file(labels_path)}}),
                encoding="utf-8",
            )
            output = root / "output"
            manifest = build_training_dataset(
                source,
                profile_path,
                labels_path,
                labels_manifest,
                output,
                root / "manifest.json",
                seed="fixture",
                train_fraction=0.6,
                validation_fraction=0.2,
                current_iou_threshold=0.5,
                batch_size=1,
            )
            rows = []
            for split in ("train", "validation", "test"):
                rows.extend(parquet.read_table(output / f"{split}.parquet").to_pylist())

        self.assertEqual(len(rows), 2)
        positive_row = next(row for row in rows if row["label_kind"] == "positive")
        self.assertEqual(positive_row["sponsor_spans"][0]["start_char"], 6)
        self.assertEqual(manifest["validation_counts"], {"accepted": 2})
        self.assertEqual(manifest["leakage"]["video"]["train_test"], 0)


if __name__ == "__main__":
    unittest.main()
