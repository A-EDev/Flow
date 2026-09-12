#!/usr/bin/env bash
set -euo pipefail

project_root=$(git rev-parse --show-toplevel)
ml_root="$project_root/ml/sponsor_detection"
runtime_root="$ml_root/artifacts/android/onnxruntime_custom"
source_root="$runtime_root/source"
working_root="$runtime_root/build"
operator_config="$ml_root/artifacts/android/ettin_17m_sponsor_v1/android/required_operators_and_types.config"
build_settings="$ml_root/config/onnxruntime_android_build.json"

if [[ ! -f "$operator_config" ]]; then
    echo "Missing operator configuration. Run sponsor-detection export-android first." >&2
    exit 2
fi

mkdir -p "$runtime_root"
if [[ ! -d "$source_root/.git" ]]; then
    git clone --depth 1 --branch v1.29.0 \
        https://github.com/microsoft/onnxruntime.git "$source_root"
fi

dockerfile="$source_root/tools/android_custom_build/Dockerfile"
# ORT v1.29's Python build scripts require 3.10, while its custom Android
# Dockerfile is still based on Ubuntu 20.04 and Python 3.8.
sed -i 's/^FROM ubuntu:20\.04$/FROM ubuntu:22.04/' "$dockerfile"
sed -i "/^RUN sed -i '1i from __future__ import annotations'/d" "$dockerfile"

export BUILDKIT_PROGRESS=plain

python3 "$source_root/tools/android_custom_build/build_custom_android_package.py" \
    "$working_root" \
    --onnxruntime_branch_or_tag v1.29.0 \
    --include_ops_by_config "$operator_config" \
    --build_settings "$build_settings" \
    --config MinSizeRel
