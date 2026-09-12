from __future__ import annotations

import json
import unittest

from sponsor_detection.data.yt_dlp_transcripts import (
    YtDlpBlockedError,
    YtDlpNoTranscriptError,
    YtDlpTranscriptClient,
)


class _Response:
    def __init__(self, payload: dict[str, object]) -> None:
        self._payload = json.dumps(payload).encode()

    def read(self) -> bytes:
        return self._payload

    def close(self) -> None:
        pass


class _YoutubeDL:
    info: dict[str, object] = {}
    error: BaseException | None = None

    def __init__(self, options: dict[str, object]) -> None:
        self.options = options

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback) -> None:
        pass

    def extract_info(self, url: str, download: bool):
        if self.error:
            raise self.error
        return self.info

    def urlopen(self, url: str) -> _Response:
        return _Response(
            {
                "events": [
                    {
                        "tStartMs": 1250,
                        "dDurationMs": 2500,
                        "segs": [{"utf8": "hello\nworld"}],
                    }
                ]
            }
        )


class YtDlpTranscriptClientTest(unittest.TestCase):
    def tearDown(self) -> None:
        _YoutubeDL.info = {}
        _YoutubeDL.error = None

    def test_prefers_manual_track_and_normalizes_json3(self) -> None:
        _YoutubeDL.info = {
            "subtitles": {
                "en-US": [{"ext": "json3", "url": "https://example.test/manual"}]
            },
            "automatic_captions": {
                "en": [{"ext": "json3", "url": "https://example.test/auto"}]
            },
        }
        delays: list[float] = []
        transcript = (
            YtDlpTranscriptClient(
                subtitle_request_delay_seconds=1.0,
                sleep=delays.append,
                youtube_dl_factory=_YoutubeDL,
            )
            .list("abcdefghijk")
            .find_transcript(["en"])
        )
        snippets = list(transcript.fetch())

        self.assertFalse(transcript.is_generated)
        self.assertEqual(transcript.source, "yt_dlp")
        self.assertEqual(transcript.language_code, "en-US")
        self.assertEqual(delays, [1.0])
        self.assertEqual(snippets[0].text, "hello world")
        self.assertEqual(snippets[0].start, 1.25)
        self.assertEqual(snippets[0].duration, 2.5)

    def test_reports_missing_requested_language(self) -> None:
        _YoutubeDL.info = {
            "subtitles": {
                "fr": [{"ext": "json3", "url": "https://example.test/fr"}]
            }
        }
        client = YtDlpTranscriptClient(
            subtitle_request_delay_seconds=0,
            youtube_dl_factory=_YoutubeDL,
        )

        with self.assertRaises(YtDlpNoTranscriptError):
            client.list("abcdefghijk").find_transcript(["en"])

    def test_treats_bot_detection_as_a_block(self) -> None:
        _YoutubeDL.error = RuntimeError("Sign in to confirm you're not a bot")
        client = YtDlpTranscriptClient(
            subtitle_request_delay_seconds=0,
            youtube_dl_factory=_YoutubeDL,
        )

        with self.assertRaises(YtDlpBlockedError):
            client.list("abcdefghijk").find_transcript(["en"])


if __name__ == "__main__":
    unittest.main()
