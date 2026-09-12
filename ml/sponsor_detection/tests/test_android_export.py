from __future__ import annotations

import unittest

from sponsor_detection.android_export import compare_evaluation_reports


class AndroidExportTest(unittest.TestCase):
    def test_compares_presence_and_exact_interval_parity(self) -> None:
        baseline = {
            "videos": [
                {
                    "video_id": "same",
                    "predicted_spans": [{"start_ms": 100, "end_ms": 200}],
                },
                {
                    "video_id": "shifted",
                    "predicted_spans": [{"start_ms": 300, "end_ms": 400}],
                },
                {"video_id": "empty", "predicted_spans": []},
            ]
        }
        candidate = {
            "videos": [
                {
                    "video_id": "same",
                    "predicted_spans": [{"start_ms": 100, "end_ms": 200}],
                },
                {
                    "video_id": "shifted",
                    "predicted_spans": [{"start_ms": 301, "end_ms": 400}],
                },
                {"video_id": "empty", "predicted_spans": []},
            ]
        }

        comparison = compare_evaluation_reports(baseline, candidate)

        self.assertEqual(comparison["videos"], 3)
        self.assertEqual(comparison["exact_interval_matches"], 2)
        self.assertEqual(comparison["presence_matches"], 3)
        self.assertEqual(comparison["differing_videos"][0]["video_id"], "shifted")

    def test_rejects_different_video_sets(self) -> None:
        with self.assertRaisesRegex(ValueError, "different video sets"):
            compare_evaluation_reports(
                {"videos": [{"video_id": "one", "predicted_spans": []}]},
                {"videos": [{"video_id": "two", "predicted_spans": []}]},
            )


if __name__ == "__main__":
    unittest.main()
