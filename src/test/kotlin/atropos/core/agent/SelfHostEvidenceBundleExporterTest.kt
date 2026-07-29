package atropos.core.agent

import atropos.core.dag.DagExecutionService
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.recovery.RestartCoordinator
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

class SelfHostEvidenceBundleExporterTest {
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
                evidence = listOf("provider token=plain-token", "jar sha256=abc123")
            )
        )
        RestartCoordinator(root, goalRunStore = store).snapshot()
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
        assertTrue(json.contains("\"nodes\""))
        assertTrue(json.contains("node-evidence"))
        assertTrue(!markdown.contains("plain-token"), markdown)
        assertTrue(!json.contains("plain-token"), json)
        assertTrue(markdown.contains("<redacted:secret>") || json.contains("<redacted:secret>"))
    }
}
