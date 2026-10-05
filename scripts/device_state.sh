#!/usr/bin/env bash
#
# Snapshot / restore Flow's on-device app state across a reinstall.
#
# Android wipes app-private data (settings, DataStore, downloaded model) on
# uninstall -- which the Gradle `connected*AndroidTest` tasks do at the end.
# A normal update (`adb install -r`) does NOT wipe it. Use this to snapshot
# before anything that uninstalls, and restore afterwards.
#
# Usage:
#   scripts/device_state.sh backup  [--with-model] [archive.tar]
#   scripts/device_state.sh restore [--with-model] [archive.tar]
#
# Env:
#   FLOW_PKG       package name (default: io.github.aedev.flow.debug)
#   ANDROID_SERIAL adb device serial (default: adb's own selection)
#
set -euo pipefail

PKG="${FLOW_PKG:-io.github.aedev.flow.debug}"
DEFAULT_ARCHIVE="flow-device-state-${PKG##*.}.tar"

adb_cmd() {
    if [ -n "${ANDROID_SERIAL:-}" ]; then
        adb -s "$ANDROID_SERIAL" "$@"
    else
        adb "$@"
    fi
}

die() {
    echo "error: $*" >&2
    exit 1
}

require_device() {
    adb_cmd get-state >/dev/null 2>&1 || die "no adb device (set ANDROID_SERIAL or connect one)"
    adb_cmd shell pm path "$PKG" >/dev/null 2>&1 || die "$PKG is not installed"
}

# The data directories worth preserving. The model is optional because it is
# large (tens of MB) and re-downloadable.
preserved_paths() {
    local with_model="$1"
    local paths=("shared_prefs" "files/datastore")
    if [ "$with_model" = "1" ]; then
        paths+=("files/sponsor_models")
    fi
    local existing=()
    for path in "${paths[@]}"; do
        if adb_cmd shell run-as "$PKG" ls "$path" >/dev/null 2>&1; then
            existing+=("$path")
        fi
    done
    printf '%s\n' "${existing[@]}"
}

do_backup() {
    local archive="$1" with_model="$2"
    require_device
    mapfile -t paths < <(preserved_paths "$with_model")
    [ "${#paths[@]}" -gt 0 ] || die "nothing to back up under $PKG"
    adb_cmd exec-out run-as "$PKG" tar -cf - "${paths[@]}" > "$archive"
    echo "saved ${#paths[@]} path(s) -> $archive ($(du -h "$archive" | cut -f1))"
    tar -tf "$archive" | sed 's/^/  /'
}

do_restore() {
    local archive="$1" with_model="$2"
    [ -f "$archive" ] || die "archive not found: $archive"
    require_device
    # Stop the app first so a live DataStore write cannot clobber the restore.
    adb_cmd shell am force-stop "$PKG" || true
    local remote="/data/local/tmp/flow-state-restore.tar"
    adb_cmd push "$archive" "$remote" >/dev/null
    adb_cmd shell chmod 644 "$remote"
    local paths
    mapfile -t paths < <(preserved_paths "$with_model")
    # Extract into the app's private data dir; run-as writes as the app uid.
    adb_cmd shell run-as "$PKG" tar -xf "$remote"
    adb_cmd shell rm -f "$remote"
    echo "restored $archive into $PKG"
    for path in "${paths[@]}"; do
        adb_cmd shell run-as "$PKG" ls -ld "$path" | sed 's/^/  /'
    done
    echo "launch the app when ready; settings and model are back in place."
}

command="${1:-}"
shift || true
with_model=0
args=()
for arg in "$@"; do
    case "$arg" in
        --with-model) with_model=1 ;;
        *) args+=("$arg") ;;
    esac
done
archive="${args[0]:-$DEFAULT_ARCHIVE}"

case "$command" in
    backup) do_backup "$archive" "$with_model" ;;
    restore) do_restore "$archive" "$with_model" ;;
    *) echo "usage: $0 {backup|restore} [--with-model] [archive.tar]" >&2; exit 2 ;;
esac
