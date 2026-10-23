/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import atropos.core.dopamine.TarjanGraphClusterer
import java.security.MessageDigest

/**
 * SD7-T1-TEL12: CDCL Conflict & SMT Proof Harvester.
 *
 * Implements automated deduction telemetry:
 * 1. Tarjan SCC relational cycle detection in clause dependency graphs.
 * 2. Minimal UNSAT core extraction from cyclical conflict cores.
 * 3. Anti-unification template generation.
 * 4. SMT-LIB2 digest generation and infeasible partition indexing.
 */
class CdclConflictAndSmtProofHarvester(
    private val ringBuffer: TelemetryRingBuffer? = null
) {
    companion object {
        const val SOLVER_TIMEOUT_MS: Long = 3000L
    }

    data class DeductionResult(
        val smtLib2Digest: String,
        val minimalUnsatCore: List<String>,
        val relationalCycles: List<Set<String>>,
        val antiUnificationTemplates: List<String>,
        val solveTimeMs: Long
    )

    /**
     * Extracts minimal UNSAT cores and relational cycles using Tarjan's SCC on conflict clauses.
     */
    fun analyzeConflictGraph(
        clauseDependencies: Map<String, Set<String>>,
        rawSmtScript: String,
        solveTimeMs: Long
    ): DeductionResult {
        val digest = sha256Hex(rawSmtScript)

        // Find relational cycles using Tarjan's SCC
        val sccs = TarjanGraphClusterer.findStronglyConnectedComponents(clauseDependencies)
        val cyclicalComponents = sccs.filter { it.size > 1 }

        // Minimal UNSAT core: the union of all cycle-participating clauses
        val minimalCore = cyclicalComponents.flatten().distinct().sorted()

        // Generate anti-unification generalization templates: negations of relational cycles
        val antiUnificationTemplates = cyclicalComponents.map { cycle ->
            val terms = cycle.sorted().joinToString(" ∧ ")
            "∀x. ¬($terms)"
        }

        return DeductionResult(
            smtLib2Digest = digest,
            minimalUnsatCore = minimalCore,
            relationalCycles = cyclicalComponents,
            antiUnificationTemplates = antiUnificationTemplates,
            solveTimeMs = solveTimeMs
        )
    }

    fun recordConflictClause(
        instanceId: String,
        clauseDependencies: Map<String, Set<String>>,
        rawSmtScript: String,
        solveTimeMs: Long,
        memoryBytes: Long = 0L
    ): FrontierSweTelemetryRecord {
        val result = analyzeConflictGraph(clauseDependencies, rawSmtScript, solveTimeMs)

        val payload = CdclConflictClauseRecord(
            clauseDigest = result.smtLib2Digest,
            minimalUnsatCoreSize = result.minimalUnsatCore.size,
            antiUnificationRule = result.antiUnificationTemplates.firstOrNull() ?: "none",
            solveTimeMs = result.solveTimeMs,
            memoryBytes = memoryBytes
        )

        val record = FrontierSweTelemetryRecord.create(
            instanceId = instanceId,
            h0PromptLineage = "smt-${result.smtLib2Digest.take(16)}",
            payload = payload,
            rewardSignal = 0.0
        )
        ringBuffer?.offer(record)
        return record
    }

    private fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
