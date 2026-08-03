#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Keep the compatibility policy deterministic and independent of a local device.
forbidden='(^|[[:space:]])import[[:space:]]+(sun\.|jdk\.internal\.|android\.|javafx\.|javax\.servlet\.)'
if rg -n --glob '*.kt' --glob '*.kts' "$forbidden" src build.gradle.kts settings.gradle.kts; then
  echo "KOTLIN_COMPAT_SCAN_FAIL forbidden platform/internal import" >&2
  exit 1
fi

if rg -n --glob 'build.gradle.kts' --glob '*.gradle.kts' \
  'implementation\([^)]*\)|api\([^)]*\)|compileOnly\([^)]*\)' \
  | rg -v 'kotlin\("stdlib"\)|kotlin\("test-junit"\)|kotlin-stdlib|testImplementation' >/dev/null; then
  echo "KOTLIN_COMPAT_SCAN_FAIL unsupported or unclassified dependency" >&2
  exit 1
fi

printf '%s\n' \
  'KOTLIN_COMPAT_SCAN_OK' \
  "root=$ROOT" \
  'policy=JVM/Kotlin source excludes internal and unclassified platform dependencies' \
  'build_execution=not_run'
