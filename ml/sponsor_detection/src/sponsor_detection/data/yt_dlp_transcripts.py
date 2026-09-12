from __future__ import annotations

import json
import re
import time
from dataclasses import dataclass
from typing import Callable, Sequence


class YtDlpTranscriptError(RuntimeError):
    pass


class YtDlpBlockedError(YtDlpTranscriptError):
    pass


class YtDlpNoTranscriptError(YtDlpTranscriptError):
    pass


class YtDlpUnavailableError(YtDlpTranscriptError):
    pass


@dataclass(frozen=True)
class YtDlpSnippet:
    text: str
    start: float
    duration: float


class YtDlpFetchedTranscript:
    def __init__(self, snippets: Sequence[YtDlpSnippet]) -> None:
        self._snippets = tuple(snippets)

    def __iter__(self):
        return iter(self._snippets)


class YtDlpTranscript:
    def __init__(
        self,
        *,
        language: str,
        language_code: str,
        is_generated: bool,
        source_version: str,
        snippets: Sequence[YtDlpSnippet],
    ) -> None:
        self.language = language
        self.language_code = language_code
        self.is_generated = is_generated
        self.source = "yt_dlp"
        self.source_version = source_version
        self._snippets = tuple(snippets)

    def fetch(self) -> YtDlpFetchedTranscript:
        return YtDlpFetchedTranscript(self._snippets)


class YtDlpTranscriptList:
    def __init__(self, client: YtDlpTranscriptClient, video_id: str) -> None:
        self._client = client
        self._video_id = video_id

    def find_transcript(self, languages: Sequence[str]) -> YtDlpTranscript:
        return self._client.fetch(self._video_id, languages)


class YtDlpTranscriptClient:
    def __init__(
        self,
        *,
        subtitle_request_delay_seconds: float = 1.0,
        sleep: Callable[[float], None] = time.sleep,
        youtube_dl_factory=None,
    ) -> None:
        if subtitle_request_delay_seconds < 0:
            raise ValueError("subtitle request delay cannot be negative")
        self._subtitle_request_delay_seconds = subtitle_request_delay_seconds
        self._sleep = sleep
        self._youtube_dl_factory = youtube_dl_factory

    def list(self, video_id: str) -> YtDlpTranscriptList:
        return YtDlpTranscriptList(self, video_id)

    def fetch(self, video_id: str, languages: Sequence[str]) -> YtDlpTranscript:
        youtube_dl_factory = self._youtube_dl_factory
        if youtube_dl_factory is None:
            from yt_dlp import YoutubeDL

            youtube_dl_factory = YoutubeDL

        options = {
            "quiet": True,
            "no_warnings": True,
            "skip_download": True,
            "retries": 0,
            "extractor_retries": 0,
            "fragment_retries": 0,
        }
        try:
            from yt_dlp.version import __version__ as yt_dlp_version

            with youtube_dl_factory(options) as downloader:
                info = downloader.extract_info(
                    f"https://www.youtube.com/watch?v={video_id}",
                    download=False,
                )
                if not isinstance(info, dict):
                    raise YtDlpUnavailableError("yt-dlp returned no video information")
                language_code, formats, is_generated = _select_track(info, languages)
                subtitle_format = _select_json3_format(formats)
                if self._subtitle_request_delay_seconds:
                    self._sleep(self._subtitle_request_delay_seconds)
                response = downloader.urlopen(subtitle_format["url"])
                try:
                    payload = response.read()
                finally:
                    response.close()
        except YtDlpTranscriptError:
            raise
        except BaseException as error:
            if isinstance(error, (KeyboardInterrupt, SystemExit)):
                raise
            raise _classify_yt_dlp_error(error) from error

        snippets = _parse_json3(payload)
        if not snippets:
            raise YtDlpNoTranscriptError("the selected caption track contained no cues")
        return YtDlpTranscript(
            language=language_code,
            language_code=language_code,
            is_generated=is_generated,
            source_version=yt_dlp_version,
            snippets=snippets,
        )


def _select_track(
    info: dict[str, object], languages: Sequence[str]
) -> tuple[str, list[dict[str, object]], bool]:
    for field, is_generated in (("subtitles", False), ("automatic_captions", True)):
        tracks = info.get(field)
        if not isinstance(tracks, dict):
            continue
        language_code = _select_language(tracks, languages)
        if language_code is None:
            continue
        formats = tracks.get(language_code)
        if isinstance(formats, list):
            usable_formats = [item for item in formats if isinstance(item, dict)]
            if usable_formats:
                return language_code, usable_formats, is_generated
    raise YtDlpNoTranscriptError("no requested caption language is available")


def _select_language(tracks: dict[str, object], languages: Sequence[str]) -> str | None:
    for requested in languages:
        if requested in tracks:
            return requested
    for requested in languages:
        requested_base = requested.partition("-")[0].lower()
        for available in tracks:
            available_base = available.partition("-")[0].lower()
            if available_base == requested_base:
                return available
    return None


def _select_json3_format(formats: Sequence[dict[str, object]]) -> dict[str, object]:
    for subtitle_format in formats:
        if subtitle_format.get("ext") == "json3" and isinstance(
            subtitle_format.get("url"), str
        ):
            return subtitle_format
    raise YtDlpTranscriptError("the caption track does not provide json3 timestamps")


def _parse_json3(payload: bytes | str) -> list[YtDlpSnippet]:
    try:
        document = json.loads(payload)
    except (TypeError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise YtDlpTranscriptError("invalid json3 caption response") from error
    snippets: list[YtDlpSnippet] = []
    for event in document.get("events", []):
        if not isinstance(event, dict) or "tStartMs" not in event:
            continue
        segments = event.get("segs")
        if not isinstance(segments, list):
            continue
        text = "".join(
            str(segment.get("utf8", ""))
            for segment in segments
            if isinstance(segment, dict)
        ).replace("\n", " ")
        text = re.sub(r"\s+", " ", text).strip()
        if not text:
            continue
        start_ms = max(0, int(event["tStartMs"]))
        duration_ms = max(0, int(event.get("dDurationMs", 0)))
        snippets.append(
            YtDlpSnippet(
                text=text,
                start=start_ms / 1000,
                duration=duration_ms / 1000,
            )
        )
    return snippets


def _classify_yt_dlp_error(error: BaseException) -> YtDlpTranscriptError:
    message = str(error)
    normalized = message.lower()
    block_markers = (
        "http error 403",
        "http error 429",
        "too many requests",
        "confirm you're not a bot",
        "confirm you’re not a bot",
        "captcha",
        "ip address is blocked",
        "this content isn't available, try again later",
    )
    if any(marker in normalized for marker in block_markers):
        return YtDlpBlockedError(message)
    unavailable_markers = (
        "private video",
        "video unavailable",
        "has been removed",
        "is not available",
    )
    if any(marker in normalized for marker in unavailable_markers):
        return YtDlpUnavailableError(message)
    return YtDlpTranscriptError(message)
