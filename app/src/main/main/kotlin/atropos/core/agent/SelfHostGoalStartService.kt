package atropos.core.agent

import atropos.core.memory.LocalMemoryStore
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

/** Creates the durable self-host goal and its cradle DAG. */
class SelfHostGoalStartService(
    private val repoRoot: Path,
    private val store: GoalRunStore,
    private val bootstrapDagFactory: SelfHostBootstrapDagFactory,
    private val memoryStore: LocalMemoryStore,
    private val stateSnapshotRecorder: SelfHostStateSnapshotRecorder,
    private val clock: () -> Instant,
    private val baselineReader: SelfHostGitBaselineReader = SelfHostGitBaselineReader(repoRoot)
) {
    fun start(goalName: String, phase: String): SelfHostResult {
        return try {
            Files.createDirectories(store.runsRoot())
            val baseline = baselineReader.read(bootstrapDagFactory::fingerprint)
            val goalId = "shg-" + UUID.randomUUID().toString().take(12)
            val now = clock()
            val record = GoalRunRecord(
                id = goalId,
                goalId = goalId,
                task = goalName.trim(),
                provider = "self-host",
                status = GoalRunStatus.RUNNING,
                baselineCommit = baseline.commit,
                dirtyStateFingerprint = baseline.dirtyFingerprint,
                activePhase = phase,
                createdAt = now,
                updatedAt = now,
                metaFile = store.runsRoot().resolve("$goalId.meta")
            )
            val stored = store.update(record)
            val bootstrapDag = bootstrapDagFactory.create(stored, phase)
            val withDag = store.update(
                stored.copy(
                    dagId = bootstrapDag.id,
                    territory = bootstrapDag.nodes.flatMap { it.territory }.distinct(),
                    currentNodeId = bootstrapDag.findReadyNodes().firstOrNull()?.id
                )
            )
            memoryStore.rememberDetailed(
                kind = atropos.core.memory.MemoryKind.SESSION,
                title = "self-host goal started: $goalName",
                body = "phase=$phase baseline=${baseline.commit?.take(12)} dag=${bootstrapDag.id}",
                tags = listOf("selfhost", "goal", "started"),
                subjectType = "selfhost_goal",
                subjectId = goalId
            )
            val snapshotted = store.update(
                withDag.copy(evidence = withDag.evidence + stateSnapshotRecorder.captureEvidence("start", withDag.id))
            )
            SelfHostResult(true, "self-host goal started: $goalId", SelfHostGoal(snapshotted, bootstrapDag))
        } catch (e: Exception) {
            SelfHostResult(false, "failed to start self-host goal: ${e.message}")
        }
    }

}
