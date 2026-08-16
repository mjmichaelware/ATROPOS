/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import java.io.File
import atropos.cli.shell.ShellCommandRunner

object DependencyDeduplicator {
    fun deduplicate(dependencies: List<String>): List<String> {
        // Global dedupe of identical dependencies across projects
        return dependencies.distinct()
    }
}

object CloudDeploymentGuard {
    fun isRemoteDeploymentSecure(deploymentUrl: String, hasUiStrip: Boolean): Boolean {
        // Cloud-loophole closure remote checks
        if (hasUiStrip && !deploymentUrl.startsWith("https://")) {
            return false // UI-stripped remote hosting must be HTTPS/secure
        }
        return true
    }
}

object ShellCommandIntercept {
    fun intercept(command: String): String {
        // Native shell command intercept mapping bare commands to slash command routing
        return when {
            command.startsWith("git ") -> "/" + command.trim()
            command.startsWith("cd ") -> "/" + command.trim()
            command.startsWith("gh ") -> "/" + command.trim()
            else -> command
        }
    }
}

class PipedStreamRouter(
    private val shell: ShellCommandRunner = ShellCommandRunner()
) {
    /**
     * Executes two bounded argv stages. The legacy string API remains for the
     * CLI contract, but command strings are tokenized without a shell and the
     * returned text is actual stage output, never a fabricated success.
     */
    fun routePipedCommand(input: String, commandA: String, commandB: String): String {
        val results = routePipedCommands(input, listOf(commandA, commandB))
        return results.joinToString("\n") { result ->
            "stage=${result.command.firstOrNull().orEmpty()} exit=${result.exitCode} output=${result.output}"
        }
    }

    fun routePipedCommands(input: String, commands: List<String>): List<atropos.cli.shell.ShellCommandResult> {
        require(commands.size >= 2) { "pipeline requires at least two stages" }
        return shell.runPiped(input, commands.map(::tokenize))
    }

    private fun tokenize(command: String): List<String> {
        require(command.none { it in ";|&<>" }) { "pipeline command contains shell syntax" }
        return command.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    }
}
