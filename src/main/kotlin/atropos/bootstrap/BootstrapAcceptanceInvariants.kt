/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bootstrap

import java.nio.file.Files
import java.nio.file.Path

object BootstrapAcceptanceInvariants {
    fun verifyInvariants(): List<String> {
        val issues = mutableListOf<String>()

        val secretPatterns = listOf("secret", "token", "credential", "password", "api.key")
        val newFiles = listOf(
            "src/main/kotlin/atropos/core/agent/SupervisedProviderSession.kt",
            "src/main/kotlin/atropos/core/agent/SupervisedSessionStore.kt",
            "src/main/kotlin/atropos/core/agent/ProviderSessionSupervisor.kt",
            "src/main/kotlin/atropos/core/agent/GoalRunModels.kt",
            "src/main/kotlin/atropos/core/agent/GoalRunStore.kt",
            "src/main/kotlin/atropos/core/agent/GoalContinuationService.kt",
            "src/main/kotlin/atropos/core/policy/AutonomyPolicyExtensions.kt",
            "src/main/kotlin/atropos/core/dag/DagModels.kt",
            "src/main/kotlin/atropos/core/dag/DagStore.kt",
            "src/main/kotlin/atropos/core/dag/DagExecutionService.kt",
            "src/main/kotlin/atropos/core/journal/EventJournalModels.kt",
            "src/main/kotlin/atropos/core/journal/EventJournalService.kt",
            "src/main/kotlin/atropos/core/observability/RunObserver.kt",
            "src/main/kotlin/atropos/core/recovery/CrashRecoveryService.kt",
            "src/main/kotlin/atropos/core/worktree/IsolatedWorktreeService.kt",
            "src/main/kotlin/atropos/core/verification/VerifiedCompletionGate.kt",
            "src/main/kotlin/atropos/bootstrap/BootstrapAcceptanceDag.kt"
        )

        for (filePath in newFiles) {
            val path = Path.of(System.getProperty("user.dir")).resolve(filePath)
            if (!Files.isRegularFile(path)) {
                issues.add("MISSING: $filePath")
                continue
            }
            val lines = Files.readAllLines(path)
            lines.forEachIndexed { idx, line ->
                val lower = line.lowercase()
                for (pattern in secretPatterns) {
                    if (lower.contains(pattern) &&
                        !lower.contains("redaction") &&
                        !lower.contains("secrets") &&
                        !line.contains("MemoryKind") &&
                        !line.contains("AutonomyActionClass") &&
                        !line.contains("AutonomyPolicyRule") &&
                        !line.contains("checkTerritoryAndSecrets")
                    ) {
                        issues.add("SECRET_PATTERN: $filePath:$idx: $line")
                    }
                }
            }
        }

        return issues
    }
}
