#!/usr/bin/env bash
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT="${ATROPOS_KOTLIN_COMPAT_SCAN_ROOT:-$SCRIPT_ROOT}"
cd "$ROOT"

# Termux installations may not expose /dev/fd or procfs. Use bounded named
# temporary lists so the scanner remains portable without process substitution.
KOTLIN_LIST="$(mktemp "${TMPDIR:-/tmp}/atropos-kotlin-compat.XXXXXX")"
BUILD_LIST="$(mktemp "${TMPDIR:-/tmp}/atropos-kotlin-build.XXXXXX")"
trap 'rm -f "$KOTLIN_LIST" "$BUILD_LIST"' EXIT

# This is a source-policy scan, not a compiler invocation.  Keep its inputs
# rooted in the checkout so the result is portable across Termux, CI, and a
# generated repository.
find "$ROOT/src" -type f \( -name '*.kt' -o -name '*.kts' \) -print0 | sort -z > "$KOTLIN_LIST"
KOTLIN_FILES=()
while IFS= read -r -d '' file; do
  KOTLIN_FILES+=("$file")
done < "$KOTLIN_LIST"
if ((${#KOTLIN_FILES[@]} == 0)); then
  echo 'KOTLIN_COMPAT_SCAN_FAIL no Kotlin source files found' >&2
  exit 1
fi

failures=0
report_failure() {
  printf 'KOTLIN_COMPAT_SCAN_FINDING %s\n' "$1" >&2
  failures=$((failures + 1))
}

# These packages are tied to one host or container runtime, or are explicitly
# excluded by Source Doc 2 .225, and cannot be imported by portable core code.
# Platform adapters must remain behind the existing platform contracts instead.
forbidden_imports='^[[:space:]]*import[[:space:]]+(sun\.|jdk\.internal\.|android\.|javafx\.|javax\.servlet\.|kotlin\.io\.path\.|kotlinx\.|kotlin\.reflect\.)'
if matches="$(grep -HnE "$forbidden_imports" "${KOTLIN_FILES[@]}" || true)"; then
  while IFS= read -r match; do
    [[ -z "$match" ]] || report_failure "forbidden-import $match"
  done <<< "$matches"
fi

forbidden_calls='Class\.forName[[:space:]]*\('
if matches="$(grep -HnE "$forbidden_calls" "${KOTLIN_FILES[@]}" || true)"; then
  while IFS= read -r match; do
    [[ -z "$match" ]] || report_failure "forbidden-reflection-call $match"
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
find "$ROOT" -path '*/build.gradle.kts' -type f -print0 | sort -z > "$BUILD_LIST"
BUILD_FILES=()
while IFS= read -r -d '' file; do
  BUILD_FILES+=("$file")
done < "$BUILD_LIST"
dependency_pattern='^[[:space:]]*(implementation|api|compileOnly|runtimeOnly|testImplementation|testRuntimeOnly|kapt)[[:space:]]*\('
for build_file in "${BUILD_FILES[@]}"; do
  [[ -f "$build_file" ]] || continue
  dependency_matches="$(grep -HnE "$dependency_pattern" "$build_file" || true)"
  while IFS= read -r match; do
    [[ -z "$match" ]] && continue
    dependency_line="${match#*:}"
    dependency_line="${dependency_line#*:}"
    normalized_dependency="$(printf '%s' "$dependency_line" | tr -d '[:space:]')"
    allowed_dependency=false
    if [[ "$normalized_dependency" == 'implementation(kotlin("stdlib"))' \
       || "$normalized_dependency" == 'testImplementation(kotlin("test-junit"))' \
       || "$normalized_dependency" =~ ^implementation\(\"org\.jetbrains\.kotlin:kotlin-stdlib:[0-9]+(\.[0-9]+){1,3}\"\)$ \
       || "$normalized_dependency" =~ ^testImplementation\(\"org\.jetbrains\.kotlin:kotlin-test-junit:[0-9]+(\.[0-9]+){1,3}\"\)$ ]]; then
      allowed_dependency=true
    elif [[ "$build_file" == "$ROOT/app/build.gradle.kts" \
         && "$normalized_dependency" =~ ^(implementation|api)\((platform\()?\"androidx\..* ]]; then
      allowed_dependency=true
    fi
    if [[ "$allowed_dependency" != true ]]; then
      report_failure "unclassified-dependency $match"
    fi
  done <<< "$dependency_matches"
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
