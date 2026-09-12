from __future__ import annotations

import unittest

from sponsor_detection.calibration import select_operating_point


def result(
    threshold: float,
    *,
    window_precision: float,
    window_f1: float,
    span_precision: float,
    span_f1: float,
) -> dict[str, object]:
    return {
        "confidence_threshold": threshold,
        "window_presence": {"precision": window_precision, "f1": window_f1},
        "span_iou_0.5": {"precision": span_precision, "f1": span_f1},
    }


class CalibrationTest(unittest.TestCase):
    def test_selects_best_span_f1_after_precision_constraints(self) -> None:
        selected = select_operating_point(
            [
                result(
                    0.5,
                    window_precision=0.94,
                    window_f1=0.92,
                    span_precision=0.91,
                    span_f1=0.90,
                ),
                result(
                    0.7,
                    window_precision=0.96,
                    window_f1=0.91,
                    span_precision=0.92,
                    span_f1=0.89,
                ),
                result(
                    0.8,
                    window_precision=0.97,
                    window_f1=0.90,
                    span_precision=0.93,
                    span_f1=0.88,
                ),
            ],
            minimum_window_precision=0.95,
            minimum_span_precision=0.90,
        )

        self.assertEqual(selected["confidence_threshold"], 0.7)

    def test_rejects_an_unreachable_precision_policy(self) -> None:
        with self.assertRaisesRegex(ValueError, "no confidence threshold"):
            select_operating_point(
                [
                    result(
                        0.5,
                        window_precision=0.94,
                        window_f1=0.92,
                        span_precision=0.89,
                        span_f1=0.90,
                    )
                ],
                minimum_window_precision=0.95,
                minimum_span_precision=0.90,
            )


if __name__ == "__main__":
    unittest.main()
