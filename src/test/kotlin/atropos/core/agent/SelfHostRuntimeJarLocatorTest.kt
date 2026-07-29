package atropos.core.agent

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelfHostRuntimeJarLocatorTest {
    @Test
    fun resolves_explicit_candidate_and_installed_runtime_jars() {
        val root = Files.createTempDirectory("atropos-self-host-jar-locator-")
        val candidate = root.resolve("candidate.jar")
        val installed = root.resolve("installed.jar")
        Files.writeString(candidate, "candidate")
        Files.writeString(installed, "installed")
        val locator = SelfHostRuntimeJarLocator(
            repoRoot = root,
            env = mapOf(
                "ATROPOS_SELF_HOST_CANDIDATE_JAR" to candidate.toString(),
                "ATROPOS_INSTALLED_JAR" to installed.toString()
            )
        )

        val result = locator.resolve()

        assertTrue(result.ok, result.message)
        assertEquals(candidate, result.paths?.candidateJar)
        assertEquals(installed, result.paths?.targetJar)
    }

    @Test
    fun typed_stops_when_candidate_is_missing() {
        val root = Files.createTempDirectory("atropos-self-host-jar-locator-missing-")
        val installed = root.resolve("installed.jar")
        Files.writeString(installed, "installed")
        val result = SelfHostRuntimeJarLocator(
            repoRoot = root,
            env = mapOf("ATROPOS_INSTALLED_JAR" to installed.toString())
        ).resolve()

        assertTrue(!result.ok)
        assertTrue(result.message.contains("candidate jar unavailable"), result.message)
    }

    @Test
    fun default_candidate_uses_gradle_atropos_jar_name() {
        val root = Files.createTempDirectory("atropos-self-host-jar-locator-default-")
        val candidate = root.resolve("build/libs/ATROPOS.jar")
        val installed = root.resolve("installed.jar")
        Files.createDirectories(candidate.parent)
        Files.writeString(candidate, "candidate")
        Files.writeString(installed, "installed")

        val result = SelfHostRuntimeJarLocator(
            repoRoot = root,
            env = mapOf("ATROPOS_INSTALLED_JAR" to installed.toString())
        ).resolve()

        assertTrue(result.ok, result.message)
        assertEquals(candidate, result.paths?.candidateJar)
    }
}
