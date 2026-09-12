from __future__ import annotations

import fcntl
import json
import os
import subprocess
import sys
from datetime import UTC, datetime
from pathlib import Path

from sponsor_detection.data.profile import sha256_file, write_json_atomic


ROOT = Path(__file__).resolve().parents[3]
BASE = ROOT / "ml/sponsor_detection"
REPORTS = BASE / "reports"
STAGES = (
    ("train", "train_ettin_17m_v4.toml"),
    ("calibrate", "calibrate_ettin_17m_v4.toml"),
    ("evaluate", "evaluate_ettin_17m_v4.toml"),
    ("evaluate-videos", "evaluate_mixed_pilot_v4.toml"),
)


def load_report(name: str) -> dict:
    return json.loads((REPORTS / name).read_text(encoding="utf-8"))


def write_comparison() -> None:
    comparison = {"status": "candidate_requires_review", "models": {}}
    for version, prefix in (("v3", "ettin_17m_replay"), ("v4", "ettin_17m_v4")):
        window = load_report(f"{prefix}_calibrated_local_span_evaluation.json")
        video = load_report(f"{prefix}_mixed_pilot_evaluation.json")
        calibration = load_report(f"{prefix}_calibration.json")
        comparison["models"][version] = {
            "window_dataset": window["dataset"],
            "video_dataset": video["dataset"],
            "validation_operating_point": calibration["selected"],
            "test_window_presence": window["window_presence"],
            "test_span_iou_05": window["span_metrics_by_character_iou"]["0.5"],
            "full_video_presence": video["video_presence"],
            "full_video_span_iou_05": video["span_metrics_by_temporal_iou"]["0.5"],
        }
    old, new = (comparison["models"][version] for version in ("v3", "v4"))
    if old["window_dataset"]["test_sha256"] != new["window_dataset"]["test_sha256"]:
        raise ValueError("window evaluation datasets differ")
    for key in ("benchmark_sha256", "transcript_set_sha256"):
        if old["video_dataset"][key] != new["video_dataset"][key]:
            raise ValueError("full-video evaluation datasets differ")
    comparison["v4_minus_v3"] = {
        group: {
            metric: new[group][metric] - old[group][metric]
            for metric in ("precision", "recall", "f1", "false_positive", "false_negative")
        }
        for group in (
            "test_window_presence", "test_span_iou_05",
            "full_video_presence", "full_video_span_iou_05",
        )
    }
    write_json_atomic(REPORTS / "ettin_17m_v4_comparison.json", comparison)


def main() -> None:
    os.chdir(ROOT)
    status_path = REPORTS / "ettin_17m_v4_run.json"
    status = {
        "started_at": datetime.now(UTC).isoformat(),
        "status": "running",
        "experiment": "v4: one low-learning-rate epoch over the full audited train split",
        "initial_checkpoint": "ettin_17m_sponsor_v3_replay",
        "initial_model_sha256": sha256_file(
            BASE / "checkpoints/ettin_17m_sponsor_v3_replay/model.safetensors"
        ),
        "configuration_sha256": {
            name: sha256_file(BASE / "config" / name) for _, name in STAGES
        },
        "completed_stages": [],
        "automatic_promotion": False,
    }
    environment = dict(os.environ, HF_HUB_OFFLINE="1", HF_DATASETS_OFFLINE="1",
                       TOKENIZERS_PARALLELISM="false", PYTHONUNBUFFERED="1")
    with (REPORTS / "ettin_17m_v4.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        output = BASE / "checkpoints/ettin_17m_sponsor_v4_full"
        if output.exists() and any(output.iterdir()):
            raise FileExistsError("v4 output exists; inspect it before resuming explicitly")
        try:
            for command, name in STAGES:
                status.update(stage=command, updated_at=datetime.now(UTC).isoformat())
                write_json_atomic(status_path, status)
                print(json.dumps(status), flush=True)
                subprocess.run(
                    [sys.executable, "-m", "sponsor_detection", command,
                     "--config", str(BASE / "config" / name)],
                    env=environment,
                    check=True,
                )
                status["completed_stages"].append(command)
            write_comparison()
            status.update(status="complete", stage="comparison")
        except (Exception, KeyboardInterrupt) as error:
            status.update(status="failed", error_type=type(error).__name__)
            raise
        finally:
            status["updated_at"] = datetime.now(UTC).isoformat()
            write_json_atomic(status_path, status)
            print(json.dumps(status), flush=True)


if __name__ == "__main__":
    main()
