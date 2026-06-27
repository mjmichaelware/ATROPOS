#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-classes}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

TMP="${TMPDIR:-/tmp}/atropos-fast-gate"
rm -rf "$TMP"
mkdir -p "$TMP/classes"

compile_classes() {
  mapfile -d '' SOURCES < <(find src/main/kotlin -type f -name '*.kt' -print0)
  kotlinc -d "$TMP/classes" "${SOURCES[@]}"
}

compile_jar() {
  mapfile -d '' SOURCES < <(find src/main/kotlin -type f -name '*.kt' -print0)
  kotlinc -include-runtime -d "$TMP/atropos.jar" "${SOURCES[@]}"
}

case "$MODE" in
  classes)
    compile_classes
    echo "FAST_CLASSES_OK"
    ;;
  jar)
    compile_jar
    echo "FAST_JAR_OK $TMP/atropos.jar"
    ;;
  install-jar)
    compile_jar
    mkdir -p .atropos/backups
    [ -f atropos.jar ] && cp atropos.jar ".atropos/backups/atropos.$(date +%s).jar"
    mv "$TMP/atropos.jar" atropos.jar
    echo "FAST_INSTALL_JAR_OK"
    ;;
  smoke)
    compile_jar
    scripts/atropos-smoke-cli.sh "$TMP/atropos.jar"
    echo "FAST_SMOKE_OK"
    ;;
  *)
    echo "usage: $0 classes|jar|install-jar|smoke" >&2
    exit 2
    ;;
esac
