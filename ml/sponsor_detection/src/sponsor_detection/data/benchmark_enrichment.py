from __future__ import annotations

import json
import tomllib
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Sequence

from sponsor_detection.data.benchmark import write_json_lines_atomic
from sponsor_detection.data.benchmark_review import load_verified_jsonl
from sponsor_detection.data.profile import sha256_file, write_json_atomic
from sponsor_detection.inference.windowing import TranscriptCue


@dataclass(frozen=True, slots=True)
class EnrichmentModel:
    name: str
    checkpoint_path: Path
    confidence_threshold: float
    checkpoint_sha256: str


def _load_models(configuration: dict[str, object]) -> list[EnrichmentModel]:
    models = []
    for raw_model in configuration.get("models", []):
        name = str(raw_model["name"])
        checkpoint_path = Path(raw_model["checkpoint_path"])
        decoder_path_value = raw_model.get("decoder_config_path")
        if decoder_path_value:
            decoder = json.loads(Path(decoder_path_value).read_text(encoding="utf-8"))
            confidence_threshold = float(decoder["confidence_threshold"])
            expected_sha256 = str(decoder["checkpoint"]["model_sha256"])
        else:
            confidence_threshold = float(raw_model["confidence_threshold"])
            expected_sha256 = sha256_file(checkpoint_path / "model.safetensors")
        actual_sha256 = sha256_file(checkpoint_path / "model.safetensors")
        if actual_sha256 != expected_sha256:
            raise ValueError(f"model {name} does not match its decoder configuration")
        if not 0 <= confidence_threshold <= 1:
            raise ValueError(f"model {name} has an invalid confidence threshold")
        models.append(
            EnrichmentModel(
                name=name,
                checkpoint_path=checkpoint_path,
                confidence_threshold=confidence_threshold,
                checkpoint_sha256=actual_sha256,
            )
        )
    if not models:
        raise ValueError("benchmark enrichment requires at least one model")
    if len({model.name for model in models}) != len(models):
        raise ValueError("benchmark enrichment model names must be unique")
    return models


def _load_cues(record: dict[str, object]) -> list[TranscriptCue]:
    transcript = record.get("transcript")
    if not isinstance(transcript, dict):
        raise ValueError(f"video {record['video_id']} has no transcript metadata")
    transcript_path = Path(str(transcript["path"]))
    if sha256_file(transcript_path) != transcript.get("sha256"):
        raise ValueError(f"transcript checksum mismatch: {record['video_id']}")
    payload = json.loads(transcript_path.read_text(encoding="utf-8"))
    return [
        TranscriptCue(
            index=int(cue["index"]),
            start_ms=int(cue["start_ms"]),
            end_ms=int(cue["end_ms"]),
            text=str(cue["text"]),
        )
        for cue in payload["cues"]
    ]


def enrich_benchmark_review(
    review_path: Path,
    review_manifest_path: Path,
    models: Sequence[EnrichmentModel],
    *,
    batch_size: int = 16,
    bf16: bool = True,
    max_length: int = 768,
    overlap_tokens: int = 128,
    merge_gap_characters: int = 24,
    merge_gap_ms: int = 1500,
    context_characters: int = 240,
    detector_factory: Callable[..., object] | None = None,
    progress_callback=None,
) -> dict[str, object]:
    if context_characters < 0:
        raise ValueError("context characters cannot be negative")
    records, manifest = load_verified_jsonl(review_path, review_manifest_path)
    source_review_sha256 = sha256_file(review_path)
    predictions_by_video: dict[str, list[dict[str, object]]] = {
        str(record["video_id"]): [] for record in records
    }
    if detector_factory is None:
        from sponsor_detection.inference.pipeline import FullTranscriptSponsorDetector

        detector_factory = FullTranscriptSponsorDetector

    for model_index, model in enumerate(models, start=1):
        detector = detector_factory(
            model.checkpoint_path,
            confidence_threshold=model.confidence_threshold,
            max_length=max_length,
            overlap_tokens=overlap_tokens,
            batch_size=batch_size,
            bf16=bf16,
            merge_gap_characters=merge_gap_characters,
            merge_gap_ms=merge_gap_ms,
        )
        for record_index, record in enumerate(records, start=1):
            prediction = detector.predict(_load_cues(record))
            text = prediction.transcript.text
            spans = []
            for span in prediction.sponsor_spans:
                context_start = max(0, span.start_char - context_characters)
                context_end = min(len(text), span.end_char + context_characters)
                spans.append(
                    {
                        "start_ms": span.start_ms,
                        "end_ms": span.end_ms,
                        "start_char": span.start_char,
                        "end_char": span.end_char,
                        "confidence": span.confidence,
                        "text": text[span.start_char : span.end_char],
                        "context": text[context_start:context_end],
                        "supporting_windows": list(span.supporting_windows),
                    }
                )
            predictions_by_video[str(record["video_id"])].append(
                {
                    "model": model.name,
                    "checkpoint_sha256": model.checkpoint_sha256,
                    "confidence_threshold": model.confidence_threshold,
                    "windows": len(prediction.windows),
                    "spans": spans,
                }
            )
            if progress_callback:
                progress_callback(
                    model_index,
                    len(models),
                    record_index,
                    len(records),
                    str(record["video_id"]),
                )

    for record in records:
        record["model_predictions"] = predictions_by_video[str(record["video_id"])]
    write_json_lines_atomic(review_path, records)
    manifest["status"] = "annotation_pending"
    manifest["enrichment"] = {
        "source_review_sha256": source_review_sha256,
        "models": [
            {
                "name": model.name,
                "checkpoint_path": str(model.checkpoint_path),
                "checkpoint_sha256": model.checkpoint_sha256,
                "confidence_threshold": model.confidence_threshold,
            }
            for model in models
        ],
        "videos": len(records),
        "predicted_spans": sum(
            len(prediction["spans"])
            for predictions in predictions_by_video.values()
            for prediction in predictions
        ),
    }
    manifest["output"] = {
        "path": str(review_path),
        "bytes": review_path.stat().st_size,
        "sha256": sha256_file(review_path),
    }
    write_json_atomic(review_manifest_path, manifest)
    return manifest


def enrich_benchmark_review_from_config(
    path: Path, *, detector_factory=None, progress_callback=None
) -> dict[str, object]:
    with path.open("rb") as source:
        configuration = tomllib.load(source)["benchmark_enrichment"]
    return enrich_benchmark_review(
        Path(configuration["review_path"]),
        Path(configuration["review_manifest_path"]),
        _load_models(configuration),
        batch_size=int(configuration.get("batch_size", 16)),
        bf16=bool(configuration.get("bf16", True)),
        max_length=int(configuration.get("max_length", 768)),
        overlap_tokens=int(configuration.get("overlap_tokens", 128)),
        merge_gap_characters=int(configuration.get("merge_gap_characters", 24)),
        merge_gap_ms=int(configuration.get("merge_gap_ms", 1500)),
        context_characters=int(configuration.get("context_characters", 240)),
        detector_factory=detector_factory,
        progress_callback=progress_callback,
    )
