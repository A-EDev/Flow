from __future__ import annotations

import unittest

from sponsor_detection.data.scriptsmith_dataset import (
    _char_span_for_interval,
    _is_english,
    _parse_cues,
    _published_at,
    reconstruct_caption_lines,
)
from sponsor_detection.inference.windowing import (
    TranscriptCue,
    assemble_transcript,
)


class ReconstructCaptionLinesTest(unittest.TestCase):
    def test_collapses_rolling_auto_caption_duplication(self) -> None:
        cues = [
            (3429, 3439, "Good day, my dear ladies and"),
            (3439, 6710, "Good day, my dear ladies and\ngentlemen, hello. I release"),
            (6710, 6720, "gentlemen, hello. I released the"),
            (6720, 9070, "gentlemen, hello. I released the\nlast video"),
            (9070, 9080, "last video"),
        ]
        lines = reconstruct_caption_lines(cues)
        texts = [text for _, _, text in lines]
        self.assertEqual(
            texts,
            [
                "Good day, my dear ladies and",
                "gentlemen, hello. I release",
                "d the",
                "last video",
            ],
        )

    def test_strips_only_the_overlapping_prefix(self) -> None:
        cues = [
            (0, 1000, "hello world"),
            (1000, 2000, "hello world again"),
        ]
        lines = reconstruct_caption_lines(cues)
        self.assertEqual([text for _, _, text in lines], ["hello world", "again"])

    def test_preserves_genuinely_repeated_words(self) -> None:
        cues = [
            (0, 1000, "what what"),
            (1000, 2000, "what what what"),
        ]
        lines = reconstruct_caption_lines(cues)
        self.assertEqual([text for _, _, text in lines], ["what what", "what"])


class EnglishFilterTest(unittest.TestCase):
    def test_matches_plain_and_regional_and_dirty_codes(self) -> None:
        for language in ["en", "en-US", "en-GB", "en-IN", "en-ehkg1hFWq8A", "en-zh"]:
            self.assertTrue(_is_english(language, ["en"]), language)

    def test_rejects_other_languages_and_missing(self) -> None:
        for language in ["ru", "pl", "hi", None, "", "eng-latn"]:
            self.assertFalse(_is_english(language, ["en"]), str(language))


class ParseCuesTest(unittest.TestCase):
    def test_converts_seconds_to_sorted_milliseconds_and_drops_invalid(self) -> None:
        payload = (
            '[{"start": 3.429, "end": 3.439, "text": "b"},'
            ' {"start": 1.0, "end": 2.0, "text": "a"},'
            ' {"start": 5.0, "end": 4.0, "text": "bad"},'
            ' {"start": 6.0, "end": 7.0}]'
        )
        self.assertEqual(_parse_cues(payload), [(1000, 2000, "a"), (3429, 3439, "b")])


class CharSpanTest(unittest.TestCase):
    def test_maps_time_interval_to_character_span(self) -> None:
        cues = [
            TranscriptCue(index=0, start_ms=0, end_ms=1000, text="intro"),
            TranscriptCue(index=1, start_ms=1000, end_ms=2000, text="sponsor"),
            TranscriptCue(index=2, start_ms=2000, end_ms=3000, text="outro"),
        ]
        transcript = assemble_transcript(cues)
        self.assertEqual(_char_span_for_interval(transcript, 1000, 2000), (6, 13))
        self.assertEqual(_char_span_for_interval(transcript, 1500, 2500), (6, 19))

    def test_returns_none_for_non_overlapping_interval(self) -> None:
        cues = [TranscriptCue(index=0, start_ms=0, end_ms=1000, text="intro")]
        transcript = assemble_transcript(cues)
        self.assertIsNone(_char_span_for_interval(transcript, 5000, 6000))


class PublishedAtTest(unittest.TestCase):
    def test_prefers_unix_timestamp(self) -> None:
        self.assertTrue(
            str(_published_at(1700000000, "20230101")).startswith("2023-11-14")
        )

    def test_falls_back_to_upload_date(self) -> None:
        self.assertEqual(_published_at(None, "20240215"), "2024-02-15T00:00:00+00:00")

    def test_returns_none_when_unavailable(self) -> None:
        self.assertIsNone(_published_at(None, None))


if __name__ == "__main__":
    unittest.main()
