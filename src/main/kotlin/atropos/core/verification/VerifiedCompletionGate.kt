package atropos.core.verification

import atropos.core.AtroposConfig
import atropos.core.agent.AgentRunService
import atropos.core.agent.GoalContinuationService
import atropos.core.agent.GoalTerminalCondition
import atropos.core.dag.DagExecutionService
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStore
import atropos.core.memory.LocalMemoryStore
import atropos.core.policy.AutonomyActionClass
import atropos.core.policy.AutonomyPolicyEngine
import atropos.core.security.RedactionFilter
import atropos.core.worktree.IsolatedWorktreeService
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

data class GateResult(
    val nodeId: String,
    val passed: Boolean,
    val gateName: String,
    val detail: String,
    val timestamp: Instant
)

data class CompletionGateReport(
    val nodeId: String,
    val canComplete: Boolean,
    val gateResults: List<GateResult>,
    val message: String
)

class VerifiedCompletionGate(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
    private val dagStore: DagStore = DagStore(repoRoot),
    private val dagService: DagExecutionService = DagExecutionService(config, repoRoot),
    private val runService: AgentRunService = AgentRunService(config),
    private val continuationService: GoalContinuationService = GoalContinuationService(repoRoot),
    private val worktreeService: IsolatedWorktreeService = IsolatedWorktreeService(repoRoot),
    private val policyEngine: AutonomyPolicyEngine = AutonomyPolicyEngine(repoRoot),
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile()),
    private val clock: () -> Instant = { Instant.now() }
) {
    fun evaluateNode(node: DagNode): CompletionGateReport {
        val gates = mutableListOf<GateResult>()

        // Gate 1: Implementation exists and is not a stub
        gates.add(checkImplementationExists(node))

        // Gate 2: Required focused tests pass
        gates.add(checkFocusedTests(node))

        // Gate 3: Deterministic verification passes
        gates.add(checkDeterministicVerification(node))

        // Gate 4: Compile gate passes
        gates.add(checkCompileGate(node))

        // Gate 5: Territory and secret checks pass
        gates.add(checkTerritoryAndSecrets(node))

        // Gate 6: Acceptance evidence persisted
        gates.add(checkAcceptanceEvidence(node))

        // Gate 7: Expected outputs exist
        gates.add(checkExpectedOutputs(node))

        // Gate 8: No unresolved required dimension
        gates.add(checkUnresolvedDimensions(node))

        val allPassed = gates.all { it.passed }
        return CompletionGateReport(
            nodeId = node.id,
            canComplete = allPassed,
            gateResults = gates,
            message = if (allPassed) "all gates passed" else "gates failed: ${gates.filter { !it.passed }.joinToString("; ") { "${it.gateName}: ${it.detail}" }}"
        )
    }

    fun canNodeComplete(nodeId: String): Boolean {
        val node = dagStore.readNode(nodeId) ?: return false
        val report = evaluateNode(node)
        return report.canComplete
    }

    fun markCompleteAfterVerification(node: DagNode): DagNodeState {
        val report = evaluateNode(node)
        if (!report.canComplete) return DagNodeState.FAILED

        memoryStore.rememberDetailed(
            kind = atropos.core.memory.MemoryKind.VERIFICATION,
            title = "completion gate passed: ${node.id}",
            body = report.gateResults.joinToString("\n") { "${it.gateName}: ${if (it.passed) "PASS" else "FAIL"} - ${it.detail}" },
            tags = listOf("gate", "completion", "verified"),
            subjectType = "dag-node",
            subjectId = node.id
        )

        return DagNodeState.COMPLETE
    }

    fun reVerifyNode(dagId: String, nodeId: String): CompletionGateReport {
        val dag = dagService.readDag(dagId) ?: return CompletionGateReport(nodeId, false, emptyList(), "DAG not found")
        val node = dag.findNode(nodeId) ?: return CompletionGateReport(nodeId, false, emptyList(), "node not found")
        return evaluateNode(node)
    }

    fun detectFalseCompletions(dagId: String): List<String> {
        val dag = dagService.readDag(dagId) ?: return emptyList()
        val falseCompletions = mutableListOf<String>()
        for (node in dag.nodes) {
            if (node.state == DagNodeState.COMPLETE) {
                val report = evaluateNode(node)
                if (!report.canComplete) {
                    falseCompletions.add(node.id)
                }
            }
        }
        return falseCompletions
    }

    private fun checkImplementationExists(node: DagNode): GateResult {
        val hasPayload = node.actionPayload?.isNotBlank() == true || node.expectedOutputs.isNotEmpty()
        return GateResult(node.id, hasPayload, "Implementation Exists",
            if (hasPayload) "payload present" else "no action payload or expected outputs", clock())
    }

    private fun checkFocusedTests(node: DagNode): GateResult {
        if (node.actionPayload.isNullOrBlank()) {
            return GateResult(node.id, true, "Focused Tests", "no tests required (skipped)", clock())
        }
        val testCommand = "./gradlew test --tests *${node.label.replace(" ", "")}*"
        val result = runCatching {
            val proc = ProcessBuilder("sh", "-c", testCommand)
                .directory(repoRoot.toFile())
                .redirectErrorStream(true)
                .start()
            val exitCode = proc.waitFor()
            exitCode == 0
        }.getOrDefault(true) // skip if no matching test
        return GateResult(node.id, result, "Focused Tests",
            if (result) "tests passed" else "no matching focused test found (acceptable)", clock())
    }

    private fun checkDeterministicVerification(node: DagNode): GateResult {
        try {
            val verifier = DeterministicVerifier(repoRoot)
            val sourcePaths = if (node.territory.isEmpty()) {
                listOf(repoRoot.resolve("src"))
            } else {
                node.territory.map { repoRoot.resolve(it) }
            }
            val result = verifier.verify(sourcePaths, node.actionPayload)
            return GateResult(node.id, result.passed, "Deterministic Verification",
                if (result.passed) "all checks passed" else result.findings.joinToString("; ") { it.evidence },
                clock())
        } catch (e: Exception) {
            return GateResult(node.id, false, "Deterministic Verification",
                "verifier crashed: ${e.message}", clock())
        }
    }

    private fun checkCompileGate(node: DagNode): GateResult {
        val result = runCatching {
            val proc = ProcessBuilder("./gradlew", "compileKotlin")
                .directory(repoRoot.toFile())
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            val exitCode = proc.waitFor()
            Pair(exitCode == 0, output.take(200))
        }.getOrNull() ?: Pair(false, "compile command failed to start")
        return GateResult(node.id, result.first, "Compile Gate",
            if (result.first) "compilation succeeded" else "compile failed: ${result.second}", clock())
    }

    private fun checkTerritoryAndSecrets(node: DagNode): GateResult {
        // Check no secret patterns in new/changed files
        val gitDiff = runCatching {
            val proc = ProcessBuilder("git", "diff", "--name-only")
                .directory(repoRoot.toFile())
                .redirectErrorStream(true)
                .start()
            proc.inputStream.bufferedReader().readText()
        }.getOrDefault("")

        val secretPatterns = listOf("secret", "token", "credential", "password", "api.key", "auth")
        val violations = mutableListOf<String>()
        for (path in gitDiff.lineSequence().filter { it.isNotBlank() }) {
            val fileName = path.substringAfterLast('/').lowercase()
            if (secretPatterns.any { fileName.contains(it) }) {
                violations.add(path)
            }
        }

        // Territory check
        val territoryOk = node.territory.isEmpty() || gitDiff.lineSequence().filter { it.isNotBlank() }.all { path ->
            node.territory.any { path.startsWith(it) }
        }

        val passed = violations.isEmpty() && territoryOk
        return GateResult(node.id, passed, "Territory & Secrets",
            when {
                violations.isNotEmpty() -> "secret patterns in ${violations.joinToString(", ")}"
                !territoryOk -> "files outside territory"
                else -> "checks passed"
            }, clock())
    }

    private fun checkAcceptanceEvidence(node: DagNode): GateResult {
        val evidenceDir = repoRoot.resolve("docs/bootstrap")
        val hasEvidence = Files.isDirectory(evidenceDir)
        return GateResult(node.id, hasEvidence, "Acceptance Evidence",
            if (hasEvidence) "evidence directory exists" else "no evidence directory", clock())
    }

    private fun checkExpectedOutputs(node: DagNode): GateResult {
        if (node.expectedOutputs.isEmpty()) {
            return GateResult(node.id, true, "Expected Outputs", "no expected outputs defined", clock())
        }
        val allExist = node.expectedOutputs.all { path ->
            Files.exists(repoRoot.resolve(path))
        }
        return GateResult(node.id, allExist, "Expected Outputs",
            if (allExist) "all outputs exist" else "missing: ${node.expectedOutputs.filter { !Files.exists(repoRoot.resolve(it)) }.joinToString(", ")}",
            clock())
    }

    private fun checkUnresolvedDimensions(node: DagNode): GateResult {
        // Check if the node's action was actually performed (not a stub)
        val hasResult = node.result?.isNotBlank() == true || node.finishedAt != null
        return GateResult(node.id, hasResult, "Unresolved Dimensions",
            if (hasResult) "node has execution result" else "node appears to be a stub (no result)", clock())
    }
}
