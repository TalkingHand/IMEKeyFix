#!/usr/bin/env bash
# Build IMEKeyFix.jar into the mod folder (one jar, no hot-reload logic jar).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HARNESS="${HARNESS:-$ROOT/../PZTestHarness}"
cd "$ROOT"
BUILD_DIR="$ROOT/build" bash "$HARNESS/tools/javabuild.sh" "$ROOT/java/src/main/java" \
  "$ROOT/Contents/mods/IMEKeyFix/42/media/java/client/IMEKeyFix.jar"
