/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import atropos.core.toolchain.ToolchainResult

/**
 * SD7-T-052: Verification scalar C_ver engine.
 * Mathematical Invariant: C_real = min(C_impl, C_int, C_ver, C_ev)
 * C_ver = 1.0 iff compiler exit 0 AND test suite exit 0 AND mutationKillRatio >= 0.85; else 0.0.
 */
class CVerScalarEngine {
    companion object {
        const val MUTATION_KILL_THRESHOLD: Double = 0.85
    }

    fun evaluate(compileResult: ToolchainResult, testResult: ToolchainResult, mutationKillRatio: Double): Double {
        if (compileResult.exitCode != 0) return 0.0
        if (testResult.exitCode != 0) return 0.0
        if (mutationKillRatio < MUTATION_KILL_THRESHOLD) return 0.0
        return 1.0
    }
}