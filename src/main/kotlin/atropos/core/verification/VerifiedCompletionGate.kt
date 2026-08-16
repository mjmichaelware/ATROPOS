package atropos.core.verification

import atropos.core.AtroposConfig
import atropos.core.AtroposRepoRootLocator
import atropos.core.agent.AgentRunService
import atropos.core.auditor.AuditorService
import atropos.core.director.DirectorService
import atropos.core.director.DirectorStore
import atropos.core.factory.FactoryLineage
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

class VerifiedCompletionGate(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val dagStore: DagStore = DagStore(repoRoot),
    private val runService: AgentRunService = AgentRunService(config),
    private val memoryStore: LocalMemoryStore? = runCatching {
        LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile())
    }.getOrNull(),
    private val auditorFactory: () -> atropos.core.auditor.AuditorService = { atropos.core.auditor.AuditorService(repoRoot) },
    private val clock: () -> Instant = { Instant.now() },
    private val gitRunner: BoundedGitWorktreeCommandRunner = BoundedGitWorktreeCommandRunner(),
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner(),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val falseGreenGuard = FalseGreenGuard()
    private val antiOscillation = AntiOscillation()
    private val sourceSecretScanner = SourceSecretScanner(redactionFilter)
    private val checks = CompletionGateChecks(repoRoot, clock, processRunner, gitRunner, redactionFilter, sourceSecretScanner)
    private val evidence = CompletionGateEvidence(repoRoot, clock)
    private val factoryVerifier = FactoryCompletionVerifier(repoRoot, clock, gitRunner)
    private val goalInvariants = GoalInvariantSet(
        rootAuthorityHash = "runtime-goal-invariants",
        clauses = listOf(
            InvariantClause(
                clauseId = "no-secret-path-mutation",
                textHash = "runtime-goal-invariants",
                prohibition = true,
                targetPathPattern = ".atropos/secrets"
            )
        )
    )

    fun evaluateNode(node: DagNode): CompletionGateReport {
        return IndependentVerificationGate(config, repoRoot, processRunner).verify(node)
    }

    fun evaluateFactory(input: FactoryCompletionInput): CompletionGateReport =
        factoryVerifier.evaluateFactory(input)


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
        val mutationPath = node.actionPayload ?: node.expectedOutputs.firstOrNull().orEmpty()
        val invariantPasses = goalInvariants.validateMutation(
            path = mutationPath,
            isProhibitedAction = node.action in setOf(DagNodeAction.CREATE_FILE, DagNodeAction.EDIT_FILE)
        )
        gates.add(
            GateResult(
                node.id,
                invariantPasses,
                "Goal Invariants",
                if (invariantPasses) "mutation satisfies goal invariant set" else "mutation violates goal invariant set",
                clock()
            )
        )
        val falseGreen = falseGreenGuard.assess(node)
        val nonRepeating = antiOscillation.observe("${node.id}:${node.result.orEmpty()}")
        gates.add(
            GateResult(
                node.id,
                falseGreen.passed && nonRepeating,
                "False-Green Guard",
                when {
                    !falseGreen.passed -> falseGreen.reasons.joinToString("; ")
                    !nonRepeating -> "identical completion outcome repeated without new evidence"
                    else -> "completion claim has required structural evidence"
                },
                clock()
            )
        )

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
        memoryStore?.rememberDetailed(
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
