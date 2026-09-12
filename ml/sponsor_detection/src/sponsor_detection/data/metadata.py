from __future__ import annotations

import json
import math
import os
import re
import tempfile
import time
from datetime import UTC, datetime
from pathlib import Path
from typing import Callable, Iterable
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import urlopen

from sponsor_detection.data.profile import sha256_file, write_json_atomic


YOUTUBE_VIDEOS_ENDPOINT = "https://www.googleapis.com/youtube/v3/videos"
DURATION_PATTERN = re.compile(
    r"^P(?:(?P<days>\d+)D)?(?:T(?:(?P<hours>\d+)H)?(?:(?P<minutes>\d+)M)?"
    r"(?:(?P<seconds>\d+(?:\.\d+)?)S)?)?$"
)


class MetadataApiError(RuntimeError):
    def __init__(self, status: int | None, reason: str) -> None:
        super().__init__(f"YouTube metadata request failed ({status or 'network'}): {reason}")
        self.status = status


def load_environment_value(path: Path, variable_name: str) -> str:
    if value := os.environ.get(variable_name):
        return value
    if not path.is_file():
        raise ValueError(f"environment file does not exist: {path}")
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.removeprefix("export ").strip()
        if key == variable_name:
            value = value.strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in {'"', "'"}:
                value = value[1:-1]
            if not value:
                raise ValueError(f"{variable_name} is empty")
            return value
    raise ValueError(f"{variable_name} is not defined in {path}")


def iso8601_duration_to_milliseconds(value: str) -> int:
    match = DURATION_PATTERN.fullmatch(value)
    if not match:
        raise ValueError(f"unsupported ISO 8601 duration: {value}")
    parts = match.groupdict(default="0")
    total_seconds = (
        int(parts["days"]) * 86_400
        + int(parts["hours"]) * 3_600
        + int(parts["minutes"]) * 60
        + float(parts["seconds"])
    )
    return round(total_seconds * 1000)


def _chunks(values: list[str], size: int) -> Iterable[list[str]]:
    for index in range(0, len(values), size):
        yield values[index : index + size]


def _api_error_reason(payload: bytes) -> str:
    try:
        parsed = json.loads(payload)
        errors = parsed.get("error", {}).get("errors", [])
        if errors and errors[0].get("reason"):
            return str(errors[0]["reason"])
        if parsed.get("error", {}).get("status"):
            return str(parsed["error"]["status"])
    except (AttributeError, TypeError, json.JSONDecodeError):
        pass
    return "request rejected"


def fetch_metadata_batch(
    video_ids: list[str],
    api_key: str,
    *,
    opener: Callable[..., object] = urlopen,
) -> dict[str, dict[str, object]]:
    if not 1 <= len(video_ids) <= 50:
        raise ValueError("metadata batches must contain between 1 and 50 video IDs")
    query = urlencode(
        {
            "part": "snippet,contentDetails,status",
            "id": ",".join(video_ids),
            "fields": (
                "items(id,snippet(channelId,publishedAt,defaultLanguage,defaultAudioLanguage),"
                "contentDetails(duration,caption),status(privacyStatus,uploadStatus))"
            ),
            "key": api_key,
        }
    )
    try:
        with opener(f"{YOUTUBE_VIDEOS_ENDPOINT}?{query}", timeout=30) as response:
            payload = json.load(response)
    except HTTPError as error:
        raise MetadataApiError(error.code, _api_error_reason(error.read())) from None
    except URLError as error:
        raise MetadataApiError(None, type(error.reason).__name__) from None

    normalized: dict[str, dict[str, object]] = {}
    for item in payload.get("items", []):
        snippet = item.get("snippet", {})
        content_details = item.get("contentDetails", {})
        status = item.get("status", {})
        duration = content_details.get("duration")
        normalized[item["id"]] = {
            "video_id": item["id"],
            "channel_id": snippet.get("channelId"),
            "published_at": snippet.get("publishedAt"),
            "default_language": snippet.get("defaultLanguage"),
            "default_audio_language": snippet.get("defaultAudioLanguage"),
            "duration_ms": iso8601_duration_to_milliseconds(duration) if duration else None,
            "captions_available": content_details.get("caption") == "true",
            "privacy_status": status.get("privacyStatus"),
            "upload_status": status.get("uploadStatus"),
        }
    return normalized


def _successful_transcript_video_ids(directory: Path) -> list[str]:
    video_ids = []
    for path in sorted(directory.glob("*.json")):
        if path.name == "state.json":
            continue
        record = json.loads(path.read_text(encoding="utf-8"))
        video_ids.append(str(record["video_id"]))
    return video_ids


def _write_json_lines_atomic(output_path: Path, records: list[dict[str, object]]) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        dir=output_path.parent,
        prefix=f".{output_path.name}.",
        suffix=".tmp",
        text=True,
    )
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as output:
            for record in records:
                json.dump(record, output, sort_keys=True, separators=(",", ":"))
                output.write("\n")
        os.replace(temporary_name, output_path)
    except BaseException:
        Path(temporary_name).unlink(missing_ok=True)
        raise


def acquire_video_metadata(
    transcript_directory: Path,
    output_path: Path,
    manifest_path: Path,
    *,
    api_key: str,
    batch_size: int = 50,
    limit: int | None = None,
    max_api_requests: int = 120,
    fetch_batch: Callable[[list[str], str], dict[str, dict[str, object]]] = fetch_metadata_batch,
) -> dict[str, object]:
    if not 1 <= batch_size <= 50:
        raise ValueError("batch_size must be between 1 and 50")
    if max_api_requests <= 0:
        raise ValueError("max_api_requests must be positive")
    video_ids = _successful_transcript_video_ids(transcript_directory)
    if limit is not None:
        if limit <= 0:
            raise ValueError("limit must be positive")
        video_ids = video_ids[:limit]
    required_requests = math.ceil(len(video_ids) / batch_size)
    if required_requests > max_api_requests:
        raise ValueError(
            f"metadata job requires {required_requests} API requests, exceeding the configured "
            f"limit of {max_api_requests}"
        )

    started = time.monotonic()
    records_by_id: dict[str, dict[str, object]] = {}
    request_count = 0
    for batch in _chunks(video_ids, batch_size):
        request_count += 1
        records_by_id.update(fetch_batch(batch, api_key))
    missing_video_ids = sorted(set(video_ids).difference(records_by_id))
    records = [records_by_id[video_id] for video_id in video_ids if video_id in records_by_id]
    _write_json_lines_atomic(output_path, records)
    report = {
        "schema_version": 1,
        "generated_at": datetime.now(tz=UTC).isoformat(),
        "configuration": {
            "batch_size": batch_size,
            "limit": limit,
            "max_api_requests": max_api_requests,
        },
        "counts": {
            "requested_videos": len(video_ids),
            "returned_videos": len(records),
            "missing_videos": len(missing_video_ids),
            "api_requests": request_count,
            "videos_with_channel": sum(bool(record["channel_id"]) for record in records),
            "videos_with_publication_date": sum(
                bool(record["published_at"]) for record in records
            ),
        },
        "output": {
            "path": str(output_path),
            "bytes": output_path.stat().st_size,
            "sha256": sha256_file(output_path),
        },
        "elapsed_seconds": round(time.monotonic() - started, 3),
    }
    write_json_atomic(manifest_path, report)
    return report


def acquire_metadata_for_video_ids(
    video_ids: list[str],
    output_path: Path,
    journal_path: Path,
    manifest_path: Path,
    *,
    api_key: str,
    batch_size: int = 50,
    max_api_requests_per_run: int = 2_000,
    request_delay_seconds: float = 0.1,
    fetch_batch: Callable[
        [list[str], str], dict[str, dict[str, object]]
    ] = fetch_metadata_batch,
    sleep: Callable[[float], None] = time.sleep,
    progress_callback: Callable[[int, int, int], None] | None = None,
) -> dict[str, object]:
    if not 1 <= batch_size <= 50:
        raise ValueError("batch_size must be between 1 and 50")
    if max_api_requests_per_run <= 0:
        raise ValueError("max API requests per run must be positive")
    if request_delay_seconds < 0:
        raise ValueError("request delay cannot be negative")

    unique_video_ids = sorted(set(video_ids))
    journal_path.parent.mkdir(parents=True, exist_ok=True)
    attempted: set[str] = set()
    records_by_id: dict[str, dict[str, object]] = {}
    if journal_path.is_file():
        with journal_path.open("r", encoding="utf-8") as source:
            for line in source:
                entry = json.loads(line)
                video_id = str(entry["video_id"])
                attempted.add(video_id)
                if entry.get("status") == "success":
                    records_by_id[video_id] = entry["metadata"]

    pending = [video_id for video_id in unique_video_ids if video_id not in attempted]
    total_batches = math.ceil(len(pending) / batch_size)
    batches_this_run = min(total_batches, max_api_requests_per_run)
    started = time.monotonic()
    request_count = 0
    with journal_path.open("a", encoding="utf-8") as journal:
        for batch in _chunks(pending, batch_size):
            if request_count >= max_api_requests_per_run:
                break
            returned = fetch_batch(batch, api_key)
            request_count += 1
            for video_id in batch:
                metadata = returned.get(video_id)
                entry = {
                    "video_id": video_id,
                    "status": "success" if metadata else "missing",
                    "metadata": metadata,
                    "updated_at": datetime.now(tz=UTC).isoformat(),
                }
                json.dump(entry, journal, sort_keys=True, separators=(",", ":"))
                journal.write("\n")
                attempted.add(video_id)
                if metadata:
                    records_by_id[video_id] = metadata
            journal.flush()
            if progress_callback:
                progress_callback(request_count, batches_this_run, len(attempted))
            if request_count < batches_this_run and request_delay_seconds:
                sleep(request_delay_seconds)

    records = [records_by_id[video_id] for video_id in unique_video_ids if video_id in records_by_id]
    _write_json_lines_atomic(output_path, records)
    remaining = len(set(unique_video_ids).difference(attempted))
    report = {
        "schema_version": 1,
        "generated_at": datetime.now(tz=UTC).isoformat(),
        "configuration": {
            "batch_size": batch_size,
            "max_api_requests_per_run": max_api_requests_per_run,
            "request_delay_seconds": request_delay_seconds,
        },
        "counts": {
            "unique_videos": len(unique_video_ids),
            "attempted_videos": len(attempted),
            "returned_videos": len(records),
            "missing_videos": len(attempted.difference(records_by_id)),
            "remaining_videos": remaining,
            "api_requests_this_run": request_count,
        },
        "complete": remaining == 0,
        "output": {
            "path": str(output_path),
            "bytes": output_path.stat().st_size,
            "sha256": sha256_file(output_path),
        },
        "elapsed_seconds": round(time.monotonic() - started, 3),
    }
    write_json_atomic(manifest_path, report)
    return report
