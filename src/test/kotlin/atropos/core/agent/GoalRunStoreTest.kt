package atropos.core.agent

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GoalRunStoreTest {
    @Test
    fun listRuns_and_resolve_include_canonical_and_legacy_self_host_records() {
        val repoRoot = Files.createTempDirectory("atropos-goal-run-store-")
        val base = Instant.parse("2026-07-27T08:10:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })

        val canonical = store.createGoalRun("canonical goal", provider = "codex")
        val legacyDir = repoRoot.resolve(".atropos/self-hosting/runs")
        Files.createDirectories(legacyDir)
        val legacyMeta = legacyDir.resolve("shg-legacy123456.meta")
        Files.writeString(
            legacyMeta,
            """
            id=shg-legacy123456
            goalId=shg-legacy123456
            projectId=
            dagId=
            atomId=
            taskB64=c2VsZi1ob3N0IGxlZ2FjeQ==
            provider=self-host
            status=RUNNING
            terminalCondition=
            continuationCount=0
            maxContinuations=10
            lastContinuationAt=
            compactStateB64=
            lastProviderResponseId=
            failureReasonB64=
            parentRunId=
            runId=
            baselineCommit=
            dirtyStateFingerprint=
            activePhase=11
            currentNodeId=node-1
            territory=
            evidenceB64=
            retryBudget=10
            lastVerifiedCheckpoint=
            createdAt=2026-07-27T08:10:05Z
            updatedAt=2026-07-27T08:10:05Z
            finishedAt=
            """.trimIndent() + "\n",
            StandardCharsets.UTF_8
        )

        val listed = store.listRuns(10)
        assertEquals(listOf("shg-legacy123456", canonical.id), listed.map { it.id })

        val resolved = store.resolve("shg-legacy123456")
        assertNotNull(resolved)
        assertEquals("self-host legacy", resolved.task)
        assertEquals("self-host", resolved.provider)
    }

    @Test
    fun self_host_start_goal_writes_to_canonical_runs_root_and_is_resolvable() {
        val repoRoot = Files.createTempDirectory("atropos-self-host-start-")
        val now = Instant.parse("2026-07-27T08:15:00Z")
        val store = GoalRunStore(repoRoot, clock = { now })
        val service = SelfHostGoalService(repoRoot = repoRoot, store = store, clock = { now })

        val started = service.startGoal("phase 11 store fix", "11")
        assertTrue(started.ok)

        val goalId = started.goal?.record?.id ?: error("missing goal id")
        val canonicalMeta = store.runsRoot().resolve("$goalId.meta")
        assertTrue(Files.isRegularFile(canonicalMeta))

        val reopened = store.resolve(goalId)
        assertNotNull(reopened)
        assertEquals(goalId, reopened.id)
        assertEquals("self-host", reopened.provider)
        assertEquals("11", reopened.activePhase)
    }

    @Test
    fun latest_and_listRuns_prefer_most_recently_updated_run() {
        val repoRoot = Files.createTempDirectory("atropos-goal-run-order-")
        val base = Instant.parse("2026-07-27T08:20:00Z")
        var tick = 0L
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })

        val older = store.createGoalRun("older goal", provider = "self-host")
        val newer = store.createGoalRun("newer goal", provider = "self-host")
        val warmedOlder = store.update(older.copy(status = GoalRunStatus.CONTINUING))

        val listed = store.listRuns(2)
        assertEquals(listOf(warmedOlder.id, newer.id), listed.map { it.id })
        assertEquals(warmedOlder.id, store.latest()?.id)
        assertEquals(warmedOlder.id, store.resolve("latest")?.id)
    }

    @Test
    fun update_redacts_durable_fields_and_preserves_pipe_bearing_evidence_entries() {
        val repoRoot = Files.createTempDirectory("atropos-goal-run-redaction-")
        val store = GoalRunStore(repoRoot)
        val secret = "sk-ABCDEFGHIJKLMNOPQRSTUVWX"
        val run = store.createGoalRun("self-host token=$secret", provider = "self-host")

        val updated = store.update(
            run.copy(
                failureReason = "api_key=$secret",
                compactState = "compact token=$secret",
                evidence = listOf(
                    "promotion_gate node=n1 canComplete=true | Compile Gate=PASS:ok",
                    "secret=$secret"
                )
            )
        )
        val rawMeta = Files.readString(updated.metaFile, StandardCharsets.UTF_8)
        val reopened = store.resolve(updated.id) ?: error("missing run")

        assertTrue(!rawMeta.contains(secret), rawMeta)
        assertTrue(!reopened.task.contains(secret), reopened.task)
        assertTrue(!reopened.failureReason.orEmpty().contains(secret))
        assertEquals(2, reopened.evidence.size)
        assertTrue(reopened.evidence.first().contains("| Compile Gate=PASS:ok"), reopened.evidence.joinToString("\n"))
        assertTrue(reopened.evidence.none { it.contains(secret) })
    }
}
