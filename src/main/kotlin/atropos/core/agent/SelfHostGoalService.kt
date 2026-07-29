package atropos.core.agent

import atropos.core.AtroposRepoRootLocator
import atropos.core.dag.DagExecutionService
import atropos.core.dag.DagNodeState
import atropos.core.memory.MemoryRecord
import atropos.core.memory.LocalMemoryStore
import atropos.core.provider.ContextEnvelope
import atropos.core.recovery.RestartCoordinator
import atropos.core.verification.VerifiedCompletionGate
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

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
    private val selector = SelfHostGoalSelector(store)
    private val benchmarkService = SelfHostBenchmarkService(selector)
    private val experienceRecorder = SelfHostExperienceRecorder(memoryStore)
    private val contextPreflight = SelfHostContextPreflight(repoRoot)
    private val dagNodeEvaluator = SelfHostDagNodeEvaluator(
        store = store,
        dagService = dagService,
        contextPreflight = contextPreflight,
        cradleVerificationGate = cradleVerificationGate,
        experienceRecorder = experienceRecorder,
        worktreeNodeExecutor = SelfHostWorktreeNodeExecutor(repoRoot)
    )
    private val promotionService = SelfHostPromotionService(
        repoRoot = repoRoot,
        store = store,
        dagService = dagService,
        completionGate = completionGate
    )
    private val evidenceBundleExporter = SelfHostEvidenceBundleExporter(
        repoRoot = repoRoot,
        store = store,
        dagService = dagService,
        restartCoordinator = restartCoordinator
    )

    fun startGoal(goalName: String, phase: String): SelfHostResult {
        try {
            Files.createDirectories(store.runsRoot())

            val baselineCommit = runCatching {
                val proc = ProcessBuilder("git", "rev-parse", "HEAD")
                    .directory(repoRoot.toFile())
                    .redirectErrorStream(true)
                    .start()
                proc.inputStream.bufferedReader().readText().trim()
            }.getOrNull()

            val dirtyFingerprint = runCatching {
                val proc = ProcessBuilder("git", "status", "--porcelain")
                    .directory(repoRoot.toFile())
                    .redirectErrorStream(true)
                    .start()
                val status = proc.inputStream.bufferedReader().readText()
                bootstrapDagFactory.fingerprint(status)
            }.getOrNull()

            val goalId = "shg-" + UUID.randomUUID().toString().take(12)
            val now = clock()
            val metaFile = store.runsRoot().resolve("$goalId.meta")
            val record = GoalRunRecord(
                id = goalId,
                goalId = goalId,
                task = goalName.trim(),
                provider = "self-host",
                status = GoalRunStatus.RUNNING,
                baselineCommit = baselineCommit,
                dirtyStateFingerprint = dirtyFingerprint,
                activePhase = phase,
                createdAt = now,
                updatedAt = now,
                metaFile = metaFile
            )

            val stored = store.update(record)
            val bootstrapDag = bootstrapDagFactory.create(stored, phase)
            val recordWithDag = store.update(
                stored.copy(
                    dagId = bootstrapDag.id,
                    territory = bootstrapDag.nodes.flatMap { it.territory }.distinct(),
                    currentNodeId = bootstrapDag.findReadyNodes().firstOrNull()?.id
                )
            )

            memoryStore.rememberDetailed(
                kind = atropos.core.memory.MemoryKind.SESSION,
                title = "self-host goal started: $goalName",
                body = "phase=$phase baseline=${baselineCommit?.take(12)} dag=${bootstrapDag.id}",
                tags = listOf("selfhost", "goal", "started"),
                subjectType = "selfhost_goal",
                subjectId = goalId
            )

            val snapshotted = store.update(
                recordWithDag.copy(evidence = recordWithDag.evidence + stateSnapshotRecorder.captureEvidence("start"))
            )
            return SelfHostResult(true, "self-host goal started: $goalId", SelfHostGoal(snapshotted, bootstrapDag))
        } catch (e: Exception) {
            return SelfHostResult(false, "failed to start self-host goal: ${e.message}")
        }
    }

    fun loadGoal(goalId: String): SelfHostResult {
        val record = store.resolve(goalId)
            ?: return SelfHostResult(false, "goal not found: $goalId")
        if (record.isTerminal()) {
            return SelfHostResult(false, "goal already terminal: ${record.terminalCondition}", SelfHostGoal(record, null))
        }
        val dag = record.dagId?.let { dagService.readDag(it) }
        return SelfHostResult(true, "goal loaded: $goalId", SelfHostGoal(record, dag))
    }

    fun loadUnfinishedGoals(): List<SelfHostGoal> {
        return selector.unfinishedSelfHostRuns()
            .mapNotNull { record ->
                val dag = record.dagId?.let { dagService.readDag(it) }
                SelfHostGoal(record, dag)
            }
    }

    fun resolveResumableGoal(goalId: String? = null): SelfHostResult {
        val record = selector.resolveSelfHostGoalRecord(
            goalId = goalId,
            requireUnfinished = true
        ) ?: return SelfHostResult(false, selector.missingSelfHostGoalMessage(goalId, requireUnfinished = true, operation = "resume"))
        val dag = record.dagId?.let { dagService.readDag(it) }
        return SelfHostResult(true, "resumable goal selected: ${record.id}", SelfHostGoal(record, dag))
    }

    fun resolveWatchGoal(goalId: String? = null): SelfHostResult {
        val record = selector.resolveSelfHostGoalRecord(
            goalId = goalId,
            requireUnfinished = false
        ) ?: return SelfHostResult(false, selector.missingSelfHostGoalMessage(goalId, requireUnfinished = false, operation = "watch"))
        val dag = record.dagId?.let { dagService.readDag(it) }
        return SelfHostResult(true, "watch goal selected: ${record.id}", SelfHostGoal(record, dag))
    }

    fun resolveStatusGoal(goalId: String? = null): SelfHostResult {
        val record = selector.resolveSelfHostGoalRecord(
            goalId = goalId,
            requireUnfinished = false
        ) ?: return SelfHostResult(false, selector.missingSelfHostGoalMessage(goalId, requireUnfinished = false, operation = "inspect"))
        val dag = record.dagId?.let { dagService.readDag(it) }
        return SelfHostResult(true, "status goal selected: ${record.id}", SelfHostGoal(record, dag))
    }

    fun resolveStoppableGoal(goalId: String? = null): SelfHostResult {
        val record = selector.resolveSelfHostGoalRecord(
            goalId = goalId,
            requireUnfinished = true
        ) ?: return SelfHostResult(false, selector.missingSelfHostGoalMessage(goalId, requireUnfinished = true, operation = "stop"))
        val dag = record.dagId?.let { dagService.readDag(it) }
        return SelfHostResult(true, "stoppable goal selected: ${record.id}", SelfHostGoal(record, dag))
    }

    fun status(goalId: String? = null): SelfHostStatus {
        val record = selector.resolveSelfHostGoalRecord(
            goalId = goalId,
            requireUnfinished = false
        ) ?: return SelfHostStatus(
            goalId = goalId ?: "none",
            status = GoalRunStatus.FAILED,
            terminalCondition = GoalTerminalCondition.TERMINAL_FAILURE,
            phase = null,
            currentNodeId = null,
            dagStatus = null,
            message = selector.missingSelfHostGoalMessage(goalId, requireUnfinished = false, operation = "inspect")
        )

        val dagStatus = record.dagId?.let { dagService.status(it) }
        return SelfHostStatus(
            goalId = record.id,
            status = record.status,
            terminalCondition = record.terminalCondition,
            phase = record.activePhase,
            currentNodeId = record.currentNodeId,
            dagStatus = dagStatus,
            message = "goal ${record.id}: ${record.status}"
        )
    }

    fun updatePhase(goalId: String, phase: String): SelfHostResult {
        val record = store.resolve(goalId)
            ?: return SelfHostResult(false, "goal not found: $goalId")
        val updated = store.update(record.copy(activePhase = phase))
        stateSnapshotRecorder.captureEvidence("phase")
        return SelfHostResult(true, "phase updated to $phase", SelfHostGoal(updated, null))
    }

    fun updateCurrentNode(goalId: String, nodeId: String): SelfHostResult {
        val record = store.resolve(goalId)
            ?: return SelfHostResult(false, "goal not found: $goalId")
        val updated = store.update(record.copy(currentNodeId = nodeId))
        stateSnapshotRecorder.captureEvidence("current-node")
        return SelfHostResult(true, "current node set to $nodeId", SelfHostGoal(updated, null))
    }

    fun setDag(goalId: String, dagId: String): SelfHostResult {
        val record = store.resolve(goalId)
            ?: return SelfHostResult(false, "goal not found: $goalId")
        val dag = dagService.readDag(dagId)
            ?: return SelfHostResult(false, "DAG not found: $dagId")
        val updated = store.update(record.copy(dagId = dagId))
        stateSnapshotRecorder.captureEvidence("dag")
        return SelfHostResult(true, "DAG set to $dagId", SelfHostGoal(updated, dag))
    }

    fun addEvidence(goalId: String, evidenceEntry: String): SelfHostResult {
        val record = store.resolve(goalId)
            ?: return SelfHostResult(false, "goal not found: $goalId")
        val updated = store.update(record.copy(
            evidence = record.evidence + evidenceEntry
        ))
        stateSnapshotRecorder.captureEvidence("evidence")
        return SelfHostResult(true, "evidence added", SelfHostGoal(updated, null))
    }

    fun setTerritory(goalId: String, territory: List<String>): SelfHostResult {
        val record = store.resolve(goalId)
            ?: return SelfHostResult(false, "goal not found: $goalId")
        val updated = store.update(record.copy(territory = territory))
        stateSnapshotRecorder.captureEvidence("territory")
        return SelfHostResult(true, "territory set", SelfHostGoal(updated, null))
    }

    fun updateVerifiedCheckpoint(goalId: String, checkpoint: String): SelfHostResult {
        val record = store.resolve(goalId)
            ?: return SelfHostResult(false, "goal not found: $goalId")
        val updated = store.update(record.copy(lastVerifiedCheckpoint = checkpoint))
        stateSnapshotRecorder.captureEvidence("checkpoint")
        return SelfHostResult(true, "checkpoint updated to $checkpoint", SelfHostGoal(updated, null))
    }

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
        val snapshotted = store.update(record.copy(evidence = record.evidence + stateSnapshotRecorder.captureEvidence("complete:${condition.name}")))
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
        stateSnapshotRecorder.captureEvidence("resume")
        val dag = updated.dagId?.let { dagService.readDag(it) }
        return SelfHostResult(true, resumed.message, SelfHostGoal(updated, dag))
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
        val updated = store.update(record.copy(currentNodeId = nextNodeId))
        stateSnapshotRecorder.captureEvidence("select:$nextNodeId")
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

    fun evaluateReadyDagNode(goalId: String, suppliedEnvelope: ContextEnvelope? = contextEnvelopeForCurrentNode(goalId)): SelfHostResult {
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

        val evaluated = evaluateReadyDagNode(goalId, suppliedEnvelope ?: contextEnvelopeForCurrentNode(goalId))
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
        stateSnapshotRecorder.captureEvidence("advance")
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
        return advanceGoal(record.id, compactState, suppliedEnvelope ?: contextEnvelopeForCurrentNode(record.id))
    }

    fun planNextAction(goalId: String? = null): SelfHostNextAction {
        val selected = selector.resolveSelfHostGoalRecord(goalId, requireUnfinished = false)
            ?: return SelfHostNextAction(SelfHostNextActionKind.COMPLETE, null, null, "no self-host goals exist")
        val dag = selected.dagId?.let { dagService.readDag(it) }
        val readyNode = dag?.findReadyNodes()?.firstOrNull()
        if (readyNode != null && !selected.isTerminal()) {
            return SelfHostNextAction(SelfHostNextActionKind.ADVANCE_NODE, selected.id, readyNode.id, "ready DAG node")
        }
        if (selected.terminalCondition == GoalTerminalCondition.VERIFIED_COMPLETE) {
            if (selected.lastVerifiedCheckpoint?.startsWith("jar:") == true) {
                return SelfHostNextAction(SelfHostNextActionKind.COMPLETE, selected.id, selected.currentNodeId, "verified jar already promoted")
            }
            return SelfHostNextAction(SelfHostNextActionKind.PROMOTE_JAR, selected.id, selected.currentNodeId, "source DAG verified; jar promotion boundary")
        }
        if (selected.terminalCondition == GoalTerminalCondition.EXTERNAL_INPUT_REQUIRED) {
            return SelfHostNextAction(SelfHostNextActionKind.WAIT_EXTERNAL_INPUT, selected.id, selected.currentNodeId, selected.failureReason ?: "external input required")
        }
        if (selected.isTerminal()) {
            return SelfHostNextAction(SelfHostNextActionKind.COMPLETE, selected.id, selected.currentNodeId, selected.terminalCondition?.name ?: selected.status.name)
        }
        return SelfHostNextAction(SelfHostNextActionKind.HARD_STOP, selected.id, selected.currentNodeId, "no ready node on unfinished self-host goal")
    }

    fun promoteVerifiedJar(
        goalId: String,
        candidateJar: Path,
        targetJar: Path,
        nodeId: String? = null
    ): SelfHostPromotionResult {
        addEvidence(goalId, stateSnapshotRecorder.captureEvidence("pre-promote"))
        val result = promotionService.promote(
            SelfHostPromotionRequest(
                goalId = goalId,
                nodeId = nodeId,
                candidateJar = candidateJar,
                targetJar = targetJar
            )
        )
        addEvidence(goalId, stateSnapshotRecorder.captureEvidence("post-promote"))
        return result
    }

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
        val restoredNode = recovered.restoredNodes.firstOrNull { it.restored }
        val evidence = listOf(
            "restart_snapshot id=${recovered.snapshot.id} goals=${recovered.snapshot.goalRuns.size} dags=${recovered.snapshot.dags.size}",
            "restart_recovery ok=${recovered.ok} restored=${recovered.restoredNodes.count { it.restored }} blocked=${recovered.restoredNodes.count { !it.restored }}",
            "restart_next goal=${record.id} node=${record.currentNodeId ?: restoredNode?.nodeId ?: "none"}",
            planNextAction(record.id).evidenceLine()
        )
        store.update(record.copy(evidence = record.evidence + evidence))
        return advanceNextResumableGoal(record.id, compactState, contextEnvelopeForCurrentNode(record.id))
    }

    fun exportEvidenceBundle(goalId: String): SelfHostEvidenceBundleResult {
        addEvidence(goalId, stateSnapshotRecorder.captureEvidence("pre-evidence-export"))
        return evidenceBundleExporter.export(goalId)
    }

    fun runNaturalLanguageSelfBuild(
        prompt: String,
        phase: String = "11"
    ): SelfHostAutonomousRunResult =
        SelfHostAutonomousRunner(
            service = this,
            jarLocator = SelfHostRuntimeJarLocator(repoRoot),
            jarBuilder = SelfHostCandidateJarBuilder(repoRoot)
        ).run(prompt, phase)

    fun history(limit: Int = 20): List<GoalRunRecord> =
        selector.allSelfHostRuns().take(limit.coerceAtLeast(1))
    fun benchmarkHistory(): List<GoalRunRecord> = benchmarkService.history()

    fun benchmark(): SelfHostBenchmark = benchmarkService.benchmark()

    fun learned(limit: Int = 20): List<MemoryRecord> =
        memoryStore.findBySubjectTypes(
            subjectTypes = setOf("selfhost_goal", "selfhost_dag_eval"),
            limit = limit
        )

}
