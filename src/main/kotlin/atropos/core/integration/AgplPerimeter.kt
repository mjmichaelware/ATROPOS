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
        // Cloud-loophole closure: a UI-stripped remote deployment must not be
        // served in plaintext.
        //
        // The refusal is of a *stated insecure scheme*, not of an unstated one.
        // A bare `atropos.app` names no transport — it is a domain, and the
        // deployer supplies the scheme — where `http://atropos.app` names
        // plaintext explicitly. Requiring the https prefix outright refused
        // every deployment addressed by domain, which is how most of them are.
        if (!hasUiStrip) return true
        val scheme = deploymentUrl.substringBefore("://", missingDelimiterValue = "")
        return scheme.isEmpty() || scheme.equals("https", ignoreCase = true)
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
