#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
JAR="${1:-}"
if [[ -z "$JAR" || ! -f "$JAR" ]]; then
  echo "usage: $0 /path/to/current/ATROPOS.jar" >&2
  exit 2
fi

JAR="$(cd "$(dirname "$JAR")" && pwd)/$(basename "$JAR")"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/atropos-app-factory-installed.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT
PROMPT="${ATROPOS_FACTORY_PROMPT:-Build a simple calculator CLI with tests and README}"
PROMPT_SHA256="$(printf '%s' "$PROMPT" | sha256sum | awk '{print $1}')"

timeout "${ATROPOS_FACTORY_INSTALLED_TIMEOUT_SECONDS:-180}" \
  sh -c 'printf "%s\n" "$2" "/exit" | java -jar "$1"' \
  sh "$JAR" "$PROMPT" >"$TMP/output.txt" 2>&1

require_output() {
  rg -q --fixed-strings "$1" "$TMP/output.txt" || {
    echo "APP_FACTORY_INSTALLED_PROOF_MISSING $1" >&2
    sed -n '1,240p' "$TMP/output.txt" >&2
    exit 1
  }
}

require_output "factory run completed:"
require_output "generated_project:"
require_output "generated_commit:"
require_output "generated_evidence:"
require_output "planning_dag:"

JAR_SHA256="$(sha256sum "$JAR" | awk '{print $1}')"
printf '%s\n' \
  'APP_FACTORY_INSTALLED_PROOF_OK' \
  "prompt_sha256=$PROMPT_SHA256" \
  "jar=$JAR" \
  "jar_sha256=$JAR_SHA256" \
  "output_sha256=$(sha256sum "$TMP/output.txt" | awk '{print $1}')"
