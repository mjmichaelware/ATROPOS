/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import atropos.cli.commands.AgentCommandOutcome
import atropos.cli.commands.AgentWorkerCommandHandler
import atropos.core.AtroposRepoRootLocator
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorkerCodeProposalServiceTest {

    @Test
    fun `worker command handler delegates invalid requests to the canonical proposal boundary`() {
        val tempDir = Files.createTempDirectory("worker-handler-test-")
        val handler = AgentWorkerCommandHandler(
            proposalService = WorkerCodeProposalService(repoRoot = tempDir),
            activeProviderName = { "local" },
            invalid = { AgentCommandOutcome.Invalid(it) }
        )

        val result = handler.propose(emptyList())
        assertTrue(result is AgentCommandOutcome.Invalid)
    }

    @Test
    fun testProposeBasic() {
        val tempDir = Files.createTempDirectory("worker-test-").toFile()
        try {
            val service = WorkerCodeProposalService(
                repoRoot = tempDir.toPath()
            )
            assertNotNull(service)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
