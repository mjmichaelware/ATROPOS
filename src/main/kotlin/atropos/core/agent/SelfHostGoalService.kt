package atropos.core.agent

import atropos.core.AtroposRepoRootLocator
import atropos.core.dag.DagExecutionService
import atropos.core.dag.DagNodeState
import atropos.core.memory.LocalMemoryStore
import atropos.core.memory.MemoryKind
import atropos.core.provider.ContextEnvelope
import atropos.core.recovery.RestartCoordinator
import atropos.core.verification.VerifiedCompletionGate
import java.nio.file.Path
import java.time.Instant

class SelfHostGoalService(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val store: GoalRunStore = GoalRunStore(repoRoot),
    private val continuationService: GoalContinuationService = GoalContinuationService(repoRoot),
    private val dagService: DagExecutionService = DagExecutionService(repoRoot = repoRoot),
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile()),
    private val cradleVerificationGate: SelfHostCradleVerificationGate = SelfHostCradleVerificationGate(),
    private val completionGate: VerifiedCompletionGate = VerifiedCompletionGate(repoRoot = repoRoot),
    private val restartCoordinator: RestartCoordinator = RestartCoordinator(repoRoot = repoRoot, goalRunStore = store),
    private val stateSnapshotRecorder: SelfHostStateSnapshotRecorder = SelfHostStateSnapshotRecorder(restartCoordinator),
    private val clock: () -> Instant = { Instant.now() }
) {
    private val bootstrapDagFactory = SelfHostBootstrapDagFactory(repoRoot, dagService, clock)
    private val goalStartService = SelfHostGoalStartService(repoRoot = repoRoot, store = store, bootstrapDagFactory = bootstrapDagFactory, memoryStore = memoryStore, stateSnapshotRecorder = stateSnapshotRecorder, clock = clock)
    private val goalQueries = SelfHostGoalQueryService(store, dagService, memoryStore)
    private val contextPreflight = SelfHostContextPreflight(repoRoot)
    private val dagNodeEvaluator = SelfHostDagNodeEvaluator(store = store, dagService = dagService, contextPreflight = contextPreflight, cradleVerificationGate = cradleVerificationGate, experienceRecorder = SelfHostExperienceRecorder(memoryStore), worktreeNodeExecutor = SelfHostWorktreeNodeExecutor(repoRoot))
    private val promotionService = SelfHostPromotionService(repoRoot = repoRoot, store = store, dagService = dagService, completionGate = completionGate)
    private val promotionBoundary = SelfHostGoalPromotionBoundary(store, stateSnapshotRecorder, promotionService)
    private val evidenceBundleExporter = SelfHostEvidenceBundleExporter(repoRoot = repoRoot, store = store, dagService = dagService, restartCoordinator = restartCoordinator)
    private val stateUpdater = SelfHostGoalStateUpdater(store, dagService, stateSnapshotRecorder)

    fun startGoal(goalName: String, phase: String): SelfHostResult = goalStartService.start(goalName, phase)

    fun loadGoal(goalId: String): SelfHostResult {
        return goalQueries.load(goalId)
    }

    fun loadUnfinishedGoals(): List<SelfHostGoal> = goalQueries.unfinished()

    fun resolveResumableGoal(goalId: String? = null): SelfHostResult {
        return goalQueries.resolve(goalId, requireUnfinished = true, operation = "resume")
    }

    fun resolveWatchGoal(goalId: String? = null): SelfHostResult {
        return goalQueries.resolve(goalId, requireUnfinished = false, operation = "watch")
    }

    fun resolveStatusGoal(goalId: String? = null): SelfHostResult {
        return goalQueries.resolve(goalId, requireUnfinished = false, operation = "status")
    }

    fun resolveStoppableGoal(goalId: String? = null): SelfHostResult {
        return goalQueries.resolve(goalId, requireUnfinished = true, operation = "stop")
    }

    fun status(goalId: String? = null): SelfHostStatus = goalQueries.status(goalId)
    fun updatePhase(goalId: String, phase: String): SelfHostResult = stateUpdater.updatePhase(goalId, phase)
    fun updateCurrentNode(goalId: String, nodeId: String): SelfHostResult = stateUpdater.updateCurrentNode(goalId, nodeId)
    fun setDag(goalId: String, dagId: String): SelfHostResult = stateUpdater.setDag(goalId, dagId)
    fun addEvidence(goalId: String, evidenceEntry: String): SelfHostResult = stateUpdater.addEvidence(goalId, evidenceEntry)
    fun setTerritory(goalId: String, territory: List<String>): SelfHostResult = stateUpdater.setTerritory(goalId, territory)
    fun updateVerifiedCheckpoint(goalId: String, checkpoint: String): SelfHostResult = stateUpdater.updateVerifiedCheckpoint(goalId, checkpoint)

    fun completeGoal(goalId: String, condition: GoalTerminalCondition, reason: String? = null): SelfHostResult {
        val result = continuationService.completeRun(goalId, condition, reason)
        if (!result.ok) return SelfHostResult(false, result.message)
        val record = result.record ?: return SelfHostResult(false, "goal not found after completion")
        memoryStore.rememberDetailed(
            kind = atropos.core.memory.MemoryKind.SESSION,
            title = "self-host goal completed: $condition",
            body = "goal=$goalId reason=${reason ?: "none"}",
            tags = listOf("selfhost", "goal", condition.name.lowercase()),
            subjectType = "selfhost_goal",
            subjectId = goalId
        )
        val snapshotted = store.update(record.copy(evidence = record.evidence + stateSnapshotRecorder.captureEvidence("complete:${condition.name}", record.id)))
        return SelfHostResult(true, "goal completed: $condition", SelfHostGoal(snapshotted, null))
    }

    fun stopForExternalInput(goalId: String, reason: String): SelfHostResult =
        completeGoal(goalId, GoalTerminalCondition.EXTERNAL_INPUT_REQUIRED, reason)

    fun resumeGoal(goalId: String, compactState: String? = null): SelfHostResult {
        val record = store.resolve(goalId)
            ?: return SelfHostResult(false, "goal not found: $goalId")
        if (record.isTerminal()) {
            return SelfHostResult(false, "goal already terminal: ${record.terminalCondition}", SelfHostGoal(record, null))
        }
        val resumed = continuationService.continueRun(
            record.id,
            GoalContinuationRequest(
                goalRunId = record.id,
                compactState = compactState,
                continuationIndex = record.continuationCount + 1,
                lastResponseSummary = null,
                provider = record.provider ?: "self-host"
            )
        )
        if (!resumed.ok) {
            return SelfHostResult(false, resumed.message, resumed.record?.let { SelfHostGoal(it, null) })
        }
        val updated = resumed.record ?: return SelfHostResult(false, "goal not found after resume")
        val snapshotted = appendSnapshotEvidence(updated, "resume")
        val dag = snapshotted.dagId?.let { dagService.readDag(it) }
        return SelfHostResult(true, resumed.message, SelfHostGoal(snapshotted, dag))
    }

    fun selectNextDagNode(goalId: String): SelfHostResult {
        val record = store.resolve(goalId)
            ?: return SelfHostResult(false, "goal not found: $goalId")
        val dagId = record.dagId ?: return SelfHostResult(false, "no DAG assigned to goal")
        val dag = dagService.readDag(dagId) ?: return SelfHostResult(false, "DAG not found: $dagId")

        val dagStatus = dagService.status(dagId) ?: return SelfHostResult(false, "unable to get DAG status")
        val readyNodes = dagStatus.readyNodes

        if (readyNodes.isEmpty()) {
            val allTerminal = dag.nodes.all { it.state.terminal }
            if (allTerminal) {
                val failedCount = dag.nodes.count { it.state == DagNodeState.FAILED }
                val blockedCount = dag.nodes.count { it.state == DagNodeState.BLOCKED }
                val completed = if (failedCount > 0 || blockedCount > 0) {
                    completeGoal(goalId, GoalTerminalCondition.TERMINAL_FAILURE, "$failedCount failed, $blockedCount blocked nodes")
                } else {
                    addEvidence(
                        goalId,
                        "dag=$dagId nodes=${dag.nodes.size} complete=" +
                            dag.nodes.count { it.state == DagNodeState.COMPLETE } +
                            " not-applicable=" + dag.nodes.count { it.state == DagNodeState.NOT_APPLICABLE }
                    )
                    completeGoal(goalId, GoalTerminalCondition.VERIFIED_COMPLETE, "all nodes complete")
                }
                if (completed.ok) {
                    return SelfHostResult(false, "no ready nodes in DAG $dagId", SelfHostGoal(completed.goal?.record ?: record, dag))
                }
            }
            return SelfHostResult(false, "no ready nodes in DAG $dagId", SelfHostGoal(record, dag))
        }

        val nextNodeId = readyNodes.first()
        val updated = appendSnapshotEvidence(store.update(record.copy(currentNodeId = nextNodeId)), "select:$nextNodeId")
        return SelfHostResult(true, "selected next node: $nextNodeId", SelfHostGoal(updated, dag))
    }

    fun contextEnvelopeForCurrentNode(goalId: String): ContextEnvelope? {
        val record = store.resolve(goalId) ?: return null
        val currentNodeId = record.currentNodeId ?: return null
        val dagId = record.dagId ?: return null
        val dag = dagService.readDag(dagId) ?: return null
        val node = dag.findNode(currentNodeId) ?: return null
        return contextPreflight.canonicalEnvelope(record, node)
    }

    fun evaluateReadyDagNode(goalId: String, suppliedEnvelope: ContextEnvelope? = null): SelfHostResult {
        return dagNodeEvaluator.evaluate(goalId, suppliedEnvelope)
    }

    fun advanceGoal(
        goalId: String,
        compactState: String? = "self-host resume",
        suppliedEnvelope: ContextEnvelope? = null
    ): SelfHostResult {
        val before = store.resolve(goalId)
            ?: return SelfHostResult(false, "goal not found: $goalId")
        val resumed = if (before.status == GoalRunStatus.RECOVERY_REQUIRED || before.status == GoalRunStatus.RUNNING) {
            resumeGoal(goalId, compactState)
        } else {
            SelfHostResult(true, "goal already continuing", SelfHostGoal(before, before.dagId?.let { dagService.readDag(it) }))
        }
        if (!resumed.ok) return resumed

        val selected = selectNextDagNode(goalId)
        if (!selected.ok) {
            val selectedRecord = selected.goal?.record
            if (selectedRecord?.terminalCondition == GoalTerminalCondition.VERIFIED_COMPLETE) {
                return SelfHostResult(true, "completed: all DAG nodes done", selected.goal)
            }
            if (selectedRecord?.terminalCondition == GoalTerminalCondition.TERMINAL_FAILURE) {
                return SelfHostResult(false, "failed: ${selectedRecord.failureReason ?: selected.message}", selected.goal)
            }
            return selected
        }

        val evaluated = evaluateReadyDagNode(goalId, suppliedEnvelope)
        val latest = store.resolve(goalId)
        val latestDag = latest?.dagId?.let { dagService.readDag(it) }
        if (latestDag != null && latestDag.nodes.all { it.state.terminal }) {
            val failedCount = latestDag.nodes.count { it.state == DagNodeState.FAILED }
            val blockedCount = latestDag.nodes.count { it.state == DagNodeState.BLOCKED }
            if (failedCount > 0 || blockedCount > 0) {
                val completed = completeGoal(goalId, GoalTerminalCondition.TERMINAL_FAILURE, "$failedCount failed, $blockedCount blocked nodes")
                return SelfHostResult(false, completed.message, completed.goal)
            }
            val completion = completeGoal(goalId, GoalTerminalCondition.VERIFIED_COMPLETE, "all nodes complete")
            if (!completion.ok) return completion
            return SelfHostResult(true, "advanced and completed: all nodes complete", SelfHostGoal(completion.goal?.record ?: latest ?: before, latestDag))
        }
        store.resolve(goalId)?.let { appendSnapshotEvidence(it, "advance") }
        return evaluated
    }

    fun advanceNextResumableGoal(
        goalId: String? = null,
        compactState: String? = "self-host automatic continuation",
        suppliedEnvelope: ContextEnvelope? = null
    ): SelfHostResult {
        val selected = resolveResumableGoal(goalId)
        if (!selected.ok) return selected
        val record = selected.goal?.record
            ?: return SelfHostResult(false, "no resumable self-host goal selected")
        // Select first, then bind the envelope to the exact node selected after
        // restart recovery. Direct advanceGoal calls remain fail-closed when
        // they omit an envelope; automatic continuation owns this binding.
        val selectedNode = selectNextDagNode(record.id)
        if (!selectedNode.ok) {
            val terminal = selectedNode.goal?.record?.terminalCondition
            return when (terminal) {
                GoalTerminalCondition.VERIFIED_COMPLETE ->
                    SelfHostResult(true, "completed: all DAG nodes done", selectedNode.goal)
                GoalTerminalCondition.TERMINAL_FAILURE ->
                    SelfHostResult(false, "failed: ${selectedNode.goal?.record?.failureReason ?: selectedNode.message}", selectedNode.goal)
                else -> selectedNode
            }
        }
        val envelope = suppliedEnvelope ?: contextEnvelopeForCurrentNode(record.id)
            ?: return SelfHostResult(false, "context envelope unavailable for selected self-host node")
        return advanceGoal(record.id, compactState, envelope)
    }

    fun planNextAction(goalId: String? = null): SelfHostNextAction = goalQueries.nextAction(goalId)

    fun promoteVerifiedJar(
        goalId: String,
        candidateJar: Path,
        targetJar: Path,
        nodeId: String? = null
    ): SelfHostPromotionResult = promotionBoundary.promote(goalId, candidateJar, targetJar, nodeId)

    fun recoverAndContinue(
        goalId: String? = null,
        compactState: String? = "self-host restart recovery"
    ): SelfHostResult {
        val recovered = restartCoordinator.recoverAndSnapshot()
        val selected = resolveResumableGoal(goalId)
        if (!selected.ok) {
            return SelfHostResult(false, "${recovered.message}; ${selected.message}", selected.goal)
        }
        val record = selected.goal?.record
            ?: return SelfHostResult(false, "${recovered.message}; no resumable self-host goal selected")
        if (!recovered.ok) {
            val refusal = "restart recovery refused continuation: ${recovered.message}"
            val recorded = addEvidence(
                record.id,
                "restart_recovery_stop goal=${record.id} reason=${recovered.message}"
            )
            return SelfHostResult(
                false,
                refusal,
                recorded.goal ?: selected.goal
            )
        }
        val restoredNode = recovered.restoredNodes
            .firstOrNull { it.restored && it.dagId == record.dagId }
        val nextAction = planNextAction(record.id)
        val evidence = listOf(
            "restart_snapshot id=${recovered.snapshot.id} goals=${recovered.snapshot.goalRuns.size} dags=${recovered.snapshot.dags.size}",
            "restart_recovery ok=${recovered.ok} restored=${recovered.restoredNodes.count { it.restored }} blocked=${recovered.restoredNodes.count { !it.restored }}",
            "restart_next goal=${record.id} node=${nextAction.nodeId ?: restoredNode?.nodeId ?: "none"}",
            nextAction.evidenceLine()
        )
        store.update(record.copy(evidence = record.evidence + evidence))
        return when (nextAction.kind) {
            SelfHostNextActionKind.ADVANCE_NODE,
            SelfHostNextActionKind.ADVANCE_GOAL -> advanceNextResumableGoal(record.id, compactState)
            SelfHostNextActionKind.PROMOTE_JAR -> SelfHostResult(
                false,
                "restart continuation stopped at promotion boundary: ${nextAction.reason}",
                SelfHostGoal(record, record.dagId?.let { dagService.readDag(it) })
            )
            SelfHostNextActionKind.WAIT_EXTERNAL_INPUT,
            SelfHostNextActionKind.HARD_STOP -> SelfHostResult(
                false,
                "restart continuation stopped: ${nextAction.reason}",
                SelfHostGoal(record, record.dagId?.let { dagService.readDag(it) })
            )
            SelfHostNextActionKind.COMPLETE -> SelfHostResult(
                true,
                "restart continuation complete: ${nextAction.reason}",
                SelfHostGoal(record, record.dagId?.let { dagService.readDag(it) })
            )
        }
    }

    fun exportEvidenceBundle(goalId: String): SelfHostEvidenceBundleResult {
        val evidenceResult = addEvidence(goalId, stateSnapshotRecorder.captureEvidence("pre-evidence-export", goalId))
        if (!evidenceResult.ok) {
            return SelfHostEvidenceBundleResult(
                ok = false,
                message = "evidence export refused: ${evidenceResult.message}",
                markdownPath = null,
                jsonPath = null,
                markdownSha256 = null,
                jsonSha256 = null,
                failureCode = SelfHostFailureCode.EVIDENCE_EXPORT_FAILED
            )
        }
        return evidenceBundleExporter.export(goalId)
    }

    fun runNaturalLanguageSelfBuild(
        prompt: String,
        phase: String = "11",
        lifecycleEmitter: (String) -> Unit = ::println
    ): SelfHostAutonomousRunResult =
        SelfHostAutonomousRunner(
            service = this,
            jarLocator = SelfHostRuntimeJarLocator(repoRoot),
            jarBuilder = SelfHostCandidateJarBuilder(repoRoot),
            gitStatusEvidence = SelfHostGitStatusEvidence(repoRoot)
        ).run(prompt, phase, lifecycleEmitter = lifecycleEmitter)

    fun history(limit: Int = 20): List<GoalRunRecord> = goalQueries.history(limit)
    fun benchmarkHistory(): List<GoalRunRecord> = goalQueries.benchmarkHistory()
    fun benchmark(): SelfHostBenchmark = goalQueries.benchmark()
    fun learned(limit: Int = 20): List<atropos.core.memory.MemoryRecord> = goalQueries.learned(limit)

    private fun appendSnapshotEvidence(record: GoalRunRecord, reason: String): GoalRunRecord =
        store.update(record.copy(evidence = record.evidence + stateSnapshotRecorder.captureEvidence(reason, record.id)))
}
