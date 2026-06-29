/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.commands.VerifyCommand
import atropos.cli.commands.VerifyCommandHandler
import atropos.cli.session.QuotaSessionTracker
import atropos.cli.shell.ShellCommandRunner
import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.MarkdownRenderer
import atropos.core.AIProvider
import atropos.core.AtroposConfig
import atropos.core.ProviderDecisionEngine
import atropos.core.ProviderFactory
import atropos.core.endpoint.StaticOperationRegistry
import atropos.cli.ui.StatusEndpointRenderer

enum class RouterOutcome { CONTINUE, EXIT }

sealed class LexResult {
    data class Success(val tokens: List<String>) : LexResult()
    data class Error(val message: String) : LexResult()
}

class CommandRouter(
    private val config: AtroposConfig,
    private val uiEngine: AnsiTerminalEngine,
    private val sessionTracker: QuotaSessionTracker,
    private val providerResolver: (String) -> AIProvider = { ProviderFactory(config).getProvider(it) },
    private val rateResolver: (String) -> Double = { 0.0 },
    private val markdownRenderer: MarkdownRenderer = MarkdownRenderer(),
    private val verifyCommand: VerifyCommandHandler = VerifyCommand(uiEngine),
    private val shellRunner: ShellCommandRunner = ShellCommandRunner()
) {
    private var activeProvider = providerResolver(config.runtime.defaultProvider)

    var currentProviderName: String = activeProvider.name
        private set

    internal fun lex(input: String): LexResult {
        val tokens = mutableListOf<String>()
        val token = StringBuilder()
        var quote: Char? = null
        var started = false
        var index = 0

        while (index < input.length) {
            val ch = input[index]
            if (quote != null) {
                when {
                    ch == quote -> quote = null
                    ch == '\\' && quote == '"' -> {
                        index++
                        if (index >= input.length) return LexResult.Error("Trailing escape character")
                        token.append(input[index])
                        started = true
                    }
                    else -> {
                        token.append(ch)
                        started = true
                    }
                }
            } else {
                when {
                    ch.isWhitespace() -> {
                        if (started) {
                            tokens += token.toString()
                            token.clear()
                            started = false
                        }
                    }
                    ch == '\'' || ch == '"' -> {
                        quote = ch
                        started = true
                    }
                    ch == '\\' -> {
                        index++
                        if (index >= input.length) return LexResult.Error("Trailing escape character")
                        token.append(input[index])
                        started = true
                    }
                    else -> {
                        token.append(ch)
                        started = true
                    }
                }
            }
            index++
        }

        if (quote != null) return LexResult.Error("Unterminated quote")
        if (started) tokens += token.toString()
        return LexResult.Success(tokens)
    }


    private fun renderShell(result: atropos.cli.shell.ShellCommandResult) {
        uiEngine.renderNotice(shellRunner.render(result))
    }

    private fun runBangShell(original: String): RouterOutcome {
        val command = original.trimStart().removePrefix("!").trim()
        if (command.isBlank()) {
            uiEngine.renderError("usage: !<command>")
            return RouterOutcome.CONTINUE
        }

        return when (val result = lex(command)) {
            is LexResult.Error -> {
                uiEngine.renderError("shell lex: ${result.message}")
                RouterOutcome.CONTINUE
            }
            is LexResult.Success -> {
                renderShell(shellRunner.run(result.tokens))
                RouterOutcome.CONTINUE
            }
        }
    }

    private fun runShellTokens(tokens: List<String>): RouterOutcome {
        if (tokens.isEmpty()) {
            uiEngine.renderError("usage: /shell <command>")
        } else {
            renderShell(shellRunner.run(tokens))
        }
        return RouterOutcome.CONTINUE
    }

    private fun changeShellDirectory(tokens: List<String>): RouterOutcome {
        if (tokens.size > 2) {
            uiEngine.renderError("usage: /cd [directory]")
        } else {
            renderShell(shellRunner.changeDirectory(tokens.getOrNull(1)))
        }
        return RouterOutcome.CONTINUE
    }

    fun handleInput(input: String): RouterOutcome {
        if (input.isBlank()) return RouterOutcome.CONTINUE
        return when (val result = lex(input)) {
            is LexResult.Error -> {
                uiEngine.renderError("lex: ${result.message}")
                RouterOutcome.CONTINUE
            }
            is LexResult.Success -> route(input, result.tokens)
        }
    }

    private fun route(original: String, tokens: List<String>): RouterOutcome {
        if (tokens.isEmpty()) return RouterOutcome.CONTINUE
        if (original.trimStart().startsWith("!")) return runBangShell(original)

        return when (tokens.first().lowercase()) {
            "/exit", "/quit", "exit" -> RouterOutcome.EXIT

            "/pwd" -> {
                uiEngine.renderNotice("cwd: ${shellRunner.currentDirectory()}")
                RouterOutcome.CONTINUE
            }

            "/cd" -> changeShellDirectory(tokens)

            "/ls" -> {
                renderShell(shellRunner.list(tokens.drop(1)))
                RouterOutcome.CONTINUE
            }

            "/git" -> {
                if (tokens.getOrNull(1)?.lowercase() == "status" && tokens.size == 2) {
                    renderShell(shellRunner.gitStatus())
                } else {
                    uiEngine.renderError("usage: /git status")
                }
                RouterOutcome.CONTINUE
            }

            "/shell" -> runShellTokens(tokens.drop(1))

            "/help" -> {
                uiEngine.renderHelp()
                uiEngine.renderNotice("  /verify <narrow|wide>")
                uiEngine.renderNotice("  !<command> | /shell <command>")
                uiEngine.renderNotice("  /pwd | /cd [dir] | /ls [args] | /git status")
                RouterOutcome.CONTINUE
            }

            "/dashboard" -> {
                uiEngine.renderStatusMatrix(config, activeProvider.name)
                RouterOutcome.CONTINUE
            }

            "/status" -> {
                val statusRenderer = atropos.cli.ui.StatusQuotaRenderer()
                when (tokens.getOrNull(1)?.lowercase()) {
                    "endpoints" -> uiEngine.renderNotice(StatusEndpointRenderer(StaticOperationRegistry()).render())
                    "quota" -> uiEngine.renderNotice(statusRenderer.renderQuota())
                    "route" -> {
                        val task = tokens.drop(2).joinToString(" ").trim()
                        if (task.isBlank()) uiEngine.renderError("/status route requires a task")
                        else uiEngine.renderNotice(statusRenderer.renderRoute(task))
                    }
                    "failures" -> uiEngine.renderNotice(statusRenderer.renderFailures())
                    "adapters" -> uiEngine.renderNotice(atropos.cli.ui.StatusAdapterRenderer().render())
                    "memory" -> uiEngine.renderNotice(atropos.cli.ui.StatusMemoryRenderer().render())
                    "ci", "queue" -> uiEngine.renderNotice(atropos.cli.ui.StatusCiRenderer().render())
                    "assets" -> uiEngine.renderNotice(atropos.cli.ui.StatusAssetsRenderer().render())
                    "paid" -> uiEngine.renderNotice(atropos.cli.ui.StatusPaidEmergencyRenderer().render())
                    "factory" -> uiEngine.renderNotice(atropos.cli.ui.AppFactoryPlanRenderer().renderStatus())
                    "security" -> uiEngine.renderNotice(atropos.cli.ui.StatusSecurityRenderer().render())
                    "tests" -> uiEngine.renderNotice(atropos.cli.ui.TestMatrixRenderer().render())
                    "ops" -> uiEngine.renderNotice(atropos.cli.ui.StatusOpsRenderer().render())
                    null -> {
                        uiEngine.renderStatusMatrix(config, activeProvider.name)
                        uiEngine.renderNotice("usage ${sessionTracker.promptCount} prompts | ~${sessionTracker.estimatedTokens} tokens")
                        uiEngine.renderNotice(statusRenderer.renderDefaultStatusSummary())
                    }
                    else -> uiEngine.renderError("usage: /status [quota|route <task>|failures|adapters|assets|paid|factory|memory|ci|queue|security|tests|ops|endpoints]")
                }
                RouterOutcome.CONTINUE
            }

            "/providers" -> {
                when (tokens.getOrNull(1)?.lowercase()) {
                    "descriptors" -> uiEngine.renderNotice(
                        atropos.cli.ui.StatusProviderDescriptorRenderer(
                            atropos.core.provider.StaticProviderDescriptorRegistry()
                        ).render()
                    )
                    "validate" -> {
                        val violations = atropos.core.provider.ProviderDescriptorValidator(
                            atropos.core.provider.StaticProviderDescriptorRegistry()
                        ).validate()
                        if (violations.isEmpty()) {
                            uiEngine.renderNotice("PROVIDER DESCRIPTORS: VALID")
                        } else {
                            uiEngine.renderNotice("PROVIDER DESCRIPTORS: INVALID")
                            violations.forEach { uiEngine.renderNotice("  - ${it.id}: ${it.message}") }
                        }
                    }
                    else -> uiEngine.renderNotice(ProviderDecisionEngine().providersReport(config))
                }
                RouterOutcome.CONTINUE
            }

            "/paid" -> {
                val gate = atropos.core.paid.EmergencyPaidGate()
                when (tokens.getOrNull(1)?.lowercase()) {
                    null, "status" -> uiEngine.renderNotice(atropos.cli.ui.StatusPaidEmergencyRenderer(gate).render())
                    "unlock" -> {
                        val provider = tokens.getOrNull(2)
                        val duration = tokens.getOrNull(3)
                        val reason = tokens.drop(4).joinToString(" ").removePrefix("reason=").ifBlank { "manual emergency unlock" }
                        if (provider == null || duration == null) {
                            uiEngine.renderError("usage: /paid unlock <provider> <duration> reason=\"...\"")
                        } else {
                            try {
                                val unlock = gate.unlock(provider, duration, reason)
                                uiEngine.renderNotice("unlocked ${unlock.providerId} until ${unlock.expiresAtEpochMs}")
                            } catch (failure: IllegalArgumentException) {
                                uiEngine.renderError(failure.message ?: "paid unlock failed")
                            }
                        }
                    }
                    "lock" -> uiEngine.renderNotice(if (gate.lock()) "paid emergency locked" else "paid emergency already locked")
                    else -> uiEngine.renderError("usage: /paid [status|unlock <provider> <duration> reason=\"...\"|lock]")
                }
                RouterOutcome.CONTINUE
            }

            "/memory" -> {
                when (tokens.getOrNull(1)?.lowercase()) {
                    "remember" -> {
                        val title = tokens.getOrNull(2) ?: "note"
                        val body = tokens.drop(3).joinToString(" ").ifBlank { title }
                        val record = atropos.core.memory.LocalMemoryStore().remember(
                            atropos.core.memory.MemoryKind.NOTE,
                            title,
                            body,
                            listOf("cli")
                        )
                        uiEngine.renderNotice("memory remembered: ${record.id}")
                    }
                    "search" -> {
                        val hits = atropos.core.memory.LocalMemoryStore().search(tokens.drop(2).joinToString(" "))
                        uiEngine.renderNotice(atropos.cli.ui.StatusMemoryRenderer().renderSearch(hits))
                    }
                    else -> uiEngine.renderNotice(atropos.cli.ui.StatusMemoryRenderer().render())
                }
                RouterOutcome.CONTINUE
            }

            "/ci" -> {
                val queue = atropos.core.execution.LocalWorkQueue()
                when (tokens.drop(1).joinToString(" ").lowercase()) {
                    "local compile" -> uiEngine.renderNotice("queued local compile: ${queue.enqueueLocalCompile().id}")
                    "run next" -> {
                        val result = queue.runNext()
                        if (result == null) uiEngine.renderNotice("queue empty")
                        else uiEngine.renderNotice("job ${result.item.id} exit=${result.exitCode}\n${result.outputTail}")
                    }
                    else -> uiEngine.renderNotice(atropos.cli.ui.StatusCiRenderer().render())
                }
                RouterOutcome.CONTINUE
            }

            "/assets" -> {
                val generator = atropos.core.assets.LocalAssetGenerator()
                when (tokens.getOrNull(1)?.lowercase()) {
                    null, "status" -> uiEngine.renderNotice(atropos.cli.ui.StatusAssetsRenderer(generator).render())
                    "text", "ansi", "svg" -> {
                        val kind = when (tokens[1].lowercase()) {
                            "ansi" -> atropos.core.assets.AssetKind.ANSI
                            "svg" -> atropos.core.assets.AssetKind.SVG
                            else -> atropos.core.assets.AssetKind.TEXT
                        }
                        val name = tokens.getOrNull(2) ?: kind.name.lowercase()
                        val prompt = tokens.drop(3).joinToString(" ").ifBlank { name }
                        val artifact = generator.generate(atropos.core.assets.AssetRequest(kind, name, prompt, listOf("cli")))
                        uiEngine.renderNotice("asset written: ${artifact.file.path} bytes=${artifact.bytes}")
                    }
                    else -> uiEngine.renderError("usage: /assets [status|text|ansi|svg] <name> <prompt>")
                }
                RouterOutcome.CONTINUE
            }

            "/factory" -> {
                val renderer = atropos.cli.ui.AppFactoryPlanRenderer()
                when (tokens.getOrNull(1)?.lowercase()) {
                    null, "status" -> uiEngine.renderNotice(renderer.renderStatus())
                    "plan" -> {
                        val prompt = tokens.drop(2).joinToString(" ")
                        if (prompt.isBlank()) uiEngine.renderError("/factory plan requires a prompt")
                        else uiEngine.renderNotice(renderer.renderPlan(prompt))
                    }
                    "run" -> {
                        val prompt = tokens.drop(2).joinToString(" ")
                        if (prompt.isBlank()) {
                            uiEngine.renderError("/factory run requires a prompt")
                        } else {
                            uiEngine.renderNotice("factory run queued:")
                            uiEngine.renderNotice(renderer.renderRun(prompt))
                        }
                    }
                    else -> uiEngine.renderError("usage: /factory [status|plan|run] <prompt>")
                }
                RouterOutcome.CONTINUE
            }

            "/security" -> {
                when (tokens.getOrNull(1)?.lowercase()) {
                    "redact" -> uiEngine.renderNotice(atropos.cli.ui.StatusSecurityRenderer().renderRedaction(tokens.drop(2).joinToString(" ")))
                    null, "status" -> uiEngine.renderNotice(atropos.cli.ui.StatusSecurityRenderer().render())
                    else -> uiEngine.renderError("usage: /security [status|redact <text>]")
                }
                RouterOutcome.CONTINUE
            }

            "/keys" -> {
                when (tokens.getOrNull(1)?.lowercase()) {
                    null, "status" -> uiEngine.renderNotice(atropos.cli.ui.StatusSecurityRenderer().renderKeysStatus())
                    "setup" -> uiEngine.renderNotice(atropos.cli.ui.StatusSecurityRenderer().renderKeysSetup())
                    else -> uiEngine.renderError("usage: /keys [status|setup]")
                }
                RouterOutcome.CONTINUE
            }

            "/tests" -> {
                when (tokens.getOrNull(1)?.lowercase()) {
                    null, "matrix" -> uiEngine.renderNotice(atropos.cli.ui.TestMatrixRenderer().render())
                    else -> uiEngine.renderError("usage: /tests matrix")
                }
                RouterOutcome.CONTINUE
            }

            "/ops" -> {
                val renderer = atropos.cli.ui.StatusOpsRenderer()
                when (tokens.getOrNull(1)?.lowercase()) {
                    null, "status" -> uiEngine.renderNotice(renderer.render())
                    "export" -> uiEngine.renderNotice(renderer.export())
                    "verify" -> uiEngine.renderNotice(renderer.verify())
                    "quota-backup" -> uiEngine.renderNotice(renderer.quotaBackup())
                    "quota-restore" -> {
                        val path = tokens.getOrNull(2)
                        if (path == null) {
                            uiEngine.renderError("usage: /ops quota-restore <backup-file>")
                        } else {
                            uiEngine.renderNotice(renderer.quotaRestore(path))
                        }
                    }
                    else -> uiEngine.renderError("usage: /ops [status|export|verify|quota-backup|quota-restore <file>]")
                }
                RouterOutcome.CONTINUE
            }

            "/route" -> {
                val prompt = tokens.drop(1).joinToString(" ").trim()
                if (prompt.isBlank()) uiEngine.renderError("/route requires a prompt")
                else uiEngine.renderNotice(atropos.core.provider.adapter.AdapterRouteFacade().renderRoute(prompt))
                RouterOutcome.CONTINUE
            }

            "/use" -> {
                if (tokens.size == 2 && tokens[1].lowercase() == "auto") {
                    currentProviderName = "auto"
                    uiEngine.renderNotice("provider routing switched to auto")
                } else {
                    switchProvider(tokens)
                }
                RouterOutcome.CONTINUE
            }

            "/verify" -> {
                verifyCommand.execute(tokens)
                RouterOutcome.CONTINUE
            }

            "/swarm" -> {
                uiEngine.renderError("swarm endpoint is not bound")
                RouterOutcome.CONTINUE
            }

            else -> {
                if (tokens.first().startsWith("/")) uiEngine.renderError("unknown command: ${tokens.first()}")
                else dispatch(original)
                RouterOutcome.CONTINUE
            }
        }
    }

    private fun switchProvider(tokens: List<String>) {
        if (tokens.size != 2 || tokens[1].isBlank()) {
            uiEngine.renderError("/use requires exactly one provider")
            return
        }
        try {
            val resolved = providerResolver(tokens[1])
            activeProvider = resolved
            currentProviderName = resolved.name
            uiEngine.renderNotice("provider switched to ${resolved.name}")
        } catch (failure: RuntimeException) {
            uiEngine.renderError(failure.message ?: "provider switch failed")
        }
    }

    private fun dispatch(prompt: String) {
        sessionTracker.recordPrompt(prompt, rateResolver(activeProvider.name))
        uiEngine.startSpinner("Thinking")
        try {
            val routedProvider =
                if (currentProviderName.lowercase() == "auto") {
                    val decision = ProviderDecisionEngine().decide(prompt, config)
                    uiEngine.renderNotice("route: ${decision.taskClass.name.lowercase()} -> ${decision.provider} (${decision.reason})")
                    decision.provider
                } else {
                    currentProviderName
                }

            val provider = providerResolver(routedProvider)
            val response = provider.complete(prompt, "CLI Context")
            uiEngine.renderNotice(markdownRenderer.render(response))
        } catch (failure: Exception) {
            uiEngine.renderError(failure.message ?: "provider dispatch failed")
        } finally {
            uiEngine.stopSpinner()
        }
    }
}
