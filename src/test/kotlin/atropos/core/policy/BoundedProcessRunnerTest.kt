package atropos.core.policy

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BoundedProcessRunnerTest {
    @Test
    fun rejects_shell_sized_argument_vectors_before_launch() {
        var launched = false
        val runner = BoundedProcessRunner { _, _, _, _ ->
            launched = true
            error("launcher must not be reached")
        }

        assertFailsWith<IllegalArgumentException> {
            runner.run(
                command = List(65) { "arg" },
                directory = Files.createTempDirectory("atropos-bounded-runner-"),
                timeoutMillis = 1_000,
                maxOutputBytes = 1_024,
                maxOutputLines = 20
            )
        }
        assertTrue(!launched)
    }

    @Test
    fun refuses_missing_working_directory_before_launch() {
        var launched = false
        val runner = BoundedProcessRunner { _, _, _, _ ->
            launched = true
            error("launcher must not be reached")
        }

        assertFailsWith<IllegalArgumentException> {
            runner.run(
                command = listOf("git", "status"),
                directory = Path.of("/definitely/missing/atropos-cwd"),
                timeoutMillis = 1_000,
                maxOutputBytes = 1_024,
                maxOutputLines = 20
            )
        }
        assertTrue(!launched)
    }

    @Test
    fun passes_literal_arguments_and_environment_without_shell_interpolation() {
        var observed: List<String> = emptyList()
        var observedEnvironment: Map<String, String> = emptyMap()
        val runner = BoundedProcessRunner { command, _, environment, _ ->
            observed = command
            observedEnvironment = environment
            ProcessBuilder("true").start()
        }

        val result = runner.run(
            command = listOf("git", "status", "--", "$(touch SHOULD_NOT_EXIST)"),
            directory = Files.createTempDirectory("atropos-bounded-runner-"),
            timeoutMillis = 1_000,
            maxOutputBytes = 1_024,
            maxOutputLines = 20,
            environment = mapOf("ATROPOS_TEST_ENV" to "bounded")
        )

        assertEquals(0, result.exitCode)
        assertEquals("$(touch SHOULD_NOT_EXIST)", observed.last())
        assertEquals("bounded", observedEnvironment["ATROPOS_TEST_ENV"])
    }

    @Test
    fun silences_stty_and_non_narrated_probes_from_thinking() {
        var eventCount = 0
        atropos.core.thinking.Thinking.subscribe { _ -> eventCount++ }

        val runner = BoundedProcessRunner { _, _, _, _ ->
            ProcessBuilder("true").start()
        }

        val initialEvents = eventCount
        runner.run(
            command = listOf("stty", "size"),
            directory = Files.createTempDirectory("atropos-stty-"),
            timeoutMillis = 1_000,
            maxOutputBytes = 1_024,
            maxOutputLines = 20
        )
        assertEquals(initialEvents, eventCount, "stty command was narrated to thinking!")

        runner.run(
            command = listOf("git", "status"),
            directory = Files.createTempDirectory("atropos-narrate-false-"),
            timeoutMillis = 1_000,
            maxOutputBytes = 1_024,
            maxOutputLines = 20,
            narrate = false
        )
        assertEquals(initialEvents, eventCount, "narrate=false command was narrated to thinking!")
    }
}
