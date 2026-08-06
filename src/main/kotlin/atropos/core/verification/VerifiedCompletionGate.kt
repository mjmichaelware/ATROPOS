package atropos.core.verification

import atropos.core.AtroposConfig
import atropos.core.AtroposRepoRootLocator
import atropos.core.agent.AgentRunService
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStore
import atropos.core.memory.LocalMemoryStore
import atropos.core.memory.MemoryKind
import atropos.core.policy.BoundedProcessRunner
import atropos.core.security.RedactionFilter
import atropos.core.security.SourceSecretScanner
import atropos.core.worktree.BoundedGitWorktreeCommandRunner
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
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val dagStore: DagStore = DagStore(repoRoot),
    private val runService: AgentRunService = AgentRunService(config),
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile()),
    private val auditorFactory: () -> atropos.core.auditor.AuditorService = { atropos.core.auditor.AuditorService(repoRoot) },
    private val clock: () -> Instant = { Instant.now() },
    private val gitRunner: BoundedGitWorktreeCommandRunner = BoundedGitWorktreeCommandRunner(),
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner(),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val sourceSecretScanner = SourceSecretScanner(redactionFilter)
    private val checks = CompletionGateChecks(repoRoot, clock, processRunner, gitRunner, redactionFilter, sourceSecretScanner)
    private val evidence = CompletionGateEvidence(repoRoot, clock)

    fun evaluateNode(node: DagNode): CompletionGateReport {
        return IndependentVerificationGate(config, repoRoot, processRunner).verify(node)
    }

    fun evaluateFactory(input: FactoryCompletionInput): CompletionGateReport {
        val required = setOf("README.md", "LICENSE", ".gitignore", "AGENTS.md")
        val sourceFiles = input.files.filter { it.startsWith("src/main/") && it.endsWith(".kt") }
        val testFiles = input.files.filter { it.startsWith("src/test/") && it.endsWith(".kt") }
        val checkResults = listOf(
            GateResult(input.nodeId, input.branch == input.expectedBranch, "Factory branch isolation", "branch=${input.branch}", clock()),
            GateResult(input.nodeId, sourceFiles.isNotEmpty(), "Factory source", "Kotlin source files present", clock()),
            GateResult(input.nodeId, testFiles.isNotEmpty(), "Factory tests", "Kotlin test files present", clock()),
            GateResult(input.nodeId, required.all(input.files::contains), "Factory repository kit", "standard files present", clock()),
            GateResult(input.nodeId, input.files.contains("verify.sh"), "Factory verifier", "bounded verifier present", clock()),
            GateResult(
                input.nodeId,
                input.verificationOutput.contains("APP_FACTORY_VERIFY_OK") &&
                    input.verificationOutput.contains("deterministic verifier:") &&
                    input.verificationOutput.contains("passed: true"),
                "Factory verification",
                "generated tests and deterministic checks passed",
                clock()
            ),
            GateResult(input.nodeId, input.auditorAllowed, "Factory auditor", "independent audit decision", clock()),
            GateResult(input.nodeId, input.sourceCommitId.matches(Regex("[0-9a-f]{40}")), "Factory source commit", "source commit recorded", clock()),
            GateResult(input.nodeId, input.sourceTreeSha256.matches(Regex("[0-9a-f]{64}")), "Factory source digest", "source tree digest recorded", clock()),
            GateResult(input.nodeId, input.promptSha256.matches(Regex("[0-9a-f]{64}")) && input.researchSha256.matches(Regex("[0-9a-f]{64}")), "Factory lineage", "prompt and research hashes present", clock())
        )
        val passed = checkResults.all { it.passed }
        return CompletionGateReport(input.nodeId, passed, checkResults, if (passed) "factory completion gate passed" else "factory gates failed: ${checkResults.filterNot { it.passed }.joinToString("; ") { it.gateName }}")
    }

    fun evaluateNodeInternal(node: DagNode): CompletionGateReport {
        val gates = mutableListOf<GateResult>()
        gates.add(checks.checkBuildMatrix(node))
        gates.add(checks.checkImplementationExists(node))
        gates.add(checks.checkFocusedTests(node))
        gates.add(checks.checkDeterministicVerification(node))
        gates.add(checks.checkCompileGate(node))
        gates.add(checks.checkTerritoryAndSecrets(node))
        gates.add(evidence.checkAcceptanceEvidence(node))
        gates.add(checks.checkExpectedOutputs(node))
        gates.add(checks.checkUnresolvedDimensions(node))
        gates.add(evidence.checkAuditorFindings(node, auditorFactory))

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
            kind = MemoryKind.VERIFICATION,
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
}
