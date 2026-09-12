from __future__ import annotations

import re
from bisect import bisect_right
from dataclasses import dataclass
from typing import Sequence


URL_PATTERN = re.compile(r"(?i)\b(?:https?://|www\.)\S+|\b\S+\.(?:com|net|org)\S*")
NUMBER_PATTERN = re.compile(r"\b\d+(?:[.,:]\d+)*\b")
WHITESPACE_PATTERN = re.compile(r"\s+")


@dataclass(frozen=True, slots=True)
class TranscriptCue:
    index: int
    start_ms: int
    end_ms: int
    text: str


@dataclass(frozen=True, slots=True)
class CueCharacterRange:
    cue_index: int
    start_char: int
    end_char: int
    start_ms: int
    end_ms: int


@dataclass(frozen=True, slots=True)
class AssembledTranscript:
    text: str
    cue_ranges: tuple[CueCharacterRange, ...]

    def timestamp_for_character(self, character: int, *, end_boundary: bool) -> int:
        if not self.cue_ranges:
            raise ValueError("cannot map an empty transcript")
        bounded = min(max(character, 0), len(self.text))
        ends = [cue_range.end_char for cue_range in self.cue_ranges]
        index = min(bisect_right(ends, bounded), len(self.cue_ranges) - 1)
        cue_range = self.cue_ranges[index]
        if bounded < cue_range.start_char and index > 0:
            cue_range = self.cue_ranges[index - 1] if end_boundary else cue_range
        length = max(1, cue_range.end_char - cue_range.start_char)
        fraction = min(max((bounded - cue_range.start_char) / length, 0.0), 1.0)
        return round(
            cue_range.start_ms + fraction * (cue_range.end_ms - cue_range.start_ms)
        )

    def timestamps_for_span(self, start_char: int, end_char: int) -> tuple[int, int]:
        if not 0 <= start_char < end_char <= len(self.text):
            raise ValueError("span must be a non-empty interval inside the transcript")
        return (
            self.timestamp_for_character(start_char, end_boundary=False),
            self.timestamp_for_character(end_char, end_boundary=True),
        )


@dataclass(frozen=True, slots=True)
class TranscriptWindow:
    index: int
    text: str
    input_ids: tuple[int, ...]
    attention_mask: tuple[int, ...]
    offset_mapping: tuple[tuple[int, int], ...]
    start_char: int
    end_char: int
    start_ms: int
    end_ms: int


def normalize_cue_text(text: str) -> str:
    normalized = WHITESPACE_PATTERN.sub(" ", text).strip().lower()
    normalized = URL_PATTERN.sub("URL_TOKEN", normalized)
    return NUMBER_PATTERN.sub("NUMBER_TOKEN", normalized)


def assemble_transcript(cues: Sequence[TranscriptCue]) -> AssembledTranscript:
    parts: list[str] = []
    ranges: list[CueCharacterRange] = []
    cursor = 0
    previous_start = -1
    for cue in cues:
        if cue.start_ms < previous_start or cue.end_ms < cue.start_ms:
            raise ValueError("transcript cues must be ordered with valid timestamps")
        previous_start = cue.start_ms
        text = normalize_cue_text(cue.text)
        if not text:
            continue
        if parts:
            parts.append(" ")
            cursor += 1
        start_char = cursor
        parts.append(text)
        cursor += len(text)
        ranges.append(
            CueCharacterRange(
                cue_index=cue.index,
                start_char=start_char,
                end_char=cursor,
                start_ms=cue.start_ms,
                end_ms=cue.end_ms,
            )
        )
    return AssembledTranscript("".join(parts), tuple(ranges))


def build_transcript_windows(
    transcript: AssembledTranscript,
    tokenizer,
    *,
    max_length: int = 1024,
    overlap_tokens: int = 128,
) -> tuple[TranscriptWindow, ...]:
    if max_length <= 0 or not 0 <= overlap_tokens < max_length:
        raise ValueError("window and overlap token counts are invalid")
    if not transcript.text:
        return ()
    encoded = tokenizer(
        transcript.text,
        truncation=True,
        max_length=max_length,
        stride=overlap_tokens,
        return_overflowing_tokens=True,
        return_offsets_mapping=True,
        padding=False,
    )
    windows: list[TranscriptWindow] = []
    for index, (input_ids, attention_mask, offsets) in enumerate(
        zip(
            encoded["input_ids"],
            encoded["attention_mask"],
            encoded["offset_mapping"],
            strict=True,
        )
    ):
        normalized_offsets = tuple((int(start), int(end)) for start, end in offsets)
        content_offsets = [pair for pair in normalized_offsets if pair[1] > pair[0]]
        if not content_offsets:
            continue
        start_char = content_offsets[0][0]
        end_char = content_offsets[-1][1]
        start_ms, end_ms = transcript.timestamps_for_span(start_char, end_char)
        windows.append(
            TranscriptWindow(
                index=index,
                text=transcript.text[start_char:end_char],
                input_ids=tuple(int(value) for value in input_ids),
                attention_mask=tuple(int(value) for value in attention_mask),
                offset_mapping=normalized_offsets,
                start_char=start_char,
                end_char=end_char,
                start_ms=start_ms,
                end_ms=end_ms,
            )
        )
    return tuple(windows)
