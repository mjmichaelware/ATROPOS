package atropos.cli.commands

import kotlin.test.Test
import kotlin.test.assertTrue

class ArtifactPromoteCommandTest {
    @Test
    fun promoteJarRequiresExplicitVerificationEvidence() {
        val command = HierarchyCommand()

        val result = command.execute(listOf("/artifact", "promote-jar", "candidate.jar", "atropos.jar", "missing-ev"))

        assertTrue(result.startsWith("JAR promote refused"), result)
    }
}
