/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The whole flow with no network and no git, because the point of the seams is
 * that a compile gate pointed at CI is testable without CI.
 */
class GitHubActionsCompileRunnerTest {

    private val repoRoot: Path = Files.createTempDirectory("atropos-ci-gate")

    /** Records what was asked of GitHub and answers with a canned listing. */
    private class FakeGitHub(
        private val runsBody: String,
        private val dispatchStatus: Int = 204
    ) {
        val requests = mutableListOf<GitHubActionsCompileRunner.Request>()

        fun handle(request: GitHubActionsCompileRunner.Request): GitHubActionsCompileRunner.Response {
            requests += request
            return if (request.method == "POST") {
                GitHubActionsCompileRunner.Response(dispatchStatus, "")
            } else {
                GitHubActionsCompileRunner.Response(200, runsBody)
            }
        }
    }

    private fun runsBody(sha: String, conclusion: String?) = """
        {"total_count":1,"workflow_runs":[{"id":42,"name":"compile gate",
        "html_url":"https://github.com/o/r/actions/runs/42",
        "conclusion":${conclusion?.let { "\"$it\"" } ?: "null"},
        "status":"completed","head_sha":"$sha"}]}
    """.trimIndent()

    private fun runner(
        github: FakeGitHub,
        sha: String = SHA,
        token: String? = "t0ken",
        slept: MutableList<Long> = mutableListOf(),
        clock: () -> Long = { 0L }
    ) = GitHubActionsCompileRunner(
        repoRoot = repoRoot,
        token = token,
        pollInterval = Duration.ofSeconds(1),
        maximumWait = Duration.ofSeconds(30),
        runGit = ScriptedGit(sha)::run,
        sleeper = slept::add,
        now = clock,
        http = github::handle
    )

    @Test
    fun a_successful_run_is_a_zero_exit_code() {
        val github = FakeGitHub(runsBody(SHA, "success"))

        val run = runner(github).invoke(listOf("github-actions"), repoRoot)

        assertEquals(0, run.exitCode)
        assertTrue(run.output.contains("conclusion=success"))
        assertTrue(run.output.contains("https://github.com/o/r/actions/runs/42"))
    }

    @Test
    fun a_failed_run_is_a_non_zero_exit_code() {
        val github = FakeGitHub(runsBody(SHA, "failure"))

        assertEquals(1, runner(github).invoke(listOf("github-actions"), repoRoot).exitCode)
    }

    @Test
    fun a_missing_token_is_refused_rather_than_reported_as_a_compile_failure() {
        // The gate distinguishes "the compile failed" from "the gate could not
        // start", and a missing token is the second. Returning a non-zero exit
        // here would send an operator to look for Kotlin errors that do not
        // exist (AGENTS.md 0.6).
        val failure = assertFailsWith<IllegalStateException> {
            runner(FakeGitHub(runsBody(SHA, "success")), token = null)
                .invoke(listOf("github-actions"), repoRoot)
        }

        assertTrue(failure.message.orEmpty().contains("ATROPOS_GITHUB_TOKEN"))
    }

    @Test
    fun a_blank_token_is_treated_as_no_token() {
        assertFailsWith<IllegalStateException> {
            runner(FakeGitHub(runsBody(SHA, "success")), token = "   ")
                .invoke(listOf("github-actions"), repoRoot)
        }
    }

    @Test
    fun it_waits_for_a_run_that_has_not_concluded() {
        val slept = mutableListOf<Long>()
        var ticks = 0L
        val github = FakeGitHub(runsBody(SHA, null))

        val failure = assertFailsWith<IllegalStateException> {
            runner(github, slept = slept, clock = { ticks += 10_000; ticks })
                .invoke(listOf("github-actions"), repoRoot)
        }

        assertTrue(failure.message.orEmpty().contains("did not conclude"))
        assertTrue(slept.isNotEmpty(), "a pending run must be polled, not read once")
    }

    @Test
    fun a_run_for_a_different_commit_is_not_read_as_this_one() {
        // Two gate runs minutes apart would otherwise read each other's
        // results, and the second would report the first one's conclusion.
        var ticks = 0L
        val github = FakeGitHub(runsBody("0000000000000000000000000000000000000000", "success"))

        val failure = assertFailsWith<IllegalStateException> {
            runner(github, clock = { ticks += 10_000; ticks }).invoke(listOf("github-actions"), repoRoot)
        }

        assertTrue(failure.message.orEmpty().contains("no run ever appeared"))
    }

    @Test
    fun a_rejected_dispatch_stops_the_gate() {
        val github = FakeGitHub(runsBody(SHA, "success"), dispatchStatus = 404)

        val failure = assertFailsWith<IllegalStateException> {
            runner(github).invoke(listOf("github-actions"), repoRoot)
        }

        assertTrue(failure.message.orEmpty().contains("404"))
    }

    @Test
    fun the_snapshot_is_pushed_before_the_workflow_is_dispatched() {
        val github = FakeGitHub(runsBody(SHA, "success"))
        val git = ScriptedGit(SHA)

        GitHubActionsCompileRunner(
            repoRoot = repoRoot,
            token = "t0ken",
            pollInterval = Duration.ofSeconds(1),
            maximumWait = Duration.ofSeconds(30),
            runGit = git::run,
            sleeper = {},
            now = { 0L },
            http = github::handle
        ).invoke(listOf("github-actions"), repoRoot)

        val commands = git.commands.map { it.joinToString(" ") }
        // `git add -A` through a throwaway index, so the operator's own index
        // is untouched -- and `commit-tree`, so untracked files a self-host run
        // has just written are in the snapshot.
        assertTrue(commands.any { it.startsWith("git add -A") }, commands.toString())
        assertTrue(commands.any { it.startsWith("git commit-tree") }, commands.toString())
        val push = commands.firstOrNull { it.startsWith("git push ") }
            ?: error("snapshot must be pushed")
        assertTrue(push.contains("refs/heads/atropos/compile-gate/$SHA"), push)
        assertTrue(!push.contains("--force"), push)
        val dispatch = github.requests.first { it.method == "POST" }
        assertTrue(dispatch.body.orEmpty().contains("atropos/compile-gate/$SHA"), dispatch.body)
        assertTrue(dispatch.body.orEmpty().contains("reason"), dispatch.body)
        assertTrue(
            git.environments.any { it.containsKey("GIT_INDEX_FILE") },
            "the snapshot must not stage into the operator's real index"
        )
    }

    @Test
    fun the_gate_defaults_to_local_and_only_moves_on_request() {
        // An existing install must not start pushing branches because a new
        // code path exists.
        assertEquals(
            listOf("./gradlew", "compileKotlin"),
            GovernedCompileGate.forRepository(repoRoot, emptyMap()).command
        )
        listOf("github", "ci", "actions", "GitHub").forEach { flag ->
            assertEquals(
                "github-actions",
                GovernedCompileGate.forRepository(
                    repoRoot,
                    mapOf(GovernedCompileGate.REMOTE_FLAG to flag)
                ).command.first(),
                "ATROPOS_COMPILE_GATE=$flag should move the compile off this machine"
            )
        }
        // Anything else keeps the local behaviour rather than guessing.
        assertEquals(
            listOf("./gradlew", "compileKotlin"),
            GovernedCompileGate.forRepository(repoRoot, mapOf(GovernedCompileGate.REMOTE_FLAG to "yes")).command
        )
    }

    private companion object {
        const val SHA = "abc1234567890abc1234567890abc1234567890a"
    }
}

/** A git that answers plausibly and records what it was asked. */
private class ScriptedGit(private val sha: String) {
    val commands = mutableListOf<List<String>>()
    val environments = mutableListOf<Map<String, String>>()

    fun run(
        command: List<String>,
        environment: Map<String, String>
    ): GitHubActionsCompileRunner.GitResult {
        commands += command
        environments += environment
        val stdout = when {
            command.contains("remote") -> "git@github.com:o/r.git"
            command.contains("write-tree") -> "tree0000"
            else -> sha
        }
        return GitHubActionsCompileRunner.GitResult(exitCode = 0, stdout = stdout)
    }
}
