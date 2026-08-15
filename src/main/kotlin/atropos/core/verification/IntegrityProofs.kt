/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

data class ProofResult(val verdict: String, val checkedAt: Long, val evidenceHash: String)

object SelfHostProof {
    fun runProof(mutatedFile: String, gitStatusOutput: String): ProofResult {
        val passed = mutatedFile.isNotEmpty() && gitStatusOutput.contains("modified")
        return ProofResult(if (passed) "VERIFIED" else "FAILED", System.currentTimeMillis(), "hash-selfhost")
    }
}

object GreenfieldFactoryProof {
    fun runProof(absentCount: Int): ProofResult {
        val passed = absentCount > 0
        return ProofResult(if (passed) "VERIFIED" else "FAILED", System.currentTimeMillis(), "hash-greenfield")
    }
}

object LongHorizonProof {
    fun runProof(stepsExecuted: Int): ProofResult {
        val passed = stepsExecuted >= 10
        return ProofResult(if (passed) "VERIFIED" else "FAILED", System.currentTimeMillis(), "hash-longhorizon")
    }
}

object RecoveryProof {
    fun runProof(recoveredState: Boolean): ProofResult {
        return ProofResult(if (recoveredState) "VERIFIED" else "FAILED", System.currentTimeMillis(), "hash-recovery")
    }
}

object SafetyProof {
    fun runProof(leaksFound: Int, outOfBoundsWrites: Int): ProofResult {
        val passed = leaksFound == 0 && outOfBoundsWrites == 0
        return ProofResult(if (passed) "VERIFIED" else "FAILED", System.currentTimeMillis(), "hash-safety")
    }
}

object FallbackProof {
    fun runProof(fallbackChainsTriggered: Boolean): ProofResult {
        return ProofResult(if (fallbackChainsTriggered) "VERIFIED" else "FAILED", System.currentTimeMillis(), "hash-fallback")
    }
}

object LearningProof {
    fun runProof(accuracyImprovement: Double): ProofResult {
        val passed = accuracyImprovement > 0.0
        return ProofResult(if (passed) "VERIFIED" else "FAILED", System.currentTimeMillis(), "hash-learning")
    }
}

object HashIntegrityProof {
    fun runProof(baselineJarHash: String, currentJarHash: String): ProofResult {
        val passed = baselineJarHash == currentJarHash
        return ProofResult(if (passed) "VERIFIED" else "FAILED", System.currentTimeMillis(), "hash-hashintegrity")
    }
}
