package atropos.core.agent

import atropos.core.memory.LocalMemoryStore
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoalContinuationServiceTest {
    @Test
    fun markRecoveryRequired_preserves_self_host_context_and_records_evidence() {
        val repoRoot = Files.createTempDirectory("atropos-goal-continuation-")
        val now = Instant.parse("2026-07-27T06:50:00Z")
        val store = GoalRunStore(repoRoot, clock = { now })
        val memoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile(), env = emptyMap())
        val service = GoalContinuationService(
            repoRoot = repoRoot,
            store = store,
            memoryStore = memoryStore,
            clock = { now }
        )

        val created = store.createGoalRun("phase 11 recovery", provider = "self-host")
        val updated = store.update(
            created.copy(
                status = GoalRunStatus.CONTINUING,
                continuationCount = 2,
                lastContinuationAt = now.minusSeconds(900),
                activePhase = "11",
                currentNodeId = "node-7",
                lastVerifiedCheckpoint = "verify-3",
                evidence = listOf("existing=evidence")
            )
        )

        val result = service.markRecoveryRequired(
            updated.id,
            "interrupted: recovered during crash recovery",
            listOf("recovery=crash", "queue=queue-1")
        )

        assertTrue(result.ok)
        val reopened = store.resolve(updated.id) ?: error("missing goal run")
        assertEquals(GoalRunStatus.RECOVERY_REQUIRED, reopened.status)
        assertNull(reopened.terminalCondition)
        assertTrue(!reopened.isTerminal())
        assertEquals("11", reopened.activePhase)
        assertEquals("node-7", reopened.currentNodeId)
        assertEquals("verify-3", reopened.lastVerifiedCheckpoint)
        assertEquals("interrupted: recovered during crash recovery", reopened.failureReason)
        assertTrue(reopened.evidence.any { it.contains("existing=evidence") })
        assertTrue(reopened.evidence.any { it.contains("recovery=crash") })
        assertTrue(reopened.evidence.any { it.contains("phase=11") && it.contains("node=node-7") && it.contains("checkpoint=verify-3") })
    }

    @Test
    fun continueRun_afterRecoveryRequired_clears_failure_and_marks_resumed() {
        val repoRoot = Files.createTempDirectory("atropos-goal-continuation-resume-")
        val now = Instant.parse("2026-07-27T06:55:00Z")
        val store = GoalRunStore(repoRoot, clock = { now })
        val memoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile(), env = emptyMap())
        val service = GoalContinuationService(
            repoRoot = repoRoot,
            store = store,
            memoryStore = memoryStore,
            clock = { now }
        )

        val created = store.createGoalRun("phase 11 recovery resume", provider = "self-host")
        val recovered = store.update(
            created.copy(
                status = GoalRunStatus.RECOVERY_REQUIRED,
                continuationCount = 2,
                activePhase = "11",
                currentNodeId = "node-8",
                lastVerifiedCheckpoint = "verify-5",
                failureReason = "interrupted: recovered during crash recovery",
                evidence = listOf("recovery=crash")
            )
        )

        val result = service.continueRun(
            recovered.id,
            GoalContinuationRequest(
                goalRunId = recovered.id,
                compactState = "resume",
                continuationIndex = 3,
                lastResponseSummary = null,
                provider = "self-host"
            )
        )

        assertTrue(result.ok)
        val reopened = store.resolve(recovered.id) ?: error("missing resumed goal run")
        assertEquals(GoalRunStatus.CONTINUING, reopened.status)
        assertEquals(3, reopened.continuationCount)
        assertNull(reopened.failureReason)
        assertTrue(reopened.evidence.any { it == "recovery=crash" })
        assertTrue(reopened.evidence.any { it.contains("recovery_resumed_at=$now") && it.contains("phase=11") && it.contains("node=node-8") && it.contains("checkpoint=verify-5") })
    }

    @Test
    fun continueRun_blocks_duplicate_response_only_inside_bounded_window() {
        val repoRoot = Files.createTempDirectory("atropos-goal-continuation-duplicate-")
        val base = Instant.parse("2026-07-27T07:05:00Z")
        var now = base
        val store = GoalRunStore(repoRoot, clock = { now })
        val memoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile(), env = emptyMap())
        val service = GoalContinuationService(
            repoRoot = repoRoot,
            store = store,
            memoryStore = memoryStore,
            clock = { now }
        )

        val created = store.createGoalRun("phase 11 duplicate window", provider = "self-host")
        val active = store.update(
            created.copy(
                status = GoalRunStatus.CONTINUING,
                continuationCount = 1,
                lastContinuationAt = base,
                lastProviderResponseId = "same-response"
            )
        )

        now = base.plusSeconds(5)
        val blocked = service.continueRun(
            active.id,
            GoalContinuationRequest(
                goalRunId = active.id,
                compactState = "resume",
                continuationIndex = 2,
                lastResponseSummary = "same-response",
                provider = "self-host"
            )
        )

        assertFalse(blocked.ok)
        assertEquals("duplicate continuation prevented (same response id)", blocked.message)

        now = base.plusSeconds(11)
        val allowed = service.continueRun(
            active.id,
            GoalContinuationRequest(
                goalRunId = active.id,
                compactState = "resume",
                continuationIndex = 2,
                lastResponseSummary = "same-response",
                provider = "self-host"
            )
        )

        assertTrue(allowed.ok)
        val reopened = store.resolve(active.id) ?: error("missing continued goal run")
        assertEquals(GoalRunStatus.CONTINUING, reopened.status)
        assertEquals(2, reopened.continuationCount)
        assertEquals(base.plusSeconds(11), reopened.lastContinuationAt)
    }

    @Test
    fun continueRun_rejects_mismatched_continuation_index_without_mutating_run() {
        val repoRoot = Files.createTempDirectory("atropos-goal-continuation-index-")
        val now = Instant.parse("2026-07-27T07:10:00Z")
        val store = GoalRunStore(repoRoot, clock = { now })
        val memoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile(), env = emptyMap())
        val service = GoalContinuationService(
            repoRoot = repoRoot,
            store = store,
            memoryStore = memoryStore,
            clock = { now }
        )

        val created = store.createGoalRun("phase 11 continuation index", provider = "self-host")
        val active = store.update(
            created.copy(
                status = GoalRunStatus.CONTINUING,
                continuationCount = 2,
                lastContinuationAt = now.minusSeconds(60)
            )
        )

        val result = service.continueRun(
            active.id,
            GoalContinuationRequest(
                goalRunId = active.id,
                compactState = "resume",
                continuationIndex = 4,
                lastResponseSummary = null,
                provider = "self-host"
            )
        )

        assertFalse(result.ok)
        assertEquals("continuation index mismatch: expected 3 but received 4", result.message)
        val reopened = store.resolve(active.id) ?: error("missing goal run after mismatch")
        assertEquals(2, reopened.continuationCount)
        assertEquals(GoalRunStatus.CONTINUING, reopened.status)
    }

    @Test
    fun continueRun_rejects_mismatched_goal_run_id_without_mutating_run() {
        val repoRoot = Files.createTempDirectory("atropos-goal-continuation-runid-")
        val now = Instant.parse("2026-07-27T07:15:00Z")
        val store = GoalRunStore(repoRoot, clock = { now })
        val memoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile(), env = emptyMap())
        val service = GoalContinuationService(
            repoRoot = repoRoot,
            store = store,
            memoryStore = memoryStore,
            clock = { now }
        )

        val created = store.createGoalRun("phase 11 goal id contract", provider = "self-host")
        val active = store.update(
            created.copy(
                status = GoalRunStatus.CONTINUING,
                continuationCount = 1,
                lastContinuationAt = now.minusSeconds(60)
            )
        )

        val result = service.continueRun(
            active.id,
            GoalContinuationRequest(
                goalRunId = "goal-wrong",
                compactState = "resume",
                continuationIndex = 2,
                lastResponseSummary = null,
                provider = "self-host"
            )
        )

        assertFalse(result.ok)
        assertEquals("goal run id mismatch: expected ${active.id} but received goal-wrong", result.message)
        val reopened = store.resolve(active.id) ?: error("missing goal run after mismatch")
        assertEquals(1, reopened.continuationCount)
        assertEquals(GoalRunStatus.CONTINUING, reopened.status)
    }
}
