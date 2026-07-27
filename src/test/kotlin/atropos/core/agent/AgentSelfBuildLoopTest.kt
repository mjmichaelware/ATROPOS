package atropos.core.agent

import atropos.ast.AstSymbolGraph
import atropos.core.ApiKeys
import atropos.core.AtroposConfig
import atropos.core.LakehouseConfig
import atropos.core.RuntimeConfig
import atropos.core.memory.LocalMemoryStore
import atropos.dloi.DloiService
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentSelfBuildLoopTest {
    @Test
    fun unsafeSmokeRefusalPersistsRestartSafeFinalReportAndMemory() {
        val repoRoot = Files.createTempDirectory("atropos-agent-self-build-")
        val collector = AgentContextCollector(repoRoot = repoRoot)
        val memory = LocalMemoryStore(root = repoRoot.resolve(".atropos/memory").toFile(), env = emptyMap())
        val service = AgentRunService(
            config = AtroposConfig(
                keys = ApiKeys(groq = "", openai = "", anthropic = "", xai = ""),
                lakehouse = LakehouseConfig(
                    mountPath = repoRoot.resolve("lakehouse").toString(),
                    dbPath = repoRoot.resolve("lakehouse/vector_storage.db").toString()
                ),
                runtime = RuntimeConfig(defaultProvider = "groq", temperature = 0.2)
            ),
            collector = collector,
            jobStore = AgentJobStore(repoRoot),
            contextExporter = AgentContextExportStore(repoRoot),
            memoryStore = memory,
            dloiService = DloiService(repoRoot),
            astSymbolGraph = AstSymbolGraph(repoRoot)
        )

        val job = service.run(
            activeProviderName = "groq",
            task = "Phase 11 Self-Build Loop bounded refusal smoke",
            smokeCommand = "git push origin main"
        )

        assertEquals(AgentJobStatus.REFUSED, job.status)
        assertTrue(job.smokeResult.orEmpty().contains("refused"))
        assertTrue(job.finalReport.orEmpty().contains("status: refused"))
        assertTrue(job.finalReport.orEmpty().contains("source: unresolved"))
        assertTrue(job.finalReport.orEmpty().contains("changed files: none"))
        assertTrue(job.nextSuggestedCommand.orEmpty().contains("safe smoke command"))
        val contextExportPath = assertNotNull(job.contextExportPath)
        assertTrue(Files.isRegularFile(repoRoot.resolve(contextExportPath)))

        val reopenedJob = assertNotNull(AgentJobStore(repoRoot).resolve("latest"))
        assertEquals(job.id, reopenedJob.id)
        assertEquals(AgentJobStatus.REFUSED, reopenedJob.status)
        assertTrue(
            LocalMemoryStore(root = repoRoot.resolve(".atropos/memory").toFile(), env = emptyMap())
                .findBySubject("job", job.id)
                .any { it.body.contains("status=REFUSED") && it.body.contains("source=unresolved") }
        )
    }
}
