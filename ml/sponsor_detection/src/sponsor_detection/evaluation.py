from __future__ import annotations

import heapq
import json
import time
import tomllib
from collections import Counter
from contextlib import nullcontext
from dataclasses import asdict
from pathlib import Path
from typing import Callable, Sequence

from sponsor_detection.data.profile import sha256_file, write_json_atomic
from sponsor_detection.model.decoder import DecodedSponsorSpan, decode_bilou
from sponsor_detection.model.token_labels import ID_TO_LABEL


Interval = tuple[int, int]
IOU_THRESHOLDS = (0.3, 0.5, 0.7)


def interval_iou(left: Interval, right: Interval) -> float:
    intersection = max(0, min(left[1], right[1]) - max(left[0], right[0]))
    union = max(left[1], right[1]) - min(left[0], right[0])
    return intersection / union if union else 0.0


def match_spans(
    predicted: Sequence[Interval],
    expected: Sequence[Interval],
    *,
    iou_threshold: float,
) -> tuple[tuple[int, int, float], ...]:
    """Return an ordered one-to-one matching maximizing count, then total IoU."""
    if not 0 <= iou_threshold <= 1:
        raise ValueError("IoU threshold must be between zero and one")
    rows = len(predicted) + 1
    columns = len(expected) + 1
    table: list[list[tuple[int, float, tuple[tuple[int, int, float], ...]]]] = [
        [(0, 0.0, ()) for _ in range(columns)] for _ in range(rows)
    ]
    for predicted_index in range(1, rows):
        for expected_index in range(1, columns):
            candidates = [
                table[predicted_index - 1][expected_index],
                table[predicted_index][expected_index - 1],
            ]
            iou = interval_iou(
                predicted[predicted_index - 1],
                expected[expected_index - 1],
            )
            if iou >= iou_threshold:
                count, total_iou, pairs = table[predicted_index - 1][expected_index - 1]
                candidates.append(
                    (
                        count + 1,
                        total_iou + iou,
                        pairs + ((predicted_index - 1, expected_index - 1, iou),),
                    )
                )
            table[predicted_index][expected_index] = max(
                candidates,
                key=lambda candidate: (candidate[0], candidate[1]),
            )
    return table[-1][-1][2]


def character_overlap(predicted: Sequence[Interval], expected: Sequence[Interval]) -> int:
    overlap = 0
    predicted_index = 0
    expected_index = 0
    while predicted_index < len(predicted) and expected_index < len(expected):
        predicted_span = predicted[predicted_index]
        expected_span = expected[expected_index]
        overlap += max(
            0,
            min(predicted_span[1], expected_span[1])
            - max(predicted_span[0], expected_span[0]),
        )
        if predicted_span[1] <= expected_span[1]:
            predicted_index += 1
        else:
            expected_index += 1
    return overlap


def _ratio(numerator: int, denominator: int) -> float:
    return numerator / denominator if denominator else 0.0


def _f1(precision: float, recall: float) -> float:
    return 2 * precision * recall / (precision + recall) if precision + recall else 0.0


def _percentile(values: Sequence[int], fraction: float) -> int | None:
    if not values:
        return None
    ordered = sorted(values)
    return ordered[round((len(ordered) - 1) * fraction)]


class EvaluationAccumulator:
    def __init__(self, *, error_limit: int) -> None:
        self.threshold_counts = {
            threshold: Counter() for threshold in IOU_THRESHOLDS
        }
        self.window_counts: Counter[str] = Counter()
        self.character_counts: Counter[str] = Counter()
        self.kind_counts: dict[str, Counter[str]] = {}
        self.start_errors: list[int] = []
        self.end_errors: list[int] = []
        self.iou_values: list[float] = []
        self.predicted_confidences: list[float] = []
        self.false_positive_heap: list[tuple[float, str, dict[str, object]]] = []
        self.missed_heap: list[tuple[int, str, dict[str, object]]] = []
        self.error_limit = error_limit

    @staticmethod
    def _serialized_spans(spans: Sequence[DecodedSponsorSpan]) -> list[dict[str, object]]:
        return [asdict(span) for span in spans]

    def add(
        self,
        *,
        example_id: str,
        video_id: str,
        label_kind: str,
        text: str,
        predicted_spans: Sequence[DecodedSponsorSpan],
        expected_spans: Sequence[Interval],
    ) -> None:
        predicted = sorted(
            (span.start_char, span.end_char) for span in predicted_spans
        )
        expected = sorted(expected_spans)
        predicted_positive = bool(predicted)
        expected_positive = bool(expected)
        window_outcome = (
            "true_positive"
            if predicted_positive and expected_positive
            else "false_positive"
            if predicted_positive
            else "false_negative"
            if expected_positive
            else "true_negative"
        )
        self.window_counts[window_outcome] += 1
        kind = self.kind_counts.setdefault(label_kind, Counter())
        kind["windows"] += 1
        kind[window_outcome] += 1
        kind["predicted_spans"] += len(predicted)
        kind["expected_spans"] += len(expected)

        predicted_characters = sum(end - start for start, end in predicted)
        expected_characters = sum(end - start for start, end in expected)
        self.character_counts["predicted"] += predicted_characters
        self.character_counts["expected"] += expected_characters
        self.character_counts["overlap"] += character_overlap(predicted, expected)
        self.predicted_confidences.extend(span.confidence for span in predicted_spans)

        matches_by_threshold = {}
        for threshold in IOU_THRESHOLDS:
            matches = match_spans(predicted, expected, iou_threshold=threshold)
            matches_by_threshold[threshold] = matches
            counts = self.threshold_counts[threshold]
            counts["true_positive"] += len(matches)
            counts["false_positive"] += len(predicted) - len(matches)
            counts["false_negative"] += len(expected) - len(matches)

        matches = matches_by_threshold[0.5]
        matched_expected = {expected_index for _, expected_index, _ in matches}
        for predicted_index, expected_index, iou in matches:
            predicted_start, predicted_end = predicted[predicted_index]
            expected_start, expected_end = expected[expected_index]
            self.start_errors.append(abs(predicted_start - expected_start))
            self.end_errors.append(abs(predicted_end - expected_end))
            self.iou_values.append(iou)

        error_record = {
            "example_id": example_id,
            "video_id": video_id,
            "label_kind": label_kind,
            "expected_spans": [
                {"start_char": start, "end_char": end} for start, end in expected
            ],
            "predicted_spans": self._serialized_spans(predicted_spans),
            "text": text,
        }
        if predicted and not expected:
            confidence = max(span.confidence for span in predicted_spans)
            heapq.heappush(
                self.false_positive_heap,
                (confidence, example_id, error_record),
            )
            if len(self.false_positive_heap) > self.error_limit:
                heapq.heappop(self.false_positive_heap)
        missed_characters = sum(
            end - start
            for index, (start, end) in enumerate(expected)
            if index not in matched_expected
        )
        if missed_characters:
            heapq.heappush(
                self.missed_heap,
                (missed_characters, example_id, error_record),
            )
            if len(self.missed_heap) > self.error_limit:
                heapq.heappop(self.missed_heap)

    def report(self) -> dict[str, object]:
        span_metrics = {}
        for threshold, counts in self.threshold_counts.items():
            precision = _ratio(
                counts["true_positive"],
                counts["true_positive"] + counts["false_positive"],
            )
            recall = _ratio(
                counts["true_positive"],
                counts["true_positive"] + counts["false_negative"],
            )
            span_metrics[str(threshold)] = {
                **dict(counts),
                "precision": precision,
                "recall": recall,
                "f1": _f1(precision, recall),
            }

        window_precision = _ratio(
            self.window_counts["true_positive"],
            self.window_counts["true_positive"] + self.window_counts["false_positive"],
        )
        window_recall = _ratio(
            self.window_counts["true_positive"],
            self.window_counts["true_positive"] + self.window_counts["false_negative"],
        )
        character_precision = _ratio(
            self.character_counts["overlap"], self.character_counts["predicted"]
        )
        character_recall = _ratio(
            self.character_counts["overlap"], self.character_counts["expected"]
        )
        return {
            "span_metrics_by_character_iou": span_metrics,
            "window_presence": {
                **dict(self.window_counts),
                "precision": window_precision,
                "recall": window_recall,
                "f1": _f1(window_precision, window_recall),
            },
            "character_coverage": {
                **dict(self.character_counts),
                "precision": character_precision,
                "recall": character_recall,
                "f1": _f1(character_precision, character_recall),
            },
            "boundary_error_characters_at_iou_0.5": {
                "matched_spans": len(self.iou_values),
                "start_median": _percentile(self.start_errors, 0.5),
                "start_p95": _percentile(self.start_errors, 0.95),
                "end_median": _percentile(self.end_errors, 0.5),
                "end_p95": _percentile(self.end_errors, 0.95),
                "iou_median": _float_percentile(self.iou_values, 0.5),
                "iou_p05": _float_percentile(self.iou_values, 0.05),
            },
            "breakdown_by_label_kind": {
                kind: dict(counts) for kind, counts in sorted(self.kind_counts.items())
            },
            "predicted_span_confidence": {
                "count": len(self.predicted_confidences),
                "median": _float_percentile(self.predicted_confidences, 0.5),
                "p05": _float_percentile(self.predicted_confidences, 0.05),
                "p95": _float_percentile(self.predicted_confidences, 0.95),
            },
        }

    def errors(self) -> dict[str, object]:
        return {
            "highest_confidence_false_positive_windows": [
                record
                for _, _, record in sorted(self.false_positive_heap, reverse=True)
            ],
            "largest_missed_sponsor_windows": [
                record for _, _, record in sorted(self.missed_heap, reverse=True)
            ],
        }


def _float_percentile(values: Sequence[float], fraction: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    return ordered[round((len(ordered) - 1) * fraction)]


def run_checkpoint_inference(
    checkpoint_path: Path,
    dataset_path: Path,
    *,
    batch_size: int,
    max_length: int,
    bf16: bool,
    prediction_callback: Callable[
        [dict[str, object], Sequence[DecodedSponsorSpan]], None
    ],
    progress_callback=None,
) -> dict[str, object]:
    import pyarrow.parquet as parquet
    import torch
    from transformers import AutoModelForTokenClassification, AutoTokenizer

    model_artifacts = (
        "model.safetensors",
        "model.safetensors.index.json",
        "pytorch_model.bin",
        "pytorch_model.bin.index.json",
    )
    if not checkpoint_path.is_dir() or not any(
        (checkpoint_path / artifact).is_file() for artifact in model_artifacts
    ):
        raise FileNotFoundError(
            f"checkpoint contains no trained model weights: {checkpoint_path}"
        )
    tokenizer = AutoTokenizer.from_pretrained(checkpoint_path)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = AutoModelForTokenClassification.from_pretrained(checkpoint_path).to(device)
    expected_labels = {int(index): name for index, name in ID_TO_LABEL.items()}
    if model.config.id2label != expected_labels:
        raise ValueError("checkpoint label mapping does not match the decoder")
    model.eval()

    use_bf16 = bf16 and device.type == "cuda"
    parquet_file = parquet.ParquetFile(dataset_path)
    total_rows = parquet_file.metadata.num_rows
    processed_rows = 0
    started = time.monotonic()
    columns = ["example_id", "video_id", "text", "sponsor_spans", "label_kind"]
    for batch in parquet_file.iter_batches(batch_size=batch_size, columns=columns):
        records = batch.to_pylist()
        encoded = tokenizer(
            [record["text"] for record in records],
            padding=True,
            truncation=True,
            max_length=max_length,
            return_offsets_mapping=True,
            return_tensors="pt",
        )
        offsets = encoded.pop("offset_mapping")
        cpu_attention_mask = encoded["attention_mask"]
        model_inputs = {name: tensor.to(device) for name, tensor in encoded.items()}
        context = (
            torch.autocast(device_type="cuda", dtype=torch.bfloat16)
            if use_bf16
            else nullcontext()
        )
        with torch.inference_mode(), context:
            logits = model(**model_inputs).logits.float().cpu()
        for index, record in enumerate(records):
            decoded = decode_bilou(
                logits[index].tolist(),
                offsets[index].tolist(),
                attention_mask=cpu_attention_mask[index].tolist(),
            )
            prediction_callback(record, decoded.spans)
        processed_rows += len(records)
        if progress_callback and (
            processed_rows == total_rows or processed_rows % (batch_size * 100) == 0
        ):
            progress_callback(processed_rows, total_rows, time.monotonic() - started)
    return {
        "device": str(device),
        "bf16": use_bf16,
        "batch_size": batch_size,
        "elapsed_seconds": round(time.monotonic() - started, 3),
        "rows": total_rows,
    }


def evaluate_from_config(path: Path, *, progress_callback=None) -> dict[str, object]:
    with path.open("rb") as source:
        configuration = tomllib.load(source)
    dataset = configuration["dataset"]
    model_configuration = configuration["model"]
    evaluation = configuration["evaluation"]
    manifest_path = Path(dataset["manifest_path"])
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    test_path = Path(dataset["test_path"])
    if sha256_file(test_path) != manifest["outputs"]["test"]["sha256"]:
        raise ValueError("test dataset does not match its manifest")

    checkpoint_path = Path(model_configuration["checkpoint_path"])
    decoder_config_path_value = model_configuration.get("decoder_config_path")
    confidence_threshold = 0.0
    decoder_configuration = None
    if decoder_config_path_value:
        decoder_config_path = Path(decoder_config_path_value)
        decoder_configuration = json.loads(decoder_config_path.read_text(encoding="utf-8"))
        expected_model_sha = decoder_configuration["checkpoint"]["model_sha256"]
        if sha256_file(checkpoint_path / "model.safetensors") != expected_model_sha:
            raise ValueError("decoder configuration does not match the checkpoint")
        confidence_threshold = float(decoder_configuration["confidence_threshold"])
    batch_size = int(evaluation.get("batch_size", 16))
    max_length = int(model_configuration.get("max_length", 1024))
    accumulator = EvaluationAccumulator(error_limit=int(evaluation.get("error_limit", 100)))
    def add_prediction(
        record: dict[str, object], predicted_spans: Sequence[DecodedSponsorSpan]
    ) -> None:
        accumulator.add(
            example_id=str(record["example_id"]),
            video_id=str(record["video_id"]),
            label_kind=str(record["label_kind"]),
            text=str(record["text"]),
            predicted_spans=tuple(
                span
                for span in predicted_spans
                if span.confidence >= confidence_threshold
            ),
            expected_spans=[
                (int(span["start_char"]), int(span["end_char"]))
                for span in record["sponsor_spans"]
            ],
        )

    runtime = run_checkpoint_inference(
        checkpoint_path,
        test_path,
        batch_size=batch_size,
        max_length=max_length,
        bf16=bool(evaluation.get("bf16", True)),
        prediction_callback=add_prediction,
        progress_callback=progress_callback,
    )

    report = {
        "schema_version": 1,
        "checkpoint": {
            "path": str(checkpoint_path),
            "model_sha256": sha256_file(checkpoint_path / "model.safetensors"),
        },
        "dataset": {
            "manifest_sha256": sha256_file(manifest_path),
            "test_sha256": manifest["outputs"]["test"]["sha256"],
            "rows": runtime["rows"],
        },
        "runtime": {key: value for key, value in runtime.items() if key != "rows"},
        "decoder": {
            "confidence_threshold": confidence_threshold,
            "config_path": (
                str(decoder_config_path_value) if decoder_configuration else None
            ),
        },
        **accumulator.report(),
    }
    write_json_atomic(Path(evaluation["report_path"]), report)
    write_json_atomic(Path(evaluation["errors_path"]), accumulator.errors())
    return report
