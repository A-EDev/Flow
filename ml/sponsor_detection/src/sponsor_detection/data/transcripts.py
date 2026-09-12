from __future__ import annotations

import json
import random
import re
import time
from collections import Counter
from datetime import UTC, datetime
from pathlib import Path
from typing import Callable, Protocol, Sequence

from sponsor_detection.data.profile import sha256_file, write_json_atomic


VIDEO_ID_PATTERN = re.compile(r"^[A-Za-z0-9_-]{11}$")
PERMANENT_FAILURE_STATUSES = frozenset(
    {"authentication_required", "invalid_video_id", "no_transcript", "unavailable"}
)


class TranscriptClient(Protocol):
    def list(self, video_id: str): ...


def _load_pilot_video_ids(pilot_path: Path, expected_sha256: str) -> list[str]:
    if sha256_file(pilot_path) != expected_sha256:
        raise ValueError("pilot JSONL checksum differs from its manifest")
    video_ids: list[str] = []
    with pilot_path.open("r", encoding="utf-8") as source:
        for line_number, line in enumerate(source, start=1):
            try:
                record = json.loads(line)
                video_ids.append(str(record["video_id"]))
            except (KeyError, TypeError, json.JSONDecodeError) as error:
                raise ValueError(f"invalid pilot record at line {line_number}") from error
    return video_ids


def _classify_error(error: BaseException) -> tuple[str, bool]:
    from sponsor_detection.data.transcript_api import (
        TranscriptApiConfigurationError,
        TranscriptApiError,
        TranscriptApiNoTranscriptError,
        TranscriptApiRetryableError,
    )
    from sponsor_detection.data.yt_dlp_transcripts import (
        YtDlpBlockedError,
        YtDlpNoTranscriptError,
        YtDlpTranscriptError,
        YtDlpUnavailableError,
    )
    from youtube_transcript_api._errors import (
        AgeRestricted,
        InvalidVideoId,
        IpBlocked,
        NoTranscriptFound,
        PoTokenRequired,
        RequestBlocked,
        TranscriptsDisabled,
        VideoUnavailable,
        VideoUnplayable,
        YouTubeTranscriptApiException,
    )

    if isinstance(error, (RequestBlocked, IpBlocked, YtDlpBlockedError)):
        return "blocked", True
    if isinstance(
        error,
        (
            TranscriptsDisabled,
            NoTranscriptFound,
            YtDlpNoTranscriptError,
            TranscriptApiNoTranscriptError,
        ),
    ):
        return "no_transcript", False
    if isinstance(error, TranscriptApiConfigurationError):
        return "provider_error", True
    if isinstance(error, TranscriptApiRetryableError):
        return "provider_unavailable", True
    if isinstance(
        error,
        (
            AgeRestricted,
            InvalidVideoId,
            VideoUnavailable,
            VideoUnplayable,
            YtDlpUnavailableError,
        ),
    ):
        return "unavailable", False
    if isinstance(error, PoTokenRequired):
        return "authentication_required", False
    if isinstance(error, YouTubeTranscriptApiException):
        return "api_error", False
    if isinstance(error, YtDlpTranscriptError):
        return "api_error", False
    if isinstance(error, TranscriptApiError):
        return "api_error", False
    return "unexpected_error", False


def _fetch_one(
    client: TranscriptClient,
    video_id: str,
    languages: Sequence[str],
) -> dict[str, object]:
    transcript = client.list(video_id).find_transcript(languages)
    fetched = transcript.fetch()
    cues = []
    for index, snippet in enumerate(fetched):
        start_ms = round(snippet.start * 1000)
        duration_ms = max(0, round(snippet.duration * 1000))
        cues.append(
            {
                "index": index,
                "start_ms": start_ms,
                "end_ms": start_ms + duration_ms,
                "text": snippet.text,
            }
        )
    return {
        "schema_version": 1,
        "video_id": video_id,
        "source": getattr(transcript, "source", "youtube_transcript_api"),
        "source_version": getattr(transcript, "source_version", None),
        "language": transcript.language,
        "language_code": transcript.language_code,
        "is_generated": bool(transcript.is_generated),
        "fetched_at": datetime.now(tz=UTC).isoformat(),
        "cues": cues,
    }


def acquire_transcript_canary(
    pilot_path: Path,
    pilot_manifest_path: Path,
    output_directory: Path,
    report_path: Path,
    *,
    languages: Sequence[str],
    limit: int,
    request_delay_seconds: float,
    stop_on_block: bool,
    client: TranscriptClient | None = None,
    sleep: Callable[[float], None] = time.sleep,
    progress_callback: Callable[[int, int, str], None] | None = None,
) -> dict[str, object]:
    if limit <= 0:
        raise ValueError("limit must be positive")
    if request_delay_seconds < 0:
        raise ValueError("request delay cannot be negative")
    if not languages:
        raise ValueError("at least one transcript language is required")

    pilot_manifest = json.loads(pilot_manifest_path.read_text(encoding="utf-8"))
    pilot_sha256 = pilot_manifest.get("output", {}).get("sha256")
    if not pilot_sha256:
        raise ValueError("pilot manifest does not contain an output SHA-256 fingerprint")
    video_ids = _load_pilot_video_ids(pilot_path, pilot_sha256)[:limit]
    if client is None:
        from youtube_transcript_api import YouTubeTranscriptApi

        client = YouTubeTranscriptApi()

    output_directory.mkdir(parents=True, exist_ok=True)
    state_path = output_directory / "state.json"
    state = json.loads(state_path.read_text(encoding="utf-8")) if state_path.is_file() else {}
    statuses: Counter[str] = Counter()
    languages_found: Counter[str] = Counter()
    attempted = successful = total_cues = 0
    stopped_for_block = False
    started = time.monotonic()

    for index, video_id in enumerate(video_ids, start=1):
        previous = state.get(video_id)
        if isinstance(previous, dict) and previous.get("status") == "success":
            transcript_path = output_directory / f"{video_id}.json"
            if transcript_path.is_file():
                transcript = json.loads(transcript_path.read_text(encoding="utf-8"))
                statuses["cached_success"] += 1
                successful += 1
                total_cues += len(transcript["cues"])
                languages_found[str(transcript["language_code"])] += 1
                continue
        attempted += 1
        if not VIDEO_ID_PATTERN.fullmatch(video_id):
            status = "invalid_video_id"
            state[video_id] = {"status": status, "updated_at": datetime.now(tz=UTC).isoformat()}
        else:
            try:
                transcript = _fetch_one(client, video_id, languages)
                transcript_path = output_directory / f"{video_id}.json"
                write_json_atomic(transcript_path, transcript)
                status = "success"
                successful += 1
                total_cues += len(transcript["cues"])
                languages_found[str(transcript["language_code"])] += 1
                state[video_id] = {
                    "status": status,
                    "transcript_sha256": sha256_file(transcript_path),
                    "updated_at": datetime.now(tz=UTC).isoformat(),
                }
            except BaseException as error:
                if isinstance(error, (KeyboardInterrupt, SystemExit)):
                    raise
                status, should_stop = _classify_error(error)
                state[video_id] = {
                    "status": status,
                    "error_type": type(error).__name__,
                    "updated_at": datetime.now(tz=UTC).isoformat(),
                }
                if should_stop and stop_on_block:
                    stopped_for_block = True
        statuses[status] += 1
        write_json_atomic(state_path, state)
        if progress_callback:
            progress_callback(index, len(video_ids), status)
        if stopped_for_block:
            break
        if index < len(video_ids) and request_delay_seconds:
            sleep(request_delay_seconds)

    report = {
        "schema_version": 1,
        "generated_at": datetime.now(tz=UTC).isoformat(),
        "source_pilot_sha256": pilot_sha256,
        "configuration": {
            "languages": list(languages),
            "limit": limit,
            "request_delay_seconds": request_delay_seconds,
            "stop_on_block": stop_on_block,
        },
        "counts": {
            "attempted": attempted,
            "successful": successful,
            "total_cues": total_cues,
            "statuses": dict(statuses),
            "languages": dict(languages_found),
        },
        "stopped_for_block": stopped_for_block,
        "elapsed_seconds": round(time.monotonic() - started, 3),
    }
    write_json_atomic(report_path, report)
    return report


def collect_transcripts(
    pilot_path: Path,
    pilot_manifest_path: Path,
    output_directory: Path,
    report_path: Path,
    *,
    languages: Sequence[str],
    target_successes: int,
    max_attempts_per_run: int,
    minimum_request_delay_seconds: float,
    maximum_request_delay_seconds: float,
    max_transient_attempts_per_video: int,
    stop_on_block: bool,
    block_cooldown_seconds: float = 0,
    provider_name: str = "youtube_transcript_api",
    client: TranscriptClient | None = None,
    sleep: Callable[[float], None] = time.sleep,
    random_delay: Callable[[float, float], float] = random.uniform,
    progress_callback: Callable[[int, int, int, str], None] | None = None,
) -> dict[str, object]:
    if target_successes <= 0:
        raise ValueError("target_successes must be positive")
    if max_attempts_per_run <= 0:
        raise ValueError("max_attempts_per_run must be positive")
    if max_transient_attempts_per_video <= 0:
        raise ValueError("max_transient_attempts_per_video must be positive")
    if minimum_request_delay_seconds < 0:
        raise ValueError("minimum request delay cannot be negative")
    if maximum_request_delay_seconds < minimum_request_delay_seconds:
        raise ValueError("maximum request delay cannot be less than the minimum")
    if block_cooldown_seconds < 0:
        raise ValueError("block cooldown cannot be negative")
    if not languages:
        raise ValueError("at least one transcript language is required")

    pilot_manifest = json.loads(pilot_manifest_path.read_text(encoding="utf-8"))
    pilot_sha256 = pilot_manifest.get("output", {}).get("sha256")
    if not pilot_sha256:
        raise ValueError("pilot manifest does not contain an output SHA-256 fingerprint")
    video_ids = _load_pilot_video_ids(pilot_path, pilot_sha256)
    if client is None:
        from youtube_transcript_api import YouTubeTranscriptApi

        client = YouTubeTranscriptApi()

    output_directory.mkdir(parents=True, exist_ok=True)
    state_path = output_directory / "state.json"
    state = json.loads(state_path.read_text(encoding="utf-8")) if state_path.is_file() else {}
    existing_successes = sum(
        1
        for video_id, record in state.items()
        if isinstance(record, dict)
        and record.get("status") == "success"
        and (output_directory / f"{video_id}.json").is_file()
    )
    total_successes = existing_successes
    new_successes = attempts_this_run = 0
    skipped_permanent = skipped_retry_exhausted = 0
    statuses: Counter[str] = Counter()
    stopped_for_block = False
    started = time.monotonic()
    cooldown_remaining_seconds = _block_cooldown_remaining_seconds(
        state, block_cooldown_seconds
    )

    if cooldown_remaining_seconds > 0:
        report = {
            "schema_version": 1,
            "generated_at": datetime.now(tz=UTC).isoformat(),
            "source_pilot_sha256": pilot_sha256,
            "configuration": {
                "provider": provider_name,
                "block_cooldown_seconds": block_cooldown_seconds,
            },
            "counts": {
                "candidate_videos": len(video_ids),
                "existing_successes": existing_successes,
                "attempts_this_run": 0,
                "new_successes": 0,
                "total_successes": total_successes,
                "skipped_permanent": 0,
                "skipped_retry_exhausted": 0,
                "statuses_this_run": {"cooldown_active": 1},
            },
            "target_reached": total_successes >= target_successes,
            "stopped_for_block": True,
            "cooldown_active": True,
            "cooldown_remaining_seconds": cooldown_remaining_seconds,
            "elapsed_seconds": round(time.monotonic() - started, 3),
        }
        write_json_atomic(report_path, report)
        return report

    for video_id in video_ids:
        if total_successes >= target_successes or attempts_this_run >= max_attempts_per_run:
            break
        previous = state.get(video_id)
        previous_status = previous.get("status") if isinstance(previous, dict) else None
        transcript_path = output_directory / f"{video_id}.json"
        if previous_status == "success" and transcript_path.is_file():
            continue
        if previous_status in PERMANENT_FAILURE_STATUSES:
            skipped_permanent += 1
            continue
        prior_attempts = int(previous.get("attempts", 1)) if isinstance(previous, dict) else 0
        if previous_status and prior_attempts >= max_transient_attempts_per_video:
            skipped_retry_exhausted += 1
            continue
        if not VIDEO_ID_PATTERN.fullmatch(video_id):
            status = "invalid_video_id"
            state[video_id] = {
                "status": status,
                "attempts": prior_attempts,
                "updated_at": datetime.now(tz=UTC).isoformat(),
            }
            statuses[status] += 1
            write_json_atomic(state_path, state)
            continue

        attempts_this_run += 1
        try:
            transcript = _fetch_one(client, video_id, languages)
            write_json_atomic(transcript_path, transcript)
            status = "success"
            new_successes += 1
            total_successes += 1
            state[video_id] = {
                "status": status,
                "attempts": prior_attempts + 1,
                "transcript_sha256": sha256_file(transcript_path),
                "updated_at": datetime.now(tz=UTC).isoformat(),
            }
        except BaseException as error:
            if isinstance(error, (KeyboardInterrupt, SystemExit)):
                raise
            status, should_stop = _classify_error(error)
            state[video_id] = {
                "status": status,
                "attempts": prior_attempts + 1,
                "error_type": type(error).__name__,
                "updated_at": datetime.now(tz=UTC).isoformat(),
            }
            if should_stop and stop_on_block:
                stopped_for_block = True
        statuses[status] += 1
        write_json_atomic(state_path, state)
        if progress_callback:
            progress_callback(
                attempts_this_run,
                max_attempts_per_run,
                total_successes,
                status,
            )
        if stopped_for_block:
            break
        if attempts_this_run < max_attempts_per_run and total_successes < target_successes:
            delay = random_delay(
                minimum_request_delay_seconds,
                maximum_request_delay_seconds,
            )
            if delay:
                sleep(delay)

    report = {
        "schema_version": 1,
        "generated_at": datetime.now(tz=UTC).isoformat(),
        "source_pilot_sha256": pilot_sha256,
        "configuration": {
            "provider": provider_name,
            "languages": list(languages),
            "target_successes": target_successes,
            "max_attempts_per_run": max_attempts_per_run,
            "minimum_request_delay_seconds": minimum_request_delay_seconds,
            "maximum_request_delay_seconds": maximum_request_delay_seconds,
            "max_transient_attempts_per_video": max_transient_attempts_per_video,
            "stop_on_block": stop_on_block,
            "block_cooldown_seconds": block_cooldown_seconds,
        },
        "counts": {
            "candidate_videos": len(video_ids),
            "existing_successes": existing_successes,
            "attempts_this_run": attempts_this_run,
            "new_successes": new_successes,
            "total_successes": total_successes,
            "skipped_permanent": skipped_permanent,
            "skipped_retry_exhausted": skipped_retry_exhausted,
            "statuses_this_run": dict(statuses),
        },
        "target_reached": total_successes >= target_successes,
        "stopped_for_block": stopped_for_block,
        "cooldown_active": False,
        "elapsed_seconds": round(time.monotonic() - started, 3),
    }
    write_json_atomic(report_path, report)
    return report


def _block_cooldown_remaining_seconds(
    state: dict[str, object], block_cooldown_seconds: float
) -> int:
    if not block_cooldown_seconds:
        return 0
    blocked_at: list[datetime] = []
    for record in state.values():
        if not isinstance(record, dict) or record.get("status") != "blocked":
            continue
        updated_at = record.get("updated_at")
        if not isinstance(updated_at, str):
            continue
        try:
            parsed = datetime.fromisoformat(updated_at)
        except ValueError:
            continue
        if parsed.tzinfo is not None:
            blocked_at.append(parsed.astimezone(UTC))
    if not blocked_at:
        return 0
    elapsed = (datetime.now(tz=UTC) - max(blocked_at)).total_seconds()
    return max(0, round(block_cooldown_seconds - elapsed))
