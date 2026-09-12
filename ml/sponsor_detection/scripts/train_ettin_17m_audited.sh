#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "$repo_root"

exec ml/sponsor_detection/.venv/bin/sponsor-detection train \
  --config ml/sponsor_detection/config/train_ettin_17m_audited.toml \
  "$@"
