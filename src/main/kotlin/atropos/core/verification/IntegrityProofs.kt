/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

data class ProofResult(val verdict: String, val checkedAt: Long, val evidenceHash: String)

private fun proofHash(label: String, evidence: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest("$label|$evidence".toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

object SelfHostProof {
    fun runProof(mutatedFile: String, gitStatusOutput: String): ProofResult {
        val passed = mutatedFile.isNotEmpty() && gitStatusOutput.contains("modified")
        return ProofResult(if (passed) "VERIFIED" else "FAILED", System.currentTimeMillis(), proofHash("selfhost", "$mutatedFile|$gitStatusOutput"))
    }
}

object GreenfieldFactoryProof {
    fun runProof(absentCount: Int): ProofResult {
        val passed = absentCount > 0
        return ProofResult(if (passed) "VERIFIED" else "FAILED", System.currentTimeMillis(), proofHash("greenfield", absentCount.toString()))
    }

    fun verifyGeneratedProject(projectRoot: Path, requiredFiles: Set<String>): ProofResult {
        val root = projectRoot.toAbsolutePath().normalize()
        val evidence = requiredFiles.sorted().joinToString("\n") { relative ->
            val candidate = root.resolve(relative).normalize()
            val valid = candidate.startsWith(root) && Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
            "$relative=${if (valid) "present" else "missing"}"
        }
        val passed = requiredFiles.isNotEmpty() && requiredFiles.all { relative ->
            val candidate = root.resolve(relative).normalize()
            candidate.startsWith(root) && Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
        }
        return ProofResult(if (passed) "VERIFIED" else "FAILED", System.currentTimeMillis(), proofHash("greenfield-surface", evidence))
    }
}

object LongHorizonProof {
    fun runProof(stepsExecuted: Int): ProofResult {
        val passed = stepsExecuted >= 10
        return ProofResult(if (passed) "VERIFIED" else "FAILED", System.currentTimeMillis(), proofHash("longhorizon", stepsExecuted.toString()))
    }
}

object RecoveryProof {
    fun runProof(recoveredState: Boolean): ProofResult {
        return ProofResult(if (recoveredState) "VERIFIED" else "FAILED", System.currentTimeMillis(), proofHash("recovery", recoveredState.toString()))
    }
}

object SafetyProof {
    fun runProof(leaksFound: Int, outOfBoundsWrites: Int): ProofResult {
        val passed = leaksFound == 0 && outOfBoundsWrites == 0
        return ProofResult(if (passed) "VERIFIED" else "FAILED", System.currentTimeMillis(), proofHash("safety", "$leaksFound|$outOfBoundsWrites"))
    }
}

object FallbackProof {
    fun runProof(fallbackChainsTriggered: Boolean): ProofResult {
        return ProofResult(if (fallbackChainsTriggered) "VERIFIED" else "FAILED", System.currentTimeMillis(), proofHash("fallback", fallbackChainsTriggered.toString()))
    }
}

object LearningProof {
    fun runProof(accuracyImprovement: Double): ProofResult {
        val passed = accuracyImprovement > 0.0
        return ProofResult(if (passed) "VERIFIED" else "FAILED", System.currentTimeMillis(), proofHash("learning", accuracyImprovement.toString()))
    }
}

object HashIntegrityProof {
    fun runProof(baselineJarHash: String, currentJarHash: String): ProofResult {
        val passed = baselineJarHash == currentJarHash
        return ProofResult(if (passed) "VERIFIED" else "FAILED", System.currentTimeMillis(), proofHash("hashintegrity", "$baselineJarHash|$currentJarHash"))
    }
}
