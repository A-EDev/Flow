# Sponsor Detection Model Architecture

## Trainable v1 bootstrap

The first trainable model is a bidirectional token extractor rather than a generative T5 model.

- Default encoder: `jhu-clsp/ettin-encoder-17m` at revision `987607455c61e7a5bbc85f7758e0512ea6d0ae4c`.
- Accuracy challenger: `jhu-clsp/ettin-encoder-32m` at revision `1b8ba06455dd44f80fc9c1ca9e22806157a57379`.
- Input: normalized transcript windows up to 1,024 tokens.
- Output: `O`, `B-SPONSOR`, `I-SPONSOR`, `L-SPONSOR`, and `U-SPONSOR` per token.
- Objective: token cross-entropy. The dataset retains per-window sample weights for a later sampler ablation; v1 does not silently apply them.
- Decoding: fuse overlapping windows, enforce valid BILOU transitions, and map predicted character spans back to transcript cue timestamps.

Ettin 17M has seven layers and a 256-dimensional hidden state. Ettin 32M has ten layers and a 384-dimensional hidden state. Both are encoder-only ModernBERT-family checkpoints with bidirectional attention and an 8K-token maximum context. The 17M model is the deployment default; the 32M model is retained only if it improves segment F1 by at least one absolute point at the same precision target.

## Cue-aware refinement

Fresh cue-level transcripts add a second training stage without invalidating the bootstrap model:

1. Pool token states inside each transcript cue.
2. Concatenate normalized cue duration, relative video position, and neighboring cue gaps.
3. Pass cue representations through a two-layer bidirectional GRU with a 128-dimensional state per direction.
4. Predict cue-level BILOU tags and start/end offsets inside boundary cues.
5. Train with token loss, cue loss, boundary Smooth L1 loss, and a window-level sponsor-presence auxiliary loss.

The temporal head is not trained from the legacy Xenova windows because they do not preserve original cue boundaries. It becomes active only when the fresh cue corpus is large enough for a clean held-out evaluation.

## Encoder decision

The shortlist was rechecked against current public model documentation instead of carrying over the old T5 choice. Ettin 17M is the default because it is a modern, genuinely small bidirectional encoder with an open training recipe and ample context for these windows. Ettin 32M measures whether extra capacity is useful. MiniLM-L12-H384 and ELECTRA-small remain sensible external baselines, but neither justifies training a text encoder from scratch before the domain task has been benchmarked.

- [Ettin encoder family and evaluation paper](https://arxiv.org/html/2507.11412)
- [Ettin 17M model card](https://huggingface.co/jhu-clsp/ettin-encoder-17m)
- [Ettin 32M model card](https://huggingface.co/jhu-clsp/ettin-encoder-32m)
- [MiniLM-L12-H384 model card](https://huggingface.co/microsoft/MiniLM-L12-H384-uncased)
- [ELECTRA-small model card](https://huggingface.co/google/electra-small-discriminator)

## Selection and export

- Select bootstrap checkpoints by validation sponsor-token F1 and report the untouched test split once.
- Once cue timestamps are available, select by highest recall at at least 95% segment precision and report temporal IoU 0.3, 0.5, and 0.7 segment F1, missed sponsor seconds, false-positive seconds per hour, and boundary error.
- Export the winning encoder and token head to ONNX, then INT8.
- INT8 must remain within one absolute segment-F1 point of FP32.
