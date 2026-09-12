from __future__ import annotations

import json
import tomllib
from datetime import UTC, datetime
from pathlib import Path

from sponsor_detection.data.profile import sha256_file, write_json_atomic


def _artifact(path: Path) -> dict[str, object]:
    if not path.is_file():
        raise FileNotFoundError(f"release artifact does not exist: {path}")
    return {
        "path": str(path),
        "bytes": path.stat().st_size,
        "sha256": sha256_file(path),
    }


def freeze_release_from_config(path: Path) -> dict[str, object]:
    with path.open("rb") as source:
        configuration = tomllib.load(source)
    release = configuration["release"]
    model = configuration["model"]
    inference = configuration["inference"]
    artifacts_configuration = configuration["artifacts"]
    checkpoint_path = Path(model["checkpoint_path"])
    checkpoint_artifacts = {
        name: _artifact(checkpoint_path / filename)
        for name, filename in {
            "model": "model.safetensors",
            "model_config": "config.json",
            "tokenizer": "tokenizer.json",
            "tokenizer_config": "tokenizer_config.json",
            "training_metrics": "metrics.json",
        }.items()
    }
    artifacts = {
        name: _artifact(Path(artifact_path))
        for name, artifact_path in artifacts_configuration.items()
    }
    decoder = json.loads(Path(artifacts_configuration["decoder_config"]).read_text())
    if checkpoint_artifacts["model"]["sha256"] != decoder["checkpoint"][
        "model_sha256"
    ]:
        raise ValueError("decoder configuration does not match the release model")

    calibrated_evaluation = json.loads(
        Path(artifacts_configuration["calibrated_window_evaluation"]).read_text()
    )
    full_video_evaluation = json.loads(
        Path(artifacts_configuration["full_video_evaluation"]).read_text()
    )
    manifest = {
        "schema_version": 1,
        "generated_at": datetime.now(tz=UTC).isoformat(),
        "name": str(release["name"]),
        "status": str(release.get("status", "release_candidate")),
        "base_encoder": {
            "name": str(model["base_encoder"]),
            "revision": str(model["base_revision"]),
        },
        "checkpoint": {
            "path": str(checkpoint_path),
            "artifacts": checkpoint_artifacts,
        },
        "decoder": {
            "confidence_threshold": float(decoder["confidence_threshold"]),
            "config": artifacts["decoder_config"],
        },
        "inference": {
            "max_length": int(inference["max_length"]),
            "overlap_tokens": int(inference["overlap_tokens"]),
            "merge_gap_characters": int(inference["merge_gap_characters"]),
            "merge_gap_ms": int(inference["merge_gap_ms"]),
            "text_normalization": [
                "collapse_whitespace",
                "lowercase",
                "replace_urls_with_URL_TOKEN",
                "replace_numbers_with_NUMBER_TOKEN",
            ],
        },
        "source_artifacts": artifacts,
        "metrics": {
            "calibrated_window_test": {
                "rows": calibrated_evaluation["dataset"]["rows"],
                "window_presence": calibrated_evaluation["window_presence"],
                "span_iou_0.5": calibrated_evaluation[
                    "span_metrics_by_character_iou"
                ]["0.5"],
                "character_coverage": calibrated_evaluation["character_coverage"],
            },
            "full_video_canary": {
                "videos": full_video_evaluation["dataset"]["videos"],
                "video_presence": full_video_evaluation["video_presence"],
                "span_temporal_iou_0.5": full_video_evaluation[
                    "span_metrics_by_temporal_iou"
                ]["0.5"],
                "temporal_coverage": full_video_evaluation["temporal_coverage"],
                "boundary_error_ms_at_iou_0.5": full_video_evaluation[
                    "boundary_error_ms_at_iou_0.5"
                ],
            },
        },
        "validation_scope": {
            "window_test_is_frozen": True,
            "full_video_canary_is_positive_only": True,
            "full_video_canary_videos": full_video_evaluation["dataset"]["videos"],
            "known_limitations": [
                "Held-out SponsorBlock-derived labels contain missing and category-noisy annotations.",
                "The full-video canary has only 20 sponsor-positive videos and cannot estimate negative-video specificity.",
                "Transcript timestamp boundaries are interpolated within caption cues.",
            ],
        },
        "release_config": _artifact(path),
    }
    write_json_atomic(Path(release["manifest_path"]), manifest)
    return manifest
