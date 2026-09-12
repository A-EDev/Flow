from __future__ import annotations

import unittest

from sponsor_detection.model.decoder import decode_bilou
from sponsor_detection.model.token_labels import IGNORED_LABEL_ID, LABEL_TO_ID


def logits_for(label: str, score: float = 8.0) -> list[float]:
    logits = [0.0] * len(LABEL_TO_ID)
    logits[LABEL_TO_ID[label]] = score
    return logits


class BilouDecoderTest(unittest.TestCase):
    def test_decodes_multi_token_and_unit_spans(self) -> None:
        offsets = [(0, 0), (0, 4), (5, 9), (10, 14), (15, 18), (0, 0)]
        logits = [
            logits_for("I-SPONSOR"),
            logits_for("B-SPONSOR"),
            logits_for("I-SPONSOR"),
            logits_for("L-SPONSOR"),
            logits_for("U-SPONSOR"),
            logits_for("B-SPONSOR"),
        ]

        decoded = decode_bilou(logits, offsets)

        self.assertEqual(decoded.labels[0], IGNORED_LABEL_ID)
        self.assertEqual(decoded.labels[-1], IGNORED_LABEL_ID)
        self.assertEqual(
            [(span.start_char, span.end_char) for span in decoded.spans],
            [(0, 14), (15, 18)],
        )
        self.assertTrue(all(0 < span.confidence <= 1 for span in decoded.spans))

    def test_repairs_an_invalid_greedy_path_globally(self) -> None:
        logits = [
            [0.0, 9.0, 10.0, 0.0, 0.0],
            [0.0, 0.0, 10.0, 9.0, 0.0],
            [10.0, 0.0, 0.0, 0.0, 0.0],
        ]

        decoded = decode_bilou(logits, [(0, 4), (5, 9), (10, 14)])

        self.assertEqual(
            decoded.labels,
            (
                LABEL_TO_ID["B-SPONSOR"],
                LABEL_TO_ID["L-SPONSOR"],
                LABEL_TO_ID["O"],
            ),
        )
        self.assertEqual(len(decoded.spans), 1)
        self.assertEqual((decoded.spans[0].start_char, decoded.spans[0].end_char), (0, 9))

    def test_ignores_masked_tokens_even_when_they_have_offsets(self) -> None:
        decoded = decode_bilou(
            [logits_for("O"), logits_for("U-SPONSOR")],
            [(0, 4), (5, 9)],
            attention_mask=[1, 0],
        )

        self.assertEqual(decoded.labels, (LABEL_TO_ID["O"], IGNORED_LABEL_ID))
        self.assertEqual(decoded.spans, ())

    def test_handles_an_input_with_only_special_tokens(self) -> None:
        decoded = decode_bilou(
            [logits_for("B-SPONSOR"), logits_for("L-SPONSOR")],
            [(0, 0), (0, 0)],
        )

        self.assertEqual(decoded.labels, (IGNORED_LABEL_ID, IGNORED_LABEL_ID))
        self.assertEqual(decoded.spans, ())
        self.assertEqual(decoded.path_log_probability, 0.0)

    def test_rejects_malformed_inputs(self) -> None:
        with self.assertRaisesRegex(ValueError, "same number"):
            decode_bilou([logits_for("O")], [])
        with self.assertRaisesRegex(ValueError, "expected 5 logits"):
            decode_bilou([[0.0]], [(0, 1)])
        with self.assertRaisesRegex(ValueError, "finite"):
            decode_bilou([[0.0, 0.0, float("nan"), 0.0, 0.0]], [(0, 1)])


if __name__ == "__main__":
    unittest.main()
