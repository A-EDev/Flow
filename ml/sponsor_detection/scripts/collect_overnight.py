from __future__ import annotations

import argparse
import fcntl
import json
import signal
import tomllib
from datetime import UTC, datetime
from pathlib import Path

import requests
from youtube_transcript_api import YouTubeTranscriptApi

from sponsor_detection.data.paced_http import PacedHTTPAdapter
from sponsor_detection.data.profile import write_json_atomic
from sponsor_detection.data.transcripts import collect_transcripts


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", type=Path, required=True)
    args = parser.parse_args()
    with args.config.open("rb") as source:
        config = tomllib.load(source)["collection"]
    output = Path(config["output_directory"])
    output.mkdir(parents=True, exist_ok=True)
    status_path = Path(config["live_status_path"])
    status = {"started_at": datetime.now(UTC).isoformat(), "status": "running"}
    consecutive_errors = 0

    def progress(attempt: int, maximum: int, successes: int, result: str) -> None:
        nonlocal consecutive_errors
        consecutive_errors = (
            consecutive_errors + 1
            if result in {"unexpected_error", "api_error", "provider_unavailable"}
            else 0
        )
        status.update(
            updated_at=datetime.now(UTC).isoformat(),
            attempts=attempt,
            maximum_attempts=maximum,
            successful_transcripts=successes,
            last_result=result,
        )
        write_json_atomic(status_path, status)
        print(json.dumps(status), flush=True)
        if consecutive_errors >= 5:
            raise RuntimeError("Stopped after five consecutive transport/API errors")

    def stop(signum, frame) -> None:
        raise KeyboardInterrupt

    signal.signal(signal.SIGTERM, stop)
    with (output / "collection.lock").open("a") as lock, requests.Session() as session:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        adapter = PacedHTTPAdapter(config["http_request_delay_seconds"])
        session.mount("http://", adapter)
        session.mount("https://", adapter)
        write_json_atomic(status_path, status)
        try:
            report = collect_transcripts(
                Path(config["pilot_path"]),
                Path(config["pilot_manifest_path"]),
                output,
                Path(config["report_path"]),
                languages=config["languages"],
                target_successes=config["target_successes"],
                max_attempts_per_run=config["max_attempts_per_run"],
                minimum_request_delay_seconds=0,
                maximum_request_delay_seconds=0,
                max_transient_attempts_per_video=3,
                stop_on_block=True,
                block_cooldown_seconds=21600,
                client=YouTubeTranscriptApi(http_client=session),
                progress_callback=progress,
            )
            status.update(
                status="complete" if report["target_reached"] else "stopped",
                report=report,
            )
        except (Exception, KeyboardInterrupt) as error:
            status.update(status="interrupted", error_type=type(error).__name__)
            raise
        finally:
            status["updated_at"] = datetime.now(UTC).isoformat()
            write_json_atomic(status_path, status)
            print(json.dumps(status), flush=True)


if __name__ == "__main__":
    main()
