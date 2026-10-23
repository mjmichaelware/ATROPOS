/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import atropos.core.verification.Z3SolverBridge

/**
 * SD7-T1-TEL12: CDCL Conflict & SMT Proof Harvester.
 *
 * Extracts conflict clauses, minimal UNSAT cores, and anti-unification invariant
 * rules from SMT solvers to prune infeasible search space partitions.
 */
class CdclConflictAndSmtProofHarvester(
    private val ringBuffer: TelemetryRingBuffer? = null,
    private val solverTimeoutMs: Long = 3000L
) {
    fun recordSmtProof(
        instanceId: String,
        smtLibDigest: String,
        unsatCoreSize: Int,
        antiUnificationRule: String,
        solveTimeMs: Long,
        memoryBytes: Long = 0L
    ) {
        val payload = CdclConflictClauseRecord(
            clauseDigest = smtLibDigest,
            minimalUnsatCoreSize = unsatCoreSize,
            antiUnificationRule = antiUnificationRule.take(500),
            solveTimeMs = solveTimeMs.coerceAtMost(solverTimeoutMs),
            memoryBytes = memoryBytes
        )

        val record = FrontierSweTelemetryRecord.create(
            instanceId = instanceId,
            h0PromptLineage = "smt-$smtLibDigest",
            payload = payload,
            rewardSignal = if (unsatCoreSize > 0) 1.0 else 0.0
        )
        ringBuffer?.offer(record)
    }
}
