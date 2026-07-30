package atropos.core.provider

import atropos.core.policy.BoundedProcessRunner
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalRootTest {
    @Test
    fun local_probe_uses_bounded_redacted_process_output() {
        val workspace = Files.createTempDirectory("atropos-local-probe-")
        val runner = BoundedProcessRunner { _, directory, _, _ ->
            ProcessBuilder("printf", "api_key=local-secret").directory(directory.toFile()).start()
        }

        val result = LocalToolchainProbe(workspace.toFile(), runner).probeGit()

        assertTrue(result.available)
        assertFalse(result.details.contains("local-secret"))
        assertTrue(result.details.contains("<redacted:secret>"))
    }

    @Test
    fun missing_local_probe_workspace_is_a_typed_unavailable_result() {
        val missing = Files.createTempDirectory("atropos-missing-probe-").resolve("missing")

        val result = LocalToolchainProbe(missing.toFile()).probeGit()

        assertFalse(result.available)
        assertTrue(result.details.contains("workspace directory missing"))
    }
}
