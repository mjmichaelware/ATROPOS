#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/atropos-hierarchy-proof.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

cat > "$TMP/HierarchyProof.kt" <<'KOTLIN'
import atropos.core.hierarchy.AgentRecord
import atropos.core.hierarchy.AgentStatus
import atropos.core.hierarchy.HierarchyDispatchContract
import atropos.core.hierarchy.HierarchyDispatchResult
import atropos.core.hierarchy.HierarchyRegistry
import atropos.core.hierarchy.HierarchyRole

fun contract(parent: String, worker: String, territory: String) = HierarchyDispatchContract(
    parentAuthorityId = parent,
    assigneeId = worker,
    sourceCoordinates = listOf("source:S1@L1-L2"),
    territory = listOf(territory),
    capabilities = listOf("kotlin"),
    budgetTokens = 100,
    acceptanceCriteria = listOf("bounded diff"),
    rollbackPlan = "revert exact files"
)

fun main() {
    val registry = HierarchyRegistry()
    val owner = AgentRecord("owner", "human", HierarchyRole.HUMAN_OWNER, territoryId = "root")
    val manager = AgentRecord("manager", "manager", HierarchyRole.MANAGER, territoryId = "src/main/kotlin/atropos/core", capabilities = listOf("kotlin"))
    val worker = AgentRecord("worker", "worker", HierarchyRole.WORKER, capabilities = listOf("kotlin"))
    listOf(owner, manager, worker).forEach(registry::register)

    check(registry.dispatch(contract(owner.id, manager.id, "src/main/kotlin/atropos/core")) is HierarchyDispatchResult.Accepted)
    check(registry.dispatch(contract(manager.id, worker.id, "src/main/kotlin/atropos/core/agent")) is HierarchyDispatchResult.Accepted)
    val refused = registry.dispatch(contract(manager.id, worker.id, "src/main/kotlin/atropos/cli"))
    check(refused is HierarchyDispatchResult.Refused && refused.reason.contains("outside parent scope"))
    check(registry.get(worker.id)?.status == AgentStatus.ASSIGNED)
    println("HIERARCHY_DISPATCH_PROOF_OK")
}
KOTLIN

OUT="$TMP/hierarchy-proof.jar"
timeout "${ATROPOS_HIERARCHY_PROOF_TIMEOUT_SECONDS:-120}" kotlinc -include-runtime -d "$OUT" \
  "$TMP/HierarchyProof.kt" \
  "$ROOT/src/main/kotlin/atropos/core/hierarchy/HierarchyModels.kt"
timeout "${ATROPOS_HIERARCHY_PROOF_TIMEOUT_SECONDS:-120}" java -jar "$OUT"
