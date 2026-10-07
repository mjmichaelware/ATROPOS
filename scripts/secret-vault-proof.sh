#!/usr/bin/env bash
set -euo pipefail

# Operator-run focused proof. It intentionally requires a supplied external key;
# the proof never prints or persists that key.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${ATROPOS_VAULT_KEY:?set ATROPOS_VAULT_KEY to a base64-encoded AES-256 key}"

KEY_BYTES="$(printf '%s' "$ATROPOS_VAULT_KEY" | base64 -d 2>/dev/null | wc -c | tr -d ' ')"
if [ "$KEY_BYTES" != "32" ]; then
  echo "vault proof refused: ATROPOS_VAULT_KEY is not a 256-bit base64 key" >&2
  exit 2
fi

cd "$ROOT"
./gradlew test \
  --tests 'atropos.core.security.SecretVaultKeyProviderTest' \
  --tests 'atropos.core.security.TokenIsolationVaultTest' \
  --tests 'atropos.core.security.TokenIsolationVaultEncryptionContractTest' \
  --tests 'atropos.core.security.SecretVaultRuntimeProofTest'

echo "vault proof passed: external key, ciphertext-at-rest, tamper refusal, wrong-key refusal, and name binding"
