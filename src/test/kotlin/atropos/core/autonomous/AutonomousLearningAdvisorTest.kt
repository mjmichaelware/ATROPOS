package atropos.core.autonomous

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutonomousLearningAdvisorTest {
    @Test
    fun ranksAlreadyEligibleTasksFromRepairEvidenceWithoutChangingTheQueueOwner() {
        val advisor = AutonomousLearningAdvisor()
        val provenRepair = AutonomousTask(
            kind = AutonomousTaskKind.REPAIR_RETRY,
            priority = AutonomousTaskPriority.MEDIUM,
            description = "retry known compiler repair",
            context = mapOf("failureSignature" to "compile")
        )
        val neutral = AutonomousTask(
            kind = AutonomousTaskKind.MEMORY_COMPACTION,
            priority = AutonomousTaskPriority.MEDIUM,
            description = "compact memory"
        )
        val repairs = listOf(
            RepairRecord(
                taskId = "older",
                failureSignature = "compile unresolved import",
                repairAction = "add import",
                success = true
            )
        )

        val ranked = advisor.rank(listOf(neutral, provenRepair), repairs, emptyList())

        assertEquals(provenRepair.id, ranked.first().id)
        assertTrue(ranked.first().context["learningScore"]!!.toInt() < ranked.last().context["learningScore"]!!.toInt())
    }

    @Test
    fun refusesAttemptsToMakeImmutableInvariantsLearnable() {
        val task = AutonomousTask(
            kind = AutonomousTaskKind.HIG_REDUCTION,
            priority = AutonomousTaskPriority.CRITICAL,
            description = "change invariant",
            context = mapOf("overrideInvariant" to "PAID_AUTO=false")
        )

        val decision = AutonomousLearningAdvisor().inspect(task)

        assertFalse(decision.accepted)
        assertTrue(decision.reason.startsWith("INVARIANT_OVERRIDE"))
    }

    @Test
    fun reducesBatchSizeAfterRepeatedRepairFailures() {
        val repairs = listOf(
            RepairRecord(taskId = "a", failureSignature = "compile", repairAction = "retry", success = false),
            RepairRecord(taskId = "b", failureSignature = "compile", repairAction = "retry", success = false),
            RepairRecord(taskId = "c", failureSignature = "compile", repairAction = "retry", success = false)
        )

        assertEquals(1, AutonomousLearningAdvisor().recommendedBatchSize(10, repairs))
    }
}
