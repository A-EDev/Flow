from __future__ import annotations

import unittest

from sponsor_detection.disagreement import presence_disagreement
from sponsor_detection.model.decoder import DecodedSponsorSpan


SPAN = (DecodedSponsorSpan(0, 10, 0.9, 1, 2),)


class DisagreementTest(unittest.TestCase):
    def test_classifies_removed_false_positive(self) -> None:
        self.assertEqual(
            presence_disagreement(SPAN, (), expected_positive=False),
            "v3_removed_false_positive",
        )

    def test_classifies_introduced_false_negative(self) -> None:
        self.assertEqual(
            presence_disagreement(SPAN, (), expected_positive=True),
            "v3_introduced_false_negative",
        )

    def test_classifies_recovered_and_added_predictions(self) -> None:
        self.assertEqual(
            presence_disagreement((), SPAN, expected_positive=True),
            "v3_recovered_positive",
        )
        self.assertEqual(
            presence_disagreement((), SPAN, expected_positive=False),
            "v3_added_false_positive",
        )


if __name__ == "__main__":
    unittest.main()
