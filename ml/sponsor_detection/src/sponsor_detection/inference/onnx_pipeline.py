from __future__ import annotations

from pathlib import Path
from typing import Sequence

from sponsor_detection.inference.pipeline import FullTranscriptPrediction
from sponsor_detection.inference.stitching import WindowSponsorSpan, stitch_window_spans
from sponsor_detection.inference.windowing import (
    TranscriptCue,
    assemble_transcript,
    build_transcript_windows,
)
from sponsor_detection.model.decoder import decode_bilou


class OnnxFullTranscriptSponsorDetector:
    def __init__(
        self,
        model_path: Path,
        tokenizer_path: Path,
        *,
        confidence_threshold: float,
        max_length: int = 768,
        overlap_tokens: int = 128,
        batch_size: int = 16,
        merge_gap_characters: int = 24,
        merge_gap_ms: int = 1500,
    ) -> None:
        import onnxruntime as ort
        from transformers import AutoTokenizer

        self.model_path = model_path
        self.confidence_threshold = confidence_threshold
        self.max_length = max_length
        self.overlap_tokens = overlap_tokens
        self.batch_size = batch_size
        self.merge_gap_characters = merge_gap_characters
        self.merge_gap_ms = merge_gap_ms
        self.tokenizer = AutoTokenizer.from_pretrained(tokenizer_path)
        options = ort.SessionOptions()
        options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        self.session = ort.InferenceSession(
            str(model_path),
            sess_options=options,
            providers=["CPUExecutionProvider"],
        )
        inputs = {value.name for value in self.session.get_inputs()}
        if inputs != {"input_ids", "attention_mask"}:
            raise ValueError(f"unexpected ONNX model inputs: {sorted(inputs)}")

    def predict(self, cues: Sequence[TranscriptCue]) -> FullTranscriptPrediction:
        transcript = assemble_transcript(cues)
        windows = build_transcript_windows(
            transcript,
            self.tokenizer,
            max_length=self.max_length,
            overlap_tokens=self.overlap_tokens,
        )
        window_spans: list[WindowSponsorSpan] = []
        for start in range(0, len(windows), self.batch_size):
            batch = windows[start : start + self.batch_size]
            encoded = self.tokenizer.pad(
                [
                    {
                        "input_ids": list(window.input_ids),
                        "attention_mask": list(window.attention_mask),
                    }
                    for window in batch
                ],
                padding=True,
                return_tensors="np",
            )
            logits = self.session.run(
                ["logits"],
                {
                    "input_ids": encoded["input_ids"],
                    "attention_mask": encoded["attention_mask"],
                },
            )[0]
            for index, window in enumerate(batch):
                length = len(window.offset_mapping)
                decoded = decode_bilou(
                    logits[index][:length].tolist(),
                    window.offset_mapping,
                    attention_mask=window.attention_mask,
                )
                window_spans.extend(
                    WindowSponsorSpan(
                        window_index=window.index,
                        start_char=span.start_char,
                        end_char=span.end_char,
                        confidence=span.confidence,
                    )
                    for span in decoded.spans
                )
        stitched = stitch_window_spans(
            transcript,
            window_spans,
            confidence_threshold=self.confidence_threshold,
            merge_gap_characters=self.merge_gap_characters,
            merge_gap_ms=self.merge_gap_ms,
        )
        return FullTranscriptPrediction(
            transcript=transcript,
            windows=windows,
            window_spans=tuple(window_spans),
            sponsor_spans=stitched,
        )
