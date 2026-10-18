/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.shell.ShellCommandResult
import atropos.cli.shell.ShellCommandRunner
import atropos.cli.ui.AnsiTerminalEngine

class ShellCommandHandler(
    private val uiEngine: AnsiTerminalEngine,
    private val shellRunner: ShellCommandRunner
) {
    fun currentDirectory(): String = shellRunner.currentDirectory()

    private val memoryLattice = atropos.core.memory.MemoryLayerLattice()

    fun bang(original: String): RouterOutcome {
        val command = original.trimStart().removePrefix("!").trim()
        if (command.isBlank()) {
            uiEngine.renderError("usage: !<command>")
            return RouterOutcome.CONTINUE
        }

        val config = atropos.core.config.AtroposUnifiedConfig.load()
        val permission = config.get("Tool_Permission")

        // 1. Strict mode blocks all shell inject
        if (permission.equals("strict", ignoreCase = true)) {
            uiEngine.renderError("shell inject (!) blocked by Tool_Permission = strict policy gate")
            emitPolicyReceipt("SHELL_INJECT_BLOCKED", command, "strict_policy")
            return RouterOutcome.CONTINUE
        }

        // 2. High-risk command pattern gating
        val dangerous = listOf("rm -rf", "sudo", "mkfs", "dd if=", ":(){ :|:& };:", "chmod -R 777")
        val isDangerous = dangerous.any { command.contains(it) }
        if (isDangerous) {
            uiEngine.renderError("shell inject (!) refused: command matches dangerous destructive invariant")
            emitPolicyReceipt("SHELL_INJECT_REFUSED_DANGEROUS", command, "high_risk_pattern")
            return RouterOutcome.CONTINUE
        }

        return when (val result = CommandLexer.lex(command)) {
            is LexResult.Error -> {
                uiEngine.renderError("shell lex: ${result.message}")
                RouterOutcome.CONTINUE
            }
            is LexResult.Success -> {
                emitPolicyReceipt("SHELL_INJECT_EXECUTED", command, "policy_cleared")
                render(shellRunner.run(result.tokens))
                RouterOutcome.CONTINUE
            }
        }
    }

    private fun emitPolicyReceipt(action: String, command: String, decision: String) {
        val timestamp = System.currentTimeMillis()
        val payload = "$timestamp:$action:$command:$decision"
        val sha256 = atropos.core.memory.MemoryLayerLattice.sha256(payload)
        val json = "{\"timestamp\":$timestamp,\"sha256\":\"$sha256\",\"action\":\"$action\"," +
            "\"actor\":\"ShellCommandHandler\",\"surface\":\"CLI\",\"before\":\"$command\",\"after\":\"$decision\"}"
        memoryLattice.put(
            layer = atropos.core.memory.MemoryLayerLattice.LayerType.EVIDENCE_LAYER,
            key = "shell_policy_$timestamp",
            content = json
        )
    }

    fun pwd(): RouterOutcome {
        uiEngine.renderNotice("cwd: ${shellRunner.currentDirectory()}")
        return RouterOutcome.CONTINUE
    }

    fun cd(tokens: List<String>): RouterOutcome {
        if (tokens.size > 2) {
            uiEngine.renderError("usage: /cd [directory]")
        } else {
            render(shellRunner.changeDirectory(tokens.getOrNull(1)))
        }
        return RouterOutcome.CONTINUE
    }

    fun ls(tokens: List<String>): RouterOutcome {
        render(shellRunner.list(tokens.drop(1)))
        return RouterOutcome.CONTINUE
    }

    fun git(tokens: List<String>): RouterOutcome {
        when {
            tokens.getOrNull(1)?.lowercase() == "status" && tokens.size == 2 -> render(shellRunner.gitStatus())
            tokens.getOrNull(1)?.lowercase() == "diff" && tokens.size == 2 -> render(shellRunner.gitDiff())
            tokens.getOrNull(1)?.lowercase() == "conflicts" && tokens.size == 2 -> render(shellRunner.gitConflicts())
            tokens.getOrNull(1)?.lowercase() in setOf("add", "commit", "rebase-continue") ->
                when (val parsed = GitMutationCommandParser.parse(tokens)) {
                    is GitMutationParse.Accepted -> render(shellRunner.runGitMutation(parsed.command, parsed.targetPaths))
                    is GitMutationParse.Refused -> uiEngine.renderError(parsed.message)
                }
            else -> uiEngine.renderError("usage: /git [status|diff|conflicts|add|commit|rebase-continue]")
        }
        return RouterOutcome.CONTINUE
    }

    fun shell(args: List<String>): RouterOutcome {
        if (args.isEmpty()) {
            uiEngine.renderError("usage: /shell <command>")
        } else {
            render(shellRunner.run(args))
        }
        return RouterOutcome.CONTINUE
    }

    private fun render(result: ShellCommandResult) {
        uiEngine.renderNotice(shellRunner.render(result))
    }
}
