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
            // Each lane reports why, not just that. A veto naming only the lane
            // leaves the operator to re-run the whole gate by hand to find out
            // what it objected to -- and a lane that is missing entirely is a
            // different problem from one that ran and refused, so the two are
            // distinguished rather than both reading as "failed".
            val failedLanes = listOfNotNull(
                lane("Auditor", auditorPassed, auditorGate),
                lane("Deterministic", deterministicPassed, deterministicGate),
                lane("Compile", compilePassed, compileGate)
            ).joinToString("; ")
            return report.copy(
                canComplete = false,
                message = "VETO: Independent verification failed on core lanes ($failedLanes). " +
                    "Proposing agent cannot self-approve."
            )
        }

        return report
    }

    /** `null` when the lane passed; otherwise the lane and its stated reason. */
    private fun lane(name: String, passed: Boolean, result: GateResult?): String? = when {
        passed -> null
        result == null -> "$name (lane did not run)"
        result.detail.isBlank() -> name
        else -> "$name: ${result.detail}"
    }
}
