package atropos.core.agent

import java.nio.file.Files
import atropos.core.policy.BoundedProcessRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentRunRepoStatusTest {
    @Test
    fun failed_git_status_is_typed_instead_of_becoming_clean_baseline() {
        val root = Files.createTempDirectory("atropos-repo-status-")
        val status = AgentRunRepoStatus(root)

        val result = status.captureResult()

        assertTrue(!result.ok)
        assertEquals(AgentExecutionFailure.REPOSITORY_COMMAND_FAILED, result.failure)
        assertTrue(result.files.isEmpty())
    }

    @Test
    fun bounded_runner_launch_failure_is_not_reported_as_clean() {
        val root = Files.createTempDirectory("atropos-repo-status-launch-")
        val runner = BoundedProcessRunner { _, _, _, _ -> error("runner refusal") }

        val result = AgentRunRepoStatus(root, runner).captureResult()

        assertTrue(!result.ok)
        assertEquals(AgentExecutionFailure.LAUNCH_FAILED, result.failure)
        assertTrue(result.files.isEmpty())
    }
}
