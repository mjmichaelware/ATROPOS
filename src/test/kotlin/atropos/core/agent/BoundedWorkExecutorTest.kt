package atropos.core.agent

import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoundedWorkExecutorTest {
    @Test
    fun blank_work_is_refused_before_queue_mutation() {
        val root = Files.createTempDirectory("atropos-bounded-work-")
        val queue = AgentQueueService(collector = AgentContextCollector(root))
        val executor = BoundedWorkExecutor(queue, BoundedAgencyGate(ExecutionPolicyEngine(root)))

        val result = executor.enqueue(BoundedWorkRequest("  "))

        assertFalse(result.accepted)
        assertTrue(result.record == null)
        assertTrue(result.reason.contains("blank"))
    }

    @Test
    fun accepted_work_is_delegated_to_the_existing_queue_service() {
        val root = Files.createTempDirectory("atropos-bounded-work-accepted-")
        val queue = AgentQueueService(collector = AgentContextCollector(root))
        val executor = BoundedWorkExecutor(queue, BoundedAgencyGate(ExecutionPolicyEngine(root)))

        val result = executor.enqueue(BoundedWorkRequest("bounded task"))

        assertTrue(result.accepted, result.reason)
        assertTrue(result.record?.task == "bounded task")
    }

    @Test
    fun batch_evaluation_refuses_nonzero_hig_or_hud_before_commit() {
        val root = Files.createTempDirectory("atropos-bounded-work-batch-")
        val queue = AgentQueueService(collector = AgentContextCollector(root))
        val executor = BoundedWorkExecutor(queue, BoundedAgencyGate(ExecutionPolicyEngine(root)))

        val result = executor.evaluateBatch(
            before = mapOf("src/A.kt" to "same"),
            after = mapOf("src/A.kt" to "same"),
            declaredTerritory = setOf("src"),
            higValue = 1.0
        )

        assertFalse(result.passed)
        assertTrue(result.reason.contains("commit precondition"))
    }
}
