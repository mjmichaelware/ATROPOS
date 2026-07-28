package atropos.core.dag

import atropos.core.ApiKeys
import atropos.core.AtroposConfig
import atropos.core.LakehouseConfig
import atropos.core.RuntimeConfig
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DagExecutionServiceTest {
    @Test
    fun evaluateNode_executes_only_the_selected_ready_node() {
        val repoRoot = Files.createTempDirectory("atropos-dag-single-node-")
        Files.createDirectories(repoRoot.resolve("src/main/kotlin/atropos"))
        Files.writeString(repoRoot.resolve("src/main/kotlin/atropos/Main.kt"), "fun main() {}\n")
        val now = Instant.parse("2026-07-27T09:00:00Z")
        val service = DagExecutionService(repoRoot = repoRoot)
        val dag = service.createDag(
            label = "single node execution",
            nodes = listOf(
                node("n1", now),
                node("n2", now)
            )
        )

        val result = service.evaluateNode(dag.id, "n1")
        val reloaded = service.readDag(dag.id) ?: error("missing DAG")

        assertTrue(result.ok, result.message)
        assertEquals(DagNodeState.COMPLETE, reloaded.findNode("n1")?.state)
        assertEquals(DagNodeState.READY, reloaded.findNode("n2")?.state)
    }

    @Test
    fun evaluateNode_refuses_a_selected_node_that_is_not_ready() {
        val repoRoot = Files.createTempDirectory("atropos-dag-single-node-not-ready-")
        Files.createDirectories(repoRoot.resolve("src/main/kotlin/atropos"))
        val now = Instant.parse("2026-07-27T09:05:00Z")
        val service = DagExecutionService(repoRoot = repoRoot)
        val dag = service.createDag(
            label = "single node dependency",
            nodes = listOf(
                node("dep", now),
                node("child", now, dependencies = listOf("dep"))
            )
        )

        val result = service.evaluateNode(dag.id, "child")

        assertTrue(!result.ok)
        assertEquals(DagNodeState.PENDING, result.state)
        assertTrue(result.message.contains("not ready"), result.message)
    }

    @Test
    fun provider_call_node_degrades_to_typed_block_when_no_attested_provider_result_exists() {
        val repoRoot = Files.createTempDirectory("atropos-dag-provider-degraded-")
        Files.createDirectories(repoRoot.resolve("src/main/kotlin/atropos"))
        Files.writeString(repoRoot.resolve("src/main/kotlin/atropos/Main.kt"), "fun main() {}\n")
        val now = Instant.parse("2026-07-27T09:08:00Z")
        val config = AtroposConfig(
            keys = ApiKeys(groq = "", openai = "", anthropic = "", xai = ""),
            lakehouse = LakehouseConfig(
                mountPath = repoRoot.resolve("lakehouse").toString(),
                dbPath = repoRoot.resolve("lakehouse/vector_storage.db").toString()
            ),
            runtime = RuntimeConfig(defaultProvider = "groq", temperature = 0.2)
        )
        val service = DagExecutionService(config = config, repoRoot = repoRoot)
        val dag = service.createDag(
            label = "provider degraded",
            nodes = listOf(
                DagNode(
                    id = "provider-node",
                    label = "provider advisory",
                    action = DagNodeAction.PROVIDER_CALL,
                    actionPayload = "inspect ATROPOS state",
                    createdAt = now,
                    updatedAt = now,
                    metaFile = java.nio.file.Path.of("unused")
                )
            )
        )

        val result = service.evaluateNode(dag.id, "provider-node")
        val reloaded = service.readDag(dag.id) ?: error("missing DAG")

        assertTrue(!result.ok)
        assertEquals(DagNodeState.BLOCKED, result.state)
        assertTrue(result.message.contains("degraded") || result.message.contains("fallback"), result.message)
        assertEquals(DagNodeState.BLOCKED, reloaded.findNode("provider-node")?.state)
    }

    private fun node(
        id: String,
        now: Instant,
        dependencies: List<String> = emptyList()
    ): DagNode = DagNode(
        id = id,
        label = id,
        dependencies = dependencies,
        territory = listOf("src/main/kotlin/atropos"),
        action = DagNodeAction.RUN_COMMAND,
        actionPayload = "ls src/main/kotlin/atropos",
        expectedOutputs = listOf("src/main/kotlin/atropos/Main.kt"),
        createdAt = now,
        updatedAt = now,
        metaFile = java.nio.file.Path.of("unused")
    )
}
