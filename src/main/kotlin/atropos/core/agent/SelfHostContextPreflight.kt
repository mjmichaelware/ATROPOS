package atropos.core.agent

import atropos.core.dag.DagNode
import atropos.core.provider.ContextEnvelope
import atropos.core.provider.ContextEnvelopeFactory
import java.nio.file.Path

data class SelfHostContextPreflightResult(
    val passed: Boolean,
    val evidence: String,
    val failureReason: String? = null
)

/**
 * Pre-dispatch context gate for self-host execution.
 *
 * Provider response attestation proves what a provider saw after dispatch.
 * This preflight proves the local self-host controller is about to dispatch
 * the exact goal/DAG/node context it claims, before any node can mutate state.
 */
class SelfHostContextPreflight(
    private val repoRoot: Path
) {
    fun canonicalEnvelope(record: GoalRunRecord, node: DagNode): ContextEnvelope =
        ContextEnvelopeFactory.createForGoal(
            providerId = "self-host",
            modelId = "cradle-local",
            task = node.actionPayload ?: node.label,
            repoRoot = repoRoot,
            goal = record,
            dagNode = node
        )

    fun verify(
        canonical: ContextEnvelope,
        supplied: ContextEnvelope?
    ): SelfHostContextPreflightResult {
        if (supplied == null) {
            return failed("context envelope missing")
        }
        if (supplied.systemIdentity != "ATROPOS") {
            return failed("context identity mismatch: ${supplied.systemIdentity}")
        }
        val recomputed = ContextEnvelopeFactory.computeHash(supplied.copy(canonicalContextHash = ""))
        if (supplied.canonicalContextHash != recomputed) {
            return failed("context hash forged: expected=$recomputed observed=${supplied.canonicalContextHash}")
        }
        val mismatches = listOfNotNull(
            mismatch("repository", canonical.repository, supplied.repository),
            mismatch("repositoryRoot", canonical.repositoryRoot, supplied.repositoryRoot),
            mismatch("branch", canonical.branch, supplied.branch),
            mismatch("baselineCommit", canonical.baselineCommit, supplied.baselineCommit),
            mismatch("goalId", canonical.goalId, supplied.goalId),
            mismatch("runId", canonical.runId, supplied.runId),
            mismatch("dagId", canonical.dagId, supplied.dagId),
            mismatch("nodeId", canonical.nodeId, supplied.nodeId),
            mismatch("task", canonical.task, supplied.task),
            mismatch("phaseOrPass", canonical.phaseOrPass, supplied.phaseOrPass),
            mismatch("hierarchyRole", canonical.hierarchyRole, supplied.hierarchyRole),
            mismatch("authority", canonical.authority, supplied.authority),
            mismatch("activePolicy", canonical.activePolicy, supplied.activePolicy),
            mismatch("providerId", canonical.providerId, supplied.providerId),
            mismatch("modelId", canonical.modelId, supplied.modelId),
            mismatch("contextVersion", canonical.contextVersion, supplied.contextVersion),
            mismatchList("permissions", canonical.permissions, supplied.permissions),
            mismatchList("assignedTerritory", canonical.assignedTerritory, supplied.assignedTerritory),
            mismatchList("prohibitedActions", canonical.prohibitedActions, supplied.prohibitedActions)
        )
        if (mismatches.isNotEmpty()) {
            return failed("context envelope mismatch: ${mismatches.joinToString("; ")}")
        }
        if (canonical.canonicalContextHash != supplied.canonicalContextHash) {
            return failed("context hash mismatch: expected=${canonical.canonicalContextHash} observed=${supplied.canonicalContextHash}")
        }
        return SelfHostContextPreflightResult(
            passed = true,
            evidence = "context_preflight_verified goal=${canonical.goalId} dag=${canonical.dagId} node=${canonical.nodeId} hash=${canonical.canonicalContextHash}"
        )
    }

    private fun failed(reason: String): SelfHostContextPreflightResult =
        SelfHostContextPreflightResult(
            passed = false,
            evidence = "context_preflight_failed reason=$reason",
            failureReason = reason
        )

    private fun mismatch(field: String, expected: String, observed: String): String? =
        if (expected == observed) null else "$field expected=$expected observed=$observed"

    private fun mismatchList(field: String, expected: List<String>, observed: List<String>): String? =
        if (expected == observed) null else "$field expected=${expected.joinToString(",")} observed=${observed.joinToString(",")}"
}
