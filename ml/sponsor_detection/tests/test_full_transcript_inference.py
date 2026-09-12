from __future__ import annotations

import unittest

from sponsor_detection.inference.stitching import (
    WindowSponsorSpan,
    stitch_window_spans,
)
from sponsor_detection.inference.windowing import (
    TranscriptCue,
    assemble_transcript,
    build_transcript_windows,
)


class CharacterTokenizer:
    def __call__(
        self,
        text: str,
        *,
        max_length: int,
        stride: int,
        **_: object,
    ) -> dict[str, list[list[object]]]:
        capacity = max_length - 2
        starts = []
        start = 0
        while start < len(text):
            starts.append(start)
            end = min(start + capacity, len(text))
            if end == len(text):
                break
            start = end - stride
        input_ids = []
        attention_masks = []
        offset_mappings = []
        for start in starts:
            end = min(start + capacity, len(text))
            offsets = [(0, 0), *[(index, index + 1) for index in range(start, end)], (0, 0)]
            input_ids.append([101, *range(start + 1, end + 1), 102])
            attention_masks.append([1] * len(offsets))
            offset_mappings.append(offsets)
        return {
            "input_ids": input_ids,
            "attention_mask": attention_masks,
            "offset_mapping": offset_mappings,
        }


class FullTranscriptInferenceTest(unittest.TestCase):
    def test_assembles_normalized_text_and_maps_time(self) -> None:
        transcript = assemble_transcript(
            [
                TranscriptCue(0, 0, 1000, "Visit https://example.com 123"),
                TranscriptCue(1, 1000, 2000, "Sponsor now"),
            ]
        )

        self.assertEqual(
            transcript.text,
            "visit URL_TOKEN NUMBER_TOKEN sponsor now",
        )
        sponsor_start = transcript.text.index("sponsor")
        start_ms, end_ms = transcript.timestamps_for_span(
            sponsor_start, len(transcript.text)
        )
        self.assertEqual(start_ms, 1000)
        self.assertEqual(end_ms, 2000)

    def test_builds_overlapping_token_windows_with_global_offsets(self) -> None:
        transcript = assemble_transcript(
            [TranscriptCue(0, 0, 2000, "abcdefghijklmnopqrst")]
        )

        windows = build_transcript_windows(
            transcript,
            CharacterTokenizer(),
            max_length=8,
            overlap_tokens=2,
        )

        self.assertEqual([(window.start_char, window.end_char) for window in windows], [
            (0, 6),
            (4, 10),
            (8, 14),
            (12, 18),
            (16, 20),
        ])
        self.assertEqual(windows[1].offset_mapping[1], (4, 5))

    def test_stitches_duplicate_and_adjacent_window_predictions(self) -> None:
        transcript = assemble_transcript(
            [TranscriptCue(0, 0, 10_000, "a" * 100)]
        )
        stitched = stitch_window_spans(
            transcript,
            [
                WindowSponsorSpan(0, 20, 50, 0.80),
                WindowSponsorSpan(1, 18, 55, 0.92),
                WindowSponsorSpan(2, 60, 70, 0.90),
                WindowSponsorSpan(3, 90, 95, 0.40),
            ],
            confidence_threshold=0.65,
            merge_gap_characters=6,
            merge_gap_ms=1000,
        )

        self.assertEqual(len(stitched), 1)
        self.assertEqual((stitched[0].start_char, stitched[0].end_char), (18, 70))
        self.assertEqual(stitched[0].supporting_windows, (0, 1, 2))
        self.assertEqual(stitched[0].confidence, 0.92)


if __name__ == "__main__":
    unittest.main()
