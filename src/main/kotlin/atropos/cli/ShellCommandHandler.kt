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

    fun bang(original: String): RouterOutcome {
        val command = original.trimStart().removePrefix("!").trim()
        if (command.isBlank()) {
            uiEngine.renderError("usage: !<command>")
            return RouterOutcome.CONTINUE
        }

        return when (val result = CommandLexer.lex(command)) {
            is LexResult.Error -> {
                uiEngine.renderError("shell lex: ${result.message}")
                RouterOutcome.CONTINUE
            }
            is LexResult.Success -> {
                render(shellRunner.run(result.tokens))
                RouterOutcome.CONTINUE
            }
        }
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
            else -> uiEngine.renderError("usage: /git [status|diff]")
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
