package atropos.core.autonomous

import atropos.core.memory.LocalMemoryStore
import atropos.core.memory.MemoryKind
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutonomousOrchestratorLearningTest {
    @Test
    fun invariantOverrideIsRecordedAsTypedSkippedStop() {
        val root = Files.createTempDirectory("atropos-autonomous-learning-")
        val backlog = AutonomousBacklogService(root)
        val memory = LocalMemoryStore(root.resolve(".atropos/memory").toFile(), env = emptyMap())
        val task = backlog.enqueue(
            kind = AutonomousTaskKind.HIG_REDUCTION,
            description = "attempt unsafe invariant override",
            priority = AutonomousTaskPriority.CRITICAL,
            context = mapOf("overrideInvariant" to "HIG=0")
        )
        val orchestrator = AutonomousOrchestrator(backlog = backlog, memory = memory)

        val result = orchestrator.tick()
        val stored = backlog.getTask(task.id) ?: error("missing task")
        val failures = memory.latestByKind(MemoryKind.FAILURE, limit = 5)

        assertTrue(result.contains("[STOP] HIG_REDUCTION"))
        assertEquals(AutonomousTaskState.SKIPPED, stored.state)
        assertTrue(stored.result!!.startsWith("INVARIANT_OVERRIDE"))
        assertTrue(failures.any { it.body.contains("INVARIANT_OVERRIDE") })
    }
}
