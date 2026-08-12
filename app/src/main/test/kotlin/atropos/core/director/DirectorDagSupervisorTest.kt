/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.director

import atropos.core.AtroposRepoRootLocator
import atropos.core.dag.DagExecutionService
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull

class DirectorDagSupervisorTest {

    @Test
    fun testSuperviseBasic() {
        val tempDir = Files.createTempDirectory("director-test-").toFile()
        try {
            val supervisor = DirectorDagSupervisor(
                repoRoot = tempDir.toPath()
            )
            assertNotNull(supervisor)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
