/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import atropos.core.AtroposRepoRootLocator
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull

class WorkerCodeProposalServiceTest {

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
