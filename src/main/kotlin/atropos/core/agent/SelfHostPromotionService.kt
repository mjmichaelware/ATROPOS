package atropos.core.agent

import atropos.core.AtroposRepoRootLocator
import atropos.core.artifact.JarSwapEvidence
import atropos.core.artifact.SafeJarSwapGate
import atropos.core.dag.DagExecutionService
import atropos.core.dag.DagNode
import atropos.core.director.DirectorService
import atropos.core.director.DirectorStore
import atropos.core.verification.CompletionGateReport
import atropos.core.verification.VerifiedCompletionGate
import atropos.core.worktree.BoundedGitWorktreeCommandRunner
import atropos.core.worktree.GitWorktreeOperation
import java.nio.file.Path
import java.time.Instant

class SelfHostPromotionService(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val store: GoalRunStore,
    private val dagService: DagExecutionService,
    private val completionGate: VerifiedCompletionGate,
    private val jarSwapGate: SafeJarSwapGate = SafeJarSwapGate(),
    private val evidenceRenderer: SelfHostPromotionEvidence = SelfHostPromotionEvidence(),
    private val safetyGate: SelfHostSafetyHardFailGate = SelfHostSafetyHardFailGate(repoRoot),
    private val directorService: DirectorService = DirectorService(DirectorStore(repoRoot), repoRoot),
    private val promotionGateContract: SelfHostPromotionGateContract = SelfHostPromotionGateContract(),
    private val evaluateGate: (DagNode) -> CompletionGateReport = completionGate::evaluateNode,
    private val commandRunner: BoundedGitWorktreeCommandRunner = BoundedGitWorktreeCommandRunner()
) {
    fun promote(request: SelfHostPromotionRequest): SelfHostPromotionResult {
        val record = store.resolve(request.goalId)
            ?: return SelfHostPromotionResult(false, "goal not found: ${request.goalId}", null, null, null)
        val dagId = record.dagId
            ?: return SelfHostPromotionResult(false, "goal has no DAG: ${record.id}", SelfHostGoal(record, null), null, null)
        val dag = dagService.readDag(dagId)
            ?: return SelfHostPromotionResult(false, "DAG not found: $dagId", SelfHostGoal(record, null), null, null)
        if (record.terminalCondition != GoalTerminalCondition.VERIFIED_COMPLETE) {
            val reason = "promotion requires VERIFIED_COMPLETE goal; observed=${record.terminalCondition ?: record.status}"
            val refused = store.update(record.copy(evidence = record.evidence + "promotion_refused reason=$reason"))
            return SelfHostPromotionResult(
                promoted = false,
                message = "promotion refused: $reason",
                goal = SelfHostGoal(refused, dag),
                gateReport = null,
                jarSwap = null
            )
        }
        val incomplete = dag.nodes.filter { it.state != atropos.core.dag.DagNodeState.COMPLETE }
        if (incomplete.isNotEmpty()) {
            val reason = "promotion requires every DAG node COMPLETE; incomplete=${incomplete.joinToString(",") { "${it.id}:${it.state}" }}"
            val refused = store.update(record.copy(evidence = record.evidence + "promotion_refused reason=$reason"))
            return SelfHostPromotionResult(
                promoted = false,
                message = "promotion refused: $reason",
                goal = SelfHostGoal(refused, dag),
                gateReport = null,
                jarSwap = null
            )
        }
        val selectedNodeId = request.nodeId ?: record.currentNodeId
            ?: return SelfHostPromotionResult(false, "goal has no selected node", SelfHostGoal(record, dag), null, null)
        val node = dag.findNode(selectedNodeId)
            ?: return SelfHostPromotionResult(false, "node not found: $selectedNodeId", SelfHostGoal(record, dag), null, null)

        val safety = safetyGate.inspect(record, node)
        val safetyEvidence = safety.evidenceLine()
        if (!safety.passed) {
            val refused = store.update(record.copy(evidence = record.evidence + safetyEvidence))
            return SelfHostPromotionResult(
                promoted = false,
                message = "promotion refused by self-host safety hard-fail gate: " +
                    safety.findings.joinToString("; ") { "${it.kind}: ${it.message}" },
                goal = SelfHostGoal(refused, dag),
                gateReport = null,
                jarSwap = null
            )
        }

        val directorAdvisory = directorService.advisoryBeforePromotion(
            goalId = record.id,
            territoryIds = node.territory,
            files = node.expectedOutputs + record.territory
        )
        val directorEvidence = evidenceRenderer.directorAdvisory(directorAdvisory)
        if (!directorAdvisory.allowed) {
            val refused = store.update(record.copy(evidence = record.evidence + safetyEvidence + directorEvidence))
            return SelfHostPromotionResult(
                promoted = false,
                message = "promotion refused by Director pre-promote advisory: ${directorAdvisory.message}",
                goal = SelfHostGoal(refused, dag),
                gateReport = null,
                jarSwap = null
            )
        }

        val gateReport = evaluateGate(node)
        val gateEvidence = evidenceRenderer.gateReport(gateReport)
        val gateRefusal = promotionGateContract.refusal(gateReport, node.id)
        if (gateRefusal != null) {
            val refused = store.update(record.copy(evidence = record.evidence + safetyEvidence + directorEvidence + gateEvidence))
            return SelfHostPromotionResult(
                promoted = false,
                message = "promotion refused by VerifiedCompletionGate: $gateRefusal",
                goal = SelfHostGoal(refused, dag),
                gateReport = gateReport,
                jarSwap = null
            )
        }

        val swap = jarSwapGate.promote(
            request.candidateJar,
            request.targetJar,
            gateReport.gateResults.map { JarSwapEvidence(it.passed, it.gateName, it.detail) }
        )

        var finalEvidence = record.evidence + safetyEvidence + directorEvidence + gateEvidence + evidenceRenderer.jarSwap(swap)
        var pushMessage = swap.message

        if (swap.promoted) {
            val statusResult = commandRunner.run(GitWorktreeOperation.STATUS_PORCELAIN, repoRoot)
            val pushResult = commandRunner.run(GitWorktreeOperation.PUSH, repoRoot)
            
            finalEvidence += listOf(
                "git_status exitCode=${statusResult.exitCode}",
                "git_push exitCode=${pushResult.exitCode}"
            )
            
            if (pushResult.exitCode != 0) {
                pushMessage += "; push failed"
            }
        }

        val updated = store.update(
            record.copy(
                status = if (swap.promoted) record.status else GoalRunStatus.FAILED,
                terminalCondition = if (swap.promoted) {
                    record.terminalCondition
                } else {
                    GoalTerminalCondition.TERMINAL_FAILURE
                },
                finishedAt = if (swap.promoted) record.finishedAt else Instant.now(),
                failureReason = if (swap.promoted) record.failureReason else "jar swap failed: ${swap.message}",
                evidence = finalEvidence,
                lastVerifiedCheckpoint = if (swap.promoted) "jar:${swap.targetJar.fileName}" else record.lastVerifiedCheckpoint
            )
        )
        return SelfHostPromotionResult(
            promoted = swap.promoted,
            message = pushMessage,
            goal = SelfHostGoal(updated, dagService.readDag(dagId) ?: dag),
            gateReport = gateReport,
            jarSwap = swap,
            promotedAt = swap.promotedAt
        )
    }
}
