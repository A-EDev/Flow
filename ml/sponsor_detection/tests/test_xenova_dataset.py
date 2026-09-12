from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path

import pyarrow as pa
import pyarrow.parquet as parquet

from sponsor_detection.data.xenova_dataset import profile_xenova_dataset


def _write_jsonl(path: Path, records: list[dict[str, object]]) -> None:
    path.write_text("".join(json.dumps(record) + "\n" for record in records), encoding="utf-8")


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class XenovaDatasetProfileTest(unittest.TestCase):
    def test_profiles_splits_leakage_and_current_label_overlap(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            positive = {
                "video_index": 1,
                "video_id": "abcdefghijk",
                "text": "a sponsor message",
                "start": 1.0,
                "end": 4.0,
                "extracted": "START_SPONSOR_TOKEN sponsor END_SPONSOR_TOKEN",
            }
            negative = {
                "video_index": 2,
                "video_id": "lmnopqrstuv",
                "text": "ordinary content",
                "start": 5.0,
                "end": 9.0,
                "extracted": "NO_SEGMENT_TOKEN",
            }
            _write_jsonl(root / "train.json", [positive, negative])
            _write_jsonl(root / "valid.json", [positive])
            _write_jsonl(root / "test.json", [])
            (root / "segments.json").write_text(
                json.dumps({"abcdefghijk": [{"start": 1, "end": 4, "category": "sponsor"}]}),
                encoding="utf-8",
            )
            (root / "processed_database.json").write_text(
                json.dumps({"abcdefghijk": [{"start": 1, "end": 4, "category": "sponsor"}]}),
                encoding="utf-8",
            )
            labels_path = root / "labels.parquet"
            parquet.write_table(
                pa.table(
                    {
                        "video_id": ["abcdefghijk", "unmatched000"],
                        "is_eligible": [True, True],
                    }
                ),
                labels_path,
            )
            expected = {
                path.name: _sha256(path)
                for path in root.glob("*.json")
            }
            report = profile_xenova_dataset(
                root,
                root / "report.json",
                revision="fixture",
                expected_sha256=expected,
                current_labels_path=labels_path,
            )

        self.assertEqual(report["totals"]["rows"], 3)
        self.assertEqual(report["totals"]["unique_video_ids"], 2)
        self.assertEqual(report["totals"]["exact_duplicate_rows"], 1)
        self.assertEqual(report["split_video_overlap"]["train_valid"], 1)
        self.assertEqual(
            report["coverage"]["current_sponsorblock_labels"]["matched_videos"], 1
        )
        self.assertEqual(report["splits"]["train"]["category_rows"], {"SPONSOR": 1})


if __name__ == "__main__":
    unittest.main()
