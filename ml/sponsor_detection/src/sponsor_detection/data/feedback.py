from __future__ import annotations

import json
from collections import Counter
from datetime import UTC, datetime
from pathlib import Path

from sponsor_detection.data.benchmark import write_json_lines_atomic
from sponsor_detection.data.profile import sha256_file, write_json_atomic


VERDICTS = frozenset({"accepted", "rejected", "corrected", "missed"})
TRANSCRIPT_SOURCES = frozenset({"youtube", "offline_caption", "unknown"})


def _integer(value: object, name: str, *, minimum: int = 0) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < minimum:
        raise ValueError(f"{name} must be an integer >= {minimum}")
    return value


def _span(value: object, name: str, *, predicted: bool) -> dict[str, object]:
    if not isinstance(value, dict):
        raise ValueError(f"{name} must be an object")
    start_ms = _integer(value.get("start_ms"), f"{name}.start_ms")
    end_ms = _integer(value.get("end_ms"), f"{name}.end_ms")
    if end_ms < start_ms:
        raise ValueError(f"{name}.end_ms must not precede start_ms")
    result: dict[str, object] = {"start_ms": start_ms, "end_ms": end_ms}
    if predicted:
        confidence = value.get("confidence")
        if (
            isinstance(confidence, bool)
            or not isinstance(confidence, (int, float))
            or not 0 <= confidence <= 1
        ):
            raise ValueError(f"{name}.confidence must be between 0 and 1")
        result["confidence"] = float(confidence)
    return result


def _validated_record(value: object) -> dict[str, object]:
    if not isinstance(value, dict) or value.get("schema_version") != 1:
        raise ValueError("record must use sponsor feedback schema version 1")
    sample_id = value.get("sample_id")
    video_id = value.get("video_id")
    if not isinstance(sample_id, str) or not sample_id.strip():
        raise ValueError("sample_id must be a non-empty string")
    if not isinstance(video_id, str) or not video_id.strip():
        raise ValueError("video_id must be a non-empty string")
    _integer(value.get("created_at_epoch_ms"), "created_at_epoch_ms")

    model = value.get("model")
    if not isinstance(model, dict):
        raise ValueError("model must be an object")
    model_name, model_sha256 = model.get("name"), model.get("sha256")
    if not isinstance(model_name, str) or not model_name.strip():
        raise ValueError("model.name must be a non-empty string")
    if (
        not isinstance(model_sha256, str)
        or len(model_sha256) != 64
        or any(character not in "0123456789abcdef" for character in model_sha256)
    ):
        raise ValueError("model.sha256 must be a lowercase SHA-256 digest")

    transcript = value.get("transcript")
    if not isinstance(transcript, dict):
        raise ValueError("transcript must be an object")
    if transcript.get("source") not in TRANSCRIPT_SOURCES:
        raise ValueError("transcript.source is unsupported")
    language_tag = transcript.get("language_tag")
    if not isinstance(language_tag, str) or not language_tag.strip():
        raise ValueError("transcript.language_tag must be a non-empty string")
    if not isinstance(transcript.get("is_auto_generated"), bool):
        raise ValueError("transcript.is_auto_generated must be a boolean")
    cues = transcript.get("cues")
    if not isinstance(cues, list) or not cues:
        raise ValueError("transcript.cues must be a non-empty array")
    for index, cue in enumerate(cues):
        if not isinstance(cue, dict):
            raise ValueError(f"transcript.cues[{index}] must be an object")
        start_ms = _integer(cue.get("start_ms"), f"transcript.cues[{index}].start_ms")
        end_ms = _integer(cue.get("end_ms"), f"transcript.cues[{index}].end_ms")
        if end_ms < start_ms:
            raise ValueError(f"transcript.cues[{index}].end_ms must not precede start_ms")
        if not isinstance(cue.get("text"), str) or not cue["text"].strip():
            raise ValueError(f"transcript.cues[{index}].text must be non-empty")

    prediction = value.get("prediction")
    if not isinstance(prediction, dict) or not isinstance(prediction.get("spans"), list):
        raise ValueError("prediction.spans must be an array")
    predicted_spans = [
        _span(span, f"prediction.spans[{index}]", predicted=True)
        for index, span in enumerate(prediction["spans"])
    ]
    if prediction.get("inference_ms") is not None:
        _integer(prediction["inference_ms"], "prediction.inference_ms")

    feedback = value.get("feedback")
    if not isinstance(feedback, dict) or feedback.get("verdict") not in VERDICTS:
        raise ValueError("feedback.verdict is unsupported")
    corrected = feedback.get("corrected_spans")
    if not isinstance(corrected, list):
        raise ValueError("feedback.corrected_spans must be an array")
    corrected_spans = [
        _span(span, f"feedback.corrected_spans[{index}]", predicted=False)
        for index, span in enumerate(corrected)
    ]
    verdict = str(feedback["verdict"])
    if verdict in {"corrected", "missed"} and not corrected_spans:
        raise ValueError(f"{verdict} feedback requires at least one corrected span")

    label_spans = predicted_spans if verdict == "accepted" else corrected_spans
    return {
        "schema_version": 1,
        "example_id": sample_id,
        "video_id": video_id,
        "created_at_epoch_ms": value["created_at_epoch_ms"],
        "model": model,
        "transcript": transcript,
        "prediction": prediction,
        "feedback_verdict": verdict,
        "sponsor_spans": [
            {"start_ms": span["start_ms"], "end_ms": span["end_ms"]}
            for span in label_spans
        ],
    }


def import_feedback_jsonl(
    input_path: Path,
    output_path: Path,
    manifest_path: Path,
) -> dict[str, object]:
    records: list[dict[str, object]] = []
    sample_ids: set[str] = set()
    verdicts: Counter[str] = Counter()
    with input_path.open("r", encoding="utf-8") as source:
        for line_number, line in enumerate(source, start=1):
            if not line.strip():
                continue
            try:
                record = _validated_record(json.loads(line))
            except (json.JSONDecodeError, ValueError) as error:
                raise ValueError(f"invalid feedback at {input_path}:{line_number}: {error}") from error
            sample_id = str(record["example_id"])
            if sample_id in sample_ids:
                raise ValueError(f"duplicate sample_id at {input_path}:{line_number}: {sample_id}")
            sample_ids.add(sample_id)
            verdicts[str(record["feedback_verdict"])] += 1
            records.append(record)

    write_json_lines_atomic(output_path, records)
    report: dict[str, object] = {
        "schema_version": 1,
        "created_at": datetime.now(tz=UTC).isoformat(),
        "input": {"path": str(input_path), "sha256": sha256_file(input_path)},
        "output": {
            "path": str(output_path),
            "sha256": sha256_file(output_path),
            "records": len(records),
        },
        "verdicts": dict(sorted(verdicts.items())),
    }
    write_json_atomic(manifest_path, report)
    return report
