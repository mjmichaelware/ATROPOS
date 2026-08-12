package atropos.core.provider

import atropos.core.policy.BoundedProcessRunner
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import atropos.core.AtroposRepoRootLocator

class LocalRootTest {
    @Test
    fun default_toolchain_workspace_uses_atropos_root() {
        val result = LocalToolchainProbe().probeWorkspace()

        assertEquals(AtroposRepoRootLocator.resolve().resolve("src/main/kotlin").toFile().exists(), result.available)
    }

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

    @Test
    fun local_state_events_remain_escaped_and_redacted() {
        val root = Files.createTempDirectory("atropos-local-state-").toFile()
        val store = LocalStateStore(root)

        store.appendEvent("events", "quoted=\"value\"\napi_key=sk-local-secret")

        val persisted = root.resolve("events.jsonl").readText()
        assertTrue(persisted.contains("\\\"value\\\""))
        assertTrue(persisted.contains("\\n"))
        assertFalse(persisted.contains("sk-local-secret"))
    }
}
