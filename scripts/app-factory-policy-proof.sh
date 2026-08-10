#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/atropos-app-policy-proof.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

cat > "$TMP/PolicyProof.kt" <<'KOTLIN'
import atropos.core.factory.AppProjectMutationGate
import java.nio.file.Files

fun main() {
    val root = Files.createTempDirectory("atropos-policy-proof-")
    val target = root.resolve(".atropos/generated-projects/weather-1")
    AppProjectMutationGate(root).requireAllowed(root, target)

    val outside = Files.createTempDirectory("atropos-policy-outside-")
    runCatching {
        AppProjectMutationGate(root).requireAllowed(root, outside.resolve("generated"))
    }.onSuccess { error("outside target was allowed") }

    val otherRoot = Files.createTempDirectory("atropos-policy-other-")
    runCatching {
        AppProjectMutationGate(root).requireAllowed(otherRoot, otherRoot.resolve(".atropos/generated-projects/app"))
    }.onSuccess { error("mismatched root was allowed") }

    val redirected = Files.createTempDirectory("atropos-policy-redirected-")
    val generatedRoot = root.resolve(".atropos/generated-projects")
    Files.createDirectories(generatedRoot)
    val link = generatedRoot.resolve("linked")
    java.nio.file.Files.createSymbolicLink(link, redirected)
    runCatching {
        AppProjectMutationGate(root).requireAllowed(root, link.resolve("app"))
    }.onSuccess { error("symlinked generated-project target was allowed") }

    println("APP_FACTORY_POLICY_PROOF_OK")
}
KOTLIN

OUT="$TMP/policy-proof.jar"
timeout "${ATROPOS_POLICY_PROOF_TIMEOUT_SECONDS:-120}" kotlinc -include-runtime -d "$OUT" \
  "$TMP/PolicyProof.kt" \
  "$ROOT/src/main/kotlin/atropos/core/AtroposRepoRootLocator.kt" \
  "$ROOT/src/main/kotlin/atropos/core/security/SecretEncodingClosure.kt" \
  "$ROOT/src/main/kotlin/atropos/core/security/KnownSecretRegistry.kt" \
  "$ROOT/src/main/kotlin/atropos/core/security/RedactionFilter.kt" \
  "$ROOT/src/main/kotlin/atropos/core/policy/ActionActor.kt" \
  "$ROOT/src/main/kotlin/atropos/core/policy/ActionProposal.kt" \
  "$ROOT/src/main/kotlin/atropos/core/policy/CapabilityEnforcer.kt" \
  "$ROOT/src/main/kotlin/atropos/core/policy/ExecutionPolicyEngine.kt" \
  "$ROOT/src/main/kotlin/atropos/core/policy/BoundedAgencyGate.kt" \
  "$ROOT/src/main/kotlin/atropos/core/director/DirectorModels.kt" \
  "$ROOT/src/main/kotlin/atropos/core/director/DirectorStore.kt" \
  "$ROOT/src/main/kotlin/atropos/core/director/DirectorService.kt" \
  "$ROOT/src/main/kotlin/atropos/core/territory/TerritoryModels.kt" \
  "$ROOT/src/main/kotlin/atropos/core/territory/TerritoryStore.kt" \
  "$ROOT/src/main/kotlin/atropos/core/territory/TerritoryService.kt" \
  "$ROOT/src/main/kotlin/atropos/core/territory/TerritoryGrantService.kt" \
  "$ROOT/src/main/kotlin/atropos/core/factory/AppProjectMutationAuthorizer.kt" \
  "$ROOT/src/main/kotlin/atropos/core/factory/AppProjectMutationGate.kt"
timeout "${ATROPOS_POLICY_PROOF_TIMEOUT_SECONDS:-120}" java -jar "$OUT"
