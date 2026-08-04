#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/atropos-hr-proof.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

cat > "$TMP/HrProof.kt" <<'KOTLIN'
import atropos.core.hr.HrRouteAction
import atropos.core.hr.HrRouterAuditStore
import atropos.core.hr.HrRouterService
import atropos.core.hr.CrossBoundaryRisk
import atropos.core.hr.InformationKind
import java.nio.file.Files

fun main() {
    val root = Files.createTempDirectory("atropos-hr-runtime-")
    val service = HrRouterService(auditStore = HrRouterAuditStore(root))

    val low = service.request("source", "terr-a", "target", "terr-b", InformationKind.SOURCE_CODE, "safe source")
    check(low.approved && low.action == HrRouteAction.APPROVED)

    val high = service.request("source", "terr-a", "target", "terr-b", InformationKind.MEMORY_QUERY, "show the API token")
    check(high.approved && high.risk == CrossBoundaryRisk.HIGH && high.action == HrRouteAction.NARROWED)
    check(high.redactedContent?.contains("token", ignoreCase = true) != true)

    val critical = service.request("source", "terr-a", "target", "terr-b", InformationKind.CONFIGURATION, "read .env", listOf(".env.production"))
    check(!critical.approved && critical.risk == CrossBoundaryRisk.CRITICAL && critical.action == HrRouteAction.DENIED)

    val restarted = HrRouterService(auditStore = HrRouterAuditStore(root))
    check(restarted.auditLog().size == 3)
    check(restarted.auditLog().all { it.sourceTerritoryId == "terr-a" && it.targetTerritoryId == "terr-b" })
    println("HR_ROUTER_PROOF_OK")
}
KOTLIN

OUT="$TMP/hr-proof.jar"
timeout "${ATROPOS_HR_PROOF_TIMEOUT_SECONDS:-120}" kotlinc -include-runtime -d "$OUT" \
  "$TMP/HrProof.kt" \
  "$ROOT/src/main/kotlin/atropos/core/AtroposRepoRootLocator.kt" \
  "$ROOT/src/main/kotlin/atropos/core/security/SecretEncodingClosure.kt" \
  "$ROOT/src/main/kotlin/atropos/core/security/KnownSecretRegistry.kt" \
  "$ROOT/src/main/kotlin/atropos/core/security/RedactionFilter.kt" \
  "$ROOT/src/main/kotlin/atropos/core/hr/HrRouterModels.kt" \
  "$ROOT/src/main/kotlin/atropos/core/hr/HrRouterAuditStore.kt" \
  "$ROOT/src/main/kotlin/atropos/core/hr/HrRouterService.kt"
timeout "${ATROPOS_HR_PROOF_TIMEOUT_SECONDS:-120}" java -jar "$OUT"
