package atropos.core.agent

import atropos.core.dag.DagExecutionService
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.recovery.RecoveryReport
import atropos.core.recovery.RestartCoordinator
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class SelfHostEvidenceBundleExporterTest {
    @Test
    fun refuses_export_when_record_references_missing_dag() {
        val root = Files.createTempDirectory("atropos-self-host-evidence-missing-dag-")
        val store = GoalRunStore(root)
        val goal = store.createGoalRun("missing dag", provider = "self-host")
        store.update(goal.copy(dagId = "missing-dag"))

        val result = SelfHostEvidenceBundleExporter(root, store, DagExecutionService(repoRoot = root)).export(goal.id)

        assertTrue(!result.ok)
        assertEquals(SelfHostFailureCode.MISSING_DAG, result.failureCode)
    }

    @Test
    fun exports_markdown_and_json_with_hashes_and_redacted_evidence() {
        val root = Files.createTempDirectory("atropos-self-host-evidence-export-")
        val store = GoalRunStore(root, clock = { Instant.parse("2026-07-29T00:03:00Z") })
        val dagService = DagExecutionService(repoRoot = root)
        val node = DagNode(
            id = "node-evidence",
            label = "Evidence export",
            territory = listOf("src/main/kotlin/atropos/core/agent"),
            action = DagNodeAction.VERIFY,
            actionPayload = "verify",
            expectedOutputs = listOf("src/main/kotlin/atropos/core/agent/SelfHostCradleRuntimeState.kt"),
            createdAt = Instant.parse("2026-07-29T00:03:01Z"),
            updatedAt = Instant.parse("2026-07-29T00:03:01Z"),
            metaFile = root.resolve(".atropos/dag/node-evidence.meta")
        )
        val expectedOutput = root.resolve("src/main/kotlin/atropos/core/agent/SelfHostCradleRuntimeState.kt")
        Files.createDirectories(expectedOutput.parent)
        Files.writeString(expectedOutput, "package atropos.core.agent\nobject SelfHostCradleRuntimeState\n")
        val dag = dagService.createDag("evidence dag", listOf(node), "atropos-self-host")
        val goal = store.createGoalRun("export secret=plain-token evidence", provider = "self-host")
        val updated = store.update(
            goal.copy(
                goalId = goal.id,
                dagId = dag.id,
                currentNodeId = node.id,
                activePhase = "11",
                territory = node.territory,
                evidence = listOf(
                    "provider token=plain-token",
                    "context_attestation hash=attest-123",
                    "promotion_gate VerifiedCompletionGate=PASS",
                    "director_pre_promote allowed=true",
                    "jar_swap promoted=true previous_sha256=old candidate_sha256=new",
                    "restart_next goal=${goal.id} node=node-evidence"
                )
            )
        )
        RestartCoordinator(root, goalRunStore = store).snapshot(
            RecoveryReport(
                recoveredAt = Instant.parse("2026-07-29T00:04:00Z"),
                staleQueueEntries = 0,
                staleSessions = 0,
                staleDagClaims = 0,
                interruptedRuns = 1,
                completedMutationsSkipped = 0,
                errors = listOf("recovery token=plain-token"),
                message = "recovered token=plain-token"
            )
        )
        val exporter = SelfHostEvidenceBundleExporter(root, store, dagService)

        val result = exporter.export(updated.id)

        assertTrue(result.ok, result.message)
        assertTrue(result.markdownSha256.orEmpty().length == 64)
        assertTrue(result.jsonSha256.orEmpty().length == 64)
        val markdown = Files.readString(result.markdownPath ?: error("missing markdown path"))
        val json = Files.readString(result.jsonPath ?: error("missing json path"))
        assertTrue(markdown.contains("ATROPOS Self-Host Evidence"))
        assertTrue(markdown.contains("sha256 `"), markdown)
        assertTrue(json.contains("\"goalId\""))
        assertTrue(json.contains("\"outputs\""))
        assertTrue(json.contains("\"sha256\""))
        assertTrue(json.contains("\"restartSnapshot\""))
        assertTrue(json.contains("\"evidenceHashes\""))
        assertTrue(json.contains("\"provenanceChainSha256\""))
        assertTrue(markdown.contains("provenance chain sha256:"))
        assertTrue(json.contains("\"attestationEvidence\""))
        assertTrue(json.contains("\"gateEvidence\""))
        assertTrue(json.contains("\"swapEvidence\""))
        assertTrue(json.contains("\"recoveryEvidence\""))
        assertTrue(json.contains("attest-123"))
        assertTrue(json.contains("messageSha256"))
        assertTrue(json.contains("\"nodes\""))
        assertTrue(json.contains("node-evidence"))
        assertTrue(!markdown.contains("plain-token"), markdown)
        assertTrue(!json.contains("plain-token"), json)
        assertTrue(markdown.contains("<redacted:secret>") || json.contains("<redacted:secret>"))
    }

    @Test
    fun an_installed_proof_claim_is_refused_when_a_load_bearing_part_is_missing() {
        // The bundle is still written — an operator debugging a failed proof has
        // to be able to read it — but it must not be mistakable for a proof.
        val root = Files.createTempDirectory("atropos-self-host-evidence-incomplete-")
        val store = GoalRunStore(root)
        val dagService = DagExecutionService(repoRoot = root)
        val goal = store.createGoalRun("incomplete proof", provider = "self-host")
        store.update(
            goal.copy(
                // A gate report and a swap, but no candidate build and no git status.
                evidence = listOf(
                    "promotion_gate canComplete=true",
                    "jar_swap promoted=true sha256=abc"
                )
            )
        )

        val result = SelfHostEvidenceBundleExporter(root, store, dagService).exportAsInstalledProof(goal.id)

        assertTrue(!result.ok, "an incomplete proof claim must be refused")
        assertEquals(SelfHostFailureCode.EVIDENCE_INCOMPLETE, result.failureCode)
        assertTrue(result.message.contains("CANDIDATE_BUILD"), result.message)
        assertTrue(result.message.contains("GIT_STATUS"), result.message)
        assertTrue(
            Files.isRegularFile(result.markdownPath ?: error("bundle must still be written")),
            "the bundle must remain readable after a refused claim"
        )
    }

    @Test
    fun an_installed_proof_claim_is_allowed_when_every_load_bearing_part_is_present() {
        val root = Files.createTempDirectory("atropos-self-host-evidence-complete-")
        val store = GoalRunStore(root)
        val dagService = DagExecutionService(repoRoot = root)
        val goal = store.createGoalRun("complete proof", provider = "self-host")
        store.update(
            goal.copy(
                evidence = listOf(
                    "candidate_jar_build ok=true proposal=p-1 candidate=ATROPOS.jar",
                    "promotion_gate canComplete=true",
                    "git_status_short ok=true exit=0 output=?? src/main/kotlin/atropos/Marker.kt",
                    "jar_swap promoted=true sha256=abc"
                )
            )
        )

        val result = SelfHostEvidenceBundleExporter(root, store, dagService).exportAsInstalledProof(goal.id)

        assertTrue(result.ok, result.message)
        val json = Files.readString(result.jsonPath ?: error("missing json path"))
        assertTrue(json.contains("\"installedProofComplete\": true"), json)
    }
}
