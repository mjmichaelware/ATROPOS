package atropos.core.agent

import java.nio.file.Files
import atropos.core.policy.BoundedProcessRunner
import kotlin.test.Test
import kotlin.test.assertTrue

class SelfHostGitStatusEvidenceTest {
    @Test
    fun traversal_status_scope_is_typed_as_refusal() {
        val root = Files.createTempDirectory("atropos-status-evidence-")

        val evidence = SelfHostGitStatusEvidence(root).capture(listOf("../outside"))

        assertTrue(evidence.contains("ok=false"))
        assertTrue(evidence.contains("failure=LAUNCH_FAILED"))
        assertTrue(!evidence.contains("outside"))
    }

    @Test
    fun bounded_runner_nonzero_status_is_evidence_failure() {
        val root = Files.createTempDirectory("atropos-status-evidence-failure-")
        val runner = BoundedProcessRunner { _, _, _, _ -> ProcessBuilder("false").start() }

        val evidence = SelfHostGitStatusEvidence(root, processRunner = runner).capture()

        assertTrue(evidence.contains("ok=false"))
        assertTrue(evidence.contains("failure=REPOSITORY_COMMAND_FAILED"))
    }
}
