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
mapfile -d '' FACTORY_SOURCES < <(
  find \
    "$ROOT/src/main/kotlin/atropos/core" \
    "$ROOT/src/main/kotlin/atropos/ast" \
    "$ROOT/src/main/kotlin/atropos/dloi" \
    "$ROOT/src/main/kotlin/atropos/cli/input" \
    -type f -name '*.kt' -print0 | sort -z
)
timeout "${ATROPOS_FACTORY_PROOF_TIMEOUT_SECONDS:-120}" kotlinc -d "$OUT" \
  "$TMP/ProductionProof.kt" "${FACTORY_SOURCES[@]}"
timeout "${ATROPOS_FACTORY_PROOF_TIMEOUT_SECONDS:-120}" kotlin -classpath "$OUT" ProductionProofKt
