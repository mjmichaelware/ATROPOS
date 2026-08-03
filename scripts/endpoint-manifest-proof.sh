#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/atropos-endpoint-proof.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

cat > "$TMP/EndpointProof.kt" <<'KOTLIN'
import atropos.core.endpoint.StaticOperationRegistry

fun main() {
    val registry = StaticOperationRegistry()
    val endpoints = registry.getAll()
    check(endpoints.isNotEmpty())
    endpoints.forEach { endpoint ->
        val manifest = endpoint.manifest
        check(manifest.owner.isNotBlank())
        check(manifest.input.isNotBlank() && manifest.output.isNotBlank())
        check(manifest.errors.isNotEmpty() && manifest.auth.isNotBlank())
        check(manifest.timeoutMs > 0 && manifest.retryPolicy.isNotBlank())
        check(manifest.testIds.isNotEmpty())
    }
    check(registry.getById("tool.git.status")?.manifest?.sideEffects?.contains("read-git-state") == true)
    println("ENDPOINT_MANIFEST_PROOF_OK")
}
KOTLIN

OUT="$TMP/endpoint-proof.jar"
timeout "${ATROPOS_ENDPOINT_PROOF_TIMEOUT_SECONDS:-120}" kotlinc -include-runtime -d "$OUT" \
  "$TMP/EndpointProof.kt" \
  "$ROOT/src/main/kotlin/atropos/core/endpoint/EndpointKind.kt" \
  "$ROOT/src/main/kotlin/atropos/core/endpoint/OperationEndpoint.kt" \
  "$ROOT/src/main/kotlin/atropos/core/endpoint/OperationRegistry.kt" \
  "$ROOT/src/main/kotlin/atropos/core/endpoint/StaticOperationRegistry.kt"
timeout "${ATROPOS_ENDPOINT_PROOF_TIMEOUT_SECONDS:-120}" java -jar "$OUT"
