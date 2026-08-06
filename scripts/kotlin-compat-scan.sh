#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# This is a source-policy scan, not a compiler invocation.  Keep its inputs
# rooted in the checkout so the result is portable across Termux, CI, and a
# generated repository.
mapfile -d '' KOTLIN_FILES < <(
  find "$ROOT/src" -type f \( -name '*.kt' -o -name '*.kts' \) -print0 | sort -z
)
if ((${#KOTLIN_FILES[@]} == 0)); then
  echo 'KOTLIN_COMPAT_SCAN_FAIL no Kotlin source files found' >&2
  exit 1
fi

failures=0
report_failure() {
  printf 'KOTLIN_COMPAT_SCAN_FINDING %s\n' "$1" >&2
  failures=$((failures + 1))
}

# These packages are tied to one host or container runtime and cannot be
# imported by the portable core.  Platform adapters must remain behind the
# existing platform contracts instead.
forbidden_imports='^[[:space:]]*import[[:space:]]+(sun\.|jdk\.internal\.|android\.|javafx\.|javax\.servlet\.)'
if matches="$(grep -HnE "$forbidden_imports" "${KOTLIN_FILES[@]}" || true)"; then
  while IFS= read -r match; do
    [[ -z "$match" ]] || report_failure "forbidden-import $match"
  done <<< "$matches"
fi

# Absolute device paths are a portability defect even when they happen to be
# hidden in a string rather than an import.  Repository-relative paths and
# injected roots remain allowed.
absolute_paths='(/data/data/|/sdcard/|/storage/emulated/|/home/|[A-Za-z]:\\\\)'
if matches="$(grep -HnE "$absolute_paths" "${KOTLIN_FILES[@]}" || true)"; then
  while IFS= read -r match; do
    [[ -z "$match" ]] || report_failure "absolute-device-path $match"
  done <<< "$matches"
fi

# Every dependency declaration must be explicit.  The current runtime has
# only Kotlin/JUnit standard modules; adding a third-party dependency requires
# an intentional policy update in this scanner rather than silent drift.
mapfile -d '' BUILD_FILES < <(
  find "$ROOT" -path '*/build.gradle.kts' -type f -print0 | sort -z
)
dependency_pattern='^[[:space:]]*(implementation|api|compileOnly|runtimeOnly|testImplementation|testRuntimeOnly|kapt)[[:space:]]*\('
for build_file in "${BUILD_FILES[@]}"; do
  [[ -f "$build_file" ]] || continue
  while IFS= read -r match; do
    [[ -z "$match" ]] && continue
    if [[ "$match" != *'implementation(kotlin("stdlib"))'* \
       && "$match" != *'testImplementation(kotlin("test-junit"))'* \
       && "$match" != *'kotlin-stdlib'* ]]; then
      report_failure "unclassified-dependency $match"
    fi
  done < <(grep -HnE "$dependency_pattern" "$build_file" || true)
done

if ((failures > 0)); then
  printf 'KOTLIN_COMPAT_SCAN_FAIL findings=%d files=%d\n' "$failures" "${#KOTLIN_FILES[@]}" >&2
  exit 1
fi

printf '%s\n' \
  'KOTLIN_COMPAT_SCAN_OK' \
  "root=$ROOT" \
  "kotlin_files=${#KOTLIN_FILES[@]}" \
  'forbidden_imports=0' \
  'absolute_device_paths=0' \
  'unclassified_dependencies=0' \
  'build_execution=not_run'
