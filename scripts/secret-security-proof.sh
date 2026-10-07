#!/usr/bin/env bash
set -euo pipefail

# Complete Phase 4 focused proof. The key is consumed by the test process only
# and is never echoed or written by this script.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${ATROPOS_VAULT_KEY:?set ATROPOS_VAULT_KEY to a base64-encoded AES-256 key}"

KEY_BYTES="$(printf '%s' "$ATROPOS_VAULT_KEY" | base64 -d 2>/dev/null | wc -c | tr -d ' ')"
if [ "$KEY_BYTES" != "32" ]; then
  echo "secret security proof refused: ATROPOS_VAULT_KEY is not a 256-bit base64 key" >&2
  exit 2
fi

cd "$ROOT"
./gradlew test \
  --tests 'atropos.core.security.SecretVaultKeyProviderTest' \
  --tests 'atropos.core.security.TokenIsolationVaultTest' \
  --tests 'atropos.core.security.TokenIsolationVaultEncryptionContractTest' \
  --tests 'atropos.core.security.SecretVaultRuntimeProofTest' \
  --tests 'atropos.core.security.RedactionFilterTest' \
  --tests 'atropos.core.security.KnownSecretEgressTest' \
  --tests 'atropos.core.security.SecretEnrollmentSourceTest' \
  --tests 'atropos.core.agent.AgentSecurityRedactionSurfaceTest' \
  --tests 'atropos.core.agent.LeaseTokenDigestTest' \
  --tests 'atropos.core.agent.LeaseTokenPersistenceTest'

echo "secret security proof passed: encrypted vault, redacted egress, typed enrollment, and non-bearer lease persistence"
