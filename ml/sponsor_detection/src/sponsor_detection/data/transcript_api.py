from __future__ import annotations

import math
import re
import time
from dataclasses import dataclass
from typing import Callable, Sequence

import requests


TRANSCRIPT_API_ENDPOINT = "https://transcriptapi.com/api/v2/youtube/transcript"


class TranscriptApiError(RuntimeError):
    pass


class TranscriptApiNoTranscriptError(TranscriptApiError):
    pass


class TranscriptApiConfigurationError(TranscriptApiError):
    pass


class TranscriptApiRetryableError(TranscriptApiError):
    pass


@dataclass(frozen=True)
class TranscriptApiSnippet:
    text: str
    start: float
    duration: float


class TranscriptApiFetchedTranscript:
    def __init__(self, snippets: Sequence[TranscriptApiSnippet]) -> None:
        self._snippets = tuple(snippets)

    def __iter__(self):
        return iter(self._snippets)


class TranscriptApiTranscript:
    def __init__(
        self,
        *,
        language_code: str,
        snippets: Sequence[TranscriptApiSnippet],
    ) -> None:
        self.language = language_code
        self.language_code = language_code
        self.is_generated = language_code.lower().startswith("asr-")
        self.source = "transcript_api"
        self.source_version = "v2"
        self._snippets = tuple(snippets)

    def fetch(self) -> TranscriptApiFetchedTranscript:
        return TranscriptApiFetchedTranscript(self._snippets)


class TranscriptApiTranscriptList:
    def __init__(self, client: TranscriptApiClient, video_id: str) -> None:
        self._client = client
        self._video_id = video_id

    def find_transcript(self, languages: Sequence[str]) -> TranscriptApiTranscript:
        return self._client.fetch(self._video_id, languages)


class TranscriptApiClient:
    def __init__(
        self,
        api_key: str,
        *,
        request_timeout_seconds: float = 30.0,
        max_retries: int = 2,
        sleep: Callable[[float], None] = time.sleep,
        request_get: Callable[..., object] = requests.get,
    ) -> None:
        if not api_key:
            raise ValueError("TranscriptAPI key cannot be empty")
        if request_timeout_seconds <= 0:
            raise ValueError("request timeout must be positive")
        if max_retries < 0:
            raise ValueError("max retries cannot be negative")
        self._api_key = api_key
        self._request_timeout_seconds = request_timeout_seconds
        self._max_retries = max_retries
        self._sleep = sleep
        self._request_get = request_get

    def list(self, video_id: str) -> TranscriptApiTranscriptList:
        return TranscriptApiTranscriptList(self, video_id)

    def fetch(
        self, video_id: str, languages: Sequence[str]
    ) -> TranscriptApiTranscript:
        parameters = {
            "video_url": video_id,
            "format": "json",
            "include_timestamp": "true",
            "language": ",".join(_language_priority(languages)),
        }
        headers = {
            "Authorization": f"Bearer {self._api_key}",
            "Accept": "application/json",
        }

        for attempt in range(self._max_retries + 1):
            try:
                response = self._request_get(
                    TRANSCRIPT_API_ENDPOINT,
                    headers=headers,
                    params=parameters,
                    timeout=self._request_timeout_seconds,
                )
                if response.status_code != 200:
                    api_error = _classify_http_error(response)
                    if not isinstance(api_error, TranscriptApiRetryableError):
                        raise api_error
                    if attempt >= self._max_retries:
                        raise api_error
                    self._sleep(_retry_delay_seconds(response.headers, attempt))
                    continue
                payload = response.json()
                return _parse_transcript(payload)
            except requests.exceptions.RequestException as error:
                api_error = TranscriptApiRetryableError(
                    f"TranscriptAPI network request failed: {type(error).__name__}"
                )
                if attempt >= self._max_retries:
                    raise api_error from None
                self._sleep(min(5.0, 2.0**attempt))

        raise AssertionError("TranscriptAPI retry loop exited unexpectedly")


def _language_priority(languages: Sequence[str]) -> list[str]:
    priority: list[str] = []
    for language in languages:
        base_language = language.partition("-")[0].lower()
        for code in (base_language, f"asr-{base_language}"):
            if code not in priority:
                priority.append(code)
    return priority


def _parse_transcript(payload: object) -> TranscriptApiTranscript:
    if not isinstance(payload, dict):
        raise TranscriptApiError("TranscriptAPI returned a non-object response")
    language_code = payload.get("language")
    raw_snippets = payload.get("transcript")
    if not isinstance(language_code, str) or not language_code:
        raise TranscriptApiError("TranscriptAPI response omitted the transcript language")
    if not isinstance(raw_snippets, list):
        raise TranscriptApiError("TranscriptAPI response omitted timestamped transcript cues")

    snippets: list[TranscriptApiSnippet] = []
    for raw_snippet in raw_snippets:
        if not isinstance(raw_snippet, dict):
            raise TranscriptApiError("TranscriptAPI returned an invalid transcript cue")
        text = re.sub(r"\s+", " ", str(raw_snippet.get("text", ""))).strip()
        try:
            start = float(raw_snippet["start"])
            duration = float(raw_snippet["duration"])
        except (KeyError, TypeError, ValueError) as error:
            raise TranscriptApiError(
                "TranscriptAPI returned a cue without valid timestamps"
            ) from error
        if not math.isfinite(start) or not math.isfinite(duration):
            raise TranscriptApiError("TranscriptAPI returned non-finite cue timestamps")
        if not text:
            continue
        snippets.append(
            TranscriptApiSnippet(
                text=text,
                start=max(0.0, start),
                duration=max(0.0, duration),
            )
        )
    if not snippets:
        raise TranscriptApiNoTranscriptError("TranscriptAPI returned no usable cues")
    return TranscriptApiTranscript(language_code=language_code, snippets=snippets)


def _classify_http_error(response) -> TranscriptApiError:
    detail = _error_detail(response)
    if response.status_code == 404:
        return TranscriptApiNoTranscriptError(detail)
    if response.status_code in {400, 401, 402, 422}:
        return TranscriptApiConfigurationError(detail)
    if response.status_code in {408, 429, 500, 503}:
        return TranscriptApiRetryableError(detail)
    return TranscriptApiError(detail)


def _error_detail(response) -> str:
    try:
        payload = response.json()
        detail = payload.get("detail")
        if isinstance(detail, dict):
            detail = detail.get("message") or detail.get("reason")
        if detail:
            return (
                f"TranscriptAPI request failed ({response.status_code}): {detail}"
            )
    except (AttributeError, TypeError, ValueError):
        pass
    return f"TranscriptAPI request failed ({response.status_code})"


def _retry_delay_seconds(headers, attempt: int) -> float:
    retry_after = headers.get("Retry-After") if headers else None
    if retry_after:
        try:
            return max(0.0, min(60.0, float(retry_after)))
        except ValueError:
            pass
    return min(5.0, 2.0**attempt)
