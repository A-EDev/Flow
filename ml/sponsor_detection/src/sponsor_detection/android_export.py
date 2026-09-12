from __future__ import annotations

import importlib.metadata
import json
import shutil
import tomllib
from pathlib import Path

from sponsor_detection.data.profile import sha256_file, write_json_atomic
from sponsor_detection.inference.onnx_pipeline import OnnxFullTranscriptSponsorDetector
from sponsor_detection.inference.windowing import normalize_cue_text
from sponsor_detection.video_evaluation import (
    evaluate_full_videos_with_detector,
    load_frozen_benchmark,
)


TOKENIZER_GOLDEN_INPUTS = (
    "This episode is brought to you by Acme.",
    "Visit https://example.com/deal and use code FLOW20.",
    "Save 20% before 12:30 today!",
    "I’m testing contractions: we're, you've, they'll.",
    "Café naïve résumé — Unicode stays deterministic.",
    "Emoji boundary 🚀 sponsor message 🎧 end.",
    "Multiple   spaces\tand\nnewlines are normalized.",
)


def compare_evaluation_reports(
    baseline: dict[str, object], candidate: dict[str, object]
) -> dict[str, object]:
    baseline_by_id = {record["video_id"]: record for record in baseline["videos"]}
    candidate_by_id = {record["video_id"]: record for record in candidate["videos"]}
    if baseline_by_id.keys() != candidate_by_id.keys():
        raise ValueError("evaluation reports contain different video sets")
    differing_videos = []
    interval_matches = 0
    presence_matches = 0
    for video_id, baseline_record in baseline_by_id.items():
        candidate_record = candidate_by_id[video_id]
        baseline_intervals = [
            (span["start_ms"], span["end_ms"])
            for span in baseline_record["predicted_spans"]
        ]
        candidate_intervals = [
            (span["start_ms"], span["end_ms"])
            for span in candidate_record["predicted_spans"]
        ]
        same_intervals = baseline_intervals == candidate_intervals
        same_presence = bool(baseline_intervals) == bool(candidate_intervals)
        interval_matches += int(same_intervals)
        presence_matches += int(same_presence)
        if not same_intervals:
            differing_videos.append(
                {
                    "video_id": video_id,
                    "baseline": baseline_intervals,
                    "candidate": candidate_intervals,
                }
            )
    total = len(baseline_by_id)
    return {
        "videos": total,
        "exact_interval_matches": interval_matches,
        "presence_matches": presence_matches,
        "exact_interval_match_rate": interval_matches / total if total else 0.0,
        "presence_match_rate": presence_matches / total if total else 0.0,
        "differing_videos": differing_videos,
    }


def _export_fp32(checkpoint_path: Path, output_path: Path, *, opset: int) -> None:
    import onnx
    import torch
    from transformers import AutoModelForTokenClassification

    class LogitsOnly(torch.nn.Module):
        def __init__(self, model) -> None:
            super().__init__()
            self.model = model

        def forward(self, input_ids, attention_mask):
            return self.model(
                input_ids=input_ids, attention_mask=attention_mask
            ).logits

    model = LogitsOnly(
        AutoModelForTokenClassification.from_pretrained(checkpoint_path)
    ).eval()
    input_ids = torch.tensor([[50281, 100, 101, 50282]], dtype=torch.long)
    attention_mask = torch.ones_like(input_ids)
    torch.onnx.export(
        model,
        (input_ids, attention_mask),
        output_path,
        input_names=["input_ids", "attention_mask"],
        output_names=["logits"],
        dynamic_shapes=(
            {0: "batch", 1: "sequence"},
            {0: "batch", 1: "sequence"},
        ),
        opset_version=opset,
        dynamo=True,
        external_data=False,
    )
    onnx.checker.check_model(onnx.load(output_path))


def _quantize(fp32_path: Path, int8_path: Path) -> None:
    from onnxruntime.quantization import QuantType, quantize_dynamic

    quantize_dynamic(
        fp32_path,
        int8_path,
        weight_type=QuantType.QInt8,
        per_channel=True,
        reduce_range=False,
        op_types_to_quantize=["Gather"],
    )


def _convert_to_ort(int8_path: Path) -> tuple[Path, Path]:
    from onnxruntime.tools.convert_onnx_models_to_ort import (
        OptimizationStyle,
        convert_onnx_models_to_ort,
    )

    convert_onnx_models_to_ort(
        int8_path,
        output_dir=int8_path.parent,
        optimization_styles=[OptimizationStyle.Fixed],
        enable_type_reduction=True,
    )
    ort_path = int8_path.with_suffix(".ort")
    operator_config_path = int8_path.with_name(
        f"{int8_path.stem}.required_operators_and_types.config"
    )
    if not ort_path.is_file() or not operator_config_path.is_file():
        raise RuntimeError("ONNX Runtime did not produce the expected Android artifacts")
    return ort_path, operator_config_path


def _write_tokenizer_goldens(checkpoint_path: Path, output_path: Path) -> None:
    from transformers import AutoTokenizer

    tokenizer = AutoTokenizer.from_pretrained(checkpoint_path)
    records = []
    for text in TOKENIZER_GOLDEN_INPUTS:
        normalized = normalize_cue_text(text)
        encoded = tokenizer(
            normalized,
            add_special_tokens=True,
            return_offsets_mapping=True,
        )
        records.append(
            {
                "input": text,
                "normalized": normalized,
                "input_ids": [int(value) for value in encoded["input_ids"]],
                "attention_mask": [
                    int(value) for value in encoded["attention_mask"]
                ],
                "offset_mapping": [
                    [int(start), int(end)]
                    for start, end in encoded["offset_mapping"]
                ],
            }
        )
    write_json_atomic(output_path, {"schema_version": 1, "cases": records})


def _artifact(path: Path) -> dict[str, object]:
    return {
        "path": str(path),
        "bytes": path.stat().st_size,
        "sha256": sha256_file(path),
    }


def export_android_bundle_from_config(
    path: Path, *, progress_callback=None
) -> dict[str, object]:
    with path.open("rb") as source:
        configuration = tomllib.load(source)["android_export"]
    checkpoint_path = Path(configuration["checkpoint_path"])
    benchmark_path = Path(configuration["benchmark_path"])
    benchmark_manifest_path = Path(configuration["benchmark_manifest_path"])
    baseline_report_path = Path(configuration["baseline_report_path"])
    output_directory = Path(configuration["output_directory"])
    report_path = Path(configuration["report_path"])
    feedback_schema_path = Path(configuration["feedback_schema_path"])
    confidence_threshold = float(configuration.get("confidence_threshold", 0.0))
    max_length = int(configuration.get("max_length", 768))
    overlap_tokens = int(configuration.get("overlap_tokens", 128))
    batch_size = int(configuration.get("batch_size", 16))
    merge_gap_characters = int(configuration.get("merge_gap_characters", 24))
    merge_gap_ms = int(configuration.get("merge_gap_ms", 1500))
    maximum_f1_drop = float(configuration.get("maximum_span_f1_drop", 0.01))
    output_directory.mkdir(parents=True, exist_ok=True)
    android_directory = output_directory / "android"
    android_directory.mkdir(parents=True, exist_ok=True)
    fp32_path = output_directory / "sponsor_detector_v1.fp32.onnx"
    int8_path = output_directory / "sponsor_detector_v1.int8.onnx"

    if progress_callback:
        progress_callback("export_fp32")
    _export_fp32(checkpoint_path, fp32_path, opset=int(configuration.get("opset", 18)))
    if progress_callback:
        progress_callback("quantize_int8")
    _quantize(fp32_path, int8_path)
    if progress_callback:
        progress_callback("convert_ort")
    ort_path, operator_config_path = _convert_to_ort(int8_path)

    bundled_model_path = android_directory / "sponsor_detector_v1.int8.ort"
    bundled_operator_config_path = android_directory / "required_operators_and_types.config"
    shutil.copyfile(ort_path, bundled_model_path)
    shutil.copyfile(operator_config_path, bundled_operator_config_path)
    for name in ("tokenizer.json", "tokenizer_config.json", "config.json"):
        shutil.copyfile(checkpoint_path / name, android_directory / name)
    tokenizer_goldens_path = android_directory / "tokenizer_goldens.json"
    _write_tokenizer_goldens(checkpoint_path, tokenizer_goldens_path)
    bundled_feedback_schema_path = android_directory / "sponsor_feedback_v1.schema.json"
    shutil.copyfile(feedback_schema_path, bundled_feedback_schema_path)

    transcript_paths, expected_by_video, dataset_provenance = load_frozen_benchmark(
        benchmark_path, benchmark_manifest_path
    )
    baseline = json.loads(baseline_report_path.read_text(encoding="utf-8"))
    if baseline["dataset"]["benchmark_sha256"] != dataset_provenance["benchmark_sha256"]:
        raise ValueError("baseline report does not match the frozen benchmark")
    checkpoint_sha256 = sha256_file(checkpoint_path / "model.safetensors")
    if baseline["checkpoint"]["model_sha256"] != checkpoint_sha256:
        raise ValueError("baseline report does not match the v1 checkpoint")

    evaluation_reports = {}
    for name, model_path in (("fp32", fp32_path), ("int8", bundled_model_path)):
        if progress_callback:
            progress_callback(f"evaluate_{name}")
        detector = OnnxFullTranscriptSponsorDetector(
            model_path,
            checkpoint_path,
            confidence_threshold=confidence_threshold,
            max_length=max_length,
            overlap_tokens=overlap_tokens,
            batch_size=batch_size,
            merge_gap_characters=merge_gap_characters,
            merge_gap_ms=merge_gap_ms,
        )
        evaluation_path = report_path.with_name(
            f"{report_path.stem}_{name}_evaluation.json"
        )
        evaluation_reports[name] = evaluate_full_videos_with_detector(
            transcript_paths,
            expected_by_video,
            detector,
            dataset_provenance,
            checkpoint_provenance={
                "path": str(model_path),
                "model_sha256": sha256_file(model_path),
                "source_checkpoint_sha256": checkpoint_sha256,
            },
            decoder_config_path=None,
            report_path=evaluation_path,
            progress_callback=None,
        )

    fp32_parity = compare_evaluation_reports(baseline, evaluation_reports["fp32"])
    int8_parity = compare_evaluation_reports(baseline, evaluation_reports["int8"])
    baseline_f1 = float(baseline["span_metrics_by_temporal_iou"]["0.5"]["f1"])
    int8_f1 = float(
        evaluation_reports["int8"]["span_metrics_by_temporal_iou"]["0.5"]["f1"]
    )
    baseline_recall = float(baseline["video_presence"]["recall"])
    int8_recall = float(evaluation_reports["int8"]["video_presence"]["recall"])
    baseline_false_positives = int(baseline["video_presence"].get("false_positive", 0))
    int8_false_positives = int(
        evaluation_reports["int8"]["video_presence"].get("false_positive", 0)
    )
    gates = {
        "fp32_presence_exact": fp32_parity["presence_matches"] == fp32_parity["videos"],
        "fp32_intervals_exact": fp32_parity["exact_interval_matches"]
        == fp32_parity["videos"],
        "int8_span_f1_within_limit": baseline_f1 - int8_f1 <= maximum_f1_drop,
        "int8_video_recall_not_lower": int8_recall >= baseline_recall,
        "int8_false_positives_not_higher": int8_false_positives
        <= baseline_false_positives,
        "int8_presence_exact": int8_parity["presence_matches"]
        == int8_parity["videos"],
    }
    status = "validated" if all(gates.values()) else "failed"

    package_files = {
        "model": _artifact(bundled_model_path),
        "operator_config": _artifact(bundled_operator_config_path),
        "tokenizer": _artifact(android_directory / "tokenizer.json"),
        "tokenizer_config": _artifact(android_directory / "tokenizer_config.json"),
        "model_config": _artifact(android_directory / "config.json"),
        "tokenizer_goldens": _artifact(tokenizer_goldens_path),
        "feedback_schema": _artifact(bundled_feedback_schema_path),
    }
    manifest = {
        "schema_version": 1,
        "name": "ettin-17m-sponsor-v1-android-int8",
        "status": status,
        "source": {
            "checkpoint_path": str(checkpoint_path),
            "checkpoint_sha256": checkpoint_sha256,
            "base_model": "jhu-clsp/ettin-encoder-17m",
            "base_model_license": "MIT",
        },
        "runtime": {
            "format": "ORT",
            "execution_provider": "CPUExecutionProvider",
            "quantization": "embedding_int8_per_channel",
            "opset": int(configuration.get("opset", 18)),
        },
        "inference": {
            "labels": ["O", "B-SPONSOR", "I-SPONSOR", "L-SPONSOR", "U-SPONSOR"],
            "confidence_threshold": confidence_threshold,
            "max_length": max_length,
            "overlap_tokens": overlap_tokens,
            "merge_gap_characters": merge_gap_characters,
            "merge_gap_ms": merge_gap_ms,
        },
        "package": {
            "directory": str(android_directory),
            "bytes": sum(int(artifact["bytes"]) for artifact in package_files.values()),
            "files": package_files,
        },
        "validation": {
            "benchmark_sha256": dataset_provenance["benchmark_sha256"],
            "baseline_report": _artifact(baseline_report_path),
            "fp32_parity": fp32_parity,
            "int8_parity": int8_parity,
            "baseline_span_f1_at_iou_0.5": baseline_f1,
            "int8_span_f1_at_iou_0.5": int8_f1,
            "maximum_allowed_span_f1_drop": maximum_f1_drop,
            "gates": gates,
            "fp32_evaluation": evaluation_reports["fp32"],
            "int8_evaluation": evaluation_reports["int8"],
        },
        "toolchain": {
            package: importlib.metadata.version(package)
            for package in (
                "torch",
                "transformers",
                "onnx",
                "onnxruntime",
                "onnxscript",
            )
        },
    }
    write_json_atomic(android_directory / "manifest.json", manifest)
    write_json_atomic(report_path, manifest)
    return manifest
