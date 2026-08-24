/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.DiffContentParser
import atropos.cli.ui.DiffContentRenderer
import atropos.cli.ui.TerminalTheme
import atropos.cli.config.ConfigurationManager
import atropos.core.policy.BoundedProcessRunner
import java.nio.file.Path

/**
 * Handles the `/diff` command: shows the current worktree diff with
 * syntax-highlighted hunks using the themed diff renderer.
 *
 * Reads the actual git diff from the repository, parses it into the
 * typed DiffContent model, and renders it through DiffContentRenderer.
 *
 * Supports:
 * - `/diff` — full worktree diff (unstaged changes)
 * - `/diff --staged` — staged changes only
 * - `/diff <path>` — diff for a specific file
 * - `/diff --no-line-numbers` — suppress line-number gutters
 */
class DiffCommandHandler(
    private val uiEngine: AnsiTerminalEngine
) {
    private val theme = TerminalTheme(ConfigurationManager())
    private val parser = DiffContentParser()
    private val renderer = DiffContentRenderer(theme)
    private val processRunner = BoundedProcessRunner()

    fun execute(tokens: List<String>): RouterOutcome {
        val args = tokens.drop(1)

        val staged = "--staged" in args || "--cached" in args
        val noLineNumbers = "--no-line-numbers" in args || "--no-ln" in args
        val paths = args.filter { !it.startsWith("--") && it != "/diff" }

        val diffText = readGitDiff(staged, paths)

        if (diffText == null) {
            uiEngine.renderError("could not read git diff — not a git repository or git not available")
            return RouterOutcome.CONTINUE
        }

        if (diffText.isBlank()) {
            val scope = when {
                staged -> "staged"
                paths.isNotEmpty() -> paths.joinToString(", ")
                else -> "worktree"
            }
            uiEngine.renderNotice("no changes in $scope")
            return RouterOutcome.CONTINUE
        }

        val diffContent = parser.parse(diffText)
        val lines = renderer.render(
            diff = diffContent,
            width = uiEngine.viewportWidth,
            showLineNumbers = !noLineNumbers
        )

        uiEngine.renderBlock(lines)
        return RouterOutcome.CONTINUE
    }

    /**
     * Reads git diff output for the current directory.
     *
     * Returns null if git is not available or the working directory is
     * not a repository. Returns empty string if there are no changes.
     */
    private fun readGitDiff(staged: Boolean, paths: List<String>): String? {
        return try {
            val command = mutableListOf("git", "diff")
            if (staged) command += "--staged"
            command += "--no-color"
            command += "--"
            command += paths
            val result = processRunner.run(
                command = command,
                directory = Path.of("").toAbsolutePath().normalize(),
                timeoutMillis = 10_000,
                maxOutputBytes = 1_000_000,
                maxOutputLines = 20_000
            )
            val output = (result.stdout + result.stderr).trimEnd()
            if (result.exitCode != 0 && output.contains("not a git repository", ignoreCase = true)) {
                null
            } else {
                output
            }
        } catch (_: Exception) {
            null
        }
    }
}
