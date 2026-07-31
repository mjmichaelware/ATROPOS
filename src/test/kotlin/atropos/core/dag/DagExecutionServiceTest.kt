package atropos.core.dag

import atropos.core.ApiKeys
import atropos.core.AtroposConfig
import atropos.core.LakehouseConfig
import atropos.core.RuntimeConfig
import atropos.core.agent.AgentAskContextOverride
import atropos.core.agent.AgentContextCollector
import atropos.core.agent.AgentRunResult
import atropos.core.agent.AgentService
import atropos.core.memory.LocalMemoryStore
import atropos.core.planning.EvidenceReceipt
import atropos.core.planning.ExecutionEvidence
import atropos.core.planning.NodeClaim
import atropos.core.planning.NodeResult
import atropos.core.planning.PlanningGraphPlugin
import atropos.core.planning.ReadyNode
import atropos.core.planning.Territory
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    @Test
    fun provider_call_node_attaches_source_pack_and_node_envelope_to_provider_ask() {
        val repoRoot = Files.createTempDirectory("atropos-dag-provider-context-")
        val territory = "src/main/kotlin/atropos/core/agent"
        Files.createDirectories(repoRoot.resolve(territory))
        Files.writeString(
            repoRoot.resolve("$territory/SelfHostCradleRuntimeState.kt"),
            "package atropos.core.agent\n\nobject SelfHostCradleRuntimeState\n"
        )
        val now = Instant.parse("2026-07-27T09:12:00Z")
        val config = AtroposConfig(
            keys = ApiKeys(groq = "", openai = "", anthropic = "", xai = ""),
            lakehouse = LakehouseConfig(
                mountPath = repoRoot.resolve("lakehouse").toString(),
                dbPath = repoRoot.resolve("lakehouse/vector_storage.db").toString()
            ),
            runtime = RuntimeConfig(defaultProvider = "groq", temperature = 0.2)
        )
        val store = DagStore(repoRoot)
        val dag = store.createDag(
            label = "provider context",
            nodes = listOf(
                DagNode(
                    id = "provider-node",
                    label = "provider advisory",
                    territory = listOf(territory),
                    action = DagNodeAction.PROVIDER_CALL,
                    actionPayload = "inspect the self-host runtime state",
                    createdAt = now,
                    updatedAt = now,
                    metaFile = java.nio.file.Path.of("unused")
                )
            )
        )
        val original = dag.findNode("provider-node") ?: error("missing provider node")
        var capturedProvider = ""
        var capturedTask = ""
        var capturedOverride: AgentAskContextOverride? = null
        val executor = DagProviderNodeExecutor(
            repoRoot = repoRoot,
            agentService = AgentService(config = config, collector = AgentContextCollector(repoRoot = repoRoot)),
            memoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile()),
            finisher = DagNodeFinisher(FakePlanningGraphPlugin()),
            askProvider = { provider, task, override ->
                capturedProvider = provider
                capturedTask = task
                capturedOverride = override
                AgentRunResult(
                    providerName = provider,
                    answerText = "attested advisory",
                    contextByteCount = override.byteCount,
                    contextAttested = true,
                    sourcePackId = override.sourcePackId,
                    fetchReceiptId = override.fetchReceiptId
                )
            }
        )

        val result = executor.execute(original, original, store)
        val override = assertNotNull(capturedOverride)

        assertTrue(result.ok, result.message)
        assertTrue(result.result?.contains("sourcePack=${override.sourcePackId}") == true, result.result ?: "missing result")
        assertTrue(result.result?.contains("fetchReceipt=${override.fetchReceiptId}") == true, result.result ?: "missing result")
        assertEquals("groq", capturedProvider)
        assertEquals("inspect the self-host runtime state", capturedTask)
        assertEquals("provider-node", override.envelope.nodeId)
        assertEquals(dag.id, override.envelope.dagId)
        assertEquals(listOf(territory), override.envelope.assignedTerritory)
        assertEquals("self_host_provider_advisory_v1", override.envelope.activePolicy)
        assertTrue(override.sourcePackId?.startsWith("pack-") == true, override.sourcePackId ?: "missing pack")
        assertTrue(override.fetchReceiptId?.startsWith("fetch-") == true, override.fetchReceiptId ?: "missing fetch")
        assertTrue(override.contextText.contains("SOURCE_PACK_ID=${override.sourcePackId}"), override.contextText)
        assertTrue(override.contextText.contains("FILE $territory/SelfHostCradleRuntimeState.kt"), override.contextText)
    }

    @Test
    fun file_mutation_node_refuses_empty_content() {
        val repoRoot = Files.createTempDirectory("atropos-dag-file-empty-")
        val now = Instant.parse("2026-07-27T09:20:00Z")
        val service = DagExecutionService(repoRoot = repoRoot)
        val dag = service.createDag(
            label = "empty file mutation",
            nodes = listOf(
                DagNode(
                    id = "empty-file-node",
                    label = "empty file node",
                    territory = listOf("src/main/kotlin/atropos"),
                    action = DagNodeAction.CREATE_FILE,
                    actionPayload = "src/main/kotlin/atropos/Empty.kt::   ",
                    createdAt = now,
                    updatedAt = now,
                    metaFile = java.nio.file.Path.of("unused")
                )
            )
        )

        val result = service.evaluateNode(dag.id, "empty-file-node")

        assertTrue(!result.ok)
        assertEquals(DagNodeState.FAILED, result.state)
        assertTrue(result.message.contains("empty content"), result.message)
        assertTrue(!Files.exists(repoRoot.resolve("src/main/kotlin/atropos/Empty.kt")))
    }

    @Test
    fun shell_node_refuses_git_push_before_process_start() {
        val repoRoot = Files.createTempDirectory("atropos-dag-shell-policy-")
        val now = Instant.parse("2026-07-27T09:30:00Z")
        val store = DagStore(repoRoot)
        val dag = store.createDag(
            label = "shell policy",
            nodes = listOf(
                DagNode(
                    id = "push-node",
                    label = "forbidden push",
                    territory = listOf("src/main/kotlin/atropos"),
                    action = DagNodeAction.RUN_COMMAND,
                    actionPayload = "git push origin main",
                    createdAt = now,
                    updatedAt = now,
                    metaFile = java.nio.file.Path.of("unused")
                )
            )
        )
        val original = dag.findNode("push-node") ?: error("missing push node")
        val executor = DagNodeShellExecutor(
            repoRoot = repoRoot,
            store = store,
            finisher = DagNodeFinisher(FakePlanningGraphPlugin()),
            territoryViolation = { _, _ -> null },
            extractCandidatePaths = { emptyList() }
        )

        val result = executor.runCommand(original, original)

        assertTrue(!result.ok)
        assertEquals(DagNodeState.FAILED, result.state)
        assertTrue(result.message.contains("refused by policy"), result.message)
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

    private class FakePlanningGraphPlugin : PlanningGraphPlugin {
        override fun getReadyNodes(projectId: String, graphVersion: String): List<ReadyNode> = emptyList()
        override fun claimNode(nodeId: String, executorId: String, territory: Territory): NodeClaim =
            NodeClaim(true, nodeId, executorId, territory)

        override fun submitEvidence(nodeId: String, evidence: ExecutionEvidence): EvidenceReceipt =
            EvidenceReceipt(nodeId, true)

        override fun completeNode(nodeId: String, result: NodeResult) = Unit
    }
}
