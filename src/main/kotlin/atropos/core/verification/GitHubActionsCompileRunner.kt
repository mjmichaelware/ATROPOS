/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import atropos.core.AtroposConfig
import atropos.core.policy.BoundedProcessRunner
import atropos.core.security.ContextPathExclusions
import atropos.core.security.SecretSinkKind
import atropos.core.security.SecretSinkMatrix
import atropos.core.thinking.Thinking
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * The compile gate's process seam, satisfied by GitHub Actions instead of a
 * local toolchain.
 *
 * ATROPOS's operator runs it from Termux on a phone, where `./gradlew
 * compileKotlin` exits 127 -- the command does not exist. A self-host run there
 * mutates source, cannot compile it, and correctly refuses to promote anything.
 * The engine is then permanently one step short of finishing its own loop on
 * the only machine available to it.
 *
 * This runs the same command on a machine that has the toolchain. It is
 * deliberately *not* a second compile gate: [GovernedCompileGate] remains the
 * single owner of proposing, policy-deciding and evidencing the compile, and
 * this satisfies the `processRunner` seam it already had. Swapping local for
 * remote changes where the command runs and nothing about who may ask for it.
 *
 * ## What it does
 *
 * 1. Snapshots the working tree -- including files the run has just created --
 *    into a commit, without touching the operator's index or worktree.
 * 2. Pushes that commit to a unique scratch ref.
 * 3. Dispatches the `compile gate` workflow against it.
 * 4. Waits for the run whose head SHA matches the snapshot, and reports its
 *    conclusion as an exit code.
 *
 * ## What it refuses to do
 *
 * Every prerequisite that is missing throws with a message naming it. It never
 * returns a zero exit code it did not read off a completed run, because the
 * only thing worse than a compile gate that cannot run is one that says the
 * compile passed when nothing compiled anything (AGENTS.md 0.6).
 *
 * The throw is deliberate rather than a non-zero exit: [GovernedCompileGate]
 * already distinguishes "the compile failed" from "the gate could not start",
 * and reporting a missing token as a compile failure would send an operator to
 * read Kotlin errors that do not exist.
 */
class GitHubActionsCompileRunner(
    private val repoRoot: Path,
    private val workflowFile: String = DEFAULT_WORKFLOW,
    private val branch: String = DEFAULT_BRANCH,
    private val token: String? = System.getenv("ATROPOS_GITHUB_TOKEN")
        ?: System.getenv("GITHUB_TOKEN")
        ?: System.getenv("GH_TOKEN"),
    private val pollInterval: Duration = Duration.ofSeconds(10),
    private val maximumWait: Duration = Duration.ofMinutes(20),
    processRunner: BoundedProcessRunner = BoundedProcessRunner(),
    /**
     * One git invocation, as a function, so the whole flow is testable without
     * a repository. The default routes through [BoundedProcessRunner] like
     * every other subprocess the engine runs, so a gate run is bounded and
     * narrated the same way a compile is.
     */
    private val runGit: (List<String>, Map<String, String>) -> GitResult = { command, environment ->
        val result = processRunner.run(
            command = command,
            directory = repoRoot,
            timeoutMillis = GIT_TIMEOUT_MILLIS,
            maxOutputBytes = 256 * 1024,
            maxOutputLines = 4_000,
            environment = environment
        )
        GitResult(
            exitCode = result.exitCode,
            stdout = result.stdout,
            failure = result.launchError ?: result.stderr
        )
    },
    private val sleeper: (Long) -> Unit = Thread::sleep,
    private val now: () -> Long = System::currentTimeMillis,
    private val http: (Request) -> Response = ::sendOverHttps
) : (List<String>, Path) -> GovernedCompileGate.CompileRun {

    /** A request, as data, so the whole flow is testable with no network. */
    data class Request(
        val method: String,
        val url: String,
        val token: String,
        val body: String? = null
    )

    data class Response(val status: Int, val body: String)

    data class GitResult(val exitCode: Int?, val stdout: String, val failure: String = "")

    override fun invoke(command: List<String>, directory: Path): GovernedCompileGate.CompileRun {
        val authorization = token?.takeIf(String::isNotBlank) ?: throw IllegalStateException(
            "no GitHub token: set ATROPOS_GITHUB_TOKEN (or GITHUB_TOKEN) to a token " +
                "with `actions: write` and `contents: write` on this repository"
        )
        val slug = resolveSlug()
        Thinking.step("ci-gate", "compiling on GitHub Actions for $slug")

        val snapshot = snapshotWorkingTree()
        Thinking.detail("ci-gate", "snapshot commit ${snapshot.take(12)} (working tree, including new files)")
        val snapshotBranch = "$branch/${snapshot.take(12)}"

        push(snapshot, snapshotBranch)
        Thinking.detail("ci-gate", "pushed snapshot to $snapshotBranch")

        dispatch(slug, authorization, snapshotBranch)
        val run = awaitRun(slug, authorization, snapshot, snapshotBranch)

        val conclusion = run.conclusion ?: "no conclusion"
        Thinking.step("ci-gate", "GitHub Actions concluded: $conclusion")
        return GovernedCompileGate.CompileRun(
            exitCode = if (conclusion == "success") 0 else 1,
            output = "github_actions_run=${run.url}\nhead_sha=$snapshot\nconclusion=$conclusion"
        )
    }

    // ----- git -------------------------------------------------------------

    /**
     * The working tree as a commit, built through a throwaway index.
     *
     * `git stash create` would be shorter and would silently omit untracked
     * files, which is precisely the content a self-host run has just written --
     * a new source file and its new test. Compiling a tree with the test
     * missing verifies the wrong thing.
     *
     * `GIT_INDEX_FILE` keeps `git add -A` out of the operator's real index, so
     * a gate run leaves `git status` exactly as it found it.
     */
    private fun snapshotWorkingTree(): String {
        val index = Files.createTempFile("atropos-compile-gate", ".index")
        Files.deleteIfExists(index)
        val environment = mapOf("GIT_INDEX_FILE" to index.toAbsolutePath().toString())
        return try {
            refuseExcludedChangedPaths()
            git(listOf("git", "add", "-A"), environment)
            val tree = git(listOf("git", "write-tree"), environment)
            val parent = git(listOf("git", "rev-parse", "HEAD"))
            git(
                listOf(
                    "git", "commit-tree", tree, "-p", parent,
                    "-m", "atropos compile gate snapshot"
                ),
                environment
            )
        } finally {
            Files.deleteIfExists(index)
        }
    }

    /** Never send a changed credential-shaped path to a hosted compiler. */
    private fun refuseExcludedChangedPaths() {
        val status = git(listOf("git", "status", "--porcelain=v1", "--untracked-files=all", "-z"))
        val excluded = status.split('\u0000')
            .asSequence()
            .filter { it.length > 3 }
            .map { it.substring(3) }
            .flatMap { path -> path.split(" -> ").asSequence() }
            .filter(ContextPathExclusions::isExcluded)
            .distinct()
            .toList()
        require(excluded.isEmpty()) {
            "compile gate snapshot refused excluded credential paths: ${excluded.joinToString(", ")}"
        }
    }

    private fun push(commit: String, snapshotBranch: String) {
        // The commit-derived ref makes concurrent gates independent and the
        // absence of --force preserves unrelated remote history.
        git(listOf("git", "push", "origin", "$commit:refs/heads/$snapshotBranch"))
    }

    private fun resolveSlug(): String {
        val remote = git(listOf("git", "remote", "get-url", "origin"))
        return SLUG_PATTERN.find(remote)?.groupValues?.get(1)?.removeSuffix(".git")
            ?: throw IllegalStateException("origin is not a GitHub remote: $remote")
    }

    private fun git(command: List<String>, environment: Map<String, String> = emptyMap()): String {
        val result = runGit(command, environment)
        if (result.exitCode != 0) {
            throw IllegalStateException(
                "${command.joinToString(" ")} exited ${result.exitCode}: " +
                    result.failure.trim().take(240)
            )
        }
        return result.stdout.trim()
    }

    // ----- GitHub ----------------------------------------------------------

    private fun dispatch(slug: String, authorization: String, snapshotBranch: String) {
        val response = http(
            Request(
                method = "POST",
                url = "$API/repos/$slug/actions/workflows/$workflowFile/dispatches",
                token = authorization,
                body = """{"ref":${quote(snapshotBranch)},"inputs":{"reason":"atropos compile gate"}}"""
            )
        )
        // 204 is the documented success. Anything else is worth reading, and a
        // 404 here almost always means the workflow file is not on the default
        // branch yet rather than that the repository is missing.
        if (response.status != 204) {
            throw IllegalStateException(
                "workflow dispatch returned ${response.status}: ${response.body.take(240)}"
            )
        }
    }

    private data class RunHandle(val id: Long, val url: String, val conclusion: String?)

    /**
     * Waits for the dispatched run, identified by the SHA it is building.
     *
     * Dispatch returns no run id, so the run has to be found. Matching on
     * `head_sha` rather than on "most recent" matters: two gate runs from the
     * same operator minutes apart would otherwise read each other's results.
     */
    private fun awaitRun(slug: String, authorization: String, headSha: String, snapshotBranch: String): RunHandle {
        val deadline = now() + maximumWait.toMillis()
        // Bounded by iterations as well as by the clock. A wall-clock bound is
        // the right one, and it is also the one that fails open if a clock
        // never advances -- which is exactly how the parse defect above turned
        // into an out-of-memory rather than a timeout. Two bounds, because the
        // cost of the second is one integer.
        val maximumPolls = (maximumWait.toMillis() / pollInterval.toMillis().coerceAtLeast(1))
            .coerceIn(1, MAXIMUM_POLLS.toLong())
            .toInt() + 1
        var polls = 0
        var seen: RunHandle? = null
        while (now() < deadline && polls < maximumPolls) {
            polls += 1
            val response = http(
                Request(
                    method = "GET",
                    url = "$API/repos/$slug/actions/workflows/$workflowFile/runs" +
                        "?branch=$snapshotBranch&per_page=20",
                    token = authorization
                )
            )
            if (response.status == 200) {
                val match = findRun(response.body, headSha)
                if (match != null) {
                    seen = match
                    if (match.conclusion != null) return match
                }
            }
            Thinking.detail(
                "ci-gate",
                if (seen == null) "waiting for the run to appear" else "run ${seen.id} still going"
            )
            sleeper(pollInterval.toMillis())
        }
        throw IllegalStateException(
            "GitHub Actions did not conclude within ${maximumWait.toMinutes()} minutes" +
                (seen?.let { " (run ${it.url})" } ?: " (no run ever appeared for $headSha)")
        )
    }

    /**
     * Finds this snapshot's run in the listing.
     *
     * Scanned rather than parsed. Bringing in a JSON library for four fields
     * would be a dependency the rest of the engine does not carry, and the
     * fields wanted here are flat scalars in a known response shape. The scan
     * is bounded to the object containing the matching `head_sha` so a field
     * belonging to a neighbouring run cannot be read as this one's.
     */
    private fun findRun(body: String, headSha: String): RunHandle? {
        val marker = body.indexOf("\"head_sha\":\"$headSha\"")
        if (marker < 0) return null
        val start = body.lastIndexOf("{\"id\":", marker).takeIf { it >= 0 } ?: 0
        val end = body.indexOf("\"head_sha\":", marker + 1).takeIf { it > marker } ?: body.length
        val slice = body.substring(start, end)

        val id = LONG_FIELD.find(slice)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        val url = field(slice, "html_url") ?: ""
        // `"conclusion":null` while the run is still going; a string once it is
        // not. Absent is the same answer as null and is treated the same way.
        val conclusion = field(slice, "conclusion")
        return RunHandle(id = id, url = url, conclusion = conclusion)
    }

    private fun field(slice: String, name: String): String? =
        Regex("\"$name\":\"([^\"]*)\"").find(slice)?.groupValues?.get(1)

    private fun quote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    companion object {
        const val DEFAULT_WORKFLOW = "compile-gate.yml"

        /**
         * A base for unique commit-derived refs.
         *
         * Not the operator's branch: a gate run is a snapshot of a tree
         * mid-mutation, and it has no business landing on a human branch. Each
         * invocation appends its commit prefix, so it never overwrites another
         * gate's ref and never needs a force push.
         */
        const val DEFAULT_BRANCH = "atropos/compile-gate"

        private const val API = "https://api.github.com"
        /**
         * The run id.
         *
         * Written as an escaped string rather than a raw one: in a raw literal
         * the pattern begins and ends with a quote, so `""""id":(\\d+)""""`
         * parses as `"id":(\\d+)"` -- with a trailing quote that never matches
         * `"id":42,`. Every run then failed to parse, `findRun` returned null
         * forever, and the poll loop spun until it exhausted the heap.
         */
        private val LONG_FIELD = Regex("\"id\":(\\d+)")

        /** `git@github.com:owner/repo` and `https://github.com/owner/repo` alike. */
        private val SLUG_PATTERN = Regex("""github\.com[:/]([^/\s]+/[^/\s]+)""")
        const val GIT_TIMEOUT_MILLIS = 120_000L

        /** Absolute cap on poll iterations, whatever the clock says. */
        const val MAXIMUM_POLLS = 10_000
    }
}

private fun sendOverHttps(request: GitHubActionsCompileRunner.Request): GitHubActionsCompileRunner.Response {
    require(URI.create(request.url).scheme.equals("https", ignoreCase = true)) {
        "GitHub Actions transport requires HTTPS"
    }
    check(!AtroposConfig.load().runtime.localOnly) {
        "GitHub Actions compile transport disabled by local-only mode"
    }
    check(SecretSinkMatrix.isEgressPermitted(SecretSinkKind.EGRESS_URL)) {
        "GitHub Actions network egress is not permitted by SecretSinkMatrix"
    }
    val builder = HttpRequest.newBuilder(URI.create(request.url))
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "Bearer ${request.token}")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .timeout(Duration.ofSeconds(60))
    when (request.method) {
        "GET" -> builder.GET()
        else -> builder.method(
            request.method,
            HttpRequest.BodyPublishers.ofString(request.body.orEmpty())
        )
    }
    val response = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()
        .send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
    val body = response.body().use { input ->
        input.readNBytes(MAX_RESPONSE_CHARS + 1).also {
            require(it.size <= MAX_RESPONSE_CHARS) {
                "GitHub Actions response exceeded $MAX_RESPONSE_CHARS characters"
            }
        }.toString(Charsets.UTF_8)
    }
    return GitHubActionsCompileRunner.Response(response.statusCode(), body)
}

private const val MAX_RESPONSE_CHARS = 1_024 * 1_024
