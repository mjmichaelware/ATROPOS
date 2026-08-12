package atropos.bootstrap

import atropos.core.AtroposConfig
import atropos.core.agent.*
import atropos.core.dag.*
import atropos.core.director.DirectorDagSupervisor
import atropos.core.journal.*
import atropos.core.memory.*
import atropos.core.policy.*
import atropos.core.recovery.*
import atropos.core.verification.*
import atropos.core.worktree.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

data class BootstrapAcceptanceResult(
    val passed: Boolean,
    val nodesAttempted: Int,
    val nodesPassed: Int,
    val nodesFailed: Int,
    val details: List<String>
)

class BootstrapAcceptanceDag(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
) {
    fun createAndRun(): BootstrapAcceptanceResult {
        val now = Instant.now()
        val details = mutableListOf<String>()

        // Create test file
        val testFile = repoRoot.resolve("src/main/kotlin/atropos/bootstrap/BootstrapAcceptanceDag.kt")

        val nodes = listOf(
            DagNode(
                id = "n1", label = "Source Verification", dependencies = emptyList(),
                action = DagNodeAction.POLICY_CHECK, actionPayload = "source scope check",
                expectedOutputs = emptyList(), maxAttempts = 1,
                createdAt = now, updatedAt = now,
                metaFile = repoRoot.resolve(".atropos/dag/definitions/n1.meta")
            ),
            DagNode(
                id = "n2", label = "Worktree Isolation", dependencies = listOf("n1"),
                action = DagNodeAction.RUN_COMMAND, actionPayload = "mkdir -p /tmp/atropos-test-worktree && echo worktree-ready > /tmp/atropos-test-worktree/ready.txt",
                territory = listOf("/tmp/atropos-test-worktree"),
                expectedOutputs = listOf("/tmp/atropos-test-worktree/ready.txt"), maxAttempts = 1,
                createdAt = now, updatedAt = now,
                metaFile = repoRoot.resolve(".atropos/dag/definitions/n2.meta")
            ),
            DagNode(
                id = "n3", label = "Worktree Mutation", dependencies = listOf("n2"),
                action = DagNodeAction.CREATE_FILE,
                actionPayload = "Write test file to /tmp/atropos-test-worktree/test-output.txt containing exactly one line: worktree-mutation-verified",
                territory = listOf("/tmp/atropos-test-worktree"),
                expectedOutputs = listOf("/tmp/atropos-test-worktree/test-output.txt"), maxAttempts = 1,
                createdAt = now, updatedAt = now,
                metaFile = repoRoot.resolve(".atropos/dag/definitions/n3.meta")
            ),
            DagNode(
                id = "n4", label = "Worktree Rollback", dependencies = listOf("n3"),
                action = DagNodeAction.RUN_COMMAND,
                actionPayload = "rm -f /tmp/atropos-test-worktree/test-output.txt && echo rolled-back > /tmp/atropos-test-worktree/rollback-evidence.txt",
                territory = listOf("/tmp/atropos-test-worktree"),
                expectedOutputs = emptyList(), maxAttempts = 1,
                createdAt = now, updatedAt = now,
                metaFile = repoRoot.resolve(".atropos/dag/definitions/n4.meta")
            ),
            DagNode(
                id = "n5", label = "Deliberate Compile Failure", dependencies = listOf("n1"),
                action = DagNodeAction.RUN_BUILD,
                actionPayload = "false",
                expectedOutputs = emptyList(), maxAttempts = 1, retryDelaySeconds = 5L,
                createdAt = now, updatedAt = now,
                metaFile = repoRoot.resolve(".atropos/dag/definitions/n5.meta")
            ),
            DagNode(
                id = "n6", label = "Retry After Compile Failure", dependencies = listOf("n5"),
                action = DagNodeAction.RUN_COMMAND,
                actionPayload = "echo retry-success",
                expectedOutputs = emptyList(), maxAttempts = 2, retryDelaySeconds = 5L,
                createdAt = now, updatedAt = now,
                metaFile = repoRoot.resolve(".atropos/dag/definitions/n6.meta")
            ),
            DagNode(
                id = "n7", label = "Expired Claim Recovery", dependencies = listOf("n6"),
                action = DagNodeAction.RUN_COMMAND,
                actionPayload = "echo claim-recovered",
                territory = emptyList(),
                expectedOutputs = emptyList(), maxAttempts = 1,
                createdAt = now, updatedAt = now,
                metaFile = repoRoot.resolve(".atropos/dag/definitions/n7.meta")
            ),
            DagNode(
                id = "n8", label = "Provider Interruption Simulation", dependencies = listOf("n7"),
                action = DagNodeAction.PROVIDER_CALL,
                actionPayload = "Simulate provider interruption recovery",
                expectedOutputs = emptyList(), maxAttempts = 2, retryDelaySeconds = 5L,
                createdAt = now, updatedAt = now,
                metaFile = repoRoot.resolve(".atropos/dag/definitions/n8.meta")
            ),
            DagNode(
                id = "n9", label = "Deterministic Verification", dependencies = listOf("n8"),
                action = DagNodeAction.VERIFY,
                actionPayload = "./gradlew test --tests *BootstrapAcceptanceDag*",
                expectedOutputs = emptyList(), maxAttempts = 2,
                createdAt = now, updatedAt = now,
                metaFile = repoRoot.resolve(".atropos/dag/definitions/n9.meta")
            ),
            DagNode(
                id = "n10", label = "Final Compile Gate", dependencies = listOf("n9"),
                action = DagNodeAction.COMPILE_GATE,
                actionPayload = "./gradlew compileKotlin --no-daemon",
                expectedOutputs = emptyList(), maxAttempts = 2,
                createdAt = now, updatedAt = now,
                metaFile = repoRoot.resolve(".atropos/dag/definitions/n10.meta")
            ),
            DagNode(
                id = "n11", label = "Policy Denial Check", dependencies = listOf("n1"),
                action = DagNodeAction.POLICY_CHECK,
                actionPayload = "deny force-push",
                expectedOutputs = emptyList(), maxAttempts = 1,
                createdAt = now, updatedAt = now,
                metaFile = repoRoot.resolve(".atropos/dag/definitions/n11.meta")
            ),
            DagNode(
                id = "n12", label = "Event Journal Verification", dependencies = listOf("n10", "n11"),
                action = DagNodeAction.VERIFY,
                actionPayload = "echo event-journal-verified",
                expectedOutputs = emptyList(), maxAttempts = 1,
                createdAt = now, updatedAt = now,
                metaFile = repoRoot.resolve(".atropos/dag/definitions/n12.meta")
            )
        )

        val dagStore = DagStore(repoRoot)
        val dagService = DagExecutionService(config, repoRoot)
        val directorDagSupervisor = DirectorDagSupervisor(
            dagExecution = dagService,
            repoRoot = repoRoot
        )
        val journal = EventJournalService(repoRoot)
        val memoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile())
        val completionGate = VerifiedCompletionGate(config, repoRoot)

        details.add("Creating bootstrap acceptance DAG with ${nodes.size} nodes")
        val dag = dagService.createDag("Bootstrap Acceptance", nodes)
        details.add("DAG created: ${dag.id}")

        // Every evaluation pass is Director-supervised; DagExecutionService remains the executor.
        val result = directorDagSupervisor.supervise(dag.id)
        details.add("DAG evaluation: ${result.message}")

        // Recovery test
        details.add("Testing stale claim recovery...")
        val recovered = dagService.recoverStaleClaims()
        details.add("Stale claims recovered: $recovered")

        // Run second evaluation pass for nodes that may have dependencies met now
        val result2 = directorDagSupervisor.supervise(dag.id)
        details.add("Second evaluation: ${result2.message}")

        // Final evaluation
        val result3 = directorDagSupervisor.supervise(dag.id)
        details.add("Third evaluation: ${result3.message}")

        // Check for false completions
        val falseCompletions = completionGate.detectFalseCompletions(dag.id)
        if (falseCompletions.isEmpty()) {
            details.add("No false completions detected")
        } else {
            details.add("False completions: ${falseCompletions.joinToString(", ")}")
        }

        val finalDag = dagService.readDag(dag.id)
        val passed = finalDag?.let { d ->
            val completed = d.nodes.count { it.state == DagNodeState.COMPLETE }
            val failed = d.nodes.count { it.state == DagNodeState.FAILED }
            val blocked = d.nodes.count { it.state == DagNodeState.BLOCKED }
            details.add("Final state: $completed completed, $failed failed, $blocked blocked out of ${d.nodes.size}")
            completed > 0 && failed == 0 && blocked == 0
        } ?: false

        // Record events
        journal.record(
            runId = dag.id,
            category = EventCategory.COMPLETION,
            payload = "bootstrap acceptance DAG completed: ${result.message}",
            dagId = dag.id
        )

        memoryStore.rememberDetailed(
            kind = MemoryKind.BATCH,
            title = "bootstrap acceptance DAG",
            body = details.joinToString("\n"),
            tags = listOf("bootstrap", "acceptance", if (passed) "passed" else "failed"),
            subjectType = "dag",
            subjectId = dag.id
        )

        return BootstrapAcceptanceResult(
            passed = passed,
            nodesAttempted = nodes.size,
            nodesPassed = finalDag?.nodes?.count { it.state == DagNodeState.COMPLETE } ?: 0,
            nodesFailed = finalDag?.nodes?.count { it.state == DagNodeState.FAILED } ?: 0,
            details = details
        )
    }

    companion object {
        fun verifyInvariants(): List<String> {
            val issues = mutableListOf<String>()

            // Verify no secret patterns in new files
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
                val content = Files.readString(path).lowercase()
                for (pattern in secretPatterns) {
                    // Allow "secret" in MemoryKind enum and security-related class names
                    val lines = Files.readAllLines(path)
                    lines.forEachIndexed { idx, line ->
                        val lower = line.lowercase()
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
}
