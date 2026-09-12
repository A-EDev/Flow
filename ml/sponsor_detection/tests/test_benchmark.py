from __future__ import annotations

import csv
import json
import tempfile
import unittest
from pathlib import Path

import pyarrow as pa
import pyarrow.parquet as pq

from sponsor_detection.data.benchmark import build_mixed_benchmark_candidates
from sponsor_detection.data.profile import sha256_file


class MixedBenchmarkTest(unittest.TestCase):
    def test_builds_balanced_queue_and_excludes_seen_videos(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            labels_path = root / "labels.parquet"
            labels_manifest_path = root / "labels-manifest.json"
            pq.write_table(
                pa.table(
                    {
                        "video_id": ["pos00000001", "pos00000002", "train000001"],
                        "segment_id": ["p1", "p2", "excluded"],
                        "start_ms": [1000, 2000, 3000],
                        "end_ms": [5000, 6000, 7000],
                        "votes": [1, 2, 3],
                        "locked": [False, False, False],
                        "is_eligible": [True, True, True],
                    }
                ),
                labels_path,
            )
            labels_manifest_path.write_text(
                json.dumps({"output": {"sha256": sha256_file(labels_path)}}),
                encoding="utf-8",
            )

            training_directory = root / "training"
            training_directory.mkdir()
            pq.write_table(
                pa.table({"video_id": ["train000001"]}),
                training_directory / "train.parquet",
            )
            existing_pilot = root / "existing.jsonl"
            existing_pilot.write_text(
                json.dumps({"video_id": "pilot000001"}) + "\n", encoding="utf-8"
            )

            mirror_path = root / "sponsorTimes.csv"
            fields = [
                "videoID",
                "startTime",
                "endTime",
                "votes",
                "locked",
                "UUID",
                "category",
                "service",
                "hidden",
                "shadowHidden",
            ]
            rows = [
                self._row("hard0000001", "selfpromo", "h1"),
                self._row("hard0000002", "interaction", "h2"),
                self._row("ordinary001", "intro", "o1"),
                self._row("ordinary002", "filler", "o2"),
                self._row("mixed000001", "selfpromo", "m1"),
                self._row("mixed000001", "sponsor", "m2"),
                self._row("train000001", "selfpromo", "excluded"),
                self._row("pilot000001", "intro", "excluded-pilot"),
            ]
            with mirror_path.open("w", encoding="utf-8", newline="") as output:
                writer = csv.DictWriter(output, fieldnames=fields)
                writer.writeheader()
                writer.writerows(rows)

            output_path = root / "candidates.jsonl"
            manifest_path = root / "manifest.json"
            report = build_mixed_benchmark_candidates(
                mirror_path,
                labels_path,
                labels_manifest_path,
                training_directory,
                output_path,
                manifest_path,
                existing_pilot_path=existing_pilot,
                target_per_class=1,
                pool_per_class=1,
                seed="test-seed",
                negative_sample_modulus=1,
                progress_every_rows=0,
            )
            records = [json.loads(line) for line in output_path.read_text().splitlines()]

        self.assertEqual(len(records), 3)
        self.assertEqual(
            [record["benchmark_class"] for record in records],
            ["sponsor_positive", "hard_negative", "ordinary_negative"],
        )
        self.assertNotIn("mixed000001", {record["video_id"] for record in records})
        self.assertNotIn("train000001", {record["video_id"] for record in records})
        self.assertNotIn("pilot000001", {record["video_id"] for record in records})
        self.assertEqual(report["counts"]["queue_videos"], 3)
        self.assertEqual(report["counts"]["excluded_sponsor"], 1)

    @staticmethod
    def _row(video_id: str, category: str, segment_id: str) -> dict[str, object]:
        return {
            "videoID": video_id,
            "startTime": "1",
            "endTime": "5",
            "votes": "1",
            "locked": "0",
            "UUID": segment_id,
            "category": category,
            "service": "YouTube",
            "hidden": "0",
            "shadowHidden": "0",
        }


if __name__ == "__main__":
    unittest.main()
