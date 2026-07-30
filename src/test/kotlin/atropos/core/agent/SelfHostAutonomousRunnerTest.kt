package atropos.core.agent

import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelfHostAutonomousRunnerTest {
    @Test
    fun natural_language_runner_advances_source_diff_then_typed_stops_when_jar_is_unavailable() {
        val root = Files.createTempDirectory("atropos-self-host-runner-")
        initializeGitRepo(root)
        val base = Instant.parse("2026-07-29T00:05:00Z")
        var tick = 0L
        val store = GoalRunStore(root, clock = { base.plusSeconds(tick++) })
        val service = SelfHostGoalService(repoRoot = root, store = store, clock = { base.plusSeconds(tick++) })
        val runner = SelfHostAutonomousRunner(
            service = service,
            jarLocator = SelfHostRuntimeJarLocator(root, env = emptyMap()),
            jarBuilder = null,
            gitStatusEvidence = SelfHostGitStatusEvidence(root)
        )

        val result = runner.run("make ATROPOS build itself from inside out", maxAdvances = 4)

        assertTrue(!result.ok)
        assertTrue(result.message.contains("stopped before jar promotion"), result.message)
        val record = result.goal?.record ?: error("missing goal")
        assertEquals(GoalTerminalCondition.EXTERNAL_INPUT_REQUIRED, record.terminalCondition)
        assertEquals(GoalRunStatus.BLOCKED, record.status)
        assertTrue(record.evidence.any { it.startsWith("jar_promotion_stop reason=candidate jar unavailable") })
        assertTrue(record.evidence.any { it.startsWith("git_status_short") && it.contains("exit=0") && it.contains("SelfHostCradleRuntimeState.kt") })
        assertTrue(record.evidence.any { it.startsWith("next_action kind=WAIT_EXTERNAL_INPUT") })
        assertTrue(record.evidence.any { it.startsWith("node_execution") && it.contains("worktree=") && it.contains("sha256=") })
        assertTrue(result.evidenceBundle?.ok == true)
        val marker = root.resolve("src/main/kotlin/atropos/core/agent/SelfHostCradleRuntimeState.kt")
        assertTrue(Files.readString(marker).contains("LAST_SELF_HOST_GOAL: String = \"${record.id}\""))
    }

    private fun initializeGitRepo(repoRoot: java.nio.file.Path) {
        ProcessBuilder("git", "init")
            .directory(repoRoot.toFile())
            .redirectErrorStream(true)
            .start()
            .waitFor()
        Files.createDirectories(repoRoot.resolve("src/main/kotlin/atropos"))
        Files.createDirectories(repoRoot.resolve("src/test/kotlin/atropos"))
        Files.writeString(repoRoot.resolve("src/main/kotlin/atropos/Main.kt"), "fun main() {}\n")
        ProcessBuilder("git", "config", "user.email", "atropos@example.invalid")
            .directory(repoRoot.toFile())
            .redirectErrorStream(true)
            .start()
            .waitFor()
        ProcessBuilder("git", "config", "user.name", "ATROPOS Test")
            .directory(repoRoot.toFile())
            .redirectErrorStream(true)
            .start()
            .waitFor()
        ProcessBuilder("git", "add", ".")
            .directory(repoRoot.toFile())
            .redirectErrorStream(true)
            .start()
            .waitFor()
        ProcessBuilder("git", "commit", "-m", "initial")
            .directory(repoRoot.toFile())
            .redirectErrorStream(true)
            .start()
            .waitFor()
    }
}
