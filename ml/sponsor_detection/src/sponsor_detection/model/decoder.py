from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Sequence

from sponsor_detection.model.token_labels import IGNORED_LABEL_ID, LABEL_TO_ID


O = LABEL_TO_ID["O"]
B = LABEL_TO_ID["B-SPONSOR"]
I = LABEL_TO_ID["I-SPONSOR"]
L = LABEL_TO_ID["L-SPONSOR"]
U = LABEL_TO_ID["U-SPONSOR"]

START_LABELS = frozenset((O, B, U))
END_LABELS = frozenset((O, L, U))
PREVIOUS_LABELS = {
    O: frozenset((O, L, U)),
    B: frozenset((O, L, U)),
    I: frozenset((B, I)),
    L: frozenset((B, I)),
    U: frozenset((O, L, U)),
}


@dataclass(frozen=True, slots=True)
class DecodedSponsorSpan:
    start_char: int
    end_char: int
    confidence: float
    start_token_index: int
    end_token_index: int


@dataclass(frozen=True, slots=True)
class DecodedSequence:
    labels: tuple[int, ...]
    spans: tuple[DecodedSponsorSpan, ...]
    path_log_probability: float


def _log_softmax(values: Sequence[float]) -> tuple[float, ...]:
    if len(values) != len(LABEL_TO_ID):
        raise ValueError(f"expected {len(LABEL_TO_ID)} logits per token")
    logits = tuple(float(value) for value in values)
    if not all(math.isfinite(value) for value in logits):
        raise ValueError("logits must be finite")
    maximum = max(logits)
    denominator = maximum + math.log(
        sum(math.exp(value - maximum) for value in logits)
    )
    return tuple(value - denominator for value in logits)


def _viterbi_path(log_probabilities: Sequence[Sequence[float]]) -> tuple[list[int], float]:
    scores = {
        label: log_probabilities[0][label]
        if label in START_LABELS
        else -math.inf
        for label in range(len(LABEL_TO_ID))
    }
    back_pointers: list[dict[int, int]] = []

    for token_probabilities in log_probabilities[1:]:
        next_scores: dict[int, float] = {}
        token_back_pointers: dict[int, int] = {}
        for label in range(len(LABEL_TO_ID)):
            previous = max(
                PREVIOUS_LABELS[label],
                key=lambda candidate: scores[candidate],
            )
            next_scores[label] = scores[previous] + token_probabilities[label]
            token_back_pointers[label] = previous
        scores = next_scores
        back_pointers.append(token_back_pointers)

    final_label = max(END_LABELS, key=lambda label: scores[label])
    path = [final_label]
    for token_back_pointers in reversed(back_pointers):
        path.append(token_back_pointers[path[-1]])
    path.reverse()
    return path, scores[final_label]


def _spans_from_path(
    path: Sequence[int],
    token_indexes: Sequence[int],
    offsets: Sequence[Sequence[int]],
    log_probabilities: Sequence[Sequence[float]],
) -> tuple[DecodedSponsorSpan, ...]:
    spans: list[DecodedSponsorSpan] = []
    position = 0
    while position < len(path):
        label = path[position]
        if label == O:
            position += 1
            continue
        end_position = position
        if label == B:
            end_position += 1
            while path[end_position] == I:
                end_position += 1
        elif label != U:
            raise AssertionError("Viterbi decoder produced an invalid BILOU path")

        selected_probabilities = [
            log_probabilities[index][path[index]]
            for index in range(position, end_position + 1)
        ]
        start_token_index = token_indexes[position]
        end_token_index = token_indexes[end_position]
        spans.append(
            DecodedSponsorSpan(
                start_char=int(offsets[start_token_index][0]),
                end_char=int(offsets[end_token_index][1]),
                confidence=math.exp(
                    sum(selected_probabilities) / len(selected_probabilities)
                ),
                start_token_index=start_token_index,
                end_token_index=end_token_index,
            )
        )
        position = end_position + 1
    return tuple(spans)


def decode_bilou(
    logits: Sequence[Sequence[float]],
    offsets: Sequence[Sequence[int]],
    *,
    attention_mask: Sequence[int] | None = None,
) -> DecodedSequence:
    """Decode CPU logits into the highest-scoring valid BILOU character spans."""
    if len(logits) != len(offsets):
        raise ValueError("logits and offsets must contain the same number of tokens")
    if attention_mask is not None and len(attention_mask) != len(logits):
        raise ValueError("attention mask must contain one value per token")

    token_indexes: list[int] = []
    for index, offset in enumerate(offsets):
        if len(offset) != 2:
            raise ValueError("each token offset must contain start and end")
        start, end = (int(value) for value in offset)
        if start < 0 or end < start:
            raise ValueError("token offsets must be ordered and non-negative")
        if end > start and (attention_mask is None or attention_mask[index]):
            token_indexes.append(index)

    labels = [IGNORED_LABEL_ID] * len(logits)
    if not token_indexes:
        return DecodedSequence(tuple(labels), (), 0.0)

    log_probabilities = [_log_softmax(logits[index]) for index in token_indexes]
    path, path_score = _viterbi_path(log_probabilities)
    for index, label in zip(token_indexes, path, strict=True):
        labels[index] = label
    spans = _spans_from_path(
        path,
        token_indexes,
        offsets,
        log_probabilities,
    )
    return DecodedSequence(tuple(labels), spans, path_score)
