from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

import pyarrow as pa
import pyarrow.parquet as pq

from sponsor_detection.data.benchmark import write_json_lines_atomic
from sponsor_detection.data.benchmark_review import (
    freeze_reviewed_benchmark,
    prepare_benchmark_review,
)
from sponsor_detection.data.profile import sha256_file


class BenchmarkReviewTest(unittest.TestCase):
    def test_prepares_and_freezes_balanced_reviewed_records(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            candidates_path = root / "candidates.jsonl"
            candidate_records = [
                self._candidate("positive001", "sponsor_positive"),
                self._candidate("hardneg0001", "hard_negative"),
                self._candidate("ordinary001", "ordinary_negative"),
            ]
            write_json_lines_atomic(candidates_path, candidate_records)
            candidates_manifest = root / "candidate-manifest.json"
            candidates_manifest.write_text(
                json.dumps({"output": {"sha256": sha256_file(candidates_path)}}),
                encoding="utf-8",
            )

            transcripts = root / "transcripts"
            transcripts.mkdir()
            metadata_path = root / "metadata.jsonl"
            metadata = []
            for index, candidate in enumerate(candidate_records):
                video_id = candidate["video_id"]
                (transcripts / f"{video_id}.json").write_text(
                    json.dumps(
                        {
                            "video_id": video_id,
                            "language_code": "asr-en" if index == 0 else "en",
                            "is_generated": True,
                            "cues": [{"index": 0, "start_ms": 0, "end_ms": 1000, "text": "text"}],
                        }
                    ),
                    encoding="utf-8",
                )
                metadata.append(
                    {
                        "video_id": video_id,
                        "channel_id": f"channel-{index}",
                        "published_at": "2025-01-01T00:00:00Z",
                        "duration_ms": 10_000,
                        "privacy_status": "public",
                    }
                )
            write_json_lines_atomic(metadata_path, metadata)

            training = root / "training"
            training.mkdir()
            pq.write_table(
                pa.table({"channel_id": ["training-channel"]}),
                training / "train.parquet",
            )
            review_path = root / "review.jsonl"
            review_manifest_path = root / "review-manifest.json"
            prepared = prepare_benchmark_review(
                candidates_path,
                candidates_manifest,
                transcripts,
                metadata_path,
                training,
                review_path,
                review_manifest_path,
                target_per_class=1,
            )
            review_records = [json.loads(line) for line in review_path.read_text().splitlines()]
            for record in review_records:
                annotation = record["annotation"]
                annotation["status"] = "reviewed"
                annotation["final_class"] = record["candidate_class"]
                if record["candidate_class"] == "sponsor_positive":
                    annotation["intervals"] = [{"start_ms": 1000, "end_ms": 5000}]
            write_json_lines_atomic(review_path, review_records)
            review_manifest = json.loads(review_manifest_path.read_text())
            review_manifest["output"]["sha256"] = sha256_file(review_path)
            review_manifest["output"]["bytes"] = review_path.stat().st_size
            review_manifest_path.write_text(json.dumps(review_manifest), encoding="utf-8")

            frozen = freeze_reviewed_benchmark(
                review_path,
                review_manifest_path,
                root / "frozen.jsonl",
                root / "frozen-manifest.json",
                target_per_class=1,
            )

        self.assertEqual(prepared["status"], "annotation_pending")
        self.assertEqual(prepared["counts"]["selected"], 3)
        self.assertEqual(review_records[0]["transcript"]["language_code"], "asr-en")
        self.assertEqual(frozen["status"], "frozen")
        self.assertEqual(frozen["counts"]["videos"], 3)
        self.assertEqual(frozen["counts"]["sponsor_intervals"], 1)

    @staticmethod
    def _candidate(video_id: str, benchmark_class: str) -> dict[str, object]:
        return {
            "video_id": video_id,
            "benchmark_class": benchmark_class,
            "selection_rank": 0,
            "source_categories": [],
            "segments": [],
        }


if __name__ == "__main__":
    unittest.main()
