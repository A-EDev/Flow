from __future__ import annotations

import io
import json
import tempfile
import unittest
from pathlib import Path
from urllib.error import HTTPError

from sponsor_detection.data.metadata import (
    acquire_metadata_for_video_ids,
    MetadataApiError,
    acquire_video_metadata,
    fetch_metadata_batch,
    iso8601_duration_to_milliseconds,
    load_environment_value,
)


class _Response(io.BytesIO):
    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        self.close()


class MetadataTest(unittest.TestCase):
    def test_loads_a_quoted_environment_value(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / ".env"
            path.write_text('YOUTUBE_DATA_API_KEY="secret"\n', encoding="utf-8")
            self.assertEqual(load_environment_value(path, "YOUTUBE_DATA_API_KEY"), "secret")

    def test_parses_youtube_duration(self) -> None:
        self.assertEqual(iso8601_duration_to_milliseconds("PT1H2M3.5S"), 3_723_500)

    def test_normalizes_a_video_api_response(self) -> None:
        payload = {
            "items": [
                {
                    "id": "abcdefghijk",
                    "snippet": {
                        "channelId": "channel-1",
                        "publishedAt": "2025-01-01T00:00:00Z",
                        "defaultAudioLanguage": "en",
                    },
                    "contentDetails": {"duration": "PT2M3S", "caption": "true"},
                    "status": {"privacyStatus": "public", "uploadStatus": "processed"},
                }
            ]
        }

        def opener(url, timeout):
            self.assertNotIn("secret", url.replace("key=secret", "key=REDACTED"))
            self.assertEqual(timeout, 30)
            return _Response(json.dumps(payload).encode("utf-8"))

        records = fetch_metadata_batch(["abcdefghijk"], "secret", opener=opener)
        self.assertEqual(records["abcdefghijk"]["duration_ms"], 123_000)
        self.assertEqual(records["abcdefghijk"]["channel_id"], "channel-1")

    def test_api_errors_do_not_expose_the_key(self) -> None:
        def opener(url, timeout):
            payload = {"error": {"errors": [{"reason": "accessNotConfigured"}]}}
            raise HTTPError(
                url,
                403,
                "Forbidden",
                {},
                io.BytesIO(json.dumps(payload).encode("utf-8")),
            )

        with self.assertRaises(MetadataApiError) as context:
            fetch_metadata_batch(["abcdefghijk"], "secret", opener=opener)
        self.assertNotIn("secret", str(context.exception))
        self.assertIn("accessNotConfigured", str(context.exception))

    def test_writes_metadata_and_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            transcripts = root / "transcripts"
            transcripts.mkdir()
            (transcripts / "abcdefghijk.json").write_text(
                json.dumps({"video_id": "abcdefghijk"}), encoding="utf-8"
            )

            def fetch_batch(video_ids, api_key):
                return {
                    "abcdefghijk": {
                        "video_id": "abcdefghijk",
                        "channel_id": "channel-1",
                        "published_at": "2025-01-01T00:00:00Z",
                        "default_language": None,
                        "default_audio_language": "en",
                        "duration_ms": 1000,
                        "captions_available": True,
                        "privacy_status": "public",
                        "upload_status": "processed",
                    }
                }

            report = acquire_video_metadata(
                transcripts,
                root / "metadata.jsonl",
                root / "manifest.json",
                api_key="secret",
                fetch_batch=fetch_batch,
            )

        self.assertEqual(report["counts"]["returned_videos"], 1)
        self.assertEqual(report["counts"]["api_requests"], 1)

    def test_refuses_to_exceed_the_api_request_budget(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            transcripts = root / "transcripts"
            transcripts.mkdir()
            for index in range(51):
                (transcripts / f"video-{index}.json").write_text(
                    json.dumps({"video_id": f"video-{index}"}), encoding="utf-8"
                )

            with self.assertRaisesRegex(ValueError, "exceeding the configured limit"):
                acquire_video_metadata(
                    transcripts,
                    root / "metadata.jsonl",
                    root / "manifest.json",
                    api_key="secret",
                    batch_size=50,
                    max_api_requests=1,
                )

    def test_bulk_metadata_collection_resumes_from_journal(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            calls: list[list[str]] = []

            def fetch_batch(video_ids, api_key):
                calls.append(video_ids)
                return {
                    video_id: {
                        "video_id": video_id,
                        "channel_id": "channel-1",
                        "published_at": "2025-01-01T00:00:00Z",
                    }
                    for video_id in video_ids
                    if video_id != "missing0000"
                }

            arguments = {
                "video_ids": ["abcdefghijk", "lmnopqrstuv", "missing0000"],
                "output_path": root / "metadata.jsonl",
                "journal_path": root / "journal.jsonl",
                "manifest_path": root / "manifest.json",
                "api_key": "secret",
                "batch_size": 2,
                "request_delay_seconds": 0,
                "fetch_batch": fetch_batch,
            }
            first = acquire_metadata_for_video_ids(
                **arguments,
                max_api_requests_per_run=1,
            )
            second = acquire_metadata_for_video_ids(
                **arguments,
                max_api_requests_per_run=2,
            )

        self.assertFalse(first["complete"])
        self.assertTrue(second["complete"])
        self.assertEqual(len(calls), 2)
        self.assertEqual(second["counts"]["returned_videos"], 2)
        self.assertEqual(second["counts"]["missing_videos"], 1)


if __name__ == "__main__":
    unittest.main()
