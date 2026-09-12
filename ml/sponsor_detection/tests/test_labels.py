from __future__ import annotations

import csv
import json
import tempfile
import unittest
from pathlib import Path

from sponsor_detection.data.labels import build_label_parquet
from sponsor_detection.data.profile import EligibilityPolicy, sha256_file
from test_profile import FIELDNAMES, _row


class BuildLabelsTest(unittest.TestCase):
    def test_builds_parquet_with_eligible_and_ambiguous_rows(self) -> None:
        import pyarrow.parquet as pq

        rows = [
            _row(),
            _row(videoID="video-2", UUID="segment-2", votes="-1"),
            _row(videoID="video-3", UUID="segment-3", category="intro"),
        ]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            input_path = root / "sponsorTimes.csv"
            profile_path = root / "profile.json"
            output_path = root / "labels.parquet"
            manifest_path = root / "manifest.json"
            with input_path.open("w", encoding="utf-8", newline="") as output:
                writer = csv.DictWriter(output, fieldnames=FIELDNAMES)
                writer.writeheader()
                writer.writerows(rows)
            profile_path.write_text(
                json.dumps(
                    {
                        "source": {
                            "bytes": input_path.stat().st_size,
                            "sha256": sha256_file(input_path),
                        },
                        "totals": {"target_rows": 2, "eligible_target_rows": 1},
                    }
                ),
                encoding="utf-8",
            )

            report = build_label_parquet(
                input_path,
                profile_path,
                output_path,
                manifest_path,
                EligibilityPolicy(),
                batch_size=1,
            )
            table = pq.read_table(output_path)
            manifest_exists = manifest_path.is_file()

        self.assertEqual(table.num_rows, 2)
        self.assertEqual(table.column("is_eligible").to_pylist(), [True, False])
        self.assertEqual(
            table.column("rejection_flags").to_pylist(),
            [[], ["below_minimum_votes"]],
        )
        self.assertEqual(report["counts"]["eligible_target_rows"], 1)
        self.assertTrue(manifest_exists)


if __name__ == "__main__":
    unittest.main()
