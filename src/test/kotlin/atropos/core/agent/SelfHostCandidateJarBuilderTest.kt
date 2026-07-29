package atropos.core.agent

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelfHostCandidateJarBuilderTest {
    @Test
    fun bounded_build_command_creates_candidate_jar_evidence() {
        val root = Files.createTempDirectory("atropos-candidate-jar-builder-")
        val expected = root.resolve("build/libs/ATROPOS.jar")
        val builder = SelfHostCandidateJarBuilder(
            repoRoot = root,
            expectedJar = expected,
            processRunner = { _, _ ->
                Files.createDirectories(expected.parent)
                Files.writeString(expected, "jar bytes")
                SelfHostCandidateJarBuilder.CommandRun(0, "BUILD SUCCESSFUL")
            }
        )

        val result = builder.build("shg-builder")

        assertTrue(result.ok, result.message)
        assertEquals(expected, result.candidateJar)
        assertTrue(result.evidenceLine().contains("candidate_jar_build ok=true"))
    }

    @Test
    fun policy_refusal_stops_before_process_runner_executes() {
        val root = Files.createTempDirectory("atropos-candidate-jar-refused-")
        var ran = false
        val builder = SelfHostCandidateJarBuilder(
            repoRoot = root,
            command = listOf("curl", "https://example.invalid/build.jar"),
            processRunner = { _, _ ->
                ran = true
                SelfHostCandidateJarBuilder.CommandRun(0, "should not run")
            }
        )

        val result = builder.build("shg-builder")

        assertTrue(!result.ok)
        assertTrue(!ran, "policy-refused builder must not execute the process runner")
        assertTrue(result.message.contains("refused"), result.message)
    }
}
