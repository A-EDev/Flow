from __future__ import annotations

import unittest

from sponsor_detection.data.transcript_api import (
    TranscriptApiClient,
    TranscriptApiConfigurationError,
    TranscriptApiNoTranscriptError,
    TranscriptApiRetryableError,
)


class _Response:
    def __init__(
        self,
        status: int,
        payload: dict[str, object],
        headers: dict[str, str] | None = None,
    ) -> None:
        self.status_code = status
        self._payload = payload
        self.headers = headers or {}

    def json(self) -> dict[str, object]:
        return self._payload


class TranscriptApiClientTest(unittest.TestCase):
    def test_fetches_timestamped_english_transcript_without_exposing_key(self) -> None:
        requests = []

        def request_get(url, *, headers, params, timeout):
            requests.append((url, headers, params, timeout))
            return _Response(
                200,
                {
                    "video_id": "abcdefghijk",
                    "language": "asr-en",
                    "transcript": [
                        {
                            "text": " hello\nworld ",
                            "start": 1.25,
                            "duration": 2.5,
                        }
                    ],
                },
            )

        transcript = (
            TranscriptApiClient("secret-key", request_get=request_get)
            .list("abcdefghijk")
            .find_transcript(["en", "en-US"])
        )
        snippets = list(transcript.fetch())

        self.assertEqual(len(requests), 1)
        self.assertEqual(requests[0][3], 30.0)
        self.assertEqual(requests[0][1]["Authorization"], "Bearer secret-key")
        self.assertNotIn("secret-key", requests[0][0])
        self.assertEqual(requests[0][2]["language"], "en,asr-en")
        self.assertEqual(transcript.source, "transcript_api")
        self.assertEqual(transcript.source_version, "v2")
        self.assertEqual(transcript.language_code, "asr-en")
        self.assertTrue(transcript.is_generated)
        self.assertEqual(snippets[0].text, "hello world")
        self.assertEqual(snippets[0].start, 1.25)
        self.assertEqual(snippets[0].duration, 2.5)

    def test_maps_404_to_no_transcript(self) -> None:
        def request_get(url, *, headers, params, timeout):
            return _Response(404, {"detail": "No transcript"})

        client = TranscriptApiClient("secret-key", request_get=request_get)

        with self.assertRaises(TranscriptApiNoTranscriptError):
            client.list("abcdefghijk").find_transcript(["en"])

    def test_stops_on_authentication_or_credit_error(self) -> None:
        def request_get(url, *, headers, params, timeout):
            return _Response(
                402,
                {
                    "detail": {
                        "message": "No credits",
                        "reason": "insufficient_credits",
                    }
                },
            )

        client = TranscriptApiClient("secret-key", request_get=request_get)

        with self.assertRaises(TranscriptApiConfigurationError):
            client.list("abcdefghijk").find_transcript(["en"])

    def test_retries_rate_limit_using_retry_after(self) -> None:
        calls = 0
        delays = []

        def request_get(url, *, headers, params, timeout):
            nonlocal calls
            calls += 1
            return _Response(429, {"detail": "Slow down"}, {"Retry-After": "3"})

        client = TranscriptApiClient(
            "secret-key",
            request_get=request_get,
            sleep=delays.append,
            max_retries=2,
        )

        with self.assertRaises(TranscriptApiRetryableError):
            client.list("abcdefghijk").find_transcript(["en"])

        self.assertEqual(calls, 3)
        self.assertEqual(delays, [3.0, 3.0])


if __name__ == "__main__":
    unittest.main()
