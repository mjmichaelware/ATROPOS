#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/atropos-governance-proof.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

cat > "$TMP/DeterministicVerifier.kt" <<'KOTLIN'
package atropos.core.verification

import java.nio.file.Path

enum class DiagnosticSeverity { ERROR, WARNING, INFO }
data class DeterministicFinding(
    val severity: DiagnosticSeverity,
    val file: String? = null,
    val evidence: String = "",
    val remediation: String = ""
)
data class DeterministicVerificationResult(val findings: List<DeterministicFinding>)
class DeterministicVerifier(private val root: Path) {
    fun verify(paths: List<Path>): DeterministicVerificationResult = DeterministicVerificationResult(emptyList())
}
KOTLIN

cat > "$TMP/GovernanceProof.kt" <<'KOTLIN'
import atropos.core.auditor.AuditSeverity
import atropos.core.auditor.AuditorService
import atropos.core.director.DirectorService
import atropos.core.director.DirectorStore
import atropos.core.director.DriftSeverity
import atropos.core.director.ObservationKind
import atropos.core.territory.TerritoryAssignment
import java.nio.file.Files

fun main() {
    val root = Files.createTempDirectory("atropos-governance-")
    val director = DirectorService(DirectorStore(root), root)
    director.observe(
        kind = ObservationKind.TERRITORY_VIOLATION,
        severity = DriftSeverity.WARNING,
        source = "proof",
        details = "api_key=proof-secret",
        files = listOf("src/main/kotlin/atropos/core/factory/AppFactoryRouter.kt"),
        goalId = "goal-proof",
        territoryId = "territory-proof"
    )
    val advisory = director.advisoryBeforePromotion(
        goalId = "goal-proof",
        territoryIds = listOf("territory-proof"),
        files = listOf("src/main/kotlin/atropos/core/factory/AppFactoryRouter.kt")
    )
    check(!advisory.allowed && advisory.blockingObservations.size == 1)
    check(!Files.readString(root.resolve(".atropos/director/observations.jsonl")).contains("proof-secret"))

    val auditor = AuditorService(root)
    auditor.auditTerritories(listOf(TerritoryAssignment("bad", "worker", "WORKER", "")))
    val blocked = auditor.blockPromotion(claimedBy = "worker", auditedBy = "auditor")
    val selfApproved = AuditorService(root).blockPromotion(claimedBy = "worker", auditedBy = "worker")
    check(!blocked.allowed && blocked.blockingFindings.any { it.severity == AuditSeverity.FAILURE })
    check(!selfApproved.allowed && selfApproved.blockingFindings.any { it.check == "auditor-independence" })
    println("GOVERNANCE_PROOF_OK")
}
KOTLIN

OUT="$TMP/governance-proof.jar"
timeout "${ATROPOS_GOVERNANCE_PROOF_TIMEOUT_SECONDS:-120}" kotlinc -include-runtime -d "$OUT" \
  "$TMP/DeterministicVerifier.kt" \
  "$TMP/GovernanceProof.kt" \
  "$ROOT/src/main/kotlin/atropos/core/AtroposRepoRootLocator.kt" \
  "$ROOT/src/main/kotlin/atropos/core/security/SecretEncodingClosure.kt" \
  "$ROOT/src/main/kotlin/atropos/core/security/KnownSecretRegistry.kt" \
  "$ROOT/src/main/kotlin/atropos/core/security/RedactionFilter.kt" \
  "$ROOT/src/main/kotlin/atropos/core/territory/TerritoryModels.kt" \
  "$ROOT/src/main/kotlin/atropos/core/director/DirectorDriftScorer.kt" \
  "$ROOT/src/main/kotlin/atropos/core/director/DirectorModels.kt" \
  "$ROOT/src/main/kotlin/atropos/core/director/DirectorStore.kt" \
  "$ROOT/src/main/kotlin/atropos/core/director/DirectorService.kt" \
  "$ROOT/src/main/kotlin/atropos/core/auditor/AuditorService.kt"
timeout "${ATROPOS_GOVERNANCE_PROOF_TIMEOUT_SECONDS:-120}" java -jar "$OUT"
