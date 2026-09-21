from __future__ import annotations

import argparse
import json
import sys
import tomllib
from pathlib import Path
from typing import Sequence

from sponsor_detection.data.profile import EligibilityPolicy, profile_csv, write_json_atomic


def _eligibility_policy(configuration: dict[str, object]) -> EligibilityPolicy:
    return EligibilityPolicy(**configuration.get("eligibility", {}))


def _load_profile_configuration(path: Path) -> tuple[Path, Path, bool, int, EligibilityPolicy]:
    with path.open("rb") as source:
        configuration = tomllib.load(source)
    profile = configuration.get("profile", {})
    return (
        Path(profile["input_path"]),
        Path(profile["output_path"]),
        bool(profile.get("compute_sha256", True)),
        int(profile.get("progress_every_rows", 0)),
        _eligibility_policy(configuration),
    )


def _load_label_configuration(
    path: Path,
) -> tuple[Path, Path, Path, Path, int, str, int, EligibilityPolicy]:
    with path.open("rb") as source:
        configuration = tomllib.load(source)
    labels = configuration.get("labels", {})
    return (
        Path(labels["input_path"]),
        Path(labels["profile_path"]),
        Path(labels["output_path"]),
        Path(labels["manifest_path"]),
        int(labels.get("batch_size", 100_000)),
        str(labels.get("compression", "zstd")),
        int(labels.get("progress_every_rows", 0)),
        _eligibility_policy(configuration),
    )


def _load_pilot_configuration(
    path: Path,
) -> tuple[Path, Path, Path, Path, int, str, int, int]:
    with path.open("rb") as source:
        configuration = tomllib.load(source)
    pilot = configuration.get("pilot", {})
    return (
        Path(pilot["labels_path"]),
        Path(pilot["labels_manifest_path"]),
        Path(pilot["output_path"]),
        Path(pilot["manifest_path"]),
        int(pilot.get("sample_size", 10_000)),
        str(pilot["seed"]),
        int(pilot.get("batch_size", 65_536)),
        int(pilot.get("progress_every_rows", 0)),
    )


def _load_benchmark_configuration(path: Path) -> dict[str, object]:
    with path.open("rb") as source:
        configuration = tomllib.load(source)
    benchmark = configuration.get("benchmark", {})
    existing_pilot = benchmark.get("existing_pilot_path")
    return {
        "mirror_csv_path": Path(benchmark["mirror_csv_path"]),
        "labels_path": Path(benchmark["labels_path"]),
        "labels_manifest_path": Path(benchmark["labels_manifest_path"]),
        "training_directory": Path(benchmark["training_directory"]),
        "output_path": Path(benchmark["output_path"]),
        "manifest_path": Path(benchmark["manifest_path"]),
        "existing_pilot_path": Path(existing_pilot) if existing_pilot else None,
        "target_per_class": int(benchmark.get("target_per_class", 20)),
        "pool_per_class": int(benchmark.get("pool_per_class", 200)),
        "seed": str(benchmark["seed"]),
        "negative_sample_modulus": int(
            benchmark.get("negative_sample_modulus", 64)
        ),
        "batch_size": int(benchmark.get("batch_size", 131_072)),
        "progress_every_rows": int(benchmark.get("progress_every_rows", 1_000_000)),
    }


def _load_benchmark_review_configuration(path: Path) -> dict[str, object]:
    with path.open("rb") as source:
        review = tomllib.load(source)["benchmark_review"]
    return {
        "candidates_path": Path(review["candidates_path"]),
        "candidates_manifest_path": Path(review["candidates_manifest_path"]),
        "transcript_directory": Path(review["transcript_directory"]),
        "metadata_path": Path(review["metadata_path"]),
        "training_directory": Path(review["training_directory"]),
        "output_path": Path(review["output_path"]),
        "manifest_path": Path(review["manifest_path"]),
        "target_per_class": int(review.get("target_per_class", 20)),
    }


def _load_benchmark_freeze_configuration(path: Path) -> dict[str, object]:
    with path.open("rb") as source:
        freeze = tomllib.load(source)["benchmark_freeze"]
    return {
        "review_path": Path(freeze["review_path"]),
        "review_manifest_path": Path(freeze["review_manifest_path"]),
        "output_path": Path(freeze["output_path"]),
        "manifest_path": Path(freeze["manifest_path"]),
        "target_per_class": int(freeze.get("target_per_class", 20)),
    }


def _load_transcript_configuration(
    path: Path,
) -> tuple[Path, Path, Path, Path, list[str], int, float, bool, str]:
    with path.open("rb") as source:
        configuration = tomllib.load(source)
    transcripts = configuration.get("transcripts", {})
    return (
        Path(transcripts["pilot_path"]),
        Path(transcripts["pilot_manifest_path"]),
        Path(transcripts["output_directory"]),
        Path(transcripts["report_path"]),
        [str(language) for language in transcripts.get("languages", ["en"])],
        int(transcripts.get("limit", 25)),
        float(transcripts.get("request_delay_seconds", 2.0)),
        bool(transcripts.get("stop_on_block", True)),
        str(transcripts.get("provider", "youtube_transcript_api")),
    )


def _load_collection_configuration(
    path: Path,
) -> tuple[
    Path,
    Path,
    Path,
    Path,
    list[str],
    int,
    int,
    float,
    float,
    int,
    bool,
    float,
    str,
    Path,
    str,
]:
    with path.open("rb") as source:
        configuration = tomllib.load(source)
    collection = configuration.get("collection", {})
    return (
        Path(collection["pilot_path"]),
        Path(collection["pilot_manifest_path"]),
        Path(collection["output_directory"]),
        Path(collection["report_path"]),
        [str(language) for language in collection.get("languages", ["en"])],
        int(collection.get("target_successes", 5_000)),
        int(collection.get("max_attempts_per_run", 50)),
        float(collection.get("minimum_request_delay_seconds", 5.0)),
        float(collection.get("maximum_request_delay_seconds", 10.0)),
        int(collection.get("max_transient_attempts_per_video", 3)),
        bool(collection.get("stop_on_block", True)),
        float(collection.get("block_cooldown_seconds", 21_600)),
        str(collection.get("provider", "youtube_transcript_api")),
        Path(collection.get("env_path", ".env")),
        str(
            collection.get(
                "api_key_environment_variable", "TRANSCRIPT_API_KEY"
            )
        ),
    )


def _transcript_client(
    provider: str,
    *,
    env_path: Path = Path(".env"),
    api_key_environment_variable: str = "TRANSCRIPT_API_KEY",
):
    if provider == "youtube_transcript_api":
        from youtube_transcript_api import YouTubeTranscriptApi

        return YouTubeTranscriptApi()
    if provider == "yt_dlp":
        from sponsor_detection.data.yt_dlp_transcripts import YtDlpTranscriptClient

        return YtDlpTranscriptClient()
    if provider == "transcript_api":
        from sponsor_detection.data.metadata import load_environment_value
        from sponsor_detection.data.transcript_api import TranscriptApiClient

        api_key = load_environment_value(env_path, api_key_environment_variable)
        return TranscriptApiClient(api_key)
    raise ValueError(f"unsupported transcript provider: {provider}")


def _load_metadata_configuration(
    path: Path,
) -> tuple[Path, Path, Path, Path, str, int, int | None, int]:
    with path.open("rb") as source:
        configuration = tomllib.load(source)
    metadata = configuration.get("metadata", {})
    limit = metadata.get("limit")
    return (
        Path(metadata["transcript_directory"]),
        Path(metadata["output_path"]),
        Path(metadata["manifest_path"]),
        Path(metadata.get("env_path", ".env")),
        str(metadata.get("api_key_environment_variable", "YOUTUBE_DATA_API_KEY")),
        int(metadata.get("batch_size", 50)),
        int(limit) if limit is not None else None,
        int(metadata.get("max_api_requests", 120)),
    )


def _load_xenova_configuration(
    path: Path,
) -> tuple[Path, Path, str, dict[str, str], Path | None]:
    with path.open("rb") as source:
        configuration = tomllib.load(source)
    dataset = configuration.get("xenova_dataset", {})
    current_labels = dataset.get("current_labels_path")
    return (
        Path(dataset["directory"]),
        Path(dataset["report_path"]),
        str(dataset["revision"]),
        {str(name): str(value) for name, value in dataset.get("sha256", {}).items()},
        Path(current_labels) if current_labels else None,
    )


def _load_training_dataset_configuration(path: Path) -> dict[str, object]:
    with path.open("rb") as source:
        configuration = tomllib.load(source)
    dataset = configuration.get("training_dataset", {})
    metadata_path = dataset.get("metadata_path")
    return {
        "dataset_directory": Path(dataset["dataset_directory"]),
        "dataset_profile_path": Path(dataset["dataset_profile_path"]),
        "current_labels_path": Path(dataset["current_labels_path"]),
        "current_labels_manifest_path": Path(dataset["current_labels_manifest_path"]),
        "output_directory": Path(dataset["output_directory"]),
        "manifest_path": Path(dataset["manifest_path"]),
        "seed": str(dataset["seed"]),
        "train_fraction": float(dataset.get("train_fraction", 0.8)),
        "validation_fraction": float(dataset.get("validation_fraction", 0.1)),
        "current_iou_threshold": float(dataset.get("current_iou_threshold", 0.5)),
        "batch_size": int(dataset.get("batch_size", 8192)),
        "metadata_path": Path(metadata_path) if metadata_path else None,
    }


def _load_xenova_metadata_configuration(path: Path) -> dict[str, object]:
    with path.open("rb") as source:
        configuration = tomllib.load(source)
    metadata = configuration.get("xenova_metadata", {})
    return {
        "dataset_directory": Path(metadata["dataset_directory"]),
        "output_path": Path(metadata["output_path"]),
        "journal_path": Path(metadata["journal_path"]),
        "manifest_path": Path(metadata["manifest_path"]),
        "env_path": Path(metadata.get("env_path", ".env")),
        "api_key_environment_variable": str(
            metadata.get("api_key_environment_variable", "YOUTUBE_DATA_API_KEY")
        ),
        "batch_size": int(metadata.get("batch_size", 50)),
        "max_api_requests_per_run": int(metadata.get("max_api_requests_per_run", 2000)),
        "request_delay_seconds": float(metadata.get("request_delay_seconds", 0.1)),
    }


def _load_scriptsmith_dataset_configuration(path: Path) -> dict[str, object]:
    with path.open("rb") as source:
        configuration = tomllib.load(source)
    dataset = configuration.get("scriptsmith_dataset", {})
    provenance_path = dataset.get("provenance_path")
    return {
        "subtitles_directory": Path(dataset["subtitles_directory"]),
        "metadata_directory": Path(dataset["metadata_directory"]),
        "mirror_path": Path(dataset["mirror_path"]),
        "exclude_split_directories": [
            Path(value) for value in dataset.get("exclude_split_directories", [])
        ],
        "output_directory": Path(dataset["output_directory"]),
        "manifest_path": Path(dataset["manifest_path"]),
        "encoder": str(dataset["encoder"]),
        "encoder_revision": str(dataset["encoder_revision"]),
        "max_length": int(dataset.get("max_length", 1024)),
        "overlap_tokens": int(dataset.get("overlap_tokens", 128)),
        "seed": str(dataset["seed"]),
        "train_fraction": float(dataset.get("train_fraction", 0.8)),
        "validation_fraction": float(dataset.get("validation_fraction", 0.1)),
        "language_prefixes": list(dataset.get("language_prefixes", ["en"])),
        "positive_categories": list(dataset.get("positive_categories", ["sponsor"])),
        "hard_negative_categories": list(
            dataset.get("hard_negative_categories", ["selfpromo", "interaction"])
        ),
        "ordinary_negative_windows_per_video": int(
            dataset.get("ordinary_negative_windows_per_video", 4)
        ),
        "maximum_video_duration_seconds": int(
            dataset.get("maximum_video_duration_seconds", 14400)
        ),
        "minimum_cues": int(dataset.get("minimum_cues", 10)),
        "ordinary_negative_weight": float(
            dataset.get("ordinary_negative_weight", 0.5)
        ),
        "maximum_videos": int(dataset.get("maximum_videos", 0)),
        "provenance_path": Path(provenance_path) if provenance_path else None,
    }


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="sponsor-detection")
    commands = parser.add_subparsers(dest="command", required=True)
    data_parser = commands.add_parser("data", help="Dataset operations")
    data_commands = data_parser.add_subparsers(dest="data_command", required=True)
    profile_parser = data_commands.add_parser("profile", help="Profile a SponsorBlock snapshot")
    profile_parser.add_argument(
        "--config",
        type=Path,
        default=Path("ml/sponsor_detection/config/profile.toml"),
    )
    profile_parser.add_argument("--input", type=Path, help="Override the configured CSV path")
    profile_parser.add_argument("--output", type=Path, help="Override the configured report path")
    profile_parser.add_argument(
        "--no-checksum",
        action="store_true",
        help="Skip the source SHA-256 pass",
    )
    labels_parser = data_commands.add_parser(
        "build-labels", help="Normalize SponsorBlock target annotations to Parquet"
    )
    labels_parser.add_argument(
        "--config",
        type=Path,
        default=Path("ml/sponsor_detection/config/labels.toml"),
    )
    pilot_parser = data_commands.add_parser(
        "sample-pilot", help="Select a deterministic transcript-acquisition pilot"
    )
    pilot_parser.add_argument(
        "--config",
        type=Path,
        default=Path("ml/sponsor_detection/config/pilot.toml"),
    )
    benchmark_parser = data_commands.add_parser(
        "sample-benchmark",
        help="Build a leakage-safe mixed full-video benchmark acquisition queue",
    )
    benchmark_parser.add_argument(
        "--config",
        type=Path,
        default=Path("ml/sponsor_detection/config/benchmark_pilot.toml"),
    )
    review_parser = data_commands.add_parser(
        "prepare-benchmark-review",
        help="Select a channel-disjoint mixed pilot and initialize review records",
    )
    review_parser.add_argument(
        "--config",
        type=Path,
        default=Path("ml/sponsor_detection/config/benchmark_review.toml"),
    )
    enrichment_parser = data_commands.add_parser(
        "enrich-benchmark-review",
        help="Attach full-transcript model predictions to benchmark review records",
    )
    enrichment_parser.add_argument(
        "--config",
        type=Path,
        default=Path("ml/sponsor_detection/config/benchmark_enrichment.toml"),
    )
    annotations_parser = data_commands.add_parser(
        "apply-benchmark-annotations",
        help="Validate and attach completed benchmark annotations",
    )
    annotations_parser.add_argument(
        "--config",
        type=Path,
        default=Path("ml/sponsor_detection/config/benchmark_annotations.toml"),
    )
    freeze_benchmark_parser = data_commands.add_parser(
        "freeze-benchmark",
        help="Validate reviewed annotations and freeze a hash-pinned benchmark",
    )
    freeze_benchmark_parser.add_argument(
        "--config",
        type=Path,
        default=Path("ml/sponsor_detection/config/benchmark_freeze.toml"),
    )
    transcripts_parser = data_commands.add_parser(
        "acquire-transcripts", help="Fetch a resumable transcript canary"
    )
    transcripts_parser.add_argument(
        "--config",
        type=Path,
        default=Path("ml/sponsor_detection/config/transcripts.toml"),
    )
    collection_parser = data_commands.add_parser(
        "collect-transcripts", help="Collect transcripts toward a validated target"
    )
    collection_parser.add_argument(
        "--config",
        type=Path,
        default=Path("ml/sponsor_detection/config/collection.toml"),
    )
    metadata_parser = data_commands.add_parser(
        "acquire-metadata", help="Fetch metadata for successful transcript videos"
    )
    metadata_parser.add_argument(
        "--config",
        type=Path,
        default=Path("ml/sponsor_detection/config/metadata.toml"),
    )
    feedback_parser = data_commands.add_parser(
        "import-feedback",
        help="Validate an exported Flow feedback journal for future training",
    )
    feedback_parser.add_argument("--input", type=Path, required=True)
    feedback_parser.add_argument("--output", type=Path, required=True)
    feedback_parser.add_argument("--manifest", type=Path, required=True)
    train_parser = commands.add_parser("train", help="Fine-tune a sponsor token extractor")
    train_parser.add_argument(
        "--config",
        type=Path,
        default=Path("ml/sponsor_detection/config/train_ettin_17m.toml"),
    )
    train_parser.add_argument("--smoke-test", action="store_true")
    train_parser.add_argument(
        "--resume-from-checkpoint",
        type=Path,
        help="Resume model, optimizer, scheduler, and trainer state from a saved checkpoint",
    )
    evaluate_parser = commands.add_parser(
        "evaluate",
        help="Evaluate decoded sponsor spans on the held-out dataset",
    )
    evaluate_parser.add_argument(
        "--config",
        type=Path,
        default=Path("ml/sponsor_detection/config/evaluate_ettin_17m.toml"),
    )
    calibrate_parser = commands.add_parser(
        "calibrate",
        help="Select a decoder confidence threshold on validation data",
    )
    calibrate_parser.add_argument(
        "--config",
        type=Path,
        default=Path("ml/sponsor_detection/config/calibrate_ettin_17m_replay.toml"),
    )
    disagreement_parser = commands.add_parser(
        "audit-disagreements",
        help="Compare two checkpoints on the same held-out windows",
    )
    disagreement_parser.add_argument(
        "--config",
        type=Path,
        default=Path("ml/sponsor_detection/config/audit_v1_v3_disagreements.toml"),
    )
    video_evaluation_parser = commands.add_parser(
        "evaluate-videos",
        help="Evaluate overlapping-window inference on complete video transcripts",
    )
    video_evaluation_parser.add_argument(
        "--config",
        type=Path,
        default=Path("ml/sponsor_detection/config/evaluate_full_videos.toml"),
    )
    freeze_parser = commands.add_parser(
        "freeze-release",
        help="Write a hash-pinned model release-candidate manifest",
    )
    freeze_parser.add_argument(
        "--config",
        type=Path,
        default=Path("ml/sponsor_detection/config/freeze_ettin_17m_replay.toml"),
    )
    android_export_parser = commands.add_parser(
        "export-android",
        help="Export, quantize, and validate an Android ONNX Runtime bundle",
    )
    android_export_parser.add_argument(
        "--config",
        type=Path,
        default=Path("ml/sponsor_detection/config/export_android_v1.toml"),
    )
    validate_tokenization_parser = data_commands.add_parser(
        "validate-tokenization",
        help="Validate every training window against a pinned tokenizer",
    )
    validate_tokenization_parser.add_argument(
        "--config",
        type=Path,
        default=Path("ml/sponsor_detection/config/train_ettin_17m.toml"),
    )
    xenova_parser = data_commands.add_parser(
        "profile-xenova", help="Profile the published Xenova SponsorBlock dataset"
    )
    xenova_parser.add_argument(
        "--config",
        type=Path,
        default=Path("ml/sponsor_detection/config/xenova_dataset.toml"),
    )
    build_dataset_parser = data_commands.add_parser(
        "build-training-dataset",
        help="Build validated leakage-safe sponsor training splits",
    )
    build_dataset_parser.add_argument(
        "--config",
        type=Path,
        default=Path("ml/sponsor_detection/config/training_dataset.toml"),
    )
    corrections_parser = data_commands.add_parser(
        "apply-audit-corrections",
        help="Create a train-only weak-label revision from audited candidates",
    )
    corrections_parser.add_argument(
        "--config",
        type=Path,
        default=Path("ml/sponsor_detection/config/audit_corrections.toml"),
    )
    replay_parser = data_commands.add_parser(
        "build-replay-dataset",
        help="Build a small corrected-example plus deterministic replay dataset",
    )
    replay_parser.add_argument(
        "--config",
        type=Path,
        default=Path("ml/sponsor_detection/config/replay_dataset.toml"),
    )
    xenova_metadata_parser = data_commands.add_parser(
        "acquire-xenova-metadata",
        help="Fetch official metadata for Xenova dataset videos",
    )
    xenova_metadata_parser.add_argument(
        "--config",
        type=Path,
        default=Path("ml/sponsor_detection/config/xenova_metadata.toml"),
    )
    scriptsmith_parser = data_commands.add_parser(
        "build-scriptsmith-dataset",
        help="Build training splits from the ScriptSmith auto-caption dataset",
    )
    scriptsmith_parser.add_argument(
        "--config",
        type=Path,
        default=Path("ml/sponsor_detection/config/scriptsmith_dataset.toml"),
    )
    return parser


def _print_progress(rows: int, elapsed_seconds: float) -> None:
    print(
        f"profiled {rows:,} rows in {elapsed_seconds:.1f}s",
        file=sys.stderr,
        flush=True,
    )


def _print_label_progress(rows: int, target_rows: int, elapsed_seconds: float) -> None:
    print(
        f"scanned {rows:,} rows; normalized {target_rows:,} target rows "
        f"in {elapsed_seconds:.1f}s",
        file=sys.stderr,
        flush=True,
    )


def _print_pilot_progress(rows: int, elapsed_seconds: float) -> None:
    print(f"sampled from {rows:,} label rows in {elapsed_seconds:.1f}s", file=sys.stderr)


def _print_transcript_progress(index: int, total: int, status: str) -> None:
    print(f"transcript {index}/{total}: {status}", file=sys.stderr, flush=True)


def _print_collection_progress(
    attempt: int,
    attempt_limit: int,
    total_successes: int,
    status: str,
) -> None:
    print(
        f"collection {attempt}/{attempt_limit}: {status}; total successes={total_successes}",
        file=sys.stderr,
        flush=True,
    )


def _print_stage_progress(message: str) -> None:
    print(message, file=sys.stderr, flush=True)


def _print_metadata_progress(request: int, request_limit: int, attempted: int) -> None:
    if request != 1 and request != request_limit and request % 25:
        return
    print(
        f"metadata request {request}/{request_limit}; attempted videos={attempted:,}",
        file=sys.stderr,
        flush=True,
    )


def main(arguments: Sequence[str] | None = None) -> int:
    options = _build_parser().parse_args(arguments)
    if options.command == "train":
        from sponsor_detection.train import train_from_config

        report = train_from_config(
            options.config,
            smoke_test=options.smoke_test,
            resume_from_checkpoint=options.resume_from_checkpoint,
        )
        print(
            f"completed {'smoke test' if options.smoke_test else 'training'} "
            f"for {report['encoder']}"
        )
        return 0
    if options.command == "data" and options.data_command == "profile":
        input_path, output_path, compute_sha256, progress_every, policy = (
            _load_profile_configuration(options.config)
        )
        input_path = options.input or input_path
        output_path = options.output or output_path
        if not input_path.is_file():
            print(f"input CSV does not exist: {input_path}", file=sys.stderr)
            return 2
        report = profile_csv(
            input_path,
            policy,
            compute_sha256=compute_sha256 and not options.no_checksum,
            progress_every_rows=progress_every,
            progress_callback=_print_progress,
        )
        write_json_atomic(output_path, report)
        totals = report["totals"]
        print(f"wrote profile: {output_path}")
        print(
            "eligible target labels: "
            f"{totals['eligible_target_rows']:,} across approximately "
            f"{totals['approximate_unique_eligible_target_videos']:,} videos"
        )
        return 0
    if options.command == "data" and options.data_command == "build-labels":
        from sponsor_detection.data.labels import build_label_parquet

        (
            input_path,
            profile_path,
            output_path,
            manifest_path,
            batch_size,
            compression,
            progress_every,
            policy,
        ) = _load_label_configuration(options.config)
        report = build_label_parquet(
            input_path,
            profile_path,
            output_path,
            manifest_path,
            policy,
            batch_size=batch_size,
            compression=compression,
            progress_every_rows=progress_every,
            progress_callback=_print_label_progress,
        )
        print(f"wrote labels: {report['output']['path']}")
        print(f"wrote manifest: {manifest_path}")
        return 0
    if options.command == "data" and options.data_command == "sample-pilot":
        from sponsor_detection.data.pilot import build_transcript_pilot

        (
            labels_path,
            labels_manifest_path,
            output_path,
            manifest_path,
            sample_size,
            seed,
            batch_size,
            progress_every,
        ) = _load_pilot_configuration(options.config)
        report = build_transcript_pilot(
            labels_path,
            labels_manifest_path,
            output_path,
            manifest_path,
            sample_size=sample_size,
            seed=seed,
            batch_size=batch_size,
            progress_every_rows=progress_every,
            progress_callback=_print_pilot_progress,
        )
        print(f"wrote transcript pilot: {report['output']['path']}")
        print(f"wrote manifest: {manifest_path}")
        return 0
    if options.command == "data" and options.data_command == "sample-benchmark":
        from sponsor_detection.data.benchmark import build_mixed_benchmark_candidates

        configuration = _load_benchmark_configuration(options.config)
        report = build_mixed_benchmark_candidates(
            **configuration,
            progress_callback=_print_pilot_progress,
        )
        print(f"wrote mixed benchmark queue: {report['output']['path']}")
        print(f"wrote manifest: {configuration['manifest_path']}")
        print(json.dumps(report["counts"]["queue_by_class"], sort_keys=True))
        return 0
    if options.command == "data" and options.data_command == "prepare-benchmark-review":
        from sponsor_detection.data.benchmark_review import prepare_benchmark_review

        configuration = _load_benchmark_review_configuration(options.config)
        report = prepare_benchmark_review(**configuration)
        print(f"wrote benchmark review records: {report['output']['path']}")
        print(f"wrote manifest: {configuration['manifest_path']}")
        print(json.dumps(report["counts"], indent=2, sort_keys=True))
        return 0 if report["status"] == "annotation_pending" else 5
    if options.command == "data" and options.data_command == "freeze-benchmark":
        from sponsor_detection.data.benchmark_review import freeze_reviewed_benchmark

        configuration = _load_benchmark_freeze_configuration(options.config)
        report = freeze_reviewed_benchmark(**configuration)
        print(f"wrote frozen benchmark: {report['output']['path']}")
        print(f"wrote manifest: {configuration['manifest_path']}")
        print(json.dumps(report["counts"], indent=2, sort_keys=True))
        return 0
    if options.command == "data" and options.data_command == "acquire-transcripts":
        from sponsor_detection.data.transcripts import acquire_transcript_canary

        (
            pilot_path,
            pilot_manifest_path,
            output_directory,
            report_path,
            languages,
            limit,
            request_delay_seconds,
            stop_on_block,
            provider,
        ) = _load_transcript_configuration(options.config)
        report = acquire_transcript_canary(
            pilot_path,
            pilot_manifest_path,
            output_directory,
            report_path,
            languages=languages,
            limit=limit,
            request_delay_seconds=request_delay_seconds,
            stop_on_block=stop_on_block,
            client=_transcript_client(provider),
            progress_callback=_print_transcript_progress,
        )
        print(f"wrote transcript canary report: {report_path}")
        return 3 if report["stopped_for_block"] else 0
    if options.command == "data" and options.data_command == "collect-transcripts":
        from sponsor_detection.data.transcripts import collect_transcripts

        (
            pilot_path,
            pilot_manifest_path,
            output_directory,
            report_path,
            languages,
            target_successes,
            max_attempts_per_run,
            minimum_delay,
            maximum_delay,
            max_transient_attempts,
            stop_on_block,
            block_cooldown_seconds,
            provider,
            env_path,
            api_key_environment_variable,
        ) = _load_collection_configuration(options.config)
        report = collect_transcripts(
            pilot_path,
            pilot_manifest_path,
            output_directory,
            report_path,
            languages=languages,
            target_successes=target_successes,
            max_attempts_per_run=max_attempts_per_run,
            minimum_request_delay_seconds=minimum_delay,
            maximum_request_delay_seconds=maximum_delay,
            max_transient_attempts_per_video=max_transient_attempts,
            stop_on_block=stop_on_block,
            block_cooldown_seconds=block_cooldown_seconds,
            provider_name=provider,
            client=_transcript_client(
                provider,
                env_path=env_path,
                api_key_environment_variable=api_key_environment_variable,
            ),
            progress_callback=_print_collection_progress,
        )
        print(f"wrote transcript collection report: {report_path}")
        return 3 if report["stopped_for_block"] else 0
    if options.command == "data" and options.data_command == "acquire-metadata":
        from sponsor_detection.data.metadata import (
            MetadataApiError,
            acquire_video_metadata,
            load_environment_value,
        )

        (
            transcript_directory,
            output_path,
            manifest_path,
            env_path,
            api_key_variable,
            batch_size,
            limit,
            max_api_requests,
        ) = _load_metadata_configuration(options.config)
        api_key = load_environment_value(env_path, api_key_variable)
        try:
            report = acquire_video_metadata(
                transcript_directory,
                output_path,
                manifest_path,
                api_key=api_key,
                batch_size=batch_size,
                limit=limit,
                max_api_requests=max_api_requests,
            )
        except MetadataApiError as error:
            print(str(error), file=sys.stderr)
            return 4
        print(f"wrote metadata: {report['output']['path']}")
        print(f"wrote manifest: {manifest_path}")
        return 0
    if options.command == "data" and options.data_command == "import-feedback":
        from sponsor_detection.data.feedback import import_feedback_jsonl

        report = import_feedback_jsonl(
            options.input,
            options.output,
            options.manifest,
        )
        print(f"wrote validated feedback: {report['output']['path']}")
        print(f"wrote manifest: {options.manifest}")
        return 0
    if options.command == "data" and options.data_command == "enrich-benchmark-review":
        from sponsor_detection.data.benchmark_enrichment import (
            enrich_benchmark_review_from_config,
        )

        report = enrich_benchmark_review_from_config(
            options.config,
            progress_callback=lambda model_index,
            model_count,
            completed,
            total,
            video_id: print(
                f"enriched model {model_index}/{model_count}, "
                f"video {completed}/{total}: {video_id}",
                file=sys.stderr,
                flush=True,
            ),
        )
        print(f"updated benchmark review: {report['output']['path']}")
        print(
            f"attached {report['enrichment']['predicted_spans']} predicted spans "
            f"across {report['enrichment']['videos']} videos"
        )
        return 0
    if options.command == "data" and options.data_command == "apply-benchmark-annotations":
        from sponsor_detection.data.benchmark_annotations import (
            apply_benchmark_annotations_from_config,
        )

        report = apply_benchmark_annotations_from_config(options.config)
        print(f"updated benchmark review: {report['output']['path']}")
        print(
            json.dumps(
                {
                    "reviewed_by_class": report["annotations"]["reviewed_by_class"],
                    "sponsor_intervals": report["annotations"]["sponsor_intervals"],
                },
                indent=2,
                sort_keys=True,
            )
        )
        return 0
    if options.command == "data" and options.data_command == "profile-xenova":
        from sponsor_detection.data.xenova_dataset import profile_xenova_dataset

        dataset_directory, report_path, revision, checksums, current_labels_path = (
            _load_xenova_configuration(options.config)
        )
        report = profile_xenova_dataset(
            dataset_directory,
            report_path,
            revision=revision,
            expected_sha256=checksums,
            current_labels_path=current_labels_path,
            progress_callback=_print_stage_progress,
        )
        print(f"wrote Xenova dataset profile: {report_path}")
        print(
            f"rows: {report['totals']['rows']:,}; "
            f"videos: {report['totals']['unique_video_ids']:,}"
        )
        return 0
    if options.command == "data" and options.data_command == "build-training-dataset":
        from sponsor_detection.data.training_dataset import build_training_dataset

        configuration = _load_training_dataset_configuration(options.config)
        manifest = build_training_dataset(
            **configuration,
            progress_callback=_print_stage_progress,
        )
        print(f"wrote training dataset manifest: {configuration['manifest_path']}")
        for split, output in manifest["outputs"].items():
            print(f"{split}: {output['rows']:,} rows across {output['videos']:,} videos")
        return 0
    if options.command == "data" and options.data_command == "build-scriptsmith-dataset":
        from sponsor_detection.data.scriptsmith_dataset import build_scriptsmith_dataset

        configuration = _load_scriptsmith_dataset_configuration(options.config)
        manifest = build_scriptsmith_dataset(
            **configuration,
            progress_callback=_print_stage_progress,
        )
        print(f"wrote ScriptSmith dataset manifest: {configuration['manifest_path']}")
        for split, output in manifest["outputs"].items():
            print(f"{split}: {output['rows']:,} rows across {output['videos']:,} videos")
        return 0
    if options.command == "data" and options.data_command == "apply-audit-corrections":
        from sponsor_detection.data.audit_corrections import apply_audit_corrections

        with options.config.open("rb") as source:
            configuration = tomllib.load(source)["audit_corrections"]
        manifest = apply_audit_corrections(
            **{
                key: (
                    Path(value)
                    if key.endswith("_directory") or key.endswith("_path")
                    else value
                )
                for key, value in configuration.items()
            }
        )
        print(f"wrote corrected training manifest: {configuration['output_manifest_path']}")
        print(f"corrected train rows: {manifest['corrections']['rows']}")
        return 0
    if options.command == "data" and options.data_command == "build-replay-dataset":
        from sponsor_detection.data.replay_dataset import build_replay_dataset

        with options.config.open("rb") as source:
            configuration = tomllib.load(source)["replay_dataset"]
        path_keys = {
            "source_directory",
            "weak_source_directory",
            "output_directory",
            "source_manifest_path",
            "weak_source_manifest_path",
            "output_manifest_path",
        }
        arguments = {
            key: Path(value) if key in path_keys else value
            for key, value in configuration.items()
        }
        manifest = build_replay_dataset(**arguments)
        print(f"wrote replay dataset manifest: {configuration['output_manifest_path']}")
        print(f"train rows: {manifest['selection']['total_rows']}")
        return 0
    if options.command == "data" and options.data_command == "validate-tokenization":
        from sponsor_detection.train import validate_tokenization_from_config

        report = validate_tokenization_from_config(options.config)
        print(json.dumps(report, indent=2, sort_keys=True))
        return 0
    if options.command == "evaluate":
        from sponsor_detection.evaluation import evaluate_from_config

        report = evaluate_from_config(
            options.config,
            progress_callback=lambda completed, total, elapsed: print(
                f"evaluated {completed:,}/{total:,} windows in {elapsed:.1f}s",
                file=sys.stderr,
                flush=True,
            ),
        )
        print(json.dumps(report, indent=2, sort_keys=True))
        return 0
    if options.command == "calibrate":
        from sponsor_detection.calibration import calibrate_from_config

        report = calibrate_from_config(
            options.config,
            progress_callback=lambda completed, total, elapsed: print(
                f"calibrated {completed:,}/{total:,} windows in {elapsed:.1f}s",
                file=sys.stderr,
                flush=True,
            ),
        )
        print(json.dumps(report["selected"], indent=2, sort_keys=True))
        return 0
    if options.command == "audit-disagreements":
        from sponsor_detection.disagreement import audit_from_config

        report = audit_from_config(
            options.config,
            progress_callback=lambda model, completed, total, elapsed: print(
                f"audited {model}: {completed:,}/{total:,} windows in {elapsed:.1f}s",
                file=sys.stderr,
                flush=True,
            ),
        )
        print(
            json.dumps(
                {
                    "presence_disagreements": report["presence_disagreements"],
                    "coverage_comparison": report["coverage_comparison"],
                },
                indent=2,
                sort_keys=True,
            )
        )
        return 0
    if options.command == "evaluate-videos":
        from sponsor_detection.video_evaluation import evaluate_full_videos_from_config

        report = evaluate_full_videos_from_config(
            options.config,
            progress_callback=lambda completed, total, video_id: print(
                f"evaluated video {completed}/{total}: {video_id}",
                file=sys.stderr,
                flush=True,
            ),
        )
        print(
            json.dumps(
                {
                    "video_presence": report["video_presence"],
                    "span_metrics_by_temporal_iou": report[
                        "span_metrics_by_temporal_iou"
                    ],
                    "temporal_coverage": report["temporal_coverage"],
                    "boundary_error_ms_at_iou_0.5": report[
                        "boundary_error_ms_at_iou_0.5"
                    ],
                },
                indent=2,
                sort_keys=True,
            )
        )
        return 0
    if options.command == "freeze-release":
        from sponsor_detection.release import freeze_release_from_config

        manifest = freeze_release_from_config(options.config)
        print(
            json.dumps(
                {
                    "name": manifest["name"],
                    "status": manifest["status"],
                    "model_sha256": manifest["checkpoint"]["artifacts"]["model"][
                        "sha256"
                    ],
                    "confidence_threshold": manifest["decoder"][
                        "confidence_threshold"
                    ],
                },
                indent=2,
                sort_keys=True,
            )
        )
        return 0
    if options.command == "export-android":
        from sponsor_detection.android_export import export_android_bundle_from_config

        report = export_android_bundle_from_config(
            options.config,
            progress_callback=lambda phase: print(
                f"android export: {phase}", file=sys.stderr, flush=True
            ),
        )
        print(
            json.dumps(
                {
                    "status": report["status"],
                    "package_bytes": report["package"]["bytes"],
                    "gates": report["validation"]["gates"],
                    "int8_span_f1_at_iou_0.5": report["validation"][
                        "int8_span_f1_at_iou_0.5"
                    ],
                },
                indent=2,
                sort_keys=True,
            )
        )
        return 0 if report["status"] == "validated" else 5
    if options.command == "data" and options.data_command == "acquire-xenova-metadata":
        from sponsor_detection.data.metadata import (
            MetadataApiError,
            acquire_metadata_for_video_ids,
            load_environment_value,
        )
        from sponsor_detection.data.xenova_dataset import xenova_video_ids

        configuration = _load_xenova_metadata_configuration(options.config)
        api_key = load_environment_value(
            configuration.pop("env_path"),
            configuration.pop("api_key_environment_variable"),
        )
        video_ids = xenova_video_ids(configuration.pop("dataset_directory"))
        try:
            report = acquire_metadata_for_video_ids(
                video_ids,
                api_key=api_key,
                progress_callback=_print_metadata_progress,
                **configuration,
            )
        except MetadataApiError as error:
            print(str(error), file=sys.stderr)
            return 4
        print(f"wrote Xenova metadata: {configuration['output_path']}")
        return 0 if report["complete"] else 5
    return 2
