from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from sponsor_detection.data.benchmark import write_json_lines_atomic
from sponsor_detection.data.profile import sha256_file
from sponsor_detection.video_evaluation import load_frozen_benchmark, temporal_span_counts


class VideoEvaluationTest(unittest.TestCase):
    def test_temporal_span_counts_are_one_to_one(self) -> None:
        counts = temporal_span_counts(
            [(1000, 5000), (7000, 9000)],
            [(1200, 4800), (20_000, 22_000)],
            iou_threshold=0.5,
        )

        self.assertEqual(counts["true_positive"], 1)
        self.assertEqual(counts["false_positive"], 1)
        self.assertEqual(counts["false_negative"], 1)

    def test_loads_hash_pinned_frozen_benchmark_with_negative_video(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            transcript_path = root / "abcdefghijk.json"
            transcript_path.write_text(
                json.dumps({"video_id": "abcdefghijk", "cues": []}), encoding="utf-8"
            )
            benchmark_path = root / "benchmark.jsonl"
            write_json_lines_atomic(
                benchmark_path,
                [
                    {
                        "video_id": "abcdefghijk",
                        "benchmark_class": "ordinary_negative",
                        "transcript": {
                            "path": str(transcript_path),
                            "sha256": sha256_file(transcript_path),
                        },
                        "intervals": [],
                    }
                ],
            )
            manifest_path = root / "manifest.json"
            manifest_path.write_text(
                json.dumps(
                    {
                        "status": "frozen",
                        "output": {"sha256": sha256_file(benchmark_path)},
                    }
                ),
                encoding="utf-8",
            )

            paths, expected, provenance = load_frozen_benchmark(
                benchmark_path, manifest_path
            )

        self.assertEqual(paths, [transcript_path])
        self.assertEqual(expected, {"abcdefghijk": []})
        self.assertEqual(
            provenance["benchmark_classes"], {"ordinary_negative": 1}
        )


if __name__ == "__main__":
    unittest.main()
