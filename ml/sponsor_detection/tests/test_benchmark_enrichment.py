from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

from sponsor_detection.data.benchmark import write_json_lines_atomic
from sponsor_detection.data.benchmark_enrichment import (
    EnrichmentModel,
    enrich_benchmark_review,
)
from sponsor_detection.data.profile import sha256_file
from sponsor_detection.inference.stitching import StitchedSponsorSpan
from sponsor_detection.inference.windowing import assemble_transcript


class _FakeDetector:
    def __init__(self, checkpoint_path: Path, **configuration: object) -> None:
        self.checkpoint_path = checkpoint_path
        self.configuration = configuration

    def predict(self, cues):
        transcript = assemble_transcript(cues)
        start = transcript.text.index("sponsor")
        end = start + len("sponsor")
        return SimpleNamespace(
            transcript=transcript,
            windows=(object(),),
            sponsor_spans=(
                StitchedSponsorSpan(
                    start_char=start,
                    end_char=end,
                    start_ms=1000,
                    end_ms=2000,
                    confidence=0.9,
                    supporting_windows=(0,),
                ),
            ),
        )


class BenchmarkEnrichmentTest(unittest.TestCase):
    def test_attaches_predictions_and_updates_manifest_hash(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            transcript_path = root / "video.json"
            transcript_path.write_text(
                json.dumps(
                    {
                        "video_id": "video",
                        "cues": [
                            {
                                "index": 0,
                                "start_ms": 0,
                                "end_ms": 3000,
                                "text": "A sponsor message here",
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )
            review_path = root / "review.jsonl"
            write_json_lines_atomic(
                review_path,
                [
                    {
                        "video_id": "video",
                        "transcript": {
                            "path": str(transcript_path),
                            "sha256": sha256_file(transcript_path),
                        },
                        "model_predictions": [],
                        "annotation": {"status": "pending"},
                    }
                ],
            )
            manifest_path = root / "manifest.json"
            manifest_path.write_text(
                json.dumps(
                    {
                        "status": "annotation_pending",
                        "output": {
                            "path": str(review_path),
                            "bytes": review_path.stat().st_size,
                            "sha256": sha256_file(review_path),
                        },
                    }
                ),
                encoding="utf-8",
            )
            report = enrich_benchmark_review(
                review_path,
                manifest_path,
                [
                    EnrichmentModel(
                        name="test-model",
                        checkpoint_path=root / "checkpoint",
                        confidence_threshold=0.5,
                        checkpoint_sha256="abc123",
                    )
                ],
                context_characters=4,
                detector_factory=_FakeDetector,
            )
            record = json.loads(review_path.read_text(encoding="utf-8"))
            output_sha256 = sha256_file(review_path)

        prediction = record["model_predictions"][0]
        self.assertEqual(prediction["model"], "test-model")
        self.assertEqual(prediction["spans"][0]["text"], "sponsor")
        self.assertIn("sponsor", prediction["spans"][0]["context"])
        self.assertEqual(report["enrichment"]["predicted_spans"], 1)
        self.assertEqual(report["output"]["sha256"], output_sha256)


if __name__ == "__main__":
    unittest.main()
