from __future__ import annotations

import csv
import json
import tempfile
import unittest
from pathlib import Path

from sponsor_detection.data.profile import (
    REQUIRED_COLUMNS,
    EligibilityPolicy,
    HyperLogLog,
    profile_csv,
    write_json_atomic,
)


FIELDNAMES = [
    "videoID",
    "startTime",
    "endTime",
    "votes",
    "locked",
    "incorrectVotes",
    "UUID",
    "userID",
    "timeSubmitted",
    "views",
    "category",
    "actionType",
    "service",
    "videoDuration",
    "hidden",
    "reputation",
    "shadowHidden",
    "hashedVideoID",
    "userAgent",
    "description",
]


def _row(**overrides: str) -> dict[str, str]:
    row = {
        "videoID": "video-1",
        "startTime": "10",
        "endTime": "20",
        "votes": "2",
        "locked": "0",
        "incorrectVotes": "0",
        "UUID": "segment-1",
        "userID": "user-1",
        "timeSubmitted": "1700000000000",
        "views": "100",
        "category": "sponsor",
        "actionType": "skip",
        "service": "YouTube",
        "videoDuration": "100",
        "hidden": "0",
        "reputation": "1",
        "shadowHidden": "0",
        "hashedVideoID": "hash",
        "userAgent": "",
        "description": "",
    }
    row.update(overrides)
    return row


class ProfileCsvTest(unittest.TestCase):
    def test_profiles_target_rows_and_rejection_reasons(self) -> None:
        rows = [
            _row(),
            _row(videoID="video-2", UUID="segment-2", votes="-1"),
            _row(videoID="video-3", UUID="segment-3", hidden="1"),
            _row(videoID="video-4", UUID="segment-4", startTime="30", endTime="20"),
            _row(videoID="video-5", UUID="segment-5", endTime="150"),
            _row(videoID="video-6", UUID="segment-6", category="intro"),
        ]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sponsorTimes.csv"
            with path.open("w", encoding="utf-8", newline="") as output:
                writer = csv.DictWriter(output, fieldnames=FIELDNAMES)
                writer.writeheader()
                writer.writerows(rows)

            report = profile_csv(path, EligibilityPolicy(), compute_sha256=False)

        self.assertEqual(report["totals"]["rows"], 6)
        self.assertEqual(report["totals"]["target_rows"], 5)
        self.assertEqual(report["totals"]["eligible_target_rows"], 1)
        self.assertEqual(report["totals"]["invalid_intervals_all_categories"], 1)
        self.assertEqual(report["counts"]["target_rejection_flags"]["below_minimum_votes"], 1)
        self.assertEqual(report["counts"]["target_rejection_flags"]["hidden"], 1)
        self.assertEqual(report["counts"]["target_rejection_flags"]["invalid_interval"], 1)
        self.assertEqual(report["counts"]["target_rejection_flags"]["duration_mismatch"], 1)

    def test_rejects_a_snapshot_with_missing_columns(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sponsorTimes.csv"
            path.write_text("videoID,startTime\nvideo-1,1\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "missing required columns"):
                profile_csv(path, EligibilityPolicy(), compute_sha256=False)

    def test_required_columns_match_fixture_schema(self) -> None:
        self.assertFalse(REQUIRED_COLUMNS.difference(FIELDNAMES))

    def test_hyperloglog_estimates_small_cardinality(self) -> None:
        estimator = HyperLogLog()
        for index in range(1_000):
            estimator.add(f"video-{index}")
        self.assertLess(abs(estimator.estimate() - 1_000), 75)

    def test_atomic_json_writer_replaces_the_destination(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output_path = Path(directory) / "report.json"
            output_path.write_text("stale", encoding="utf-8")
            write_json_atomic(output_path, {"schema_version": 1})
            self.assertEqual(json.loads(output_path.read_text(encoding="utf-8")), {"schema_version": 1})


if __name__ == "__main__":
    unittest.main()

