/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import atropos.core.AtroposConfig
import atropos.core.AtroposRepoRootLocator
import atropos.core.dag.DagNode
import atropos.core.policy.BoundedProcessRunner
import java.nio.file.Path

/**
 * IndependentVerificationGate - The facade for enforcing independent verifications.
 *
 * Implements the "no self-approval" rule by aggregating deterministic verification,
 * compiler verification, auditor blockages, and expected output validation.
 */
class IndependentVerificationGate(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner()
) {
    /**
     * Run all verification gates independently for the given DAG node.
     * Enforces that a proposing agent cannot self-approve their changes without passing
     * every single deterministic validation gate.
     */
    fun verify(node: DagNode): CompletionGateReport {
        val gate = VerifiedCompletionGate(config = config, repoRoot = repoRoot, processRunner = processRunner)
        val report = gate.evaluateNodeInternal(node)

        val auditorGate = report.gateResults.firstOrNull { it.gateName == "Auditor Findings" }
        val deterministicGate = report.gateResults.firstOrNull { it.gateName == "Deterministic Verification" }
        val compileGate = report.gateResults.firstOrNull { it.gateName == "Compile Gate" }

        // Strict Enforcement: If any of these core verification lanes failed or are missing,
        // we veto completion immediately to guarantee no self-approval.
        val auditorPassed = auditorGate?.passed ?: false
        val deterministicPassed = deterministicGate?.passed ?: false
        val compilePassed = compileGate?.passed ?: false

        if (!auditorPassed || !deterministicPassed || !compilePassed) {
            val failedLanes = listOfNotNull(
                if (!auditorPassed) "Auditor" else null,
                if (!deterministicPassed) "Deterministic" else null,
                if (!compilePassed) "Compile" else null
            ).joinToString(", ")
            return report.copy(
                canComplete = false,
                message = "VETO: Independent verification failed on core lanes ($failedLanes). Proposing agent cannot self-approve."
            )
        }

        return report
    }
}
