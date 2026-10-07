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
require_output "generated_branch:"
require_output "generated_evidence:"
require_output "planning_dag:"

generated_project() {
  sed -n 's/^  generated_project: //p' "$TMP/output.txt" | tail -1
}

generated_evidence() {
  sed -n 's/^  generated_evidence: //p' "$TMP/output.txt" | tail -1
}

generated_commit() {
  sed -n 's/^  generated_commit: //p' "$TMP/output.txt" | tail -1
}

generated_branch() {
  sed -n 's/^  generated_branch: //p' "$TMP/output.txt" | tail -1
}

PROJECT="$(generated_project)"
EVIDENCE="$(generated_evidence)"
COMMIT="$(generated_commit)"
BRANCH="$(generated_branch)"
[[ -n "$PROJECT" && -d "$PROJECT" ]] || {
  echo "APP_FACTORY_INSTALLED_PROOF_INVALID_PROJECT" >&2
  exit 1
}
[[ -n "$EVIDENCE" && -f "$EVIDENCE" ]] || {
  echo "APP_FACTORY_INSTALLED_PROOF_INVALID_EVIDENCE" >&2
  exit 1
}

for required in README.md LICENSE .gitignore AGENTS.md; do
  [[ -f "$PROJECT/$required" ]] || {
    echo "APP_FACTORY_INSTALLED_PROOF_MISSING_FILE $required" >&2
    exit 1
  }
done

find "$PROJECT/src/main" -type f -name '*.kt' -print -quit | grep -q . || {
  echo "APP_FACTORY_INSTALLED_PROOF_MISSING_SOURCE" >&2
  exit 1
}
find "$PROJECT/src/test" -type f -name '*.kt' -print -quit | grep -q . || {
  echo "APP_FACTORY_INSTALLED_PROOF_MISSING_TESTS" >&2
  exit 1
}
git -C "$PROJECT" rev-parse --verify HEAD >/dev/null 2>&1 || {
  echo "APP_FACTORY_INSTALLED_PROOF_MISSING_GIT_HISTORY" >&2
  exit 1
}
[[ "$COMMIT" == "$(git -C "$PROJECT" rev-parse HEAD)" ]] || {
  echo "APP_FACTORY_INSTALLED_PROOF_COMMIT_MISMATCH" >&2
  exit 1
}
[[ "$BRANCH" == "$(git -C "$PROJECT" branch --show-current)" ]] || {
  echo "APP_FACTORY_INSTALLED_PROOF_BRANCH_MISMATCH" >&2
  exit 1
}
rg -q -- 'prompt_fingerprint=|tree_sha256=' "$EVIDENCE" || {
  echo "APP_FACTORY_INSTALLED_PROOF_INCOMPLETE_EVIDENCE" >&2
  exit 1
}
if rg -n -- 'Calculator: calculator|feature-string-only' "$PROJECT/src" >/dev/null 2>&1 || {
  rg -q -- 'isNotBlank\(\)' "$PROJECT/src" &&
  ! rg -q -- 'fun (runApp|evaluate)\(' "$PROJECT/src"
}; then
  echo "APP_FACTORY_INSTALLED_PROOF_SCAFFOLD_OUTPUT" >&2
  exit 1
fi

JAR_SHA256="$(sha256sum "$JAR" | awk '{print $1}')"
printf '%s\n' \
  'APP_FACTORY_INSTALLED_PROOF_OK' \
  "generated_project=$PROJECT" \
  "generated_branch=$BRANCH" \
  "generated_commit=$COMMIT" \
  "generated_evidence=$EVIDENCE" \
  "prompt_sha256=$PROMPT_SHA256" \
  "jar=$JAR" \
  "jar_sha256=$JAR_SHA256" \
  "output_sha256=$(sha256sum "$TMP/output.txt" | awk '{print $1}')"
