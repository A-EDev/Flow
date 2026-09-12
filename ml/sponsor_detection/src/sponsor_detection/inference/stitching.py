from __future__ import annotations

from dataclasses import dataclass
from typing import Sequence

from sponsor_detection.inference.windowing import AssembledTranscript


@dataclass(frozen=True, slots=True)
class WindowSponsorSpan:
    window_index: int
    start_char: int
    end_char: int
    confidence: float


@dataclass(frozen=True, slots=True)
class StitchedSponsorSpan:
    start_char: int
    end_char: int
    start_ms: int
    end_ms: int
    confidence: float
    supporting_windows: tuple[int, ...]


def _fuse_cluster(
    transcript: AssembledTranscript,
    cluster: Sequence[WindowSponsorSpan],
) -> StitchedSponsorSpan:
    start_char = min(span.start_char for span in cluster)
    end_char = max(span.end_char for span in cluster)
    start_ms, end_ms = transcript.timestamps_for_span(start_char, end_char)
    return StitchedSponsorSpan(
        start_char=start_char,
        end_char=end_char,
        start_ms=start_ms,
        end_ms=end_ms,
        confidence=max(span.confidence for span in cluster),
        supporting_windows=tuple(sorted({span.window_index for span in cluster})),
    )


def stitch_window_spans(
    transcript: AssembledTranscript,
    spans: Sequence[WindowSponsorSpan],
    *,
    confidence_threshold: float,
    merge_gap_characters: int = 24,
    merge_gap_ms: int = 1500,
) -> tuple[StitchedSponsorSpan, ...]:
    if not 0 <= confidence_threshold <= 1:
        raise ValueError("confidence threshold must be between zero and one")
    if merge_gap_characters < 0 or merge_gap_ms < 0:
        raise ValueError("merge gaps cannot be negative")
    selected = sorted(
        (
            span
            for span in spans
            if span.confidence >= confidence_threshold
            and 0 <= span.start_char < span.end_char <= len(transcript.text)
        ),
        key=lambda span: (span.start_char, span.end_char),
    )
    if not selected:
        return ()

    clusters: list[list[WindowSponsorSpan]] = []
    for span in selected:
        if clusters and span.start_char <= max(item.end_char for item in clusters[-1]):
            clusters[-1].append(span)
        else:
            clusters.append([span])
    fused = [_fuse_cluster(transcript, cluster) for cluster in clusters]

    merged: list[StitchedSponsorSpan] = []
    for span in fused:
        if not merged:
            merged.append(span)
            continue
        previous = merged[-1]
        character_gap = span.start_char - previous.end_char
        time_gap = span.start_ms - previous.end_ms
        if character_gap <= merge_gap_characters and time_gap <= merge_gap_ms:
            start_ms, end_ms = transcript.timestamps_for_span(
                previous.start_char, span.end_char
            )
            merged[-1] = StitchedSponsorSpan(
                start_char=previous.start_char,
                end_char=span.end_char,
                start_ms=start_ms,
                end_ms=end_ms,
                confidence=max(previous.confidence, span.confidence),
                supporting_windows=tuple(
                    sorted(set(previous.supporting_windows + span.supporting_windows))
                ),
            )
        else:
            merged.append(span)
    return tuple(merged)
