package atropos.core.recovery

import atropos.core.AtroposRepoRootLocator
import atropos.core.agent.GoalRunStatus
import atropos.core.agent.GoalRunStore
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStore
import atropos.core.memory.LocalMemoryStore
import atropos.core.security.RedactionFilter
import atropos.core.worktree.IsolatedWorktreeService
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

class RestartCoordinator(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val crashRecovery: CrashRecoveryService = CrashRecoveryService(repoRoot = repoRoot),
    private val goalRunStore: GoalRunStore = GoalRunStore(repoRoot),
    private val dagStore: DagStore = DagStore(repoRoot),
    private val worktreeService: IsolatedWorktreeService = IsolatedWorktreeService(repoRoot),
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile()),
    private val dagNodeRestorer: DagNodeRestorer = DagNodeRestorer(dagStore),
    private val clock: () -> Instant = { Instant.now() },
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val snapshotDir = repoRoot.resolve(".atropos/recovery/snapshots").normalize()

    fun snapshot(report: RecoveryReport? = null): StateSnapshot {
        val capturedAt = clock()
        val state = StateSnapshot(
            id = "snapshot-" + UUID.randomUUID().toString().take(12),
            capturedAt = capturedAt,
            goalRuns = goalRunStore.listRuns(Int.MAX_VALUE).map {
                GoalRunSnapshot(
                    id = it.id,
                    status = it.status.name,
                    dagId = it.dagId,
                    currentNodeId = it.currentNodeId,
                    continuationCount = it.continuationCount,
                    recoveryRequired = it.status == GoalRunStatus.RECOVERY_REQUIRED,
                    evidenceCount = it.evidence.size,
                    territory = it.territory.map(redactionFilter::redact),
                    evidenceHashes = it.evidence.map { evidence -> sha256(redactionFilter.redact(evidence)) },
                    task = redactionFilter.redact(it.task),
                    baselineCommit = it.baselineCommit?.let(redactionFilter::redact),
                    dirtyStateFingerprint = it.dirtyStateFingerprint?.let(redactionFilter::redact),
                    parentRunId = it.parentRunId?.let(redactionFilter::redact),
                    runId = it.runId?.let(redactionFilter::redact),
                    maxContinuations = it.maxContinuations,
                    retryBudget = it.retryBudget,
                    lastVerifiedCheckpoint = it.lastVerifiedCheckpoint?.let(redactionFilter::redact)
                )
            },
            dags = dagStore.listDags().map { dag ->
                DagSnapshot(
                    id = dag.id,
                    label = dag.label,
                    ready = dag.nodes.count { it.state == DagNodeState.READY || it.state == DagNodeState.PENDING },
                    running = dag.nodes.count { it.state == DagNodeState.CLAIMED || it.state == DagNodeState.RUNNING || it.state == DagNodeState.VERIFYING },
                    blocked = dag.nodes.count { it.state == DagNodeState.BLOCKED },
                    complete = dag.nodes.count { it.state == DagNodeState.COMPLETE },
                    failed = dag.nodes.count { it.state == DagNodeState.FAILED }
                )
            },
            dagNodes = dagStore.listDags().flatMap { dag ->
                dag.nodes.map { node ->
                    DagNodeSnapshot(
                        dagId = dag.id,
                        nodeId = node.id,
                        state = node.state.name,
                        action = node.action.name,
                        territory = node.territory.map(redactionFilter::redact),
                        expectedOutputs = node.expectedOutputs.map(redactionFilter::redact),
                        resultHash = node.result?.let { sha256(redactionFilter.redact(it)) },
                        failureHash = node.failureReason?.let { sha256(redactionFilter.redact(it)) },
                        claimOwner = node.claimOwner?.let(redactionFilter::redact),
                        attempts = node.attempts,
                        maxAttempts = node.maxAttempts
                    )
                }
            },
            worktrees = worktreeService.listWorktrees().map {
                WorktreeSnapshot(
                    id = it.id,
                    jobId = it.jobId,
                    path = it.worktreePath.toString(),
                    verified = it.verified,
                    rolledBack = it.rolledBack,
                    mergedBack = it.mergedBack,
                    territory = it.territory.map(redactionFilter::redact)
                )
            },
            memoryRecords = memoryStore.status().totalRecords,
            recoveryReport = report
        )
        persist(state)
        return state
    }

    fun recoverAndSnapshot(): RestartCoordinatorResult {
        val report = crashRecovery.recover()
        val restored = dagNodeRestorer.restoreInterruptedNodes()
        val state = snapshot(report)
        val ok = report.errors.isEmpty() && restored.none { !it.restored && it.reason != "retry budget exhausted" }
        return RestartCoordinatorResult(
            ok = ok,
            snapshot = state,
            restoredNodes = restored,
            message = "restart recovery: ${report.message}; restored=${restored.count { it.restored }} blocked=${restored.count { !it.restored }}"
        )
    }

    fun latestSnapshot(goalId: String? = null): StateSnapshot? {
        if (!Files.isDirectory(snapshotDir)) return null
        return Files.list(snapshotDir).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".snapshot") }
                .toList()
                .sortedByDescending { it.fileName.toString() }
                .mapNotNull(::readSnapshot)
                .firstOrNull { snapshot ->
                    goalId == null || snapshot.goalRuns.any { it.id == goalId }
                }
        }
    }

    private fun persist(snapshot: StateSnapshot) {
        Files.createDirectories(snapshotDir)
        val target = snapshotDir.resolve("${snapshot.capturedAt.toEpochMilli()}-${snapshot.id}.snapshot")
        val tmp = target.resolveSibling("${target.fileName}.${System.nanoTime()}.tmp")
        Files.writeString(tmp, render(snapshot), StandardCharsets.UTF_8)
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun render(snapshot: StateSnapshot): String = buildString {
        appendLine("id=${snapshot.id}")
        appendLine("capturedAt=${snapshot.capturedAt}")
        appendLine("memoryRecords=${snapshot.memoryRecords}")
        snapshot.recoveryReport?.let { report ->
            appendLine("recoveryRecoveredAt=${report.recoveredAt}")
            appendLine("recoveryStaleQueueEntries=${report.staleQueueEntries}")
            appendLine("recoveryStaleSessions=${report.staleSessions}")
            appendLine("recoveryStaleDagClaims=${report.staleDagClaims}")
            appendLine("recoveryInterruptedRuns=${report.interruptedRuns}")
            appendLine("recoveryCompletedMutationsSkipped=${report.completedMutationsSkipped}")
            appendLine("recoveryErrorsB64=${encode(report.errors.joinToString("\n") { redactionFilter.redact(it) })}")
            appendLine("recoveryMessageB64=${encode(redactionFilter.redact(report.message))}")
        }
        snapshot.goalRuns.forEach {
            appendLine("goal=${listOf(
                it.id,
                it.status,
                it.dagId.orEmpty(),
                it.currentNodeId.orEmpty(),
                it.continuationCount,
                it.recoveryRequired,
                it.evidenceCount,
                encode(it.territory.joinToString("\u0000")),
                it.evidenceHashes.joinToString(","),
                encode(it.task),
                encode(it.baselineCommit.orEmpty()),
                encode(it.dirtyStateFingerprint.orEmpty()),
                encode(it.parentRunId.orEmpty()),
                encode(it.runId.orEmpty()),
                it.maxContinuations,
                it.retryBudget,
                encode(it.lastVerifiedCheckpoint.orEmpty())
            ).joinToString("|")}")
        }
        snapshot.dags.forEach {
            appendLine("dag=${listOf(it.id, encode(it.label), it.ready, it.running, it.blocked, it.complete, it.failed).joinToString("|")}")
        }
        snapshot.dagNodes.forEach {
            appendLine("node=${listOf(it.dagId, it.nodeId, it.state, it.action, encode(it.territory.joinToString(",")), encode(it.expectedOutputs.joinToString(",")), it.resultHash.orEmpty(), it.failureHash.orEmpty(), encode(it.claimOwner.orEmpty()), it.attempts, it.maxAttempts).joinToString("|")}")
        }
        snapshot.worktrees.forEach {
            appendLine("worktree=${listOf(it.id, it.jobId, encode(redactionFilter.redact(it.path)), it.verified, it.rolledBack, it.mergedBack, it.territory.joinToString(",")).joinToString("|")}")
        }
    }

    private fun readSnapshot(path: Path): StateSnapshot? {
        val lines = runCatching { Files.readAllLines(path, StandardCharsets.UTF_8) }.getOrNull() ?: return null
        val keyed = lines.mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
        }
        val id = keyed.firstOrNull { it.first == "id" }?.second ?: return null
        val capturedAt = keyed.firstOrNull { it.first == "capturedAt" }?.second
            ?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        val memoryRecords = keyed.firstOrNull { it.first == "memoryRecords" }?.second?.toIntOrNull() ?: 0
        val recoveryReport = parseRecoveryReport(keyed, capturedAt)
        return StateSnapshot(
            id = id,
            capturedAt = capturedAt,
            goalRuns = keyed.filter { it.first == "goal" }.mapNotNull { parseGoal(it.second) },
            dags = keyed.filter { it.first == "dag" }.mapNotNull { parseDag(it.second) },
            dagNodes = keyed.filter { it.first == "node" }.mapNotNull { parseNode(it.second) },
            worktrees = keyed.filter { it.first == "worktree" }.mapNotNull { parseWorktree(it.second) },
            memoryRecords = memoryRecords,
            recoveryReport = recoveryReport
        )
    }

    private fun parseRecoveryReport(keyed: List<Pair<String, String>>, capturedAt: Instant): RecoveryReport? {
        val message = keyed.firstOrNull { it.first == "recoveryMessageB64" }
            ?.second
            ?.let(::decode)
            ?.takeIf { it.isNotBlank() }
        val recoveredAt = keyed.firstOrNull { it.first == "recoveryRecoveredAt" }
            ?.second
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: return message?.let {
                RecoveryReport(capturedAt, 0, 0, 0, 0, 0, emptyList(), it)
            }
        val errors = keyed.firstOrNull { it.first == "recoveryErrorsB64" }
            ?.second
            ?.let(::decode)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            .orEmpty()
        return RecoveryReport(
            recoveredAt = recoveredAt,
            staleQueueEntries = keyed.value("recoveryStaleQueueEntries"),
            staleSessions = keyed.value("recoveryStaleSessions"),
            staleDagClaims = keyed.value("recoveryStaleDagClaims"),
            interruptedRuns = keyed.value("recoveryInterruptedRuns"),
            completedMutationsSkipped = keyed.value("recoveryCompletedMutationsSkipped"),
            errors = errors,
            message = message.orEmpty()
        )
    }

    private fun List<Pair<String, String>>.value(key: String): Int =
        firstOrNull { it.first == key }?.second?.toIntOrNull() ?: 0

    private fun parseGoal(raw: String): GoalRunSnapshot? {
        val p = raw.split("|")
        if (p.size < 7) return null
        return GoalRunSnapshot(
            id = p[0],
            status = p[1],
            dagId = p[2].ifBlank { null },
            currentNodeId = p[3].ifBlank { null },
            continuationCount = p[4].toIntOrNull() ?: 0,
            recoveryRequired = p[5].toBoolean(),
            evidenceCount = p[6].toIntOrNull() ?: 0,
            territory = p.getOrNull(7)?.let(::decode)?.split("\u0000")?.filter { it.isNotBlank() }.orEmpty(),
            evidenceHashes = p.getOrNull(8)?.split(",")?.filter { it.isNotBlank() }.orEmpty(),
            task = p.getOrNull(9)?.let(::decode).orEmpty(),
            baselineCommit = p.getOrNull(10)?.let(::decode)?.takeIf { it.isNotBlank() },
            dirtyStateFingerprint = p.getOrNull(11)?.let(::decode)?.takeIf { it.isNotBlank() },
            parentRunId = p.getOrNull(12)?.let(::decode)?.takeIf { it.isNotBlank() },
            runId = p.getOrNull(13)?.let(::decode)?.takeIf { it.isNotBlank() },
            maxContinuations = p.getOrNull(14)?.toIntOrNull() ?: 0,
            retryBudget = p.getOrNull(15)?.toIntOrNull() ?: 0,
            lastVerifiedCheckpoint = p.getOrNull(16)?.let(::decode)?.takeIf { it.isNotBlank() }
        )
    }

    private fun parseDag(raw: String): DagSnapshot? {
        val p = raw.split("|")
        if (p.size < 7) return null
        return DagSnapshot(p[0], decode(p[1]), p[2].toIntOrNull() ?: 0, p[3].toIntOrNull() ?: 0, p[4].toIntOrNull() ?: 0, p[5].toIntOrNull() ?: 0, p[6].toIntOrNull() ?: 0)
    }

    private fun parseNode(raw: String): DagNodeSnapshot? {
        val p = raw.split("|")
        if (p.size < 11) return null
        return DagNodeSnapshot(
            dagId = p[0],
            nodeId = p[1],
            state = p[2],
            action = p[3],
            territory = decode(p[4]).split(",").filter { it.isNotBlank() },
            expectedOutputs = decode(p[5]).split(",").filter { it.isNotBlank() },
            resultHash = p[6].ifBlank { null },
            failureHash = p[7].ifBlank { null },
            claimOwner = decode(p[8]).ifBlank { null },
            attempts = p[9].toIntOrNull() ?: 0,
            maxAttempts = p[10].toIntOrNull() ?: 0
        )
    }

    private fun parseWorktree(raw: String): WorktreeSnapshot? {
        val p = raw.split("|")
        if (p.size < 7) return null
        return WorktreeSnapshot(p[0], p[1], decode(p[2]), p[3].toBoolean(), p[4].toBoolean(), p[5].toBoolean(), p[6].split(",").filter { it.isNotBlank() })
    }

    private fun encode(text: String): String =
        java.util.Base64.getEncoder().encodeToString(text.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String =
        runCatching { String(java.util.Base64.getDecoder().decode(value), StandardCharsets.UTF_8) }.getOrDefault("")

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
