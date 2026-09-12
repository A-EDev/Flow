from __future__ import annotations

from contextlib import nullcontext
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from sponsor_detection.inference.stitching import (
    StitchedSponsorSpan,
    WindowSponsorSpan,
    stitch_window_spans,
)
from sponsor_detection.inference.windowing import (
    AssembledTranscript,
    TranscriptCue,
    TranscriptWindow,
    assemble_transcript,
    build_transcript_windows,
)
from sponsor_detection.model.decoder import decode_bilou
from sponsor_detection.model.token_labels import ID_TO_LABEL


@dataclass(frozen=True, slots=True)
class FullTranscriptPrediction:
    transcript: AssembledTranscript
    windows: tuple[TranscriptWindow, ...]
    window_spans: tuple[WindowSponsorSpan, ...]
    sponsor_spans: tuple[StitchedSponsorSpan, ...]


class FullTranscriptSponsorDetector:
    def __init__(
        self,
        checkpoint_path: Path,
        *,
        confidence_threshold: float,
        max_length: int = 768,
        overlap_tokens: int = 128,
        batch_size: int = 16,
        bf16: bool = True,
        merge_gap_characters: int = 24,
        merge_gap_ms: int = 1500,
    ) -> None:
        import torch
        from transformers import AutoModelForTokenClassification, AutoTokenizer

        self.checkpoint_path = checkpoint_path
        self.confidence_threshold = confidence_threshold
        self.max_length = max_length
        self.overlap_tokens = overlap_tokens
        self.batch_size = batch_size
        self.merge_gap_characters = merge_gap_characters
        self.merge_gap_ms = merge_gap_ms
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        self.use_bf16 = bf16 and self.device.type == "cuda"
        self.tokenizer = AutoTokenizer.from_pretrained(checkpoint_path)
        self.model = AutoModelForTokenClassification.from_pretrained(checkpoint_path).to(
            self.device
        )
        expected_labels = {int(index): name for index, name in ID_TO_LABEL.items()}
        if self.model.config.id2label != expected_labels:
            raise ValueError("checkpoint label mapping does not match the decoder")
        self.model.eval()

    def predict(self, cues: Sequence[TranscriptCue]) -> FullTranscriptPrediction:
        import torch

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
                return_tensors="pt",
            )
            model_inputs = {
                name: tensor.to(self.device) for name, tensor in encoded.items()
            }
            context = (
                torch.autocast(device_type="cuda", dtype=torch.bfloat16)
                if self.use_bf16
                else nullcontext()
            )
            with torch.inference_mode(), context:
                logits = self.model(**model_inputs).logits.float().cpu()
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
