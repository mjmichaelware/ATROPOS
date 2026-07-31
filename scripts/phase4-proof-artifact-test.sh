#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
OUTPUT="$TMP/phase4-proof.json"

ATROPOS_VAULT_KEY='AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=' \
  "$ROOT/scripts/phase4-proof-artifact.sh" --dry-run --output "$OUTPUT" >/dev/null

[[ -f "$OUTPUT" ]]
artifact="$(cat "$OUTPUT")"
for field in \
  '"timestamp"' \
  '"source_fingerprint"' \
  '"build_fingerprint"' \
  '"test_outcomes"' \
  '"redaction_result":{"status":"not_run","raw_test_output_persisted":false}' \
  '"vault_result":{"status":"not_run","key_material_persisted":false}' \
  '"proof_payload_sha256"'; do
  grep -Fq "$field" "$OUTPUT"
done

if grep -Fq 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=' "$OUTPUT"; then
  echo "phase4 proof artifact leaked key material" >&2
  exit 1
fi

printf '%s' "$artifact" | grep -Eq '"source_fingerprint":"[0-9a-f]{64}"'
printf '%s' "$artifact" | grep -Eq '"build_fingerprint":"[0-9a-f]{64}"'
