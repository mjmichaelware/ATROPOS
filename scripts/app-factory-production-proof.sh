#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/atropos-app-factory-production.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

cat > "$TMP/ProductionProof.kt" <<'KOTLIN'
import atropos.core.factory.AppProjectGenerator
import atropos.core.factory.AppProjectSpecParser
import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val root = Files.createTempDirectory("atropos-production-factory-")
    val generated = AppProjectGenerator(root).generateApp(
        AppProjectSpecParser().parse("Build a calculator CLI with tests and README"),
        "production-proof",
        planningDagId = "dag-production-proof",
        plannedAtomIds = listOf("atom-plan", "atom-code", "atom-verify")
    )
    val target = Path.of(generated.path)
    check(Files.exists(target.resolve("README.md")))
    check(Files.exists(target.resolve("LICENSE")))
    check(Files.exists(target.resolve(".gitignore")))
    check(Files.exists(target.resolve("AGENTS.md")))
    check(generated.commitId.matches(Regex("[0-9a-f]{40}")))
    check(Files.size(Path.of(generated.exportPath)) > 0)
    val evidence = Files.readString(Path.of(generated.evidencePath))
    check("planning_dag=dag-production-proof" in evidence)
    check("planning_atoms=atom-plan,atom-code,atom-verify" in evidence)
    println("APP_FACTORY_PRODUCTION_PROOF_OK")
}
KOTLIN

OUT="$TMP/production-proof.jar"
timeout "${ATROPOS_FACTORY_PROOF_TIMEOUT_SECONDS:-120}" kotlinc -d "$OUT" \
  "$TMP/ProductionProof.kt" \
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
  "$ROOT/src/main/kotlin/atropos/core/factory/AppActionRegistry.kt" \
  "$ROOT/src/main/kotlin/atropos/core/factory/AppIntent.kt" \
  "$ROOT/src/main/kotlin/atropos/core/factory/AppProjectSpec.kt" \
  "$ROOT/src/main/kotlin/atropos/core/factory/AppProjectSpecParser.kt" \
  "$ROOT/src/main/kotlin/atropos/core/hierarchy/HierarchyModels.kt" \
  "$ROOT/src/main/kotlin/atropos/core/hierarchy/HierarchyTaskLifecycle.kt" \
  "$ROOT/src/main/kotlin/atropos/core/hierarchy/HierarchyRegistry.kt" \
  "$ROOT/src/main/kotlin/atropos/core/factory/FactoryHierarchyGate.kt" \
  "$ROOT/src/main/kotlin/atropos/core/factory/AppProjectMutationAuthorizer.kt" \
  "$ROOT/src/main/kotlin/atropos/core/factory/AppProjectMutationGate.kt" \
  "$ROOT/src/main/kotlin/atropos/core/factory/RepoScaffold.kt" \
  "$ROOT/src/main/kotlin/atropos/core/factory/EvidenceManifest.kt" \
  "$ROOT/src/main/kotlin/atropos/core/factory/AppProjectGenerator.kt" \
  "$ROOT/src/main/kotlin/atropos/core/worktree/BoundedGitWorktreeCommandRunner.kt"
timeout "${ATROPOS_FACTORY_PROOF_TIMEOUT_SECONDS:-120}" kotlin -classpath "$OUT" ProductionProofKt
