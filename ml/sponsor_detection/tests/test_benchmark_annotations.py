from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from sponsor_detection.data.benchmark import write_json_lines_atomic
from sponsor_detection.data.benchmark_annotations import apply_benchmark_annotations
from sponsor_detection.data.profile import sha256_file


class BenchmarkAnnotationsTest(unittest.TestCase):
    def test_applies_complete_annotations_and_rehashes_review(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            review_path = root / "review.jsonl"
            write_json_lines_atomic(
                review_path,
                [
                    {
                        "video_id": "positive",
                        "duration_ms": 3000,
                        "annotation": {"status": "pending"},
                    },
                    {
                        "video_id": "negative",
                        "duration_ms": 3000,
                        "annotation": {"status": "pending"},
                    },
                ],
            )
            manifest_path = root / "manifest.json"
            manifest_path.write_text(
                json.dumps({"output": {"sha256": sha256_file(review_path)}}),
                encoding="utf-8",
            )
            annotations_path = root / "annotations.json"
            annotations_path.write_text(
                json.dumps(
                    {
                        "positive": {
                            "final_class": "sponsor_positive",
                            "intervals": [{"start_ms": 100, "end_ms": 1000}],
                            "notes": "reviewed",
                        },
                        "negative": {
                            "final_class": "hard_negative",
                            "intervals": [],
                        },
                    }
                ),
                encoding="utf-8",
            )
            report = apply_benchmark_annotations(
                review_path, manifest_path, annotations_path
            )
            records = [json.loads(line) for line in review_path.read_text().splitlines()]
            output_sha256 = sha256_file(review_path)

        self.assertEqual(report["status"], "reviewed")
        self.assertEqual(report["annotations"]["reviewed_videos"], 2)
        self.assertEqual(report["output"]["sha256"], output_sha256)
        self.assertTrue(all(record["annotation"]["status"] == "reviewed" for record in records))

    def test_rejects_annotations_with_missing_video(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            review_path = root / "review.jsonl"
            write_json_lines_atomic(review_path, [{"video_id": "video"}])
            manifest_path = root / "manifest.json"
            manifest_path.write_text(
                json.dumps({"output": {"sha256": sha256_file(review_path)}}),
                encoding="utf-8",
            )
            annotations_path = root / "annotations.json"
            annotations_path.write_text("{}", encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "do not match"):
                apply_benchmark_annotations(review_path, manifest_path, annotations_path)


if __name__ == "__main__":
    unittest.main()
