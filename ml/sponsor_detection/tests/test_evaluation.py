from __future__ import annotations

import unittest

from sponsor_detection.evaluation import (
    EvaluationAccumulator,
    character_overlap,
    interval_iou,
    match_spans,
)
from sponsor_detection.model.decoder import DecodedSponsorSpan


class SpanEvaluationTest(unittest.TestCase):
    def test_interval_iou_and_character_overlap(self) -> None:
        self.assertAlmostEqual(interval_iou((0, 10), (5, 15)), 5 / 15)
        self.assertEqual(character_overlap([(0, 10), (20, 30)], [(5, 25)]), 10)

    def test_matching_is_one_to_one_and_thresholded(self) -> None:
        matches = match_spans(
            [(0, 10), (20, 30)],
            [(1, 11), (19, 31)],
            iou_threshold=0.5,
        )
        self.assertEqual([(left, right) for left, right, _ in matches], [(0, 0), (1, 1)])
        self.assertEqual(match_spans([(0, 2)], [(10, 12)], iou_threshold=0.5), ())

    def test_accumulator_reports_span_window_and_boundary_metrics(self) -> None:
        accumulator = EvaluationAccumulator(error_limit=5)
        accumulator.add(
            example_id="positive",
            video_id="video-a",
            label_kind="positive",
            text="sponsor words",
            predicted_spans=(DecodedSponsorSpan(1, 9, 0.9, 1, 2),),
            expected_spans=((0, 10),),
        )
        accumulator.add(
            example_id="negative",
            video_id="video-b",
            label_kind="hard_negative",
            text="ordinary words",
            predicted_spans=(DecodedSponsorSpan(0, 5, 0.8, 1, 1),),
            expected_spans=(),
        )

        report = accumulator.report()

        self.assertEqual(report["span_metrics_by_character_iou"]["0.5"]["true_positive"], 1)
        self.assertEqual(report["span_metrics_by_character_iou"]["0.5"]["false_positive"], 1)
        self.assertEqual(report["window_presence"]["false_positive"], 1)
        self.assertEqual(report["boundary_error_characters_at_iou_0.5"]["start_median"], 1)
        self.assertEqual(len(accumulator.errors()["highest_confidence_false_positive_windows"]), 1)


if __name__ == "__main__":
    unittest.main()
