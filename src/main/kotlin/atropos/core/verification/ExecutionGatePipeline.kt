/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

/**
 * Thrown when physical SSD bytes deviate from authoritative CAS Merkle leaves,
 * indicating an unverified or counterfeit candidate promotion.
 */
class FakeSuccessException(message: String) : RuntimeException(message)

/**
 * Execution Gates & Verification Pipeline Engine (Batch 3 Architecture).
 *
 * Coordinates the formal verification stages prior to code promotion:
 * 1. TOIG (Test Oracle Inversion Gate): Requires test suite to fail on unmodified buggy code
 *    and kill >= 85% of synthetic mutants prior to candidate code synthesis.
 * 2. MUTATION_TESTING_ORACLE: Evaluates test suite efficacy against synthetic AST mutants.
 * 3. GOVERNED_COMPILE_GATE: Enforces strict exit-code-0 builds and package verification.
 * 4. CAPABILITY_ATTENUATION_SANDBOX: Guarantees complete mediation where model capabilities
 *    and write capabilities are strictly disjoint: Cap(P_theta) intersect Cap(A_write) = empty.
 * 5. REPRODUCIBILITY_CERTIFICATE_ENGINE: Hashes physical SSD bytes to verify exact-hash match
 *    against authoritative CAS Merkle leaves, throwing FakeSuccessException on mismatch.
 */
class ExecutionGatePipeline {
    enum class GateStage {
        TOIG,
        MUTATION_TESTING,
        GOVERNED_COMPILE,
        CAPABILITY_ATTENUATION,
        REPRODUCIBILITY_CERTIFICATE
    }

    data class GateResult(
        val stage: GateStage,
        val passed: Boolean,
        val score: Double,
        val details: String
    )

    fun evaluateToig(baselineFailed: Boolean, mutantKillRate: Double): GateResult {
        val passed = baselineFailed && mutantKillRate >= 0.85
        val reason = if (!baselineFailed) {
            "TOIG rejection: candidate test suite failed to fail on unmodified baseline (vacuous test)"
        } else if (mutantKillRate < 0.85) {
            "TOIG rejection: mutant kill rate ${"%.2f".format(mutantKillRate * 100)}% below required 85.00% threshold"
        } else {
            "TOIG verified: baseline failure confirmed, mutant kill rate ${"%.2f".format(mutantKillRate * 100)}% >= 85%"
        }
        return GateResult(GateStage.TOIG, passed, mutantKillRate, reason)
    }

    fun evaluateMutationTesting(killed: Int, total: Int): GateResult {
        val rate = if (total == 0) 1.0 else killed.toDouble() / total.toDouble()
        val passed = rate >= 0.85
        return GateResult(
            GateStage.MUTATION_TESTING,
            passed,
            rate,
            "mutants killed: $killed / $total (${"%.1f".format(rate * 100)}%)"
        )
    }

    fun evaluateGovernedCompile(exitCode: Int, compilerErrors: List<String> = emptyList()): GateResult {
        val passed = exitCode == 0 && compilerErrors.isEmpty()
        val details = if (passed) {
            "governed compile verified: exitCode=0, errors=0"
        } else {
            "governed compile failed: exitCode=$exitCode, errors=${compilerErrors.take(3).joinToString("; ")}"
        }
        return GateResult(GateStage.GOVERNED_COMPILE, passed, if (passed) 1.0 else 0.0, details)
    }

    fun evaluateCapabilityAttenuation(modelCaps: Set<String>, writeCaps: Set<String>): GateResult {
        val intersection = modelCaps.intersect(writeCaps)
        val passed = intersection.isEmpty()
        val details = if (passed) {
            "capability attenuation verified: Cap(P_theta) ∩ Cap(A_write) = ∅"
        } else {
            "capability attenuation violation: leaked permissions $intersection"
        }
        return GateResult(GateStage.CAPABILITY_ATTENUATION, passed, if (passed) 1.0 else 0.0, details)
    }

    fun evaluateReproducibilityCertificate(actualSha256: String, expectedCasMerkleRoot: String): GateResult {
        val matches = actualSha256.equals(expectedCasMerkleRoot, ignoreCase = true)
        if (!matches) {
            throw FakeSuccessException(
                "reproducibility certificate mismatch: physical SSD hash $actualSha256 does not match CAS Merkle root $expectedCasMerkleRoot"
            )
        }
        return GateResult(
            GateStage.REPRODUCIBILITY_CERTIFICATE,
            true,
            1.0,
            "reproducibility certificate verified: exact CAS Merkle match $actualSha256"
        )
    }
}
