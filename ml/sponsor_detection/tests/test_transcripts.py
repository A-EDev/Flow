from __future__ import annotations

import json
import tempfile
import unittest
from datetime import UTC, datetime
from pathlib import Path

from youtube_transcript_api._errors import RequestBlocked

from sponsor_detection.data.profile import sha256_file
from sponsor_detection.data.transcripts import acquire_transcript_canary, collect_transcripts


class _Snippet:
    def __init__(self, text: str, start: float, duration: float) -> None:
        self.text = text
        self.start = start
        self.duration = duration


class _FetchedTranscript:
    def __iter__(self):
        return iter([_Snippet("hello", 1.25, 2.5)])


class _Transcript:
    language = "English"
    language_code = "en"
    is_generated = True

    def fetch(self):
        return _FetchedTranscript()


class _TranscriptList:
    def find_transcript(self, languages):
        return _Transcript()


class _Client:
    def __init__(self, blocked: bool = False) -> None:
        self.blocked = blocked
        self.calls = 0

    def list(self, video_id: str):
        self.calls += 1
        if self.blocked:
            raise RequestBlocked(video_id)
        return _TranscriptList()


def _write_pilot(root: Path, video_ids: list[str]) -> tuple[Path, Path]:
    pilot_path = root / "pilot.jsonl"
    pilot_path.write_text(
        "".join(json.dumps({"video_id": video_id}) + "\n" for video_id in video_ids),
        encoding="utf-8",
    )
    manifest_path = root / "pilot-manifest.json"
    manifest_path.write_text(
        json.dumps({"output": {"sha256": sha256_file(pilot_path)}}),
        encoding="utf-8",
    )
    return pilot_path, manifest_path


class AcquireTranscriptCanaryTest(unittest.TestCase):
    def test_writes_timestamped_transcript_and_resumes_success(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            pilot_path, pilot_manifest = _write_pilot(root, ["abcdefghijk"])
            output_directory = root / "transcripts"
            report_path = root / "report.json"
            client = _Client()
            first = acquire_transcript_canary(
                pilot_path,
                pilot_manifest,
                output_directory,
                report_path,
                languages=["en"],
                limit=1,
                request_delay_seconds=0,
                stop_on_block=True,
                client=client,
            )
            second = acquire_transcript_canary(
                pilot_path,
                pilot_manifest,
                output_directory,
                report_path,
                languages=["en"],
                limit=1,
                request_delay_seconds=0,
                stop_on_block=True,
                client=client,
            )
            transcript = json.loads(
                (output_directory / "abcdefghijk.json").read_text(encoding="utf-8")
            )

        self.assertEqual(first["counts"]["successful"], 1)
        self.assertEqual(second["counts"]["statuses"], {"cached_success": 1})
        self.assertEqual(second["counts"]["successful"], 1)
        self.assertEqual(second["counts"]["total_cues"], 1)
        self.assertEqual(client.calls, 1)
        self.assertEqual(transcript["cues"][0]["start_ms"], 1250)
        self.assertEqual(transcript["cues"][0]["end_ms"], 3750)


class CollectTranscriptsTest(unittest.TestCase):
    def test_skips_permanent_failures_and_stops_at_the_success_target(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            video_ids = ["abcdefghijk", "lmnopqrstuv", "wxyzABCDEF0"]
            pilot_path, pilot_manifest = _write_pilot(root, video_ids)
            output_directory = root / "transcripts"
            output_directory.mkdir()
            (output_directory / "state.json").write_text(
                json.dumps(
                    {
                        "abcdefghijk": {
                            "status": "no_transcript",
                            "attempts": 1,
                        }
                    }
                ),
                encoding="utf-8",
            )
            client = _Client()
            delays = []
            report = collect_transcripts(
                pilot_path,
                pilot_manifest,
                output_directory,
                root / "report.json",
                languages=["en"],
                target_successes=2,
                max_attempts_per_run=5,
                minimum_request_delay_seconds=5,
                maximum_request_delay_seconds=10,
                max_transient_attempts_per_video=3,
                stop_on_block=True,
                client=client,
                sleep=delays.append,
                random_delay=lambda minimum, maximum: 7.0,
            )

        self.assertEqual(client.calls, 2)
        self.assertEqual(delays, [7.0])
        self.assertEqual(report["counts"]["new_successes"], 2)
        self.assertEqual(report["counts"]["skipped_permanent"], 1)
        self.assertTrue(report["target_reached"])

    def test_does_not_retry_an_exhausted_transient_failure(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            pilot_path, pilot_manifest = _write_pilot(root, ["abcdefghijk"])
            output_directory = root / "transcripts"
            output_directory.mkdir()
            (output_directory / "state.json").write_text(
                json.dumps(
                    {
                        "abcdefghijk": {
                            "status": "api_error",
                            "attempts": 3,
                        }
                    }
                ),
                encoding="utf-8",
            )
            client = _Client()
            report = collect_transcripts(
                pilot_path,
                pilot_manifest,
                output_directory,
                root / "report.json",
                languages=["en"],
                target_successes=1,
                max_attempts_per_run=5,
                minimum_request_delay_seconds=0,
                maximum_request_delay_seconds=0,
                max_transient_attempts_per_video=3,
                stop_on_block=True,
                client=client,
            )

        self.assertEqual(client.calls, 0)
        self.assertEqual(report["counts"]["skipped_retry_exhausted"], 1)

    def test_stops_immediately_when_youtube_blocks_requests(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            pilot_path, pilot_manifest = _write_pilot(
                root, ["abcdefghijk", "lmnopqrstuv"]
            )
            client = _Client(blocked=True)
            report = collect_transcripts(
                pilot_path,
                pilot_manifest,
                root / "transcripts",
                root / "report.json",
                languages=["en"],
                target_successes=2,
                max_attempts_per_run=2,
                minimum_request_delay_seconds=0,
                maximum_request_delay_seconds=0,
                max_transient_attempts_per_video=3,
                stop_on_block=True,
                client=client,
            )

        self.assertTrue(report["stopped_for_block"])
        self.assertEqual(report["counts"]["attempts_this_run"], 1)
        self.assertEqual(client.calls, 1)

    def test_does_not_request_during_a_block_cooldown(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            pilot_path, pilot_manifest = _write_pilot(root, ["abcdefghijk"])
            output_directory = root / "transcripts"
            output_directory.mkdir()
            (output_directory / "state.json").write_text(
                json.dumps(
                    {
                        "abcdefghijk": {
                            "status": "blocked",
                            "attempts": 1,
                            "updated_at": datetime.now(tz=UTC).isoformat(),
                        }
                    }
                ),
                encoding="utf-8",
            )
            client = _Client()
            report = collect_transcripts(
                pilot_path,
                pilot_manifest,
                output_directory,
                root / "report.json",
                languages=["en"],
                target_successes=1,
                max_attempts_per_run=1,
                minimum_request_delay_seconds=0,
                maximum_request_delay_seconds=0,
                max_transient_attempts_per_video=3,
                stop_on_block=True,
                block_cooldown_seconds=3600,
                client=client,
            )

        self.assertTrue(report["cooldown_active"])
        self.assertEqual(report["counts"]["attempts_this_run"], 0)
        self.assertEqual(client.calls, 0)


if __name__ == "__main__":
    unittest.main()
