package atropos.core.agent

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AgentDaemonRootResolverTest {
    @Test
    fun walks_from_nested_directory_to_atropos_root_when_env_is_absent() {
        val root = Files.createTempDirectory("atropos-daemon-root-")
        Files.writeString(root.resolve("settings.gradle.kts"), "pluginManagement {}\n")
        Files.writeString(root.resolve("build.gradle.kts"), "plugins {}\n")
        Files.createDirectories(root.resolve("src/main/kotlin/atropos"))
        val nested = root.resolve("apps/specgraph-foundry").also { Files.createDirectories(it) }

        val resolved = AgentDaemonRootResolver.resolve(env = emptyMap(), userDir = nested)

        assertEquals(root.toAbsolutePath().normalize(), resolved)
    }

    @Test
    fun explicit_atropos_root_env_wins_over_user_dir() {
        val root = Files.createTempDirectory("atropos-daemon-env-root-")
        val other = Files.createTempDirectory("atropos-daemon-other-")

        val resolved = AgentDaemonRootResolver.resolve(
            env = mapOf("ATROPOS_ROOT" to root.toString()),
            userDir = other
        )

        assertEquals(root.toAbsolutePath().normalize(), resolved)
    }

    @Test
    fun daemon_process_launcher_rejects_jar_outside_repository_root() {
        val root = Files.createTempDirectory("atropos-daemon-launch-root-")
        val outside = Files.createTempFile("outside-daemon-", ".jar")
        val log = root.resolve("daemon.log")

        assertFailsWith<IllegalArgumentException> {
            AgentDaemonProcessLauncher().launchForeground(root, outside, log)
        }
    }

    @Test
    fun daemon_process_launcher_rejects_unlisted_lifecycle_tools() {
        assertFailsWith<IllegalArgumentException> {
            AgentDaemonProcessLauncher().runWakeTool("sh -c echo unsafe")
        }
    }
}
