package atropos.core.agent

import atropos.core.artifact.SafeJarSwapGate
import atropos.core.dag.DagExecutionService
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.director.DirectorService
import atropos.core.director.DirectorStore
import atropos.core.director.DriftSeverity
import atropos.core.director.ObservationKind
import atropos.core.verification.CompletionGateReport
import atropos.core.verification.GateResult
import atropos.core.verification.VerifiedCompletionGate
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelfHostPromotionServiceTest {
    @Test
    fun failed_completion_gate_refuses_promotion_and_preserves_active_jar() {
        val root = Files.createTempDirectory("atropos-self-host-promote-refuse-")
        val candidate = root.resolve("candidate.jar")
        val target = root.resolve("atropos.jar")
        Files.writeString(candidate, "new jar")
        Files.writeString(target, "old jar")
        val fixture = fixture(root)
        val service = SelfHostPromotionService(
            repoRoot = root,
            store = fixture.store,
            dagService = fixture.dagService,
            completionGate = VerifiedCompletionGate(repoRoot = root),
            jarSwapGate = SafeJarSwapGate(clock = { Instant.parse("2026-07-29T00:00:00Z") }),
            evaluateGate = { node ->
                CompletionGateReport(
                    nodeId = node.id,
                    canComplete = false,
                    gateResults = listOf(GateResult(node.id, false, "Deterministic Verification", "failed assertion", Instant.parse("2026-07-29T00:00:01Z"))),
                    message = "gates failed"
                )
            }
        )

        val result = service.promote(SelfHostPromotionRequest(fixture.goal.id, fixture.node.id, candidate, target))

        assertTrue(!result.promoted)
        assertTrue(result.message.contains("VerifiedCompletionGate"), result.message)
        assertEquals("old jar", Files.readString(target))
        val reopened = fixture.store.resolve(fixture.goal.id) ?: error("missing goal")
        assertTrue(reopened.evidence.any { it.contains("promotion_gate") && it.contains("canComplete=false") })
        assertTrue(reopened.lastVerifiedCheckpoint == null)
    }

    @Test
    fun green_completion_gate_is_the_only_path_to_safe_jar_swap() {
        val root = Files.createTempDirectory("atropos-self-host-promote-green-")
        val candidate = root.resolve("candidate.jar")
        val target = root.resolve("atropos.jar")
        Files.writeString(candidate, "new jar")
        Files.writeString(target, "old jar")
        val fixture = fixture(root)
        val service = SelfHostPromotionService(
            repoRoot = root,
            store = fixture.store,
            dagService = fixture.dagService,
            completionGate = VerifiedCompletionGate(repoRoot = root),
            jarSwapGate = SafeJarSwapGate(clock = { Instant.parse("2026-07-29T00:01:00Z") }),
            evaluateGate = { node ->
                CompletionGateReport(
                    nodeId = node.id,
                    canComplete = true,
                    gateResults = listOf(GateResult(node.id, true, "Compile Gate", "compilation succeeded", Instant.parse("2026-07-29T00:01:01Z"))),
                    message = "all gates passed"
                )
            }
        )

        val result = service.promote(SelfHostPromotionRequest(fixture.goal.id, fixture.node.id, candidate, target))

        assertTrue(result.promoted, result.message)
        assertEquals("new jar", Files.readString(target))
        val backup = result.jarSwap?.backupJar ?: error("missing backup")
        assertEquals("old jar", Files.readString(backup))
        val reopened = fixture.store.resolve(fixture.goal.id) ?: error("missing goal")
        assertTrue(reopened.evidence.any { it.contains("jar_swap promoted=true") && it.contains("sha256=") })
        assertEquals("jar:atropos.jar", reopened.lastVerifiedCheckpoint)
    }

    @Test
    fun director_pre_promote_advisory_blocks_jar_swap_before_completion_gate() {
        val root = Files.createTempDirectory("atropos-self-host-promote-director-")
        val candidate = root.resolve("candidate.jar")
        val target = root.resolve("atropos.jar")
        Files.writeString(candidate, "new jar")
        Files.writeString(target, "old jar")
        val fixture = fixture(root)
        val director = DirectorService(DirectorStore(root), root)
        director.observe(
            kind = ObservationKind.TERRITORY_VIOLATION,
            severity = DriftSeverity.WARNING,
            source = "test",
            details = "out-of-territory edit",
            files = listOf("src/main/kotlin/atropos/core/agent/SelfHostCradleRuntimeState.kt"),
            goalId = fixture.goal.id,
            territoryId = "src/main/kotlin/atropos/core/agent"
        )
        var completionGateCalled = false
        val service = SelfHostPromotionService(
            repoRoot = root,
            store = fixture.store,
            dagService = fixture.dagService,
            completionGate = VerifiedCompletionGate(repoRoot = root),
            jarSwapGate = SafeJarSwapGate(clock = { Instant.parse("2026-07-29T00:03:00Z") }),
            directorService = director,
            evaluateGate = { node ->
                completionGateCalled = true
                CompletionGateReport(
                    nodeId = node.id,
                    canComplete = true,
                    gateResults = listOf(GateResult(node.id, true, "Compile Gate", "should not run", Instant.parse("2026-07-29T00:03:01Z"))),
                    message = "should not run"
                )
            }
        )

        val result = service.promote(SelfHostPromotionRequest(fixture.goal.id, fixture.node.id, candidate, target))

        assertTrue(!result.promoted)
        assertTrue(result.message.contains("Director pre-promote advisory"), result.message)
        assertTrue(!completionGateCalled)
        assertEquals("old jar", Files.readString(target))
        val reopened = fixture.store.resolve(fixture.goal.id) ?: error("missing goal")
        assertTrue(reopened.evidence.any { it.contains("director_pre_promote allowed=false") })
    }

    @Test
    fun safety_hard_fail_blocks_before_director_completion_gate_and_swap() {
        val root = Files.createTempDirectory("atropos-self-host-promote-safety-")
        val candidate = root.resolve("candidate.jar")
        val target = root.resolve("atropos.jar")
        Files.writeString(candidate, "new jar")
        Files.writeString(target, "old jar")
        val fixture = fixture(root)
        val unsafeGoal = fixture.store.update(
            fixture.goal.copy(evidence = fixture.goal.evidence + "fake_success placeholder green")
        )
        var completionGateCalled = false
        val service = SelfHostPromotionService(
            repoRoot = root,
            store = fixture.store,
            dagService = fixture.dagService,
            completionGate = VerifiedCompletionGate(repoRoot = root),
            jarSwapGate = SafeJarSwapGate(clock = { Instant.parse("2026-07-29T00:04:00Z") }),
            evaluateGate = { node ->
                completionGateCalled = true
                CompletionGateReport(
                    nodeId = node.id,
                    canComplete = true,
                    gateResults = listOf(GateResult(node.id, true, "Compile Gate", "should not run", Instant.parse("2026-07-29T00:04:01Z"))),
                    message = "should not run"
                )
            }
        )

        val result = service.promote(SelfHostPromotionRequest(unsafeGoal.id, fixture.node.id, candidate, target))

        assertTrue(!result.promoted)
        assertTrue(result.message.contains("self-host safety hard-fail gate"), result.message)
        assertTrue(!completionGateCalled)
        assertEquals("old jar", Files.readString(target))
        val reopened = fixture.store.resolve(unsafeGoal.id) ?: error("missing goal")
        assertTrue(reopened.evidence.any { it.contains("self_host_safety passed=false") && it.contains("fake_success") })
        assertTrue(reopened.lastVerifiedCheckpoint == null)
    }

    private fun fixture(root: java.nio.file.Path): Fixture {
        val store = GoalRunStore(root, clock = { Instant.parse("2026-07-29T00:02:00Z") })
        val dagService = DagExecutionService(repoRoot = root)
        val goal = store.createGoalRun("self-host promote", provider = "self-host")
        val node = DagNode(
            id = "node-promote",
            label = "Promote verified ATROPOS jar",
            territory = listOf("src/main/kotlin/atropos/core/agent", "build/libs"),
            action = DagNodeAction.ACCEPTANCE_GATE,
            actionPayload = "promote candidate jar",
            expectedOutputs = listOf("build/libs/ATROPOS.jar"),
            optionalChecks = setOf("Focused Tests", "Compile Gate", "Acceptance Evidence"),
            createdAt = Instant.parse("2026-07-29T00:02:01Z"),
            updatedAt = Instant.parse("2026-07-29T00:02:01Z"),
            metaFile = root.resolve(".atropos/dag/node-promote.meta")
        )
        val dag = dagService.createDag("promotion dag", listOf(node), "atropos-self-host")
        val updated = store.update(
            goal.copy(
                goalId = goal.id,
                dagId = dag.id,
                currentNodeId = node.id,
                activePhase = "11",
                territory = node.territory
            )
        )
        return Fixture(store, dagService, updated, node.copy(dagId = dag.id))
    }

    private data class Fixture(
        val store: GoalRunStore,
        val dagService: DagExecutionService,
        val goal: GoalRunRecord,
        val node: DagNode
    )
}
