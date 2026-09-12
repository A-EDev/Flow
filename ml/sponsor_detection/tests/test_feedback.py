from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from sponsor_detection.data.feedback import import_feedback_jsonl


def _sample(verdict: str = "corrected") -> dict[str, object]:
    return {
        "schema_version": 1,
        "sample_id": "sample-1",
        "created_at_epoch_ms": 1_700_000_000_000,
        "video_id": "video-1",
        "model": {"name": "ettin_17m_sponsor_v1", "sha256": "a" * 64},
        "transcript": {
            "language_tag": "en",
            "source": "youtube",
            "is_auto_generated": True,
            "cues": [{"start_ms": 0, "end_ms": 1000, "text": "Sponsor cue"}],
        },
        "prediction": {
            "inference_ms": 12,
            "spans": [{"start_ms": 0, "end_ms": 1000, "confidence": 0.9}],
        },
        "feedback": {
            "verdict": verdict,
            "corrected_spans": [{"start_ms": 100, "end_ms": 900}],
        },
    }


class FeedbackImportTest(unittest.TestCase):
    def test_import_preserves_transcript_and_uses_corrected_spans_as_labels(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            input_path = root / "feedback.jsonl"
            input_path.write_text(json.dumps(_sample()) + "\n", encoding="utf-8")

            report = import_feedback_jsonl(
                input_path,
                root / "training_feedback.jsonl",
                root / "manifest.json",
            )

            record = json.loads((root / "training_feedback.jsonl").read_text())
            self.assertEqual(record["transcript"]["cues"][0]["text"], "Sponsor cue")
            self.assertEqual(record["sponsor_spans"], [{"start_ms": 100, "end_ms": 900}])
            self.assertEqual(report["verdicts"], {"corrected": 1})

    def test_rejects_duplicate_sample_ids(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            input_path = root / "feedback.jsonl"
            line = json.dumps(_sample())
            input_path.write_text(f"{line}\n{line}\n", encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "duplicate sample_id"):
                import_feedback_jsonl(
                    input_path,
                    root / "training_feedback.jsonl",
                    root / "manifest.json",
                )
