from __future__ import annotations

import json
import tomllib
from pathlib import Path

from sponsor_detection.data.profile import sha256_file
from sponsor_detection.data.profile import write_json_atomic
from sponsor_detection.model.token_labels import ID_TO_LABEL, LABEL_TO_ID, bilou_labels_for_offsets


def _load_configuration(path: Path) -> dict[str, object]:
    with path.open("rb") as source:
        return tomllib.load(source)


def _verify_dataset(configuration: dict[str, object]) -> dict[str, object]:
    dataset = configuration["dataset"]
    manifest_path = Path(dataset["manifest_path"])
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    for split in ("train", "validation", "test"):
        expected = manifest["outputs"][split]
        path = Path(dataset[f"{split}_path"])
        if sha256_file(path) != expected["sha256"]:
            raise ValueError(f"{split} dataset does not match its manifest")
    return manifest


def _tokenize_batch(tokenizer, max_length: int):
    def tokenize(batch):
        encoded = tokenizer(
            batch["text"],
            truncation=True,
            max_length=max_length,
            return_offsets_mapping=True,
        )
        encoded["labels"] = [
            bilou_labels_for_offsets(offsets, spans)
            for offsets, spans in zip(
                encoded["offset_mapping"], batch["sponsor_spans"], strict=True
            )
        ]
        del encoded["offset_mapping"]
        return encoded

    return tokenize


def _compute_metrics(prediction) -> dict[str, float]:
    import numpy as np

    predictions = np.argmax(prediction.predictions, axis=-1)
    labels = prediction.label_ids
    valid = labels != -100
    predicted_positive = (predictions != LABEL_TO_ID["O"]) & valid
    actual_positive = (labels != LABEL_TO_ID["O"]) & valid
    true_positive = int(np.sum(predicted_positive & actual_positive))
    false_positive = int(np.sum(predicted_positive & ~actual_positive & valid))
    false_negative = int(np.sum(~predicted_positive & actual_positive & valid))
    precision = true_positive / (true_positive + false_positive) if true_positive + false_positive else 0
    recall = true_positive / (true_positive + false_negative) if true_positive + false_negative else 0
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0
    token_accuracy = float(np.mean(predictions[valid] == labels[valid])) if np.any(valid) else 0
    return {
        "sponsor_token_precision": precision,
        "sponsor_token_recall": recall,
        "sponsor_token_f1": f1,
        "token_accuracy": token_accuracy,
    }


def _percentile(sorted_values: list[int], fraction: float) -> int:
    if not sorted_values:
        return 0
    return sorted_values[round((len(sorted_values) - 1) * fraction)]


def validate_tokenization_from_config(path: Path) -> dict[str, object]:
    import pyarrow.parquet as parquet
    from transformers import AutoTokenizer

    configuration = _load_configuration(path)
    manifest = _verify_dataset(configuration)
    model_configuration = configuration["model"]
    model_name = str(model_configuration["encoder"])
    revision = str(model_configuration["revision"])
    max_length = int(model_configuration["max_length"])
    tokenizer = AutoTokenizer.from_pretrained(model_name, revision=revision)

    split_reports: dict[str, object] = {}
    for split in ("train", "validation", "test"):
        lengths: list[int] = []
        overlength_windows = 0
        clipped_sponsor_spans = 0
        unaligned_sponsor_spans = 0
        path_for_split = Path(configuration["dataset"][f"{split}_path"])
        parquet_file = parquet.ParquetFile(path_for_split)
        for batch in parquet_file.iter_batches(
            batch_size=256,
            columns=["text", "sponsor_spans"],
        ):
            records = batch.to_pydict()
            encoded = tokenizer(
                records["text"],
                truncation=False,
                return_offsets_mapping=True,
            )
            for text, spans, input_ids, offsets in zip(
                records["text"],
                records["sponsor_spans"],
                encoded["input_ids"],
                encoded["offset_mapping"],
                strict=True,
            ):
                lengths.append(len(input_ids))
                if len(input_ids) > max_length:
                    overlength_windows += 1
                    retained = tokenizer(
                        text,
                        truncation=True,
                        max_length=max_length,
                        return_offsets_mapping=True,
                    )["offset_mapping"]
                    retained_end = max((end for start, end in retained if end > start), default=0)
                    clipped_sponsor_spans += sum(
                        int(int(span["end_char"]) > retained_end) for span in spans
                    )
                for span in spans:
                    start_char = int(span["start_char"])
                    end_char = int(span["end_char"])
                    if not any(
                        token_end > start_char and token_start < end_char
                        for token_start, token_end in offsets
                        if token_end > token_start
                    ):
                        unaligned_sponsor_spans += 1
        lengths.sort()
        split_reports[split] = {
            "rows": len(lengths),
            "max_tokens": lengths[-1] if lengths else 0,
            "p50_tokens": _percentile(lengths, 0.50),
            "p95_tokens": _percentile(lengths, 0.95),
            "p99_tokens": _percentile(lengths, 0.99),
            "overlength_windows": overlength_windows,
            "clipped_sponsor_spans": clipped_sponsor_spans,
            "unaligned_sponsor_spans": unaligned_sponsor_spans,
        }

    report = {
        "encoder": model_name,
        "revision": revision,
        "max_length": max_length,
        "dataset_manifest_sha256": sha256_file(Path(configuration["dataset"]["manifest_path"])),
        "splits": split_reports,
        "valid": all(
            split["clipped_sponsor_spans"] == 0
            and split["unaligned_sponsor_spans"] == 0
            for split in split_reports.values()
        ),
        "dataset_rows": {
            split: manifest["outputs"][split]["rows"]
            for split in ("train", "validation", "test")
        },
    }
    report_path = Path(
        configuration.get("validation", {}).get(
            "tokenization_report_path",
            "ml/sponsor_detection/reports/tokenization_validation.json",
        )
    )
    write_json_atomic(report_path, report)
    if not report["valid"]:
        raise ValueError(f"tokenization validation failed; see {report_path}")
    return report


def train_from_config(
    path: Path,
    *,
    smoke_test: bool = False,
    resume_from_checkpoint: Path | None = None,
) -> dict[str, object]:
    from datasets import load_dataset
    from transformers import (
        AutoModelForTokenClassification,
        AutoTokenizer,
        DataCollatorForTokenClassification,
        Trainer,
        TrainingArguments,
        set_seed,
    )

    configuration = _load_configuration(path)
    manifest = _verify_dataset(configuration)
    model_configuration = configuration["model"]
    training = configuration["training"]
    seed = int(training["seed"])
    set_seed(seed)

    data_files = {
        split: str(configuration["dataset"][f"{split}_path"])
        for split in ("train", "validation", "test")
    }
    dataset = load_dataset("parquet", data_files=data_files)
    if smoke_test:
        dataset["train"] = dataset["train"].select(range(min(16, len(dataset["train"]))))
        dataset["validation"] = dataset["validation"].select(
            range(min(8, len(dataset["validation"])))
        )
        dataset["test"] = dataset["test"].select(range(min(8, len(dataset["test"]))))

    model_name = str(model_configuration["encoder"])
    revision = str(model_configuration["revision"])
    initial_checkpoint = model_configuration.get("initial_checkpoint")
    model_source = str(initial_checkpoint) if initial_checkpoint else model_name
    model_source_kwargs = {} if initial_checkpoint else {"revision": revision}
    max_length = int(model_configuration["max_length"])
    tokenizer = AutoTokenizer.from_pretrained(model_source, **model_source_kwargs)
    columns = dataset["train"].column_names
    tokenized = dataset.map(
        _tokenize_batch(tokenizer, max_length),
        batched=True,
        num_proc=1 if smoke_test else int(training.get("preprocessing_workers", 4)),
        remove_columns=columns,
        desc="Aligning sponsor character spans to tokens",
    )
    model = AutoModelForTokenClassification.from_pretrained(
        model_source,
        **model_source_kwargs,
        num_labels=len(LABEL_TO_ID),
        id2label=ID_TO_LABEL,
        label2id=LABEL_TO_ID,
        classifier_dropout=float(model_configuration.get("dropout", 0.1)),
    )
    output_directory = Path(str(training["output_directory"]))
    arguments = TrainingArguments(
        output_dir=str(output_directory),
        learning_rate=float(training["learning_rate"]),
        weight_decay=float(training["weight_decay"]),
        warmup_steps=0 if smoke_test else int(training["warmup_steps"]),
        num_train_epochs=0.01 if smoke_test else float(training["epochs"]),
        per_device_train_batch_size=int(training["train_batch_size"]),
        per_device_eval_batch_size=int(training["eval_batch_size"]),
        gradient_accumulation_steps=int(training["gradient_accumulation_steps"]),
        bf16=bool(training.get("bf16", True)),
        eval_strategy="steps",
        eval_steps=int(training["eval_steps"]),
        eval_accumulation_steps=int(training.get("eval_accumulation_steps", 16)),
        save_strategy="no" if smoke_test else "steps",
        save_steps=int(training["save_steps"]),
        logging_steps=1 if smoke_test else int(training["logging_steps"]),
        save_total_limit=int(training.get("save_total_limit", 2)),
        load_best_model_at_end=not smoke_test,
        metric_for_best_model="sponsor_token_f1",
        greater_is_better=True,
        report_to=[],
        seed=seed,
        data_seed=seed,
        max_steps=1 if smoke_test else -1,
    )
    trainer = Trainer(
        model=model,
        args=arguments,
        train_dataset=tokenized["train"],
        eval_dataset=tokenized["validation"],
        processing_class=tokenizer,
        data_collator=DataCollatorForTokenClassification(tokenizer),
        compute_metrics=_compute_metrics,
    )
    if resume_from_checkpoint is not None:
        if not resume_from_checkpoint.is_dir():
            raise FileNotFoundError(
                f"resume checkpoint does not exist: {resume_from_checkpoint}"
            )
        train_result = trainer.train(resume_from_checkpoint=str(resume_from_checkpoint))
    else:
        train_result = trainer.train()
    evaluation = trainer.evaluate()
    test_evaluation = (
        None
        if smoke_test
        else trainer.evaluate(tokenized["test"], metric_key_prefix="test")
    )
    if not smoke_test:
        trainer.save_model()
        tokenizer.save_pretrained(output_directory)
    report = {
        "encoder": model_name,
        "revision": revision,
        "initial_checkpoint": str(initial_checkpoint) if initial_checkpoint else None,
        "resumed_from_checkpoint": (
            str(resume_from_checkpoint) if resume_from_checkpoint else None
        ),
        "dataset_manifest_sha256": sha256_file(Path(configuration["dataset"]["manifest_path"])),
        "dataset_rows": {
            split: manifest["outputs"][split]["rows"]
            for split in ("train", "validation", "test")
        },
        "smoke_test": smoke_test,
        "train_metrics": train_result.metrics,
        "validation_metrics": evaluation,
        "test_metrics": test_evaluation,
    }
    report_path = output_directory / ("smoke_test_metrics.json" if smoke_test else "metrics.json")
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return report
