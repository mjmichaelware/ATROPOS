package atropos.core.agent

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelfHostStartupContinuationServiceTest {
    @Test
    fun startup_continuation_advances_selected_goal_once() {
        val goal = GoalRunStore(Files.createTempDirectory("atropos-startup-continuation-")).createGoalRun("startup goal")
        var calls = 0
        val service = SelfHostStartupContinuationService(
            hasUnfinishedGoals = { true },
            resolveResumable = { SelfHostResult(true, "selected", SelfHostGoal(goal, null)) },
            recoverAndContinue = { id ->
                calls += 1
                assertEquals(goal.id, id)
                SelfHostResult(true, "advanced")
            }
        )

        val first = service.continueOnce(true)
        val second = service.continueOnce(true)

        assertTrue(first.attempted)
        assertTrue(first.ok)
        assertFalse(second.attempted)
        assertTrue(second.ok)
        assertEquals(1, calls)
    }

    @Test
    fun unavailable_recovery_never_advances_goal() {
        var calls = 0
        val service = SelfHostStartupContinuationService(
            hasUnfinishedGoals = { true },
            resolveResumable = { error("must not resolve") },
            recoverAndContinue = { calls += 1; error("must not recover") }
        )

        val result = service.continueOnce(false)

        assertFalse(result.attempted)
        assertFalse(result.ok)
        assertEquals(0, calls)
    }

    @Test
    fun resolver_failure_is_reported_and_can_be_retried() {
        var calls = 0
        val service = SelfHostStartupContinuationService(
            hasUnfinishedGoals = { true },
            resolveResumable = {
                calls += 1
                SelfHostResult(false, "store unavailable")
            }
        )

        val first = service.continueOnce(true)
        val second = service.continueOnce(true)

        assertTrue(first.attempted)
        assertFalse(first.ok)
        assertTrue(second.attempted)
        assertFalse(second.ok)
        assertEquals(2, calls)
    }
    @Test
    fun no_unfinished_goal_is_normal_startup_noop() {
        var resolveCalls = 0
        var recoverCalls = 0

        val service = SelfHostStartupContinuationService(
            hasUnfinishedGoals = { false },
            resolveResumable = {
                resolveCalls += 1
                error("must not resolve")
            },
            recoverAndContinue = { _ ->
                recoverCalls += 1
                error("must not recover")
            }
        )

        val result = service.continueOnce(true)

        assertFalse(result.attempted)
        assertTrue(result.ok)
        assertEquals(
            "startup self-host continuation: no resumable goal",
            result.message
        )
        assertEquals(0, resolveCalls)
        assertEquals(0, recoverCalls)
    }

}
