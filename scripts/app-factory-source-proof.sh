#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/atropos-app-factory-proof.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

cat > "$TMP/FactoryProof.kt" <<'KOTLIN'
import atropos.core.factory.AppProjectGenerator
import atropos.core.factory.AppProjectMutationAuthorizer
import atropos.core.factory.AppProjectSpecParser
import java.nio.file.Files

fun main() {
    val root = Files.createTempDirectory("atropos-factory-source-proof-")
    val authorizer = AppProjectMutationAuthorizer { repoRoot, target ->
        check(target.normalize().startsWith(repoRoot.resolve(".atropos/generated-projects").normalize()))
    }
    val generator = AppProjectGenerator(root, mutationGate = authorizer)
    val calculator = generator.generateApp(
        AppProjectSpecParser().parse("Build a simple calculator CLI with tests and README"),
        "calculator-proof",
        planningDagId = "dag-calculator-proof",
        plannedAtomIds = listOf("atom-plan", "atom-code", "atom-verify")
    )
    val notes = generator.generateApp("Make a notes CLI with tests", "notes-proof")

    listOf(calculator, notes).forEach { project ->
        val target = project.path
        check(Files.exists(java.nio.file.Path.of(target, "README.md")))
        check(Files.exists(java.nio.file.Path.of(target, "LICENSE")))
        check(Files.exists(java.nio.file.Path.of(target, ".gitignore")))
        check(Files.exists(java.nio.file.Path.of(target, "AGENTS.md")))
        check(project.commitId.matches(Regex("[0-9a-f]{40}")))
        check(Files.size(java.nio.file.Path.of(project.exportPath)) > 0)
        check(Files.readString(java.nio.file.Path.of(project.evidencePath)).contains("tree_sha256=${project.treeSha256}"))
    }
    val calculatorEvidence = Files.readString(java.nio.file.Path.of(calculator.evidencePath))
    check("planning_dag=dag-calculator-proof" in calculatorEvidence)
    check("planning_atoms=atom-plan,atom-code,atom-verify" in calculatorEvidence)
    println("APP_FACTORY_SOURCE_PROOF_OK")
}
KOTLIN

cat > "$TMP/AppProjectMutationGate.kt" <<'KOTLIN'
package atropos.core.factory

import java.nio.file.Path

class AppProjectMutationGate(private val root: Path) : AppProjectMutationAuthorizer {
    override fun requireAllowed(repoRoot: Path, target: Path) {
        check(repoRoot.toAbsolutePath().normalize() == root.toAbsolutePath().normalize())
        check(target.normalize().startsWith(root.resolve(".atropos/generated-projects").normalize()))
    }
}
KOTLIN

OUT="$TMP/factory-proof.jar"
if timeout "${ATROPOS_SOURCE_PROOF_TIMEOUT_SECONDS:-120}" kotlinc -d "$OUT" \
  "$TMP/FactoryProof.kt" \
  "$TMP/AppProjectMutationGate.kt" \
  "$ROOT/src/main/kotlin/atropos/core/security/SecretEncodingClosure.kt" \
  "$ROOT/src/main/kotlin/atropos/core/security/KnownSecretRegistry.kt" \
  "$ROOT/src/main/kotlin/atropos/core/security/RedactionFilter.kt" \
  "$ROOT/src/main/kotlin/atropos/core/factory/AppActionRegistry.kt" \
  "$ROOT/src/main/kotlin/atropos/core/factory/AppIntent.kt" \
  "$ROOT/src/main/kotlin/atropos/core/factory/AppProjectSpec.kt" \
  "$ROOT/src/main/kotlin/atropos/core/factory/AppProjectSpecParser.kt" \
  "$ROOT/src/main/kotlin/atropos/core/hierarchy/HierarchyModels.kt" \
  "$ROOT/src/main/kotlin/atropos/core/hierarchy/HierarchyTaskLifecycle.kt" \
  "$ROOT/src/main/kotlin/atropos/core/hierarchy/HierarchyRegistry.kt" \
  "$ROOT/src/main/kotlin/atropos/core/factory/FactoryHierarchyGate.kt" \
  "$ROOT/src/main/kotlin/atropos/core/factory/AppProjectMutationAuthorizer.kt" \
  "$ROOT/src/main/kotlin/atropos/core/factory/RepoScaffold.kt" \
  "$ROOT/src/main/kotlin/atropos/core/factory/EvidenceManifest.kt" \
  "$ROOT/src/main/kotlin/atropos/core/factory/AppProjectGenerator.kt" \
  "$ROOT/src/main/kotlin/atropos/core/worktree/BoundedGitWorktreeCommandRunner.kt"; then
  :
else
  status=$?
  if [[ "$status" -eq 124 ]]; then
    echo "APP_FACTORY_SOURCE_PROOF_TIMEOUT seconds=${ATROPOS_SOURCE_PROOF_TIMEOUT_SECONDS:-120}" >&2
  fi
  exit "$status"
fi
kotlin -classpath "$OUT" FactoryProofKt
