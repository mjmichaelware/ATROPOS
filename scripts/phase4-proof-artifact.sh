#!/usr/bin/env bash
set -euo pipefail

# Persist Phase 4 proof metadata without retaining command output or key material.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT="$ROOT/.atropos/self-hosting/proofs/phase4-security-proof.json"
DRY_RUN=false

usage() {
  echo "usage: $0 [--output PATH] [--dry-run]" >&2
}

while (($#)); do
  case "$1" in
    --output)
      OUTPUT="${2:?missing output path}"
      shift 2
      ;;
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    *)
      usage
      exit 2
      ;;
  esac
done

: "${ATROPOS_VAULT_KEY:?set ATROPOS_VAULT_KEY to a base64-encoded AES-256 key}"
KEY_BYTES="$(printf '%s' "$ATROPOS_VAULT_KEY" | base64 -d 2>/dev/null | wc -c | tr -d ' ')"
if [[ "$KEY_BYTES" != "32" ]]; then
  echo "phase4 proof artifact refused: ATROPOS_VAULT_KEY is not a 256-bit base64 key" >&2
  exit 2
fi

sha256() {
  sha256sum | awk '{print $1}'
}

status_for() {
  local name="$1"
  shift
  if "$@" >/dev/null 2>&1; then
    printf '%s\t%s\n' "$name" "passed"
  else
    printf '%s\t%s\n' "$name" "failed"
  fi
}

source_fingerprint="$((cd "$ROOT" && git ls-files -s) | sha256)"
build_fingerprint="$({
  cd "$ROOT"
  git rev-parse HEAD
  sha256sum gradlew
  sha256sum gradle/wrapper/gradle-wrapper.properties
} | sha256)"
timestamp="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

if "$DRY_RUN"; then
  vault_status="not_run"
  redaction_status="not_run"
  persistence_status="not_run"
else
  vault_status="$(status_for encrypted_vault_runtime bash "$ROOT/scripts/secret-vault-proof.sh" | cut -f2)"
  redaction_status="$(status_for redaction_canary "$ROOT/gradlew" -p "$ROOT" test \
    --tests 'atropos.core.security.RedactionFilterTest' \
    --tests 'atropos.core.security.KnownSecretEgressTest' \
    --tests 'atropos.core.security.SecretEnrollmentSourceTest' | cut -f2)"
  persistence_status="$(status_for persisted_surface bash "$ROOT/scripts/secret-security-proof.sh" | cut -f2)"
fi

outcomes_json=$(printf '{"encrypted_vault_runtime":"%s","redaction_canary":"%s","persisted_surface":"%s"}' \
  "$vault_status" "$redaction_status" "$persistence_status")
redaction_result="$(if [[ "$redaction_status" == "passed" ]]; then printf 'passed'; else printf 'failed'; fi)"
vault_result="$(if [[ "$vault_status" == "passed" ]]; then printf 'passed'; else printf 'failed'; fi)"
if "$DRY_RUN"; then
  redaction_result="not_run"
  vault_result="not_run"
fi

payload=$(printf '{"schema_version":1,"timestamp":"%s","source_fingerprint":"%s","build_fingerprint":"%s","test_outcomes":%s,"redaction_result":{"status":"%s","raw_test_output_persisted":false},"vault_result":{"status":"%s","key_material_persisted":false}}' \
  "$timestamp" "$source_fingerprint" "$build_fingerprint" "$outcomes_json" "$redaction_result" "$vault_result")
payload_sha256="$(printf '%s' "$payload" | sha256)"
artifact=$(printf '{"phase":"C1-P4","proof":%s,"hashes":{"proof_payload_sha256":"%s","source_manifest_sha256":"%s","build_inputs_sha256":"%s"}}\n' \
  "$payload" "$payload_sha256" "$source_fingerprint" "$build_fingerprint")

mkdir -p "$(dirname "$OUTPUT")"
umask 077
printf '%s' "$artifact" >"$OUTPUT"

if ! "$DRY_RUN" && [[ "$vault_result" != "passed" || "$redaction_result" != "passed" || "$persistence_status" != "passed" ]]; then
  echo "phase4 proof artifact recorded non-passing result: $OUTPUT" >&2
  exit 1
fi

echo "phase4 proof artifact recorded: $OUTPUT"
