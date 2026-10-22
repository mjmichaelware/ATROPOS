/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.benchmark

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import atropos.core.github.GitHubActionsCheckDispatcher

class RemoteBenchmarkOrchestratorTest {

    @Test
    fun `generates valid workflow dispatch and triggers remote batch`() {
        val tempDir = Files.createTempDirectory("benchmark-orch-test")
        val dispatcher = GitHubActionsCheckDispatcher { "dummy-token" }
        val orchestrator = RemoteBenchmarkOrchestrator(dispatcher, tempDir)

        val matrix = BenchmarkMatrixJob(
            batchId = "batch-01",
            instanceSlice = listOf("sympy__sympy-11438", "astropy__astropy-11693"),
            providerConfig = "gemini-flash",
            maxConcurrency = 4
        )

        val dispatchJson = orchestrator.generateWorkflowDispatch(matrix)
        assertTrue(dispatchJson.contains("swe_bench_matrix_batch"))
        assertTrue(dispatchJson.contains("sympy__sympy-11438"))

        val runId = orchestrator.triggerRemoteBatch(matrix)
        assertTrue(runId > 0)

        val progress = orchestrator.pollBatchProgress(runId)
        assertNotNull(progress)

        val cert = orchestrator.aggregateEvaluationReceipts(runId)
        assertNotNull(cert)
        assertTrue(cert.mutationScoreHash.contains("0.98"))

        tempDir.toFile().deleteRecursively()
    }
}
