/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.ops

import atropos.core.autonomy.RewardPenaltyStore
import atropos.core.security.SecretSinkMatrix
import java.nio.file.Files
import java.nio.file.Path

/**
 * SD7-T-022: Cloud deploy artifact sanitizer parsing .gitignore + .vercelignore and scanning secrets.
 * Mathematical Invariant: Cap(P_theta) ∩ Cap(A_write) = ∅
 * Upload of any matched secret aborts build and logs ZERO_REWARD_PENALTY.
 */
object CloudDeploySanitizer {

    fun sanitizeBeforeUpload(worktree: Path): Boolean {
        if (!Files.exists(worktree)) return false

        val ignorePatterns = mutableListOf<String>()
        val gitignore = worktree.resolve(".gitignore")
        if (Files.isRegularFile(gitignore)) {
            ignorePatterns.addAll(Files.readAllLines(gitignore).map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") })
        }
        val vercelignore = worktree.resolve(".vercelignore")
        if (Files.isRegularFile(vercelignore)) {
            ignorePatterns.addAll(Files.readAllLines(vercelignore).map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") })
        }

        val allFiles = Files.walk(worktree, 5)
            .filter { Files.isRegularFile(it) }
            .toList()

        for (file in allFiles) {
            val relativePath = worktree.relativize(file).toString()
            if (ignorePatterns.any { pattern -> relativePath.contains(pattern) }) {
                continue
            }
            if (Files.size(file) > 10 * 1024 * 1024) continue // Skip large binaries from string scan

            val content = Files.readString(file)
            val scrubbed = SecretSinkMatrix.scrubTokens(content)
            if (scrubbed != content) {
                // Secret detected
                RewardPenaltyStore().recordZeroRewardPenalty(
                    agentId = "cloud-uploader",
                    reason = "Deployment artifact contains unredacted credentials at $relativePath",
                    violationClass = "SECRET_LEAK_IN_DEPLOY_ARTIFACT"
                )
                return false
            }
        }
        return true
    }
}
