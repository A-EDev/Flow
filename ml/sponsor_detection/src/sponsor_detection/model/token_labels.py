from __future__ import annotations

from typing import Sequence


LABEL_TO_ID = {"O": 0, "B-SPONSOR": 1, "I-SPONSOR": 2, "L-SPONSOR": 3, "U-SPONSOR": 4}
ID_TO_LABEL = {value: key for key, value in LABEL_TO_ID.items()}
IGNORED_LABEL_ID = -100


def bilou_labels_for_offsets(
    offsets: Sequence[Sequence[int]],
    sponsor_spans: Sequence[dict[str, object]],
) -> list[int]:
    labels = [
        IGNORED_LABEL_ID if start == end else LABEL_TO_ID["O"]
        for start, end in offsets
    ]
    for span in sponsor_spans:
        span_start = int(span["start_char"])
        span_end = int(span["end_char"])
        token_indexes = [
            index
            for index, (start, end) in enumerate(offsets)
            if start != end and min(end, span_end) > max(start, span_start)
        ]
        if not token_indexes:
            continue
        if any(labels[index] != LABEL_TO_ID["O"] for index in token_indexes):
            raise ValueError("overlapping sponsor character spans")
        if len(token_indexes) == 1:
            labels[token_indexes[0]] = LABEL_TO_ID["U-SPONSOR"]
            continue
        labels[token_indexes[0]] = LABEL_TO_ID["B-SPONSOR"]
        labels[token_indexes[-1]] = LABEL_TO_ID["L-SPONSOR"]
        for index in token_indexes[1:-1]:
            labels[index] = LABEL_TO_ID["I-SPONSOR"]
    return labels
