package atropos.core.verification

import atropos.core.AtroposConfig
import atropos.core.agent.AgentRunService
import atropos.core.agent.GoalContinuationService
import atropos.core.agent.GoalTerminalCondition
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStore
import atropos.core.memory.LocalMemoryStore
import atropos.core.policy.AutonomyActionClass
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
    private val runService: AgentRunService = AgentRunService(config),
    private val continuationService: GoalContinuationService = GoalContinuationService(repoRoot),
    private val worktreeService: IsolatedWorktreeService = IsolatedWorktreeService(repoRoot),
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile()),
    /**
     * A fresh auditor per evaluation. [atropos.core.auditor.AuditorService]
     * accumulates findings in mutable state, so a shared instance would let one
     * node's findings refuse another's completion.
     */
    private val auditorFactory: () -> atropos.core.auditor.AuditorService =
        { atropos.core.auditor.AuditorService() },
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

        // Gate 9: no blocking Auditor finding
        gates.add(checkAuditorFindings(node))

        // The Auditor can only ever subtract: completion needs every gate, so a
        // clean audit never makes a failing node completable. That is what
        // "cannot approve its own work" means here.
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
        val dag = dagStore.readDag(dagId) ?: return CompletionGateReport(nodeId, false, emptyList(), "DAG not found")
        val node = dag.findNode(nodeId) ?: return CompletionGateReport(nodeId, false, emptyList(), "node not found")
        return evaluateNode(node)
    }

    fun detectFalseCompletions(dagId: String): List<String> {
        val dag = dagStore.readDag(dagId) ?: return emptyList()
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
            return nothingToInspect(node, FOCUSED_TESTS, "no payload to derive a focused test from")
        }
        val testCommand = "./gradlew test --tests *${node.label.replace(" ", "")}*"
        val result = runCatching {
            val proc = ProcessBuilder("sh", "-c", testCommand)
                .directory(repoRoot.toFile())
                .redirectErrorStream(true)
                .start()
            val exitCode = proc.waitFor()
            exitCode == 0
        }.getOrElse {
            // The run failed to start. Nothing was established, so nothing is
            // claimed: this used to pass on exception.
            return GateResult(node.id, false, FOCUSED_TESTS, "focused test run failed to start: ${it.message}", clock())
        }
        return GateResult(node.id, result, FOCUSED_TESTS,
            if (result) "tests passed" else "focused tests failed", clock())
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
        }.getOrElse {
            // An unreadable diff is not a clean diff. This used to fall back to
            // an empty string, which read as "nothing changed, all clear".
            return GateResult(node.id, false, TERRITORY_AND_SECRETS,
                "could not read the working diff: ${it.message}", clock())
        }

        val changed = gitDiff.lineSequence().filter { it.isNotBlank() }.toList()
        if (node.territory.isEmpty()) {
            return nothingToInspect(node, TERRITORY_AND_SECRETS, "node declared no territory to check changes against")
        }

        val secretPatterns = listOf("secret", "token", "credential", "password", "api.key", "auth")
        val violations = mutableListOf<String>()
        for (path in changed) {
            val fileName = path.substringAfterLast('/').lowercase()
            if (secretPatterns.any { fileName.contains(it) }) {
                violations.add(path)
            }
        }

        val territoryOk = changed.all { path -> node.territory.any { path.startsWith(it) } }

        val passed = violations.isEmpty() && territoryOk
        return GateResult(node.id, passed, TERRITORY_AND_SECRETS,
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
            return nothingToInspect(node, EXPECTED_OUTPUTS, "node declared no expected outputs")
        }
        val allExist = node.expectedOutputs.all { path ->
            Files.exists(repoRoot.resolve(path))
        }
        return GateResult(node.id, allExist, EXPECTED_OUTPUTS,
            if (allExist) "all outputs exist" else "missing: ${node.expectedOutputs.filter { !Files.exists(repoRoot.resolve(it)) }.joinToString(", ")}",
            clock())
    }

    /**
     * Asks the Auditor about the files this node touched.
     *
     * `FAILURE` and `CRITICAL` findings refuse completion; warnings and passes
     * do not. The Auditor is consulted at the completion boundary rather than
     * on every intermediate step.
     */
    private fun checkAuditorFindings(node: DagNode): GateResult {
        val files = (node.territory + node.expectedOutputs)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { repoRoot.resolve(it).toString() }
            .distinct()

        if (files.isEmpty()) {
            return nothingToInspect(node, AUDITOR, "node named no files for the auditor to review")
        }

        val auditor = auditorFactory()
        auditor.auditSecrets(files)
        auditor.auditDeterministic(files)

        val blocking = auditor.report().findings.filter {
            it.severity == atropos.core.auditor.AuditSeverity.FAILURE ||
                it.severity == atropos.core.auditor.AuditSeverity.CRITICAL
        }

        return GateResult(
            node.id,
            blocking.isEmpty(),
            AUDITOR,
            if (blocking.isEmpty()) {
                "auditor raised no blocking finding across ${files.size} file(s)"
            } else {
                "auditor blocked: " + blocking.joinToString("; ") { "${it.check} ${it.message}" }
            },
            clock()
        )
    }

    /**
     * A check that established nothing.
     *
     * Fail-closed by default: claiming safety without inspecting anything is how
     * a gate looks like it works while guarding nothing. A node may opt a check
     * out explicitly through [DagNode.optionalChecks], and only then.
     */
    private fun nothingToInspect(node: DagNode, gateName: String, why: String): GateResult =
        if (gateName in node.optionalChecks) {
            GateResult(node.id, true, gateName, "$why (declared optional by the node contract)", clock())
        } else {
            GateResult(node.id, false, gateName, "$why; nothing was verified", clock())
        }

    private companion object {
        const val FOCUSED_TESTS = "Focused Tests"
        const val TERRITORY_AND_SECRETS = "Territory & Secrets"
        const val EXPECTED_OUTPUTS = "Expected Outputs"
        const val AUDITOR = "Auditor Findings"
    }

    private fun checkUnresolvedDimensions(node: DagNode): GateResult {
        // Check if the node's action was actually performed (not a stub)
        val hasResult = node.result?.isNotBlank() == true || node.finishedAt != null
        return GateResult(node.id, hasResult, "Unresolved Dimensions",
            if (hasResult) "node has execution result" else "node appears to be a stub (no result)", clock())
    }
}
