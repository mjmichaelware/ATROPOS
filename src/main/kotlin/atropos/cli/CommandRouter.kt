/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.commands.VerifyCommand
import atropos.cli.commands.VerifyCommandHandler
import atropos.cli.commands.AgentCommand
import atropos.cli.commands.HierarchyCommand
import atropos.cli.commands.ProjectCommandHandler
import atropos.cli.commands.SelfHostNaturalLanguageRouter
import atropos.cli.session.QuotaSessionTracker
import atropos.cli.session.SessionTabs
import atropos.cli.shell.ShellCommandRunner
import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.AIProvider
import atropos.core.AtroposConfig
import atropos.core.ProviderFactory

enum class RouterOutcome { CONTINUE, EXIT }

class CommandRouter(
    private val config: AtroposConfig,
    private val uiEngine: AnsiTerminalEngine,
    private val sessionTracker: QuotaSessionTracker,
    private val providerResolver: (String) -> AIProvider = { ProviderFactory(config).getProvider(it) },
    private val rateResolver: (String) -> Double = { 0.0 },
    private val verifyCommand: VerifyCommandHandler = VerifyCommand(uiEngine),
    private val shellRunner: ShellCommandRunner = ShellCommandRunner(),
    private val factoryCommandOverride: FactoryCommandHandler? = null,
    /** The only way this router reaches DLOI: failures arrive typed, not thrown. */
    private val higZeroGuard: atropos.dloi.HigZeroGuard = atropos.dloi.HigZeroGuard(atropos.dloi.DloiService())
) {
    /** A failing command renders an error; it must not end the session. */
    private val failureBoundary = CommandFailureBoundary(uiEngine)

    private var activeProvider = providerResolver(config.runtime.defaultProvider)

    var currentProviderName: String = activeProvider.name
        private set

    private val agentCommand = AgentCommand(
        ui = uiEngine,
        config = config,
        activeProviderName = { currentProviderName }
    )

    private val hierarchyCommand = HierarchyCommand()

    private val theme =
        atropos.cli.ui.TerminalTheme(atropos.cli.config.ConfigurationManager())

    private val dashboardRenderer = atropos.cli.ui.DashboardRenderer(theme)

    private val homeStateProvider = atropos.cli.ui.HomeStateProvider()

    private val projectCommand = ProjectCommandHandler(uiEngine)

    private val selfHostNaturalLanguageRouter = SelfHostNaturalLanguageRouter()
    private val statusCommand = StatusCommandHandler(config, uiEngine, sessionTracker)
    private val providerCommand = ProviderCommandHandler(config, uiEngine)
    private val dloiCommand = DloiCommandHandler(uiEngine, higZeroGuard)
    private val shellCommand = ShellCommandHandler(uiEngine, shellRunner)
    private val paidCommand = PaidCommandHandler(uiEngine)
    private val memoryCommand = MemoryCommandHandler(uiEngine)
    private val ciCommand = CiCommandHandler(uiEngine)
    private val assetCommand = AssetCommandHandler(uiEngine)
    private val factoryCommand = factoryCommandOverride ?: FactoryCommandHandler(uiEngine)
    private val naturalLanguageRiskGuard = NaturalLanguageRiskGuard()
    private var pendingRiskyNaturalLanguage: String? = null
    private val securityCommand = SecurityCommandHandler(uiEngine)
    private val keysCommand = KeysCommandHandler(uiEngine)
    private val authCommand = AuthCommandHandler(uiEngine)
    private val storageCommand = StorageCommandHandler(uiEngine)
    private val interruptCommand = InterruptCommandHandler(uiEngine)
    private val exportCommand = ExportCommandHandler(uiEngine)
    private val thinkingCommand = ThinkingCommandHandler(uiEngine)
    private val themeCommand = ThemeCommandHandler(uiEngine)
    private val testsCommand = TestsCommandHandler(uiEngine)
    private val opsCommand = OpsCommandHandler(uiEngine)
    private val routeCommand = RouteCommandHandler(uiEngine)
    private val astCommand = AstCommandHandler(uiEngine)
    private val providerChatDispatcher = ProviderChatDispatcher(
        config = config,
        uiEngine = uiEngine,
        sessionTracker = sessionTracker,
        providerResolver = providerResolver,
        rateResolver = rateResolver,
        cwd = shellCommand::currentDirectory
    )

    val tabs = SessionTabs(
        initialProvider = activeProvider.name,
        initialWorkingDirectory = shellCommand.currentDirectory()
    )

    private val tabCommand = TabCommandHandler(uiEngine, tabs) { currentProviderName }

    internal fun lex(input: String): LexResult = CommandLexer.lex(input)

    fun handleInput(input: String): RouterOutcome {
        if (input.isBlank()) return RouterOutcome.CONTINUE

        // A pasted block can hold more than one command. Handled here rather
        // than in the lexer because the lexer's job is one command's tokens,
        // and teaching it about command boundaries would make it depend on the
        // registry. See PastedInputSplitter for why a line only starts a new
        // command when its head is a registered family.
        val pasted = atropos.cli.input.PastedInputSplitter.split(input)
        if (pasted.size > 1) {
            uiEngine.renderNotice("Running ${pasted.size} pasted commands in order.")
            for (command in pasted) {
                if (handleSingleInput(command) == RouterOutcome.EXIT) return RouterOutcome.EXIT
            }
            return RouterOutcome.CONTINUE
        }
        return handleSingleInput(pasted.single())
    }

    private fun handleSingleInput(input: String): RouterOutcome {
        if (input.isBlank()) return RouterOutcome.CONTINUE
        pendingRiskyNaturalLanguage?.let { pending ->
            when (input.trim().lowercase()) {
                "y", "yes", "confirm", "confirmed" -> {
                    pendingRiskyNaturalLanguage = null
                    return handleInput(pending)
                }
                "n", "no", "cancel" -> {
                    pendingRiskyNaturalLanguage = null
                    uiEngine.renderNotice("request cancelled before risky execution")
                    return RouterOutcome.CONTINUE
                }
                else -> {
                    uiEngine.renderNotice("verification required: reply yes to continue or no to cancel")
                    return RouterOutcome.CONTINUE
                }
            }
        }
        return when (val result = lex(input)) {
            is LexResult.Error -> {
                uiEngine.renderError("lex: ${result.message}")
                RouterOutcome.CONTINUE
            }
            is LexResult.Success -> failureBoundary.guard(result.tokens.firstOrNull() ?: "command") {
                route(input, result.tokens)
            }
        }
    }

    private fun route(original: String, tokens: List<String>): RouterOutcome {
        if (tokens.isEmpty()) return RouterOutcome.CONTINUE
        if (original.trimStart().startsWith("!")) return shellCommand.bang(original)

        val first = tokens.first().lowercase()
        if (first in setOf("?", "/?", "help", "/help", "usage", "/usage")) {
            renderHelpPage(tokens.drop(1).joinToString(" "))
            return RouterOutcome.CONTINUE
        }

        if (first in setOf("/self-host", "self-host")) {
            selfHostAlias(tokens)
            return RouterOutcome.CONTINUE
        }

        return when (tokens.first().lowercase()) {
            "/exit", "/quit", "exit" -> RouterOutcome.EXIT

            "/pwd" -> shellCommand.pwd()

            "/cd" -> shellCommand.cd(tokens)

            "/ls" -> shellCommand.ls(tokens)

            "/git" -> shellCommand.git(tokens)

            "/shell" -> shellCommand.shell(tokens.drop(1))

            "/dashboard", "/home" -> {
                tabs.goHome()
                // §0.1: Home answers the six continuous questions without the
                // operator searching for them.
                uiEngine.renderBlock(
                    dashboardRenderer.render(
                        homeStateProvider.capture(activeProvider.name),
                        uiEngine.viewportWidth
                    )
                )
                uiEngine.renderDashboard(
                    activeProvider = activeProvider.name,
                    activeTab = "tab ${tabs.active.id}",
                    activeScreen = tabs.active.title,
                    openTabCount = tabs.snapshot().tabs.size
                )
                RouterOutcome.CONTINUE
            }

            "/project", "/projects" -> {
                // §2.2: every meaningful activity belongs to a project, and
                // that boundary is reachable from the terminal, not only the web.
                projectCommand.execute(tokens.drop(1))
                RouterOutcome.CONTINUE
            }

            "/tabs" -> tabCommand.list()

            "/tab" -> tabCommand.handle(tokens.drop(1))

            "/status" -> {
                statusCommand.execute(tokens, activeProvider.name)
                RouterOutcome.CONTINUE
            }

            "/providers" -> {
                providerCommand.execute(tokens, currentProviderName)
                RouterOutcome.CONTINUE
            }

            "/agent" -> {
                announce(agentCommand.execute(tokens))
                uiEngine.updateAgentPatchState(agentCommand.lastKnownPatchId)
                RouterOutcome.CONTINUE
            }

            "/paid" -> paidCommand.execute(tokens)

            "/memory" -> memoryCommand.execute(tokens)

            "/ci" -> ciCommand.execute(tokens)

            "/assets" -> assetCommand.execute(tokens)

            "/factory" -> factoryCommand.execute(tokens)

            "/verbose", "/debug" -> {
                uiEngine.toggleVerboseExecution()
                RouterOutcome.CONTINUE
            }

            "/security" -> securityCommand.execute(tokens)

            "/keys" -> keysCommand.execute(tokens)

            "/auth" -> authCommand.execute(tokens)

            "/storage", "/gc" -> storageCommand.execute(tokens)

            "/interrupt", "/pause", "/resume" -> interruptCommand.execute(tokens)

            "/export" -> exportCommand.execute(tokens)

            "/thinking" -> thinkingCommand.execute(tokens)

            "/theme" -> themeCommand.execute(tokens)

            "/tests" -> testsCommand.execute(tokens)

            "/ops" -> opsCommand.execute(tokens)

            "/route" -> routeCommand.execute(tokens)

            "/dloi" -> {
                dloiCommand.execute(tokens)
                RouterOutcome.CONTINUE
            }

            "/ast" -> astCommand.execute(tokens)

            "/use" -> {
                if (tokens.size == 2 && tokens[1].lowercase() == "auto") {
                    currentProviderName = "auto"
                    uiEngine.setProvider(currentProviderName)
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

            "/director", "/territory", "/hr", "/auditor", "/custodian", "/hierarchy", "/dag" -> {
                uiEngine.renderNotice(hierarchyCommand.execute(tokens))
                RouterOutcome.CONTINUE
            }

            "/snapshot", "/inspect", "/platform", "/artifact", "/autonomous" -> {
                uiEngine.renderNotice(hierarchyCommand.execute(tokens))
                RouterOutcome.CONTINUE
            }

            "/swarm" -> {
                uiEngine.renderError("swarm endpoint is not bound")
                RouterOutcome.CONTINUE
            }

            else -> {
                if (tokens.first().startsWith("/")) uiEngine.renderError("unknown command: ${tokens.first()}")
                else {
                    naturalLanguageRiskGuard.classify(original)?.let { risk ->
                        pendingRiskyNaturalLanguage = original
                        uiEngine.renderNotice(
                            "verification required before risky NL action (${risk.name.lowercase()}); " +
                                "reply yes to continue or no to cancel"
                        )
                        return RouterOutcome.CONTINUE
                    }
                    val selfHostTokens = selfHostNaturalLanguageRouter.route(tokens)
                    when {
                        selfHostTokens != null -> {
                            if (selfHostTokens.firstOrNull() == "/factory") {
                                factoryCommand.execute(selfHostTokens)
                            } else {
                                announce(agentCommand.execute(selfHostTokens))
                                uiEngine.updateAgentPatchState(agentCommand.lastKnownPatchId)
                            }
                        }
                        tokens.size == 1 && tokens.first().equals("ATROPOS", ignoreCase = true) -> {
                            announce(agentCommand.execute(listOf("/agent", "ask", "ATROPOS")))
                            uiEngine.updateAgentPatchState(agentCommand.lastKnownPatchId)
                        }
                        else -> providerChatDispatcher.dispatch(original, currentProviderName)
                    }
                }
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
            uiEngine.setProvider(resolved.name)
            uiEngine.renderNotice("provider switched to ${resolved.name}")
        } catch (failure: RuntimeException) {
            uiEngine.renderError(failure.message ?: "provider switch failed")
        }
    }

    /**
     * Shows a refusal the handler built but never printed.
     *
     * The single boundary where an unrendered [AgentCommandOutcome.Invalid]
     * becomes visible. Handlers that render their own richer output mark
     * themselves rendered and pass through here untouched.
     */
    private fun announce(outcome: atropos.cli.commands.AgentCommandOutcome) {
        if (outcome is atropos.cli.commands.AgentCommandOutcome.Invalid && !outcome.rendered) {
            uiEngine.renderError(outcome.message)
        }
    }

    private fun selfHostAlias(tokens: List<String>) {
        val remainder = tokens.drop(1)
        val subcommand = remainder.firstOrNull()?.lowercase()
        if (subcommand in setOf("help", "usage", "?", "/?", "/help", "/usage")) {
            renderHelpPage("self-host")
            return
        }
        val translated = SelfHostAliasTranslator.translate(tokens)
        if (translated == null) {
            // Used to `return` silently. A command the operator typed that
            // produces no output at all is indistinguishable from a broken
            // terminal, and they retype it rather than reading the answer that
            // was never printed.
            uiEngine.renderError(
                "'${tokens.joinToString(" ")}' is not a self-host command. " +
                    "Try '/self-host status' or '/help self-host'."
            )
            return
        }
        /* Bare shorthand is the operator's self-build command, not a read-only status query. */
        announce(agentCommand.execute(translated))
        uiEngine.updateAgentPatchState(agentCommand.lastKnownPatchId)
    }

    /**
     * `/help [summary|full|expert] [query]`.
     *
     * `SUP.UX.HELP-GENERATOR`: "Generate help text and tab-completion from
     * registry only. No hard-coded help strings remain."
     *
     * The list of commands that used to be typed out here is gone. It had
     * already drifted -- it named five families and the registry holds many
     * more -- which is precisely the failure the atom describes: a hand-written
     * help page is correct on the day it is written and wrong from the next
     * commit onward, and nothing ever tells you which lines went stale.
     */
    private fun renderHelpPage(query: String = "") {
        val words = query.trim().split(' ').filter { it.isNotBlank() }
        val level = atropos.cli.help.HelpLevel.fromCanonical(words.firstOrNull())
        val remainder = if (level.canonical.equals(words.firstOrNull(), ignoreCase = true)) {
            words.drop(1).joinToString(" ")
        } else {
            words.joinToString(" ")
        }

        // A search keeps the search renderer, which highlights matches; only
        // the browse path is generated.
        if (remainder.isNotBlank()) {
            uiEngine.renderHelp(remainder)
            return
        }
        atropos.cli.help.HelpGenerator().render(level).forEach(uiEngine::renderNotice)
    }

}
