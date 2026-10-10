#!/usr/bin/env bash
# Builds the reduced ONNX Runtime Android package (a few MB per ABI instead of ~30 MB) that the
# on-device sponsor model needs, and copies the AAR to app/libs/ where app/build.gradle.kts picks it up.
#
# Requirements: Docker, git, python3. The first run clones ONNX Runtime v1.29.0 and builds a container
# image, so expect it to take a while. Re-runs reuse both.
#
#   tools/onnxruntime/build-onnxruntime-android.sh
#
# Environment overrides:
#   ORT_WORK_DIR   where the ONNX Runtime source and build output live (default: build/onnxruntime)
#
# Without the AAR, the app builds against the full Maven artifact, which works but is much larger.
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd -- "$script_dir/../.." && pwd)
work_dir="${ORT_WORK_DIR:-$repo_root/build/onnxruntime}"
source_dir="$work_dir/source"
output_dir="$work_dir/output"
ort_version=1.29.0

mkdir -p "$work_dir"
if [[ ! -d "$source_dir/.git" ]]; then
    git clone --depth 1 --branch "v$ort_version" https://github.com/microsoft/onnxruntime.git "$source_dir"
fi

dockerfile="$source_dir/tools/android_custom_build/Dockerfile"
# ORT 1.29's build scripts need Python 3.10, but its custom Android Dockerfile still starts from
# Ubuntu 20.04 (Python 3.8).
sed -i 's/^FROM ubuntu:20\.04$/FROM ubuntu:22.04/' "$dockerfile"
sed -i "/^RUN sed -i '1i from __future__ import annotations'/d" "$dockerfile"

export BUILDKIT_PROGRESS=plain
python3 "$source_dir/tools/android_custom_build/build_custom_android_package.py" \
    "$output_dir" \
    --onnxruntime_branch_or_tag "v$ort_version" \
    --include_ops_by_config "$script_dir/required_operators_and_types.config" \
    --build_settings "$script_dir/android_build.json" \
    --config MinSizeRel

aar=$(find "$output_dir" -name "onnxruntime-android-$ort_version.aar" | head -n 1)
if [[ -z "$aar" ]]; then
    echo "Build finished but no AAR was found under $output_dir" >&2
    exit 1
fi
mkdir -p "$repo_root/app/libs"
cp "$aar" "$repo_root/app/libs/onnxruntime-android-$ort_version.aar"
echo "Wrote app/libs/onnxruntime-android-$ort_version.aar"
