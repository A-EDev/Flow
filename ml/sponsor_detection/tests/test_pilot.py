from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

import pyarrow as pa
import pyarrow.parquet as pq

from sponsor_detection.data.pilot import build_transcript_pilot
from sponsor_detection.data.profile import sha256_file


class BuildTranscriptPilotTest(unittest.TestCase):
    def test_sampling_is_deterministic_and_retains_selected_segments(self) -> None:
        table = pa.table(
            {
                "video_id": ["a", "b", "c", "d", "a", "b"],
                "segment_id": ["a1", "b1", "c1", "d1", "a2", "b2"],
                "start_ms": [1, 2, 3, 4, 5, 6],
                "end_ms": [11, 12, 13, 14, 15, 16],
                "votes": [1, 2, 3, 4, 5, 6],
                "locked": [False, False, False, True, False, False],
                "is_eligible": [True, True, True, True, True, True],
            }
        )
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            labels_path = root / "labels.parquet"
            labels_manifest_path = root / "labels_manifest.json"
            first_output = root / "pilot-1.jsonl"
            second_output = root / "pilot-2.jsonl"
            first_manifest = root / "pilot-1-manifest.json"
            second_manifest = root / "pilot-2-manifest.json"
            pq.write_table(table, labels_path)
            labels_manifest_path.write_text(
                json.dumps({"output": {"sha256": sha256_file(labels_path)}}),
                encoding="utf-8",
            )

            first_report = build_transcript_pilot(
                labels_path,
                labels_manifest_path,
                first_output,
                first_manifest,
                sample_size=2,
                seed="test-seed",
                batch_size=2,
            )
            second_report = build_transcript_pilot(
                labels_path,
                labels_manifest_path,
                second_output,
                second_manifest,
                sample_size=2,
                seed="test-seed",
                batch_size=3,
            )
            first_contents = first_output.read_text(encoding="utf-8")
            second_contents = second_output.read_text(encoding="utf-8")

        self.assertEqual(first_contents, second_contents)
        self.assertEqual(first_report["counts"]["pilot_videos"], 2)
        self.assertEqual(
            first_report["counts"]["pilot_segments"],
            second_report["counts"]["pilot_segments"],
        )


if __name__ == "__main__":
    unittest.main()
