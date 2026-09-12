from __future__ import annotations

import unittest

from sponsor_detection.model.token_labels import (
    IGNORED_LABEL_ID,
    LABEL_TO_ID,
    bilou_labels_for_offsets,
)
from sponsor_detection.train import _percentile


class TokenLabelsTest(unittest.TestCase):
    def test_assigns_bilou_and_ignores_special_tokens(self) -> None:
        offsets = [(0, 0), (0, 5), (6, 10), (11, 15), (16, 20), (0, 0)]
        labels = bilou_labels_for_offsets(
            offsets,
            [{"start_char": 6, "end_char": 15}],
        )
        self.assertEqual(
            labels,
            [
                IGNORED_LABEL_ID,
                LABEL_TO_ID["O"],
                LABEL_TO_ID["B-SPONSOR"],
                LABEL_TO_ID["L-SPONSOR"],
                LABEL_TO_ID["O"],
                IGNORED_LABEL_ID,
            ],
        )

    def test_assigns_unit_label_for_one_token_span(self) -> None:
        labels = bilou_labels_for_offsets(
            [(0, 0), (0, 5), (0, 0)],
            [{"start_char": 1, "end_char": 4}],
        )
        self.assertEqual(labels[1], LABEL_TO_ID["U-SPONSOR"])

    def test_percentile_uses_nearest_rank_index(self) -> None:
        self.assertEqual(_percentile([1, 2, 3, 4, 5], 0.50), 3)
        self.assertEqual(_percentile([1, 2, 3, 4, 5], 0.95), 5)


if __name__ == "__main__":
    unittest.main()
